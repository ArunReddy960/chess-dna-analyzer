import { useState, useEffect } from "react";
import Badge from "./Badge";

// ── Chess fun facts — rotates every 5 seconds ──────────────────────────────
const FUN_FACTS = [
  "♟ Did you know? There are more possible chess games than atoms in the observable universe.",
  "♞ Did you know? The word 'checkmate' comes from the Persian phrase 'Shah Mat' — the king is dead.",
  "♝ Did you know? The longest chess game theoretically possible is 5,949 moves.",
  "♜ Did you know? Magnus Carlsen became a Grandmaster at just 13 years old.",
  "♛ Did you know? The first chess computer program was written in 1951 by Alan Turing.",
  "♟ Did you know? In 1997, Deep Blue became the first computer to defeat a reigning world champion.",
  "♞ Did you know? The number of possible unique chess positions is 10^120 — known as the Shannon Number.",
  "♝ Did you know? Stockfish, the engine powering this analysis, is open-source and free for everyone.",
  "♜ Did you know? The queen became the most powerful piece after a rule change around 1475.",
  "♛ Did you know? Bobby Fischer could reportedly recall every game he ever played.",
  "♟ Did you know? The folding chess board was invented by a priest to hide it from authorities in 1125.",
  "♞ Did you know? Anatoly Karpov played 144 games in a single world championship match in 1984.",
];

// ── What's happening at each stage ─────────────────────────────────────────
const STAGES = [
  { after: 0,  label: "Fetching your games from Lichess..." },
  { after: 5,  label: "Converting moves to board positions..." },
  { after: 12, label: "Stockfish is evaluating every move..." },
  { after: 40, label: "Classifying moves by game phase..." },
  { after: 55, label: "Identifying your patterns across games..." },
  { after: 70, label: "Claude is writing your coaching report..." },
  { after: 90, label: "Almost there — finalizing your Chess DNA..." },
];

const STATUS_COLORS = {
  PENDING: "neutral",
  IN_PROGRESS: "blue",
  COMPLETED: "green",
  FAILED: "red",
};

const STAGE_LABELS = {
  QUEUED: "Waiting for an analysis worker...",
  FETCHING_GAMES: "Fetching recent games...",
  ANALYZING: "Stockfish is evaluating your moves...",
  GENERATING_REPORT: "Generating your Chess DNA coaching report...",
  COMPLETED: "Analysis complete",
  FAILED: "Analysis failed",
};

// ── Chess pieces for the animated board ────────────────────────────────────
const PIECES = ["♔", "♕", "♖", "♗", "♘", "♙", "♚", "♛", "♜", "♝", "♞", "♟"];

export default function AnalysisProgress({
                                           username, platform, mode, depth, gameCount, jobId, status, elapsed, progress,
                                         }) {
  const [factIndex, setFactIndex] = useState(0);
  const [fadeIn, setFadeIn] = useState(true);

  // Rotate fun facts every 5 seconds with fade transition
  useEffect(() => {
    const interval = setInterval(() => {
      setFadeIn(false);
      setTimeout(() => {
        setFactIndex((i) => (i + 1) % FUN_FACTS.length);
        setFadeIn(true);
      }, 400);
    }, 5000);
    return () => clearInterval(interval);
  }, []);

  const isActive  = status === "IN_PROGRESS";
  const isPending = status === "PENDING";

  // Pick the right stage label based on elapsed seconds
  const currentStage = [...STAGES]
      .reverse()
      .find((s) => elapsed >= s.after) || STAGES[0];

  const elapsedDisplay = elapsed > 0
      ? elapsed >= 60
          ? `${Math.floor(elapsed / 60)}m ${elapsed % 60}s`
          : `${elapsed}s`
      : null;
  const platformLabel = platform === "chesscom" ? "Chess.com" : "Lichess";
  const completedGames = progress?.completedGames || 0;
  const progressPercent = progress?.stage === "GENERATING_REPORT"
      ? 95
      : Math.min(90, Math.round((completedGames / Math.max(1, gameCount)) * 90));
  const stageLabel = STAGE_LABELS[progress?.stage] || currentStage.label;

  return (
      <div style={{
        background: "linear-gradient(135deg, #0d1117 0%, #161b22 100%)",
        border: "1px solid #30363d",
        borderRadius: 16,
        padding: 36,
        maxWidth: 600,
        margin: "0 auto",
      }}>

        {/* ── Animated chess board ─────────────────────────────────────────── */}
        <div style={{
          display: "grid",
          gridTemplateColumns: "repeat(8, 1fr)",
          gap: 2,
          marginBottom: 32,
          borderRadius: 8,
          overflow: "hidden",
          border: "1px solid #21262d",
          opacity: 0.6,
        }}>
          {Array.from({ length: 64 }, (_, i) => {
            const row = Math.floor(i / 8);
            const col = i % 8;
            const isLight = (row + col) % 2 === 0;
            // Randomly place a few pieces
            const hasPiece = [4, 11, 19, 27, 36, 44, 52, 59].includes(i);
            const piece = hasPiece ? PIECES[i % PIECES.length] : null;

            return (
                <div
                    key={i}
                    style={{
                      width: "100%",
                      paddingBottom: "100%",
                      position: "relative",
                      background: isLight ? "#2d2417" : "#1a1408",
                    }}
                >
                  {piece && (
                      <div style={{
                        position: "absolute",
                        inset: 0,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        fontSize: 14,
                        color: i % 16 < 8 ? "#c9a84c" : "#e8e0cc",
                        animation: `chessPulse ${1.5 + (i % 3) * 0.5}s ease-in-out infinite`,
                        animationDelay: `${(i % 5) * 0.3}s`,
                      }}>
                        {piece}
                      </div>
                  )}
                </div>
            );
          })}
        </div>

        {/* ── Header ───────────────────────────────────────────────────────── */}
        <div style={{ textAlign: "center", marginBottom: 28 }}>
          <div style={{ fontSize: 20, fontWeight: 700, color: "#e8e0cc", marginBottom: 8 }}>
            Analyzing {username}
          </div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "center" }}>
            <Badge variant={mode === "quick" ? "neutral" : "blue"}>
              {mode === "quick" ? "⚡ Quick" : "🔬 Deep"}
            </Badge>
            <Badge variant="neutral">depth {depth}</Badge>
            <Badge variant="neutral">{gameCount} games</Badge>
            <Badge variant="neutral">{platformLabel}</Badge>
            <Badge variant={STATUS_COLORS[status] || "neutral"}>{status}</Badge>
          </div>
        </div>

        {/* ── Progress bar ─────────────────────────────────────────────────── */}
        <div style={{
          height: 4,
          borderRadius: 2,
          background: "#21262d",
          overflow: "hidden",
          marginBottom: 12,
        }}>
          {isActive && progress?.stage === "ANALYZING" && (
              <div style={{
                height: "100%",
                width: `${progressPercent}%`,
                background: "linear-gradient(90deg, #8b6f2e, #c9a84c)",
                borderRadius: 2,
                transition: "width 0.4s ease",
              }} />
          )}
          {isActive && progress?.stage !== "ANALYZING" && (
              <div style={{
                height: "100%",
                width: progress?.stage === "GENERATING_REPORT" ? "95%" : "40%",
                background: "linear-gradient(90deg, transparent, #c9a84c, transparent)",
                borderRadius: 2,
                animation: "indeterminate 1.8s ease-in-out infinite",
              }} />
          )}
          {isPending && (
              <div style={{ height: "100%", width: "8%", background: "#30363d", borderRadius: 2 }} />
          )}
        </div>

        {/* ── Current stage ────────────────────────────────────────────────── */}
        <div style={{
          fontSize: 13,
          color: "#c9a84c",
          textAlign: "center",
          marginBottom: 28,
          minHeight: 20,
          fontStyle: "italic",
        }}>
          {isActive ? stageLabel : isPending ? "Waiting to start..." : status}
          {progress?.stage === "ANALYZING" && (
              <span style={{ color: "#8b949e", marginLeft: 8 }}>
                ({completedGames}/{gameCount} games)
              </span>
          )}
          {elapsedDisplay && (
              <span style={{ color: "#8b949e", marginLeft: 8 }}>({elapsedDisplay})</span>
          )}
        </div>

        {/* ── Fun fact card ─────────────────────────────────────────────────── */}
        <div style={{
          background: "#0d1117",
          border: "1px solid #21262d",
          borderRadius: 10,
          padding: "16px 20px",
          transition: "opacity 0.4s ease",
          opacity: fadeIn ? 1 : 0,
        }}>
          <div style={{
            fontSize: 10,
            letterSpacing: "0.15em",
            textTransform: "uppercase",
            color: "#c9a84c",
            marginBottom: 8,
            fontWeight: 600,
          }}>
            While you wait
          </div>
          <div style={{
            fontSize: 13,
            color: "#8b949e",
            lineHeight: 1.7,
          }}>
            {FUN_FACTS[factIndex]}
          </div>
        </div>

        {/* ── Footer ───────────────────────────────────────────────────────── */}
        <div style={{
          fontSize: 11,
          color: "#484f58",
          textAlign: "center",
          marginTop: 20,
        }}>
          Job #{jobId} · Polling every 5 seconds
          {progress?.cacheHits > 0 && ` · ${progress.cacheHits} cached game${progress.cacheHits === 1 ? "" : "s"}`}
        </div>

        <style>{`
        @keyframes indeterminate {
          0%   { transform: translateX(-200%); }
          100% { transform: translateX(350%); }
        }
        @keyframes chessPulse {
          0%, 100% { opacity: 0.4; transform: scale(0.9); }
          50%       { opacity: 1;   transform: scale(1.1); }
        }
      `}</style>
      </div>
  );
}
