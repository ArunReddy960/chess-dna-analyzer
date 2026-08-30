// src/components/RecentAnalyses.jsx
//
// Shows history of recent completed analysis jobs.
// Uses persisted AnalysisJob data from the backend (PostgreSQL via JPA).
//
// WHY SEPARATE?
// This section is only visible on the form view, has its own data fetching,
// and has independent display logic. Keeping it separate avoids polluting App.jsx.
//
// PROPS:
//   jobs      — array of AnalysisJob objects from the backend
//   onOpen    — function(job) called when user clicks "View report"

import Badge from "./Badge";
import { depthToMode } from "../services/analysisApi";

const STATUS_VARIANT = {
  COMPLETED: "green",
  FAILED: "red",
  IN_PROGRESS: "blue",
  PENDING: "neutral",
};

function formatDate(dateStr) {
  if (!dateStr) return "–";
  try {
    const d = new Date(dateStr);
    return d.toLocaleDateString(undefined, {
      month: "short", day: "numeric",
      hour: "2-digit", minute: "2-digit",
    });
  } catch {
    return "–";
  }
}

function JobRow({ job, onOpen }) {
  const mode = depthToMode(job.depth);

  return (
    <div style={{
      display: "flex",
      alignItems: "center",
      gap: 12,
      padding: "12px 0",
      borderBottom: "1px solid #21262d",
      flexWrap: "wrap",
    }}>
      {/* Username */}
      <div style={{ fontSize: 13, fontWeight: 600, color: "#e6edf3", minWidth: 120 }}>
        {job.username || "–"}
      </div>

      {/* Badges */}
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", flex: 1 }}>
        <Badge variant={mode === "quick" ? "neutral" : "blue"}>
          {mode === "quick" ? "Quick" : "Deep"}
        </Badge>
        <Badge variant="neutral">depth {job.depth ?? "–"}</Badge>
        <Badge variant="neutral">{job.gameCount ?? "–"} games</Badge>
        <Badge variant="neutral">{job.platform === "chesscom" ? "Chess.com" : "Lichess"}</Badge>
      </div>

      {/* Status */}
      <Badge variant={STATUS_VARIANT[job.status] || "neutral"}>
        {job.status}
      </Badge>

      {/* Date */}
      <span style={{ fontSize: 11, color: "#8b949e" }}>
        {formatDate(job.createdAt)}
      </span>

      {/* Open button — only for completed jobs */}
      {job.status === "COMPLETED" && (
        <button
          onClick={() => onOpen(job)}
          style={{
            padding: "4px 14px",
            borderRadius: "var(--radius-sm)",
            background: "transparent",
            border: "1px solid #30363d",
            color: "#58a6ff",
            cursor: "pointer",
            fontSize: 12,
            fontWeight: 500,
          }}
        >
          View report
        </button>
      )}
    </div>
  );
}

export default function RecentAnalyses({ jobs, onOpen }) {
  if (!Array.isArray(jobs) || jobs.length === 0) return null;

  return (
    <div style={{
      background: "#161b22",
      border: "1px solid #21262d",
      borderRadius: 12,
      padding: 24,
    }}>
      <div style={{ fontSize: 14, fontWeight: 600, color: "#e6edf3", marginBottom: 4 }}>
        Recent analyses
      </div>
      <div style={{ fontSize: 12, color: "#8b949e", marginBottom: 16 }}>
        Previously completed jobs
      </div>
      {jobs.slice(0, 8).map((job) => (
        <JobRow key={job.id} job={job} onOpen={onOpen} />
      ))}
    </div>
  );
}
