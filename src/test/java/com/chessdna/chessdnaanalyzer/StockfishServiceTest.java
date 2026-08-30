package com.chessdna.chessdnaanalyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockfishServiceTest {

    private StockfishService stockfishService;

    @BeforeEach
    void setUp() {
        // We're testing PURE LOGIC methods only — no Spring context needed,
        // no Stockfish process needed, just a plain object
        stockfishService = new StockfishService();
    }

    // ── classifyMoveQuality tests ──────────────────────────────────────

    @Test
    void classifyMoveQuality_negativeValue_returnsBest() {
        assertEquals("BEST", stockfishService.classifyMoveQuality(-50));
    }

    @Test
    void classifyMoveQuality_zero_returnsBest() {
        assertEquals("BEST", stockfishService.classifyMoveQuality(0));
    }

    @Test
    void classifyMoveQuality_10_returnsBest() {
        assertEquals("BEST", stockfishService.classifyMoveQuality(10));
    }

    @Test
    void classifyMoveQuality_11_returnsExcellent() {
        assertEquals("EXCELLENT", stockfishService.classifyMoveQuality(11));
    }

    @Test
    void classifyMoveQuality_25_returnsExcellent() {
        assertEquals("EXCELLENT", stockfishService.classifyMoveQuality(25));
    }

    @Test
    void classifyMoveQuality_26_returnsGood() {
        assertEquals("GOOD", stockfishService.classifyMoveQuality(26));
    }

    @Test
    void classifyMoveQuality_100_returnsInaccuracy() {
        assertEquals("INACCURACY", stockfishService.classifyMoveQuality(100));
    }

    @Test
    void classifyMoveQuality_200_returnsMistake() {
        assertEquals("MISTAKE", stockfishService.classifyMoveQuality(200));
    }

    @Test
    void classifyMoveQuality_201_returnsBlunder() {
        assertEquals("BLUNDER", stockfishService.classifyMoveQuality(201));
    }

    // ── determinePhase tests ───────────────────────────────────────────

    @Test
    void determinePhase_lowPieceCount_returnsEndgame() {
        // Only kings + 4 pawns each + 2 rooks = 10 pieces total (≤ 12 → endgame)
        String fen = "8/pppp4/8/8/8/8/PPPP4/4K2R w K - 0 40";
        assertEquals("endgame", stockfishService.determinePhase(fen, 40));
    }

    @Test
    void determinePhase_queensGone_returnsEndgame() {
        // No queens (Q or q) in this position, but plenty of pieces
        String fen = "rnb2bnr/pppppppp/8/8/8/8/PPPPPPPP/RNB2BNR w KQkq - 0 20";
        assertEquals("endgame", stockfishService.determinePhase(fen, 20));
    }

    @Test
    void determinePhase_earlyMove_returnsOpening() {
        // Starting position, move 5
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertEquals("opening", stockfishService.determinePhase(fen, 5));
    }

    @Test
    void determinePhase_move15_returnsOpening() {
        // Move 15 should STILL be opening (boundary check)
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertEquals("opening", stockfishService.determinePhase(fen, 15));
    }

    @Test
    void determinePhase_move16_returnsMiddlegame() {
        // Move 16 should be middlegame — just past opening threshold
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertEquals("middlegame", stockfishService.determinePhase(fen, 16));
    }

    // ── normalizeToWhitePerspective tests ──────────────────────────────

    @Test
    void normalize_whiteTurn_returnsUnchanged() {
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1";
        assertEquals(50, stockfishService.normalizeToWhitePerspective(50, fen));
    }

    @Test
    void normalize_blackTurn_returnsFlipped() {
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertEquals(-50, stockfishService.normalizeToWhitePerspective(50, fen));
    }

    @Test
    void normalize_blackTurn_negativeValue_returnsPositive() {
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertEquals(30, stockfishService.normalizeToWhitePerspective(-30, fen));
    }

    @Test
    void centipawnLoss_whiteMove_usesEvaluationDrop() {
        assertEquals(80, stockfishService.calculateCentipawnLoss(120, 40, PlayerColor.WHITE));
    }

    @Test
    void centipawnLoss_blackMove_usesEvaluationIncrease() {
        assertEquals(220, stockfishService.calculateCentipawnLoss(-50, 170, PlayerColor.BLACK));
    }

    @Test
    void centipawnLoss_betterMove_clampsNegativeNoiseToZero() {
        assertEquals(0, stockfishService.calculateCentipawnLoss(20, 80, PlayerColor.WHITE));
        assertEquals(0, stockfishService.calculateCentipawnLoss(80, 20, PlayerColor.BLACK));
    }

    @Test
    void sideToMove_readsBothColorsFromFen() {
        assertEquals(PlayerColor.WHITE, stockfishService.sideToMove(
                "8/8/8/8/8/8/8/8 w - - 0 1"));
        assertEquals(PlayerColor.BLACK, stockfishService.sideToMove(
                "8/8/8/8/8/8/8/8 b - - 0 1"));
    }
}
