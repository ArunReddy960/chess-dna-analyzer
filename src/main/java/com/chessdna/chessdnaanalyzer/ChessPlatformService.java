package com.chessdna.chessdnaanalyzer;

import java.util.List;

public interface ChessPlatformService {
    ChessPlatform platform();
    List<ChessGame> fetchGames(String username, int gameCount);
}
