package com.chessdna.chessdnaanalyzer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class GameAnalysisCacheService {

    private static final TypeReference<List<StockfishService.AnalyzedMove>> ANALYSIS_TYPE =
            new TypeReference<>() {};

    private final GameAnalysisCacheRepository repository;
    private final ObjectMapper objectMapper;

    public GameAnalysisCacheService(GameAnalysisCacheRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<List<StockfishService.AnalyzedMove>> find(
            ChessGame game, int depth, String engineVersion) {
        String key = cacheKey(game, depth, engineVersion);
        try {
            return repository.findById(key).flatMap(entry -> {
                try {
                    return Optional.of(objectMapper.readValue(entry.getAnalysisJson(), ANALYSIS_TYPE));
                } catch (Exception ignored) {
                    try {
                        repository.delete(entry);
                    } catch (Exception deleteFailure) {
                        // A broken cache entry is treated as a miss even if cleanup fails.
                    }
                    return Optional.empty();
                }
            });
        } catch (Exception ignored) {
            // Analysis must remain available if the optional cache cannot be read.
            return Optional.empty();
        }
    }

    public void store(ChessGame game,
                      int depth,
                      String engineVersion,
                      List<StockfishService.AnalyzedMove> analysis) {
        try {
            GameAnalysisCache entry = new GameAnalysisCache();
            entry.setCacheKey(cacheKey(game, depth, engineVersion));
            entry.setPlatform(game.platform().apiValue());
            entry.setDepth(depth);
            entry.setEngineVersion(engineVersion);
            entry.setAnalysisJson(objectMapper.writeValueAsString(analysis));
            repository.save(entry);
        } catch (Exception e) {
            // Cache failures must never fail a user's analysis.
            System.err.println("Could not store game analysis cache entry: " + e.getMessage());
        }
    }

    String cacheKey(ChessGame game, int depth, String engineVersion) {
        String material = game.platform().apiValue() + "|" + game.id() + "|"
                + game.playerColor() + "|" + depth + "|" + engineVersion;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
