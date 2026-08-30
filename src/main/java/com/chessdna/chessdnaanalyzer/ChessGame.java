package com.chessdna.chessdnaanalyzer;

public record ChessGame(
        String id,
        ChessPlatform platform,
        String sourceUrl,
        String pgn,
        PlayerColor playerColor
) {}
