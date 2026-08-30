// src/constants/analysisConfig.js
//
// WHY THIS FILE EXISTS:
// Instead of scattering magic numbers (12, 18, 5000) throughout the codebase,
// we define them once here. If we change polling interval from 5s to 3s,
// we change it in ONE place, not 10 places.
// Same idea as Java's public static final constants.

export const DEPTH = {
  QUICK: 8,
  DEEP: 12,
};

export const PLATFORMS = {
  chesscom: {
    key: "chesscom",
    label: "Chess.com",
    example: "Hikaru",
  },
  lichess: {
    key: "lichess",
    label: "Lichess",
    example: "DrNykterstein",
  },
};

export const POLLING_INTERVAL_MS = 5000; // 5 seconds between job status checks

export const GAME_COUNT = {
  MIN: 1,
  MAX: 50,
  DEFAULT: 10,
  PRESETS: [5, 10, 20, 50],
};

export const ANALYSIS_MODES = {
  quick: {
    key: "quick",
    label: "Quick analysis",
    sub: `Depth ${DEPTH.QUICK}`,
    badge: "Recommended",
    bullets: ["Faster results", "Best for pattern detection"],
    depth: DEPTH.QUICK,
    secondsPerGame: [10, 15],
  },
  deep: {
    key: "deep",
    label: "Deep analysis",
    sub: `Depth ${DEPTH.DEEP}`,
    badge: "Most accurate",
    bullets: ["More accurate", "Detailed coaching insights"],
    depth: DEPTH.DEEP,
    secondsPerGame: [18, 25],
  },
};

export const JOB_STATUS = {
  PENDING: "PENDING",
  IN_PROGRESS: "IN_PROGRESS",
  COMPLETED: "COMPLETED",
  FAILED: "FAILED",
};

export const PHASE_COLORS = {
  opening: "#58a6ff",
  middlegame: "#d29922",
  endgame: "#3fb950",
};
