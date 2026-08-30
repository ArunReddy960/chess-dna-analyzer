// src/services/analysisApi.js
//
// WHY THIS FILE EXISTS:
// This is the SERVICE LAYER — exactly like your Spring Boot @Service classes.
// All network calls live here. Components never call fetch() directly.
//
// Benefits:
// 1. One place to change if the API URL or structure changes
// 2. Consistent error handling — components just get clean data or a clear error
// 3. Easy to mock in tests
// 4. Same separation of concerns as Controller → Service → Repository in Spring Boot

// ENVIRONMENT VARIABLE:
// Vite (the build tool) exposes env vars prefixed with VITE_ via import.meta.env
// In production, VITE_API_BASE_URL would be set to your deployed backend URL
// Falls back to localhost for local development
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

// ── HELPER ────────────────────────────────────────────────────────────────────
// Wraps every fetch call with consistent error handling.
// Returns the parsed JSON on success, throws a user-friendly Error on failure.
// WHY async/await? Same as Java's CompletableFuture — lets us write async code
// that reads like synchronous code, no callback nesting.
async function apiFetch(url, options = {}) {
  let response;
  try {
    response = await fetch(url, options);
  } catch {
    // fetch() itself throws only on network failure (server unreachable, DNS error)
    // NOT on HTTP error codes (404, 500) — those come back as response.ok = false
    throw new Error("Could not reach the server. Is your Spring Boot app running on port 8080?");
  }

  if (!response.ok) {
    // HTTP error — try to get message from body, fall back to status text
    let message = `Server error (${response.status})`;
    try {
      const body = await response.json();
      if (body.message) message = body.message;
      else if (body.error) message = body.error;
    } catch {
      // body wasn't JSON — use status text
      message = `Server returned ${response.status}: ${response.statusText}`;
    }
    throw new Error(message);
  }

  try {
    return await response.json();
  } catch {
    throw new Error("Server returned an unexpected response format.");
  }
}

// ── PUBLIC API FUNCTIONS ──────────────────────────────────────────────────────

/**
 * Start a quick analysis (depth 8) for a player on the selected platform.
 * Maps to: POST /api/games/{username}/analyze-quick?gameCount=N
 * Returns the AnalysisJob object with id, status, etc.
 */
export async function startQuickAnalysis(username, gameCount, platform = "lichess") {
  return apiFetch(
    `${API_BASE_URL}/api/games/${encodeURIComponent(username)}/analyze-quick?gameCount=${gameCount}&platform=${encodeURIComponent(platform)}`,
    { method: "POST" }
  );
}

/**
 * Start a deep analysis (depth 12) for a player on the selected platform.
 * Maps to: POST /api/games/{username}/analyze-deep?gameCount=N
 */
export async function startDeepAnalysis(username, gameCount, platform = "lichess") {
  return apiFetch(
    `${API_BASE_URL}/api/games/${encodeURIComponent(username)}/analyze-deep?gameCount=${gameCount}&platform=${encodeURIComponent(platform)}`,
    { method: "POST" }
  );
}

/**
 * Poll for the current status of an analysis job.
 * Maps to: GET /api/games/jobs/{jobId}
 * Returns the full AnalysisJob — including resultJson and coachingReport when COMPLETED.
 */
export async function getJobStatus(jobId) {
  return apiFetch(`${API_BASE_URL}/api/games/jobs/${jobId}`);
}

/**
 * Fetch recent completed analysis jobs for the history panel.
 * Maps to: GET /api/games/jobs/recent
 * Returns array of AnalysisJob objects.
 */
export async function getRecentJobs() {
  return apiFetch(`${API_BASE_URL}/api/games/jobs/recent`);
}

/**
 * Safely parse the resultJson string from a completed job.
 * Returns array of PhaseStats, or null if parsing fails.
 * WHY: resultJson is stored as a TEXT column in PostgreSQL (raw JSON string).
 * We must convert it back to a JS object before using it.
 */
export function parsePhaseStats(resultJson) {
  if (!resultJson) return null;
  try {
    const parsed = JSON.parse(resultJson);
    if (!Array.isArray(parsed)) return null;
    return parsed;
  } catch {
    return null;
  }
}

/**
 * Determine analysis mode label from depth integer.
 * depth 8 → "quick", depth 12 → "deep", anything else → "custom"
 */
export function depthToMode(depth) {
  if (depth === 8) return "quick";
  if (depth === 12) return "deep";
  return "custom";
}
