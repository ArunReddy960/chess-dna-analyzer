package com.chessdna.chessdnaanalyzer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class LichessService implements ChessPlatformService {

    private static final String API_URL = "https://lichess.org/api/games/user/";

    private final RestTemplate restTemplate;
    private final PgnGameParser pgnParser;

    @Autowired
    public LichessService(PgnGameParser pgnParser) {
        this(new RestTemplate(), pgnParser);
    }

    LichessService(RestTemplate restTemplate, PgnGameParser pgnParser) {
        this.restTemplate = restTemplate;
        this.pgnParser = pgnParser;
    }

    @Override
    public ChessPlatform platform() {
        return ChessPlatform.LICHESS;
    }

    @Override
    public List<ChessGame> fetchGames(String username, int gameCount) {
        validateRequest(username, gameCount);
        String url = API_URL + username + "?max=" + gameCount;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/x-chess-pgn");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            List<ChessGame> games = new ArrayList<>();
            for (String pgn : pgnParser.splitGames(response.getBody())) {
                games.add(pgnParser.toGame(username, platform(), pgn, null));
            }
            return games;
        } catch (HttpClientErrorException.NotFound e) {
            throw new GameProviderException("Lichess username '" + username + "' was not found.", e);
        } catch (GameProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new GameProviderException("Could not retrieve games from Lichess. Please try again.", e);
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
}
