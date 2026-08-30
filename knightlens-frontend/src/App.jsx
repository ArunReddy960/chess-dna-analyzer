// src/App.jsx
//
// ROOT COMPONENT — the entry point of the application.
// After refactoring, App.jsx's ONLY job is:
//   1. Managing top-level view state (which "page" are we on?)
//   2. Orchestrating data flow between child components
//   3. Running the polling effect
//
// It does NOT contain form logic, styling definitions, or API call details.
// Those live in their own files.
//
// Think of it like a Spring Boot Controller:
//   - It receives requests (user actions)
//   - Calls the right service (API functions)
//   - Passes results to the right view (component)
// It doesn't contain business logic itself.

import { useState, useEffect, useRef } from "react";
import "./styles/global.css";

import AnalysisForm from "./components/AnalysisForm";
import AnalysisProgress from "./components/AnalysisProgress";
import ResultsDashboard from "./components/ResultsDashboard";
import RecentAnalyses from "./components/RecentAnalyses";
import ErrorAlert from "./components/ErrorAlert";

import {
  startQuickAnalysis,
  startDeepAnalysis,
  getJobStatus,
  getRecentJobs,
  parsePhaseStats,
  depthToMode,
} from "./services/analysisApi";

import { JOB_STATUS, DEPTH } from "./constants/analysisConfig";

// ── VIEW NAMES ─────────────────────────────────────────────────────────────────
// Using string constants instead of magic strings prevents typos.
// Same reason Java uses enums instead of String comparisons.
const VIEWS = {
  FORM:     "form",
  PROGRESS: "progress",
  RESULT:   "result",
};

export default function App() {
  // ── TOP-LEVEL VIEW STATE ───────────────────────────────────────────────────
  const [view, setView] = useState(VIEWS.FORM);
  console.log("Current view:", view);

  // ── FORM STATE (preserved across retries) ─────────────────────────────────
  const [formValues, setFormValues] = useState({
    username: "", gameCount: 10, mode: "quick", platform: "chesscom",
  });

  // ── JOB STATE ─────────────────────────────────────────────────────────────
  const [jobId, setJobId] = useState(null);
  const [jobStatus, setJobStatus] = useState(null);
  const [jobDepth, setJobDepth] = useState(null);
  const [elapsed, setElapsed] = useState(0);
  const [jobProgress, setJobProgress] = useState(null);

  // ── RESULT STATE ──────────────────────────────────────────────────────────
  const [phases, setPhases] = useState(null);
  const [report, setReport] = useState(null);
  const [personalityJson, setPersonalityJson] = useState(null);
  const [resultUsername, setResultUsername] = useState("");
  const [resultDepth, setResultDepth] = useState(null);
  const [resultPlatform, setResultPlatform] = useState("lichess");

  // ── UI STATE ──────────────────────────────────────────────────────────────
  const [loading, setLoading] = useState(false);   // true while POST is in flight
  const [error, setError] = useState(null);         // user-facing error message
  const [history, setHistory] = useState([]);       // recent completed jobs

  // ── REFS ──────────────────────────────────────────────────────────────────
  // useRef stores values that persist across renders WITHOUT causing re-renders.
  // WHY for interval? If we stored the interval ID in useState, updating it
  // would cause a re-render, which could start another polling loop.
  // useRef is the correct tool for "I need to remember this but it's not UI data."
  const intervalRef = useRef(null);
  const elapsedIntervalRef = useRef(null);

  // ── LOAD HISTORY ON MOUNT ─────────────────────────────────────────────────
  // useEffect with empty [] runs ONCE when the component first appears on screen.
  // Equivalent to @PostConstruct in Spring Boot — initialization code.
  useEffect(() => {
    getRecentJobs()
      .then((data) => setHistory(Array.isArray(data) ? data : []))
      .catch(() => {}); // history is non-critical — silently ignore failures
  }, []);

  // ── POLLING EFFECT ────────────────────────────────────────────────────────
  // Runs whenever jobId or jobStatus changes.
  // If jobId is null or job is done, immediately returns (no polling needed).
  // Otherwise starts polling every 5 seconds.
  useEffect(() => {
    // Stop conditions — clear any existing interval and exit
    if (!jobId) return;
    if (jobStatus === JOB_STATUS.COMPLETED || jobStatus === JOB_STATUS.FAILED) {
      clearInterval(intervalRef.current);
      clearInterval(elapsedIntervalRef.current);
      return;
    }

    // Elapsed time counter (updates every second for UX display)
    elapsedIntervalRef.current = setInterval(() => {
      setElapsed((e) => e + 1);
    }, 1000);

    // POLLING: check job status every 5 seconds
    intervalRef.current = setInterval(async () => {
      try {
        const data = await getJobStatus(jobId);

        // Prevent overwriting a COMPLETED state with an in-flight response
        // that arrived late. Once COMPLETED, we stop updating status.
        if (jobStatus === JOB_STATUS.COMPLETED) return;

        setJobStatus(data.status);
        setJobProgress(data);

        if (data.status === JOB_STATUS.COMPLETED) {
          // Parse phase data safely — returns null if invalid
          const parsedPhases = parsePhaseStats(data.resultJson);
          setPhases(parsedPhases);
          setReport(data.coachingReport || null);
          setPersonalityJson(data.personalityJson || null);
          setResultUsername(data.username || formValues.username);
          setResultDepth(data.depth);
          setResultPlatform(data.platform || formValues.platform);
          setView(VIEWS.RESULT);
          // Add to history
          setHistory((h) => [data, ...h.filter((j) => j.id !== data.id)]);
        }

        if (data.status === JOB_STATUS.FAILED) {
          setError(data.errorMessage || "Analysis failed. Try a different username or fewer games.");
          setView(VIEWS.FORM);
        }

      } catch {
        // IMPORTANT: A single network failure should NOT destroy a completed result.
        // We only show errors if we're still in the progress view.
        if (view === VIEWS.PROGRESS) {
          setError("Lost connection to server. Is Spring Boot running on port 8080?");
          setView(VIEWS.FORM);
        }
      }
    }, 5000);

    // CLEANUP FUNCTION:
    // React calls this before re-running the effect AND when the component unmounts.
    // WHY? Without cleanup, old intervals keep running even after new ones start,
    // causing multiple simultaneous polls, memory leaks, and race conditions.
    return () => {
      clearInterval(intervalRef.current);
      clearInterval(elapsedIntervalRef.current);
    };
  }, [jobId, jobStatus]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── SUBMIT HANDLER ────────────────────────────────────────────────────────
  const handleSubmit = async (username, gameCount, mode, platform) => {
    // Prevent duplicate submissions
    if (loading) return;

    setError(null);
    setPhases(null);
    setReport(null);
    setPersonalityJson(null);
    setJobId(null);
    setJobStatus(null);
    setElapsed(0);
    setJobProgress(null);
    setLoading(true);

    // Preserve form values so Retry can restore them
    setFormValues({ username, gameCount, mode, platform });

    try {
      const apiFn = mode === "quick" ? startQuickAnalysis : startDeepAnalysis;
      const job = await apiFn(username, gameCount, platform);

      setJobId(job.id);
      setJobStatus(job.status);
      setJobDepth(job.depth ?? (mode === "quick" ? DEPTH.QUICK : DEPTH.DEEP));
      setJobProgress(job);
      setView(VIEWS.PROGRESS);
    } catch (err) {
      // Show the error but keep the form — don't navigate away
      setError(err.message || "Failed to start analysis.");
    } finally {
      setLoading(false);
    }
  };

  // ── RETRY HANDLER ─────────────────────────────────────────────────────────
  const handleRetry = () => {
    // Stop any active polling before returning to form
    clearInterval(intervalRef.current);
    clearInterval(elapsedIntervalRef.current);
    setError(null);
    setJobId(null);
    setJobStatus(null);
    setView(VIEWS.FORM);
    // formValues are preserved — user sees their previous inputs
  };

  // ── OPEN HISTORY ITEM ─────────────────────────────────────────────────────
  const handleOpenHistory = (job) => {
    // Restore all values from the saved job — not the current form values
    const parsedPhases = parsePhaseStats(job.resultJson);
    setPhases(parsedPhases);
    setReport(job.coachingReport || null);
    setPersonalityJson(job.personalityJson || null);
    // IMPORTANT: use the job's own username/depth, not current form state
    setResultUsername(job.username || "Player");
    setResultDepth(job.depth);
    setResultPlatform(job.platform || "lichess");
    setView(VIEWS.RESULT);
  };

  // ── RENDER ────────────────────────────────────────────────────────────────
  return (
    <div style={{ minHeight: "100vh", background: "var(--color-bg-primary)", color: "var(--color-text-primary)" }}>

      {/* HEADER */}
      <header style={{
        borderBottom: "1px solid var(--color-border)",
        padding: "18px 32px",
        display: "flex",
        alignItems: "center",
        gap: 12,
      }}>
        <span aria-hidden="true" style={{ fontSize: 22 }}>♞</span>
        <div>
          <div style={{ fontSize: 18, fontWeight: 700, color: "var(--color-accent)", margin: 0 }}>
            KnightLens
          </div>
          <div style={{ fontSize: 12, color: "var(--color-text-muted)", margin: 0 }}>
            Chess performance intelligence powered by Stockfish
          </div>
        </div>
      </header>

      {/* SUPPORTING TEXT — shown only on form view */}
      {view === VIEWS.FORM && (
        <div style={{
          maxWidth: 860,
          margin: "0 auto",
          padding: "28px 20px 0",
          fontSize: 14,
          color: "var(--color-text-muted)",
          lineHeight: 1.6,
        }}>
          Analyze your recent games and uncover recurring weaknesses across the opening, middlegame, and endgame.
        </div>
      )}

      {/* MAIN CONTENT */}
      <main style={{ maxWidth: 860, margin: "0 auto", padding: "24px 20px" }}>

        {/* Global error (shown above any view) */}
        {error && view !== VIEWS.FORM && (
          <ErrorAlert message={error} onRetry={handleRetry} />
        )}

        {/* FORM VIEW */}
        {view === VIEWS.FORM && (
          <>
            <AnalysisForm
              onSubmit={handleSubmit}
              initialValues={formValues}
              loading={loading}
              error={error}
              onRetry={handleRetry}
            />
            <RecentAnalyses jobs={history} onOpen={handleOpenHistory} />
          </>
        )}

        {/* PROGRESS VIEW */}
        {view === VIEWS.PROGRESS && (
          <AnalysisProgress
            username={formValues.username}
            platform={formValues.platform}
            mode={depthToMode(jobDepth) || formValues.mode}
            depth={jobDepth}
            gameCount={formValues.gameCount}
            jobId={jobId}
            status={jobStatus}
            elapsed={elapsed}
            progress={jobProgress}
          />
        )}

        {/* RESULT VIEW */}
        {view === VIEWS.RESULT && (
            <ResultsDashboard
                username={resultUsername}
                phases={phases}
                report={report}
                personalityJson={personalityJson}
                depth={resultDepth}
                platform={resultPlatform}
                onNewAnalysis={() => setView(VIEWS.FORM)}
            />
        )}

      </main>
    </div>
  );
}
