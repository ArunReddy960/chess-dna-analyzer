// src/components/AnalysisForm.jsx
//
// The main input form — username, game count, mode selection, submit button.
// WHY SEPARATE?
// This has its own validation logic and UI state (game count error, etc.)
// Keeping it separate from App.jsx means App only handles top-level
// navigation between views (form / progress / result).
//
// PROPS:
//   onSubmit     — async function(username, gameCount, mode) called on valid submit
//   initialValues — object to restore form values after error (username, gameCount, mode)
//   loading      — boolean, true while POST request is in flight
//   error        — string or null, error to display

import { useState } from "react";
import { ANALYSIS_MODES, GAME_COUNT, PLATFORMS } from "../constants/analysisConfig";
import AnalysisModeCard from "./AnalysisModeCard";
import ErrorAlert from "./ErrorAlert";

export default function AnalysisForm({ onSubmit, initialValues = {}, loading, error, onRetry }) {
    const [username, setUsername] = useState(initialValues.username || "");
    const [gameCount, setGameCount] = useState(initialValues.gameCount || GAME_COUNT.DEFAULT);
    const [gameCountInput, setGameCountInput] = useState(String(initialValues.gameCount || GAME_COUNT.DEFAULT));
    const [gameCountError, setGameCountError] = useState("");
    const [mode, setMode] = useState(initialValues.mode || "quick");
    const [platform, setPlatform] = useState(initialValues.platform || "chesscom");

    // ── GAME COUNT VALIDATION ─────────────────────────────────────────────────
    // WHY CLIENT-SIDE VALIDATION?
    // Shows errors immediately without a network round-trip.
    // Backend still validates — this is just UX improvement.
    const handleGameCountChange = (val) => {
        setGameCountInput(val);
        const n = parseInt(val, 10);
        if (!val.trim()) {
            setGameCountError("Enter a number.");
            return;
        }
        if (isNaN(n) || !Number.isInteger(Number(val))) {
            setGameCountError("Must be a whole number.");
            return;
        }
        if (n < GAME_COUNT.MIN) {
            setGameCountError(`Minimum ${GAME_COUNT.MIN} game.`);
            return;
        }
        if (n > GAME_COUNT.MAX) {
            setGameCountError(`Maximum ${GAME_COUNT.MAX} games.`);
            return;
        }
        setGameCountError("");
        setGameCount(n);
    };

    const setPreset = (n) => {
        setGameCount(n);
        setGameCountInput(String(n));
        setGameCountError("");
    };

    // ── ESTIMATED TIME ────────────────────────────────────────────────────────
    // Recalculates live when mode or gameCount changes.
    const estimatedTime = () => {
        const cfg = ANALYSIS_MODES[mode];
        if (!cfg) return "";
        const [lo, hi] = cfg.secondsPerGame;
        const loTotal = Math.ceil(lo * gameCount / 60);
        const hiTotal = Math.ceil(hi * gameCount / 60);
        if (hiTotal < 1) return `${lo * gameCount}–${hi * gameCount}s`;
        if (loTotal === hiTotal) return `~${loTotal} min`;
        return `${loTotal}–${hiTotal} min`;
    };

    const isValid =
        username.trim().length > 0 &&
        !gameCountError &&
        gameCount >= GAME_COUNT.MIN &&
        gameCount <= GAME_COUNT.MAX;

    const handleSubmit = async () => {
        if (!isValid || loading) return;

        onSubmit(username.trim(), gameCount, mode, platform);
    };

    const modeConfig = ANALYSIS_MODES[mode];
    const buttonLabel = loading
        ? "Starting analysis..."
        : `Analyze with ${modeConfig?.label?.split(" ")[0] || mode}`;

    return (
        <div>
            <ErrorAlert message={error} onRetry={onRetry}/>

            <div style={{
                background: "#161b22",
                border: "1px solid #21262d",
                borderRadius: 12,
                padding: 28,
                marginBottom: 24,
            }}>
                <div style={{fontSize: 15, fontWeight: 600, color: "#e6edf3", marginBottom: 20}}>
                    Analyze a player
                </div>

                {/* PLATFORM SELECTOR */}
                <div style={{marginBottom: 18}}>
                    <div
                        id="platform-label"
                        style={{display: "block", fontSize: 12, color: "#8b949e", marginBottom: 8, fontWeight: 500}}
                    >
                        Chess platform
                    </div>
                    <div
                        role="radiogroup"
                        aria-labelledby="platform-label"
                        style={{display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10}}
                    >
                        {Object.values(PLATFORMS).map((item) => {
                            const selected = platform === item.key;
                            return (
                                <button
                                    key={item.key}
                                    type="button"
                                    role="radio"
                                    aria-checked={selected}
                                    onClick={() => setPlatform(item.key)}
                                    style={{
                                        padding: "12px 16px",
                                        borderRadius: "var(--radius-md)",
                                        background: selected ? "#1f3a5f" : "#0d1117",
                                        border: `1px solid ${selected ? "#58a6ff" : "#30363d"}`,
                                        color: selected ? "#e6edf3" : "#8b949e",
                                        cursor: "pointer",
                                        fontSize: 13,
                                        fontWeight: 600,
                                    }}
                                >
                                    {item.label}
                                </button>
                            );
                        })}
                    </div>
                </div>

                {/* USERNAME INPUT */}
                <label
                    htmlFor="player-username"
                    style={{display: "block", fontSize: 12, color: "#8b949e", marginBottom: 6, fontWeight: 500}}
                >
                    {PLATFORMS[platform].label} username
                </label>
                <input
                    id="player-username"
                    type="text"
                    placeholder={`e.g. ${PLATFORMS[platform].example}`}
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && handleSubmit()}
                    autoComplete="off"
                    spellCheck={false}
                    aria-label={`${PLATFORMS[platform].label} username`}
                    style={{
                        width: "100%",
                        background: "#0d1117",
                        border: "1px solid #30363d",
                        borderRadius: "var(--radius-md)",
                        padding: "10px 14px",
                        color: "#e6edf3",
                        fontSize: 14,
                        outline: "none",
                    }}
                />

                {/* GAME COUNT */}
                <div style={{marginTop: 18}}>
                    <label
                        htmlFor="game-count"
                        style={{display: "block", fontSize: 12, color: "#8b949e", marginBottom: 6, fontWeight: 500}}
                    >
                        Games to analyze
                    </label>

                    {/* Preset buttons */}
                    <div style={{display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 8}} role="group"
                         aria-label="Game count presets">
                        {GAME_COUNT.PRESETS.map((n) => (
                            <button
                                key={n}
                                onClick={() => setPreset(n)}
                                aria-pressed={gameCount === n && !gameCountError}
                                style={{
                                    padding: "5px 16px",
                                    borderRadius: "var(--radius-sm)",
                                    fontSize: 12,
                                    fontWeight: 500,
                                    cursor: "pointer",
                                    background: gameCount === n && !gameCountError ? "#1f3a5f" : "#0d1117",
                                    border: `1px solid ${gameCount === n && !gameCountError ? "#58a6ff" : "#30363d"}`,
                                    color: gameCount === n && !gameCountError ? "#58a6ff" : "#8b949e",
                                }}
                            >
                                {n}
                            </button>
                        ))}

                        {/* Custom number input */}
                        <input
                            id="game-count"
                            type="number"
                            min={GAME_COUNT.MIN}
                            max={GAME_COUNT.MAX}
                            step={1}
                            value={gameCountInput}
                            onChange={(e) => handleGameCountChange(e.target.value)}
                            aria-label="Custom game count"
                            aria-describedby={gameCountError ? "game-count-error" : undefined}
                            aria-invalid={!!gameCountError}
                            style={{
                                width: 72,
                                background: "#0d1117",
                                border: `1px solid ${gameCountError ? "#da3633" : "#30363d"}`,
                                borderRadius: "var(--radius-md)",
                                padding: "5px 10px",
                                color: "#e6edf3",
                                fontSize: 13,
                                outline: "none",
                            }}
                        />
                    </div>

                    {/* Inline validation error */}
                    {gameCountError && (
                        <div id="game-count-error" role="alert" style={{fontSize: 12, color: "#f85149", marginTop: 4}}>
                            {gameCountError}
                        </div>
                    )}
                </div>

                {/* ANALYSIS MODE SELECTOR */}
                <div style={{marginTop: 18}}>
                    <div
                        style={{fontSize: 12, color: "#8b949e", marginBottom: 8, fontWeight: 500}}
                        id="mode-label"
                    >
                        Analysis depth
                    </div>
                    {/* role="radiogroup" is correct ARIA for mutually exclusive choices */}
                    <div
                        role="radiogroup"
                        aria-labelledby="mode-label"
                        style={{display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10}}
                    >
                        {Object.values(ANALYSIS_MODES).map((m) => (
                            <AnalysisModeCard
                                key={m.key}
                                mode={m}
                                selected={mode === m.key}
                                onSelect={setMode}
                            />
                        ))}
                    </div>
                </div>

                {/* SUBMIT + TIME ESTIMATE */}
                <div style={{marginTop: 20, display: "flex", alignItems: "center", gap: 16, flexWrap: "wrap"}}>
                    <button
                        onClick={handleSubmit}
                        disabled={!isValid || loading}
                        aria-busy={loading}
                        style={{
                            padding: "10px 28px",
                            borderRadius: "var(--radius-md)",
                            fontSize: 14,
                            fontWeight: 600,
                            cursor: isValid && !loading ? "pointer" : "not-allowed",
                            background: isValid && !loading ? "#1f6feb" : "#21262d",
                            border: `1px solid ${isValid && !loading ? "#388bfd" : "#30363d"}`,
                            color: isValid && !loading ? "#fff" : "#8b949e",
                        }}
                    >
                        {buttonLabel}
                    </button>

                    <div style={{fontSize: 12, color: "#8b949e"}}>
                        Estimated:{" "}
                        <span style={{color: "#e6edf3", fontWeight: 500}}>{estimatedTime()}</span>
                        <span style={{marginLeft: 6, fontSize: 11}}>· varies by game complexity</span>
                    </div>
                </div>
            </div>
        </div>
    );
}
