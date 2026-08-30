package com.chessdna.chessdnaanalyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PgnGameParserTest {

    private PgnGameParser parser;

    @BeforeEach
    void setUp() {
        parser = new PgnGameParser();
    }

    @Test
    void splitGames_handlesStandardPgnBoundaries() {
        String pgn = """
                [Event "Game 1"]
                [White "Arun"]
                [Black "Opponent"]

                1. e4 e5 1-0

                [Event "Game 2"]
                [White "Opponent"]
                [Black "Arun"]

                1. d4 d5 0-1
                """;

        List<String> games = parser.splitGames(pgn);
        assertEquals(2, games.size());
    }

    @Test
    void toGame_identifiesPlayerColorCaseInsensitively() {
        String pgn = """
                [Event "Live Chess"]
                [Site "https://www.chess.com/game/live/123"]
                [White "Opponent"]
                [Black "ARUN"]

                1. e4 e5 0-1
                """;

        ChessGame game = parser.toGame("arun", ChessPlatform.CHESS_COM, pgn, null);

        assertEquals(PlayerColor.BLACK, game.playerColor());
        assertEquals("https://www.chess.com/game/live/123", game.id());
        assertEquals(ChessPlatform.CHESS_COM, game.platform());
    }

    @Test
    void extractFens_returnsOnePositionPerPly() {
        String pgn = """
                [Event "Test"]
                [White "Arun"]
                [Black "Opponent"]
                [Result "*"]

                1. e4 e5 2. Nf3 *
                """;

        assertEquals(3, parser.extractFens(pgn).size());
    }

    @Test
    void extractFens_parsesChessComClockAnnotations() {
        String pgn = """
                [Event "Live Chess"]
                [Site "Chess.com"]
                [White "Arun"]
                [Black "Opponent"]
                [Result "1-0"]

                1. d4 {[%clk 0:15:08.9]}
                1... d5 {[%clk 0:15:08.8]}
                2. Nf3 {[%clk 0:15:18.1]}
                2... Nf6 {[%clk 0:15:14.4]}
                3. e3 {[%clk 0:15:27.2]} 1-0
                """;

        assertEquals(5, parser.extractFens(pgn).size());
    }
}
