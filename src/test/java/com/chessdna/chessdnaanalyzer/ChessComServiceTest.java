package com.chessdna.chessdnaanalyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChessComServiceTest {

    private static final String BASE_URL = "https://api.chess.com/pub";
    private static final String ARCHIVE_URL = BASE_URL + "/player/arun/games/2026/08";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private List<Long> retryDelays;
    private ChessComService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        retryDelays = new ArrayList<>();
        service = new ChessComService(
                restTemplate,
                new ObjectMapper(),
                new PgnGameParser(),
                BASE_URL,
                "KnightLens-Test/1.0",
                retryDelays::add);
    }

    @Test
    void fetchGames_readsNewestArchiveAndPreservesPlayerColor() {
        server.expect(once(), requestTo(BASE_URL + "/player/arun/games/archives"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("User-Agent", "KnightLens-Test/1.0"))
                .andRespond(withSuccess(
                        "{\"archives\":[\"" + ARCHIVE_URL + "\"]}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(ARCHIVE_URL))
                .andRespond(withSuccess(archiveBody(), MediaType.APPLICATION_JSON));

        List<ChessGame> games = service.fetchGames("arun", 1);

        assertEquals(1, games.size());
        assertEquals(PlayerColor.BLACK, games.get(0).playerColor());
        assertEquals(ChessPlatform.CHESS_COM, games.get(0).platform());
        server.verify();
    }

    @Test
    void fetchGames_retriesRateLimitedRequestUsingRetryAfter() {
        server.expect(once(), requestTo(BASE_URL + "/player/arun/games/archives"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        server.expect(once(), requestTo(BASE_URL + "/player/arun/games/archives"))
                .andRespond(withSuccess(
                        "{\"archives\":[\"" + ARCHIVE_URL + "\"]}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(ARCHIVE_URL))
                .andRespond(withSuccess(archiveBody(), MediaType.APPLICATION_JSON));

        assertEquals(1, service.fetchGames("arun", 1).size());
        assertEquals(List.of(0L), retryDelays);
        server.verify();
    }

    private String archiveBody() {
        String pgn = "[Event \\\"Live Chess\\\"]\\n"
                + "[Site \\\"https://www.chess.com/game/live/123\\\"]\\n"
                + "[White \\\"Opponent\\\"]\\n"
                + "[Black \\\"Arun\\\"]\\n\\n"
                + "1. e4 e5 0-1";
        return "{\"games\":[{\"url\":\"https://www.chess.com/game/live/123\",\"pgn\":\""
                + pgn + "\"}]}";
    }
}
