package com.chessdna.chessdnaanalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AnalysisJobService {

    private final AnalysisJobRepository jobRepository;
    private final ChessPlatformRegistry platformRegistry;
    private final PgnGameParser pgnParser;
    private final StockfishService stockfishService;
    private final PatternAnalysisService patternAnalysisService;
    private final ClaudeService claudeService;
    private final GameAnalysisCacheService cacheService;
    private final ObjectMapper objectMapper;

    private AnalysisJobService self;

    public AnalysisJobService(AnalysisJobRepository jobRepository,
                              ChessPlatformRegistry platformRegistry,
                              PgnGameParser pgnParser,
                              StockfishService stockfishService,
                              PatternAnalysisService patternAnalysisService,
                              ClaudeService claudeService,
                              GameAnalysisCacheService cacheService,
                              ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.platformRegistry = platformRegistry;
        this.pgnParser = pgnParser;
        this.stockfishService = stockfishService;
        this.patternAnalysisService = patternAnalysisService;
        this.claudeService = claudeService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setSelf(@Lazy AnalysisJobService self) {
        this.self = self;
    }

    public AnalysisJob startJob(String username, int gameCount, int depth, ChessPlatform platform) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (gameCount < 1 || gameCount > 50) {
            throw new IllegalArgumentException("Game count must be between 1 and 50.");
        }

        AnalysisJob job = new AnalysisJob();
        job.setUsername(username.trim());
        job.setPlatform(platform.apiValue());
        job.setGameCount(gameCount);
        job.setStatus("PENDING");
        job.setStage("QUEUED");
        job.setDepth(depth);
        AnalysisJob savedJob = jobRepository.save(job);
        self.processJobAsync(savedJob.getId(), username.trim(), gameCount, depth, platform);
        return savedJob;
    }

    @Async
    public void processJobAsync(Long jobId,
                                String username,
                                int gameCount,
                                int depth,
                                ChessPlatform platform) {
        AnalysisJob job = jobRepository.findById(jobId).orElseThrow();

        try {
            updateProgress(job, "IN_PROGRESS", "FETCHING_GAMES", 0, 0);
            List<ChessGame> games = platformRegistry.providerFor(platform).fetchGames(username, gameCount);
            if (games.isEmpty()) {
                throw new GameProviderException(
                        "No public games were found for '" + username + "' on " + platform.displayName() + ".");
            }

            List<List<StockfishService.AnalyzedMove>> allGamesAnalysis = new ArrayList<>();
            int cacheHits = 0;

            for (int index = 0; index < games.size(); index++) {
                ChessGame game = games.get(index);
                updateProgress(job, "IN_PROGRESS", "ANALYZING", index, cacheHits);

                Optional<List<StockfishService.AnalyzedMove>> cached = cacheService.find(
                        game, depth, stockfishService.getCacheVersion());

                List<StockfishService.AnalyzedMove> analysis;
                if (cached.isPresent()) {
                    analysis = cached.get();
                    cacheHits++;
                } else {
                    List<String> fens = pgnParser.extractFens(game.pgn());
                    analysis = stockfishService.analyzeGame(fens, depth, game.playerColor());
                    cacheService.store(game, depth, stockfishService.getCacheVersion(), analysis);
                }

                allGamesAnalysis.add(analysis);
                updateProgress(job, "IN_PROGRESS", "ANALYZING", index + 1, cacheHits);
            }

            List<PatternAnalysisService.PhaseStats> patterns =
                    patternAnalysisService.analyzePatterns(allGamesAnalysis);

            updateProgress(job, "IN_PROGRESS", "GENERATING_REPORT", games.size(), cacheHits);
            String claudeJson = claudeService.generateAnalysis(patterns, username);
            JsonNode claudeNode = objectMapper.readTree(claudeJson);

            job.setPersonalityJson(objectMapper.writeValueAsString(claudeNode.path("personality")));
            job.setCoachingReport(objectMapper.writeValueAsString(claudeNode.path("report")));
            job.setResultJson(objectMapper.writeValueAsString(patterns));
            updateProgress(job, "COMPLETED", "COMPLETED", games.size(), cacheHits);
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setStage("FAILED");
            job.setErrorMessage(safeMessage(e));
            jobRepository.save(job);
            System.err.println("Job " + jobId + " failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateProgress(AnalysisJob job,
                                String status,
                                String stage,
                                int completedGames,
                                int cacheHits) {
        job.setStatus(status);
        job.setStage(stage);
        job.setCompletedGames(completedGames);
        job.setCacheHits(cacheHits);
        jobRepository.save(job);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Analysis failed. Please try again.";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
