package com.chessdna.chessdnaanalyzer;

public enum PlayerColor {
    WHITE("w"),
    BLACK("b");

    private final String fenTurn;

    PlayerColor(String fenTurn) {
        this.fenTurn = fenTurn;
    }

    public boolean isToMove(String fen) {
        String[] fields = fen.split(" ");
        return fields.length > 1 && fenTurn.equals(fields[1]);
    }
}
