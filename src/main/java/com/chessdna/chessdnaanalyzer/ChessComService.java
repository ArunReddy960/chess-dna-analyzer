package com.chessdna.chessdnaanalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChessComService implements ChessPlatformService {

    private static final int MAX_ATTEMPTS = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PgnGameParser pgnParser;
    private final String apiBaseUrl;
    private final String userAgent;
    private final Sleeper sleeper;
    private final Map<String, CachedResponse> responseCache = new ConcurrentHashMap<>();

    @Autowired
    public ChessComService(
            ObjectMapper objectMapper,
            PgnGameParser pgnParser,
            @Value("${chess-com.api.base-url:https://api.chess.com/pub}") String apiBaseUrl,
            @Value("${chess-com.api.user-agent:KnightLens/1.0 (https://knightlens.co)}") String userAgent) {
        this(new RestTemplate(), objectMapper, pgnParser, apiBaseUrl, userAgent, Thread::sleep);
    }

    ChessComService(RestTemplate restTemplate,
                    ObjectMapper objectMapper,
                    PgnGameParser pgnParser,
                    String apiBaseUrl,
                    String userAgent,
                    Sleeper sleeper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.pgnParser = pgnParser;
        this.apiBaseUrl = apiBaseUrl.replaceAll("/$", "");
        this.userAgent = userAgent;
        this.sleeper = sleeper;
    }

    @Override
    public ChessPlatform platform() {
        return ChessPlatform.CHESS_COM;
    }

    @Override
    public List<ChessGame> fetchGames(String username, int gameCount) {
        validateRequest(username, gameCount);

        String archivesJson = getJson(apiBaseUrl + "/player/" + username + "/games/archives");
        List<String> archives = readArchiveUrls(archivesJson);
        Collections.reverse(archives);

        List<ChessGame> games = new ArrayList<>();
        for (String archiveUrl : archives) {
            validateArchiveUrl(archiveUrl);
            JsonNode archive = readJson(getJson(archiveUrl));
            List<JsonNode> archiveGames = new ArrayList<>();
            archive.path("games").forEach(archiveGames::add);
            Collections.reverse(archiveGames);
            for (JsonNode gameNode : archiveGames) {
                String pgn = gameNode.path("pgn").asText("");
                if (pgn.isBlank()) continue;

                String sourceUrl = gameNode.path("url").asText(null);
                try {
                    games.add(pgnParser.toGame(username, platform(), pgn, sourceUrl));
                } catch (GameProviderException ignored) {
                    // Ignore malformed or unrelated records instead of failing the entire archive.
                }

                if (games.size() == gameCount) {
                    return games;
                }
            }
        }
        return games;
    }

    List<String> readArchiveUrls(String json) {
        JsonNode root = readJson(json);
        List<String> urls = new ArrayList<>();
        for (JsonNode archive : root.path("archives")) {
            if (archive.isTextual()) urls.add(archive.asText());
        }
        return urls;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new GameProviderException("Chess.com returned an unexpected response.", e);
        }
    }

    private String getJson(String url) {
        CachedResponse cached = responseCache.get(url);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("User-Agent", userAgent);
            if (cached != null) {
                if (cached.etag() != null) headers.setIfNoneMatch(cached.etag());
                if (cached.lastModified() > 0) headers.setIfModifiedSince(cached.lastModified());
            }

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

                if (response.getStatusCode().value() == 304 && cached != null) {
                    return cached.body();
                }

                String body = response.getBody();
                if (body == null || body.isBlank()) {
                    throw new GameProviderException("Chess.com returned an empty response.");
                }

                responseCache.put(url, new CachedResponse(
                        body,
                        response.getHeaders().getETag(),
                        response.getHeaders().getLastModified()));
                return body;
            } catch (HttpClientErrorException.NotFound e) {
                throw new GameProviderException("Chess.com username or game archive was not found.", e);
            } catch (HttpStatusCodeException e) {
                if (!isRetryable(e.getStatusCode()) || attempt == MAX_ATTEMPTS) {
                    throw new GameProviderException(
                            "Chess.com request failed with status " + e.getStatusCode().value() + ".", e);
                }
                sleepBeforeRetry(e.getResponseHeaders(), attempt);
            }
        }
        throw new GameProviderException("Chess.com request failed after retries.");
    }

    private boolean isRetryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private void sleepBeforeRetry(HttpHeaders headers, int attempt) {
        long delayMillis = Duration.ofSeconds(1L << (attempt - 1)).toMillis();
        if (headers != null && headers.getFirst("Retry-After") != null) {
            try {
                delayMillis = Duration.ofSeconds(Long.parseLong(headers.getFirst("Retry-After"))).toMillis();
            } catch (NumberFormatException ignored) {
                // Retry-After can also be an HTTP date; exponential backoff remains safe.
            }
        }
        try {
            sleeper.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GameProviderException("Chess.com retry was interrupted.", e);
        }
    }

    private void validateArchiveUrl(String value) {
        try {
            URI uri = URI.create(value);
            URI baseUri = URI.create(apiBaseUrl);
            if (!uri.getScheme().equalsIgnoreCase(baseUri.getScheme())
                    || !uri.getHost().equalsIgnoreCase(baseUri.getHost())
                    || !uri.getPath().startsWith("/pub/player/")) {
                throw new GameProviderException("Chess.com returned an invalid archive URL.");
            }
        } catch (IllegalArgumentException e) {
            throw new GameProviderException("Chess.com returned an invalid archive URL.", e);
        }
    }

    private void validateRequest(String username, int gameCount) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (gameCount < 1 || gameCount > 50) {
            throw new IllegalArgumentException("Game count must be between 1 and 50.");
        }
    }

    record CachedResponse(String body, String etag, long lastModified) {}

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
