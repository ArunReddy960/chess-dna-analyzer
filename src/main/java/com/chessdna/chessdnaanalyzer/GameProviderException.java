package com.chessdna.chessdnaanalyzer;

public class GameProviderException extends RuntimeException {
    public GameProviderException(String message) {
        super(message);
    }

    public GameProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
