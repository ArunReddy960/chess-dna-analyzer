package com.chessdna.chessdnaanalyzer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/games")
public class AnalysisController {

    private static final int QUICK_DEPTH = 8;
    private static final int DEEP_DEPTH = 12;

    private final ChessPlatformRegistry platformRegistry;
    private final PgnGameParser pgnParser;
    private final StockfishService stockfishService;
    private final PatternAnalysisService patternAnalysisService;
    private final AnalysisJobService analysisJobService;
    private final AnalysisJobRepository jobRepository;

    public AnalysisController(ChessPlatformRegistry platformRegistry,
                              PgnGameParser pgnParser,
                              StockfishService stockfishService,
                              PatternAnalysisService patternAnalysisService,
                              AnalysisJobService analysisJobService,
                              AnalysisJobRepository jobRepository) {
        this.platformRegistry = platformRegistry;
        this.pgnParser = pgnParser;
        this.stockfishService = stockfishService;
        this.patternAnalysisService = patternAnalysisService;
        this.analysisJobService = analysisJobService;
        this.jobRepository = jobRepository;
    }

    @GetMapping("/{username}")
    public List<ChessGame> getGames(
            @PathVariable String username,
            @RequestParam(defaultValue = "10") int gameCount,
            @RequestParam(defaultValue = "lichess") String platform) {
        ChessPlatform selectedPlatform = ChessPlatform.from(platform);
        return platformRegistry.providerFor(selectedPlatform).fetchGames(username, gameCount);
    }

    @GetMapping("/{username}/fens")
    public List<List<String>> getGamesFens(
            @PathVariable String username,
            @RequestParam(defaultValue = "2") int gameCount,
            @RequestParam(defaultValue = "lichess") String platform) {
        List<ChessGame> games = platformRegistry.providerFor(ChessPlatform.from(platform))
                .fetchGames(username, gameCount);
        return games.stream().map(game -> pgnParser.extractFens(game.pgn())).toList();
    }

    @GetMapping("/bestmove")
    public String getBestMoveForFen(@RequestParam String fen) throws IOException, InterruptedException {
        return stockfishService.analyzePosition(fen, DEEP_DEPTH).bestMove();
    }

    @GetMapping("/{username}/analyze")
    public List<List<StockfishService.AnalyzedMove>> analyzeGames(
            @PathVariable String username,
            @RequestParam(defaultValue = "1") int gameCount,
            @RequestParam(defaultValue = "12") int depth,
            @RequestParam(defaultValue = "lichess") String platform) throws InterruptedException {
        List<ChessGame> games = platformRegistry.providerFor(ChessPlatform.from(platform))
                .fetchGames(username, gameCount);
        List<List<StockfishService.AnalyzedMove>> analyses = new ArrayList<>();
        for (ChessGame game : games) {
            analyses.add(stockfishService.analyzeGame(
                    pgnParser.extractFens(game.pgn()), depth, game.playerColor()));
        }
        return analyses;
    }

    @GetMapping("/{username}/patterns")
    public List<PatternAnalysisService.PhaseStats> analyzePatterns(
            @PathVariable String username,
            @RequestParam(defaultValue = "5") int gameCount,
            @RequestParam(defaultValue = "12") int depth,
            @RequestParam(defaultValue = "lichess") String platform) throws InterruptedException {
        return patternAnalysisService.analyzePatterns(
                analyzeGames(username, gameCount, depth, platform));
    }

    @GetMapping("/jobs/{jobId}")
    public AnalysisJob getJobStatus(@PathVariable Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }

    @PostMapping("/{username}/analyze-quick")
    public AnalysisJob quickAnalysis(
            @PathVariable String username,
            @RequestParam(defaultValue = "10") int gameCount,
            @RequestParam(defaultValue = "lichess") String platform) {
        return analysisJobService.startJob(
                username, gameCount, QUICK_DEPTH, ChessPlatform.from(platform));
    }

    @PostMapping("/{username}/analyze-deep")
    public AnalysisJob deepAnalysis(
            @PathVariable String username,
            @RequestParam(defaultValue = "10") int gameCount,
            @RequestParam(defaultValue = "lichess") String platform) {
        return analysisJobService.startJob(
                username, gameCount, DEEP_DEPTH, ChessPlatform.from(platform));
    }

    @GetMapping("/jobs/recent")
    public ResponseEntity<List<AnalysisJob>> getRecentJobs() {
        return ResponseEntity.ok(jobRepository.findTop10ByStatusOrderByUpdatedAtDesc("COMPLETED"));
    }
}
