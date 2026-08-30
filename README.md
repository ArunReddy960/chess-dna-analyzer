# KnightLens — Chess Performance Intelligence

> Decode your chess DNA. Find exactly where your games slip away.

**Live Demo:** [knightlens.vercel.app](https://knightlens.vercel.app)  
**GitHub:** [github.com/ArunReddy960/knightlens](https://github.com/ArunReddy960/knightlens)

---

![KnightLens Demo](https://i.imgur.com/placeholder.png)

---

## What It Does

KnightLens fetches a player's recent games from **Chess.com or Lichess**, normalizes both providers into one game model, evaluates the player's moves through a Stockfish engine pool, and generates a personalized **Chess DNA report** — including a player archetype, GM comparison, and AI coaching report.

Most chess tools explain individual moves. KnightLens models the **player** — aggregating patterns across 10–20 games to answer: *where do you consistently lose advantage?*

---

## Full Stack Architecture

```
Browser (React + Vite)
        ↓  POST /api/games/{username}/analyze-quick
        ↓  GET  /api/games/jobs/{jobId}  (polling every 5s)
        
Spring Boot Backend (Java 17)
        ↓
ChessPlatformRegistry   → routes to Chess.com or Lichess provider
        ↓
Canonical ChessGame     → PGN + source URL + platform + player color
        ↓
Chesspresso              → parses PGN into per-move FEN positions
        ↓
StockfishService         → parallel engine analysis
  ┌─────────────────────────────────────────────────┐
  │  BlockingQueue<StockfishEngine> (pool of 2-8)   │
  │  CompletableFuture.supplyAsync() — all moves    │
  │  evaluated in parallel across pool              │
  └─────────────────────────────────────────────────┘
        ↓
GameAnalysisCache        → reuses analysis by game/depth/engine version
        ↓
PatternAnalysisService   → aggregates the requested player's moves by phase
        ↓
ClaudeService            → generates Chess DNA personality + coaching report
        ↓
PostgreSQL               → async job tracking (PENDING→IN_PROGRESS→COMPLETED)
        ↓
REST API                 → Spring Boot, async job pattern, CORS config

Deployment:
  Frontend → Vercel (auto-deploy on git push)
  Backend  → Render (Docker, auto-deploy on git push)
  Database → Render PostgreSQL
```

---

## Chess DNA Report

Every analysis produces a structured personality profile:

```json
{
  "personality": {
    "archetype": "The Middlegame Monster",
    "tagline": "Creates chaos, forgets to finish",
    "playStyle": "Tactical, aggressive",
    "strength": "Explosive combinations in complex positions",
    "blindSpot": "Converting won endgames",
    "riskLevel": "High",
    "similarGM": "Mikhail Tal",
    "gmDescription": "Like Tal, brilliant in complications but inconsistent in simplified positions"
  },
  "report": {
    "overallAssessment": "...",
    "biggestStrength": "...",
    "primaryWeakness": "...",
    "actionPlan": ["...", "...", "..."],
    "thisWeekFocus": {
      "drill": "...",
      "studyThis": "...",
      "inYourNextGame": "..."
    }
  }
}
```

---

## Key Engineering Decisions

### Multi-Platform Provider Layer
`ChessPlatformService` defines a provider contract implemented by `ChessComService` and `LichessService`. A registry selects the provider at runtime, while both implementations return the same `ChessGame` record. PGN parsing, Stockfish evaluation, pattern analysis, and AI coaching therefore remain independent of the upstream platform.

The Chess.com client walks monthly archives newest-first and makes archive requests serially to respect PubAPI behavior. It sends a descriptive `User-Agent`, retries `429` and server errors with exponential backoff, honors `Retry-After`, and revalidates cached responses with `ETag` and `Last-Modified`.

### Color-Correct, Player-Only Evaluation
Stockfish scores are normalized to White's perspective, but centipawn loss must still be calculated differently for a White move and a Black move. KnightLens reads the requested player's color from each PGN, filters out the opponent's moves, and applies color-aware loss calculation before classification. This prevents Black mistakes from appearing as negative-loss `BEST` moves and keeps opponent decisions out of the player's profile.

### Content-Addressed Analysis Cache
Completed per-game engine results are stored in PostgreSQL under a SHA-256 key derived from platform, game ID, player color, search depth, and engine cache version. Repeat analyses skip Stockfish work for matching games while a version field makes cache invalidation explicit when engine behavior changes.

### Stockfish Process Pool
Stockfish is a native binary — spawning a new process per move costs ~200ms startup overhead. Instead, a `BlockingQueue<StockfishEngine>` pre-warms N engine processes at startup and manages them as a pool (borrow → use → return), cutting per-move overhead to near-zero.

```java
// Pool initialization (@PostConstruct)
for (int i = 0; i < POOL_SIZE; i++) {
    enginePool.offer(new StockfishEngine(stockfishPath));
}

// Borrow → Use → Return (finally block prevents leaks)
StockfishEngine engine = enginePool.poll(30, TimeUnit.SECONDS);
try {
    return engine.analyze(fen, depth);
} finally {
    enginePool.offer(engine); // always returns, even on exception
}
```

### Parallel Move Analysis (Two-Phase)
Each position's evaluation is independent. Phase 1 evaluates all moves simultaneously using `CompletableFuture.supplyAsync()`. Phase 2 calculates centipawn loss sequentially — since each loss requires the preceding evaluation. This separation reduced single-game analysis from ~85s → ~15s.

```java
// PHASE 1: parallel evaluation (independent)
List<CompletableFuture<Integer>> futures = fens.stream()
    .map(fen -> CompletableFuture.supplyAsync(() -> analyzePosition(fen, depth)))
    .toList();
List<Integer> evaluations = futures.stream()
    .map(CompletableFuture::join)
    .toList();

// PHASE 2: sequential loss calculation (dependent)
for (int i = 1; i < evaluations.size(); i++) {
    int loss = evaluations.get(i - 1) - evaluations.get(i);
    results.add(new AnalyzedMove(fens.get(i), loss));
}
```

### Async Job Pattern
Full analysis (5 games ≈ 2-3 minutes) would block HTTP threads. The endpoint creates a job record, returns the job ID in ~615ms, and offloads processing to a Spring `@Async` background thread. Clients poll `/jobs/{id}` every 5 seconds.

```java
// Returns immediately (~615ms)
public AnalysisJob startJob(String username, int gameCount, int depth) {
    AnalysisJob job = new AnalysisJob(username, gameCount, "PENDING");
    AnalysisJob saved = jobRepository.save(job);
    self.processJobAsync(saved.getId(), username, gameCount, depth); // @Async
    return saved; // client gets this immediately
}
```

**@Async self-invocation fix:** Calling `@Async` methods from the same class bypasses Spring's proxy. Fixed by injecting `self` with `@Lazy` and calling via `self.processJobAsync()`.

### Perspective Normalization Bug Fix
Stockfish's `score cp` is always relative to the side-to-move. Without normalization, consecutive moves alternate between large positive/negative values — producing fictional 1000+ centipawn blunders.

```java
private int normalizeToWhitePerspective(int evaluation, String fen) {
    String turn = fen.split(" ")[1]; // "w" or "b"
    return turn.equals("b") ? -evaluation : evaluation;
}
```

This single fix transformed the output from random noise to coherent player patterns.

### Phase Classification (Board State, Not Move Number)
Using move number for phase classification is inaccurate — a 40-move game and a 100-move game have completely different structures. Rules in priority order:

```
1. Total pieces ≤ 12           → ENDGAME
2. Both queens exchanged        → ENDGAME (override)
3. Move number ≤ 15            → OPENING
4. Otherwise                   → MIDDLEGAME
```

Phase is determined using the **before-move FEN** — matching what the player was actually reasoning about when they made the decision.

### Claude AI Integration
Phase statistics are sent to Claude with a structured prompt requesting JSON output. The response is parsed and stored in two separate database columns:

```java
// ClaudeService returns structured JSON
String claudeJson = callClaude(prompt);
JsonNode root = objectMapper.readTree(claudeJson);

// Stored separately for clean frontend access
job.setPersonalityJson(objectMapper.writeValueAsString(root.path("personality")));
job.setCoachingReport(objectMapper.writeValueAsString(root.path("report")));
```

---

## API Reference

### Start Analysis
```
POST /api/games/{username}/analyze-quick?gameCount=5&platform=chesscom  → depth 8
POST /api/games/{username}/analyze-deep?gameCount=5&platform=lichess    → depth 12
```
Returns job ID immediately (~615ms).

### Poll Status
```
GET /api/games/jobs/{jobId}
```
Returns `PENDING` → `IN_PROGRESS` → `COMPLETED` with `stage`, `completedGames`, and `cacheHits` progress fields.

### Completed Response
```json
{
  "id": 42,
  "username": "DrNykterstein",
  "platform": "lichess",
  "status": "COMPLETED",
  "stage": "COMPLETED",
  "depth": 12,
  "completedGames": 10,
  "cacheHits": 7,
  "personalityJson": "{\"archetype\":\"The Opening Maestro\", ...}",
  "coachingReport": "{\"overallAssessment\":\"...\", ...}",
  "resultJson": "[{\"phaseName\":\"opening\",\"accuracyPercentage\":96.0,...}]",
  "createdAt": "2026-07-23T15:49:24",
  "updatedAt": "2026-07-23T15:49:59"
}
```

---

## Move Quality Buckets

| Quality | Centipawn Loss | Meaning |
|---------|---------------|---------|
| Best | ≤ 10 cp | Engine-level move |
| Excellent | 11–25 cp | Strong move |
| Good | 26–50 cp | Solid move |
| Inaccuracy | 51–100 cp | Minor error |
| Mistake | 101–200 cp | Significant error |
| Blunder | > 200 cp | Game-changing error |

---

## Performance

| Metric | Value |
|--------|-------|
| POST response time | ~615ms (async) |
| Repeat game at same depth/version | Stockfish result loaded from PostgreSQL |
| Single game (136 moves) | ~15s (was 85s before pool) |
| Speedup from parallelism | ~5.6x |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19, Vite |
| Backend | Java 17, Spring Boot 3.5, Gradle |
| Database | PostgreSQL (Render) |
| ORM | Spring Data JPA / Hibernate |
| Chess Engine | Stockfish (subprocess, UCI protocol) |
| PGN Parsing | Chesspresso |
| AI Coaching | Anthropic Claude API |
| Game Data | Chess.com Published Data API, Lichess API |
| Async | Spring @Async, CompletableFuture, BlockingQueue |
| Deployment | Vercel (frontend) + Render Docker (backend) |

---

## Local Setup

### Prerequisites
- Java 17+
- PostgreSQL running locally
- [Stockfish](https://stockfishchess.org/download/) installed
- Node.js 18+ (for frontend)
- Anthropic API key

### Backend Environment Variables
```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/chessdna
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password
STOCKFISH_PATH=C:\path\to\stockfish.exe
CLAUDE_API_KEY=sk-ant-...
GITHUB_TOKEN=ghp_...
```

### Run Backend
```bash
./gradlew bootRun
# Starts on http://localhost:8080
```

### Run Frontend
```bash
cd knightlens-frontend
npm install
npm run dev
# Opens on http://localhost:5173
```

---

## Automated Tests

```
StockfishServiceTest
  ✓ classifyMoveQuality — all 6 buckets + boundary values
  ✓ determinePhase — endgame conditions, opening/middlegame boundaries
  ✓ normalizeToWhitePerspective — white/black turn, negative values
  ✓ color-aware centipawn loss for White and Black

ChessComServiceTest
  ✓ archive ingestion and player-color preservation
  ✓ 429 retry behavior with Retry-After

PgnGameParserTest
  ✓ provider-neutral PGN splitting, metadata, and FEN extraction

PatternAnalysisServiceTest (4 tests)
  ✓ Empty games → zero stats
  ✓ Accuracy = (BEST + EXCELLENT) / total × 100
  ✓ Multiple games → correctly aggregated
```

---

## Real Results

Analysis of **DrNykterstein** (Magnus Carlsen's Lichess account), 10 games, depth 12:

```
Opening:     93.3% accuracy  (0 blunders, 150 moves)
Middlegame:  78.6% accuracy  (5 blunders, 378 moves)
Endgame:     78.5% accuracy  (7 blunders, 311 moves)

Chess DNA: "The Opening Maestro Who Loses the Thread"
Similar to: Ruslan Ponomariov
```

---

## Resume Bullet Points

- Architected a provider-agnostic chess analytics pipeline integrating Chess.com and Lichess through a Strategy-based registry and canonical game model, enabling multi-platform Stockfish analysis without duplicating downstream processing
- Built a Stockfish process pool (BlockingQueue, N pre-warmed UCI processes) eliminating ~200ms per-position startup overhead; combined with CompletableFuture.supplyAsync() for parallel move evaluation, reducing analysis from 85s → 15s (5.6x speedup)
- Implemented async job orchestration (@Async, @EnableAsync) with PostgreSQL job tracking — HTTP responses return in ~615ms while Stockfish analysis runs in background; clients poll /jobs/{id} for status
- Engineered a rate-limit-aware Chess.com client with newest-first archive pagination, conditional HTTP caching, `Retry-After` support, and exponential backoff for 429/5xx responses
- Implemented content-addressed PostgreSQL caching keyed by platform, game, player color, search depth, and engine version, eliminating repeat Stockfish evaluation for cache hits
- Corrected color-aware centipawn-loss calculations and restricted aggregation to player-owned moves, preventing Black-side scoring inversion and opponent-move contamination
- Built a React 19 interface with platform selection, persisted job history, stage-level progress, cache-hit visibility, and live polling; added GitHub Actions CI for backend tests and frontend build/lint verification
