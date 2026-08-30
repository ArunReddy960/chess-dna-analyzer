package com.chessdna.chessdnaanalyzer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
public class StockfishService {

    @Value("${stockfish.path}")
    private String stockfishPath;

    @Value("${stockfish.depth:18}")
    private int defaultDepth;

    @Value("${stockfish.pool-size:2}")
    private int POOL_SIZE;

    @Value("${stockfish.cache-version:stockfish-v2-pgn-fix}")
    private String cacheVersion;

    private BlockingQueue<StockfishEngine> enginePool;

    // Custom thread pool — POOL_SIZE * 2 threads ensures engines are never idle
    private ExecutorService analysisThreadPool;

    @PostConstruct
    public void initPool() throws IOException {
        enginePool = new ArrayBlockingQueue<>(POOL_SIZE);
        analysisThreadPool = Executors.newFixedThreadPool(POOL_SIZE * 2);
        System.out.println("=== Starting Stockfish pool with " + POOL_SIZE + " engines ===");
        for (int i = 0; i < POOL_SIZE; i++) {
            enginePool.offer(new StockfishEngine(stockfishPath));
            System.out.println("=== Engine " + (i + 1) + " of " + POOL_SIZE + " started ===");
        }
        System.out.println("=== Pool ready: " + enginePool.size() + " engines ===");
        System.out.println("=== Thread pool: " + POOL_SIZE * 2 + " threads ===");
    }

    @PreDestroy
    public void shutdownPool() {
        analysisThreadPool.shutdown();
        for (StockfishEngine engine : enginePool) {
            engine.close();
        }
    }

    public AnalysisResult analyzePosition(String fen, int depth) throws IOException, InterruptedException {
        StockfishEngine engine = enginePool.poll(120, TimeUnit.SECONDS);
        if (engine == null) {
            throw new RuntimeException("No Stockfish engine available — pool timeout");
        }
        try {
            return engine.analyze(fen, depth);
        } finally {
            enginePool.offer(engine);
        }
    }

    public List<AnalyzedMove> analyzeGame(List<String> fens, int depth) throws InterruptedException {
        return analyzeGame(fens, depth, null);
    }

    public List<AnalyzedMove> analyzeGame(
            List<String> fens,
            int depth,
            PlayerColor playerColor) throws InterruptedException {

        // ── PHASE 1: Evaluate ALL positions in PARALLEL using custom thread pool ──
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        String startingFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

        List<String> allFens = new ArrayList<>();
        allFens.add(startingFen);
        allFens.addAll(fens);

        for (String fen : allFens) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                try {
                    AnalysisResult result = analyzePosition(fen, depth);
                    return normalizeToWhitePerspective(result.evaluationCentipawns(), fen);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, analysisThreadPool); // ← CUSTOM THREAD POOL — KEY FIX
            futures.add(future);
        }

        List<Integer> allEvaluations = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // ── PHASE 2: Calculate differences + classify phase/quality ──
        List<AnalyzedMove> results = new ArrayList<>();
        for (int i = 1; i < allEvaluations.size(); i++) {
            int previousEval = allEvaluations.get(i - 1);
            int currentEval = allEvaluations.get(i);

            String fenBeforeThisMove = allFens.get(i - 1);
            PlayerColor mover = sideToMove(fenBeforeThisMove);
            if (playerColor != null && mover != playerColor) {
                continue;
            }

            int cpLoss = calculateCentipawnLoss(previousEval, currentEval, mover);
            int fullMoveNumber = fullMoveNumber(fenBeforeThisMove, i);
            String phase = determinePhase(fenBeforeThisMove, fullMoveNumber);
            String quality = classifyMoveQuality(cpLoss);

            results.add(new AnalyzedMove(i, cpLoss, phase, quality));
        }

        return results;
    }

    public int calculateCentipawnLoss(int previousWhiteEvaluation,
                                      int currentWhiteEvaluation,
                                      PlayerColor mover) {
        int rawLoss = mover == PlayerColor.WHITE
                ? previousWhiteEvaluation - currentWhiteEvaluation
                : currentWhiteEvaluation - previousWhiteEvaluation;
        return Math.max(0, rawLoss);
    }

    PlayerColor sideToMove(String fen) {
        String[] fields = fen.split(" ");
        if (fields.length < 2) {
            throw new IllegalArgumentException("Invalid FEN: missing side to move");
        }
        return "w".equals(fields[1]) ? PlayerColor.WHITE : PlayerColor.BLACK;
    }

    private int fullMoveNumber(String fen, int fallbackPly) {
        String[] fields = fen.split(" ");
        if (fields.length > 5) {
            try {
                return Integer.parseInt(fields[5]);
            } catch (NumberFormatException ignored) {
                // Fall through to a safe value derived from the ply index.
            }
        }
        return (fallbackPly + 1) / 2;
    }

    public String getCacheVersion() {
        return cacheVersion;
    }

    public int normalizeToWhitePerspective(int evaluation, String fen) {
        String turnIndicator = fen.split(" ")[1];
        if (turnIndicator.equals("b")) {
            return -evaluation;
        }
        return evaluation;
    }

    private int countPieces(String fen) {
        String boardPart = fen.split(" ")[0];
        int count = 0;
        for (char c : boardPart.toCharArray()) {
            if (Character.isLetter(c)) {
                count++;
            }
        }
        return count;
    }

    public String determinePhase(String fen, int moveNumber) {
        int pieceCount = countPieces(fen);
        boolean queensGone = !fen.split(" ")[0].contains("Q")
                && !fen.split(" ")[0].contains("q");

        if (pieceCount <= 12) return "endgame";
        if (queensGone) return "endgame";
        if (moveNumber <= 15) return "opening";
        return "middlegame";
    }

    public String classifyMoveQuality(int cpLoss) {
        if (cpLoss <= 10) return "BEST";
        if (cpLoss <= 25) return "EXCELLENT";
        if (cpLoss <= 50) return "GOOD";
        if (cpLoss <= 100) return "INACCURACY";
        if (cpLoss <= 200) return "MISTAKE";
        return "BLUNDER";
    }

    public record AnalysisResult(String bestMove, int evaluationCentipawns) {}
    public record AnalyzedMove(int moveNumber, int centipawnLoss, String phase, String quality) {}

    private static class StockfishEngine {
        private final Process process;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        StockfishEngine(String path) throws IOException {
            ProcessBuilder builder = new ProcessBuilder(path);
            builder.redirectErrorStream(true);
            this.process = builder.start();
            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            sendCommand("uci");
            waitForResponse("uciok");
            sendCommand("isready");
            waitForResponse("readyok");
        }

        AnalysisResult analyze(String fen, int depth) throws IOException {
            sendCommand("position fen " + fen);
            sendCommand("go depth " + depth);

            String bestMove = null;
            int evaluation = 0;
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.contains("score cp")) {
                    evaluation = extractScore(line);
                }
                if (line.startsWith("bestmove")) {
                    bestMove = line.split(" ")[1];
                    break;
                }
            }

            return new AnalysisResult(bestMove, evaluation);
        }

        private void sendCommand(String command) throws IOException {
            writer.write(command);
            writer.newLine();
            writer.flush();
        }

        private void waitForResponse(String expectedKeyword) throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(expectedKeyword)) break;
            }
        }

        private int extractScore(String infoLine) {
            String[] parts = infoLine.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("cp")) {
                    return Integer.parseInt(parts[i + 1]);
                }
            }
            return 0;
        }

        void close() {
            try {
                sendCommand("quit");
                process.destroy();
            } catch (IOException ignored) {}
        }
    }
}
