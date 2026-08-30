package com.chessdna.chessdnaanalyzer;

import java.util.Arrays;

public enum ChessPlatform {
    LICHESS("lichess", "Lichess"),
    CHESS_COM("chesscom", "Chess.com");

    private final String apiValue;
    private final String displayName;

    ChessPlatform(String apiValue, String displayName) {
        this.apiValue = apiValue;
        this.displayName = displayName;
    }

    public String apiValue() {
        return apiValue;
    }

    public String displayName() {
        return displayName;
    }

    public static ChessPlatform from(String value) {
        if (value == null || value.isBlank()) {
            return LICHESS;
        }

        String normalized = value.trim().toLowerCase().replace(".", "").replace("_", "").replace("-", "");
        return Arrays.stream(values())
                .filter(platform -> platform.apiValue.equals(normalized)
                        || platform.name().replace("_", "").equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported platform '" + value + "'. Use lichess or chesscom."));
    }
}
