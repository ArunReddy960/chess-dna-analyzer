import PhaseCard from "./PhaseCard";
import Badge from "./Badge";
import { depthToMode } from "../services/analysisApi";

const RISK_COLORS = {
    Low:     { bg: "rgba(46,160,67,0.1)",  border: "#2ea043", text: "#3fb950" },
    Medium:  { bg: "rgba(210,153,34,0.1)", border: "#d2991e", text: "#e3b341" },
    High:    { bg: "rgba(248,113,113,0.1)",border: "#f87171", text: "#f87171" },
    Extreme: { bg: "rgba(248,81,73,0.15)", border: "#f85149", text: "#f85149" },
};

const RISK_FILL = { Low: 25, Medium: 50, High: 75, Extreme: 100 };

function parsePersonality(personalityJson) {
    if (!personalityJson) return null;
    try {
        const data = typeof personalityJson === "string"
            ? JSON.parse(personalityJson)
            : personalityJson;
        return data.personality || data;
    } catch {
        return null;
    }
}

function parseReport(coachingReport) {
    if (!coachingReport) return null;
    try {
        const data = typeof coachingReport === "string"
            ? JSON.parse(coachingReport)
            : coachingReport;
        return data.report || data;
    } catch {
        return null;
    }
}

function ReportSection({ emoji, title, children }) {
    return (
        <div style={{
            background: "#0d1117",
            border: "1px solid #21262d",
            borderRadius: 10,
            padding: "18px 20px",
            marginBottom: 12,
        }}>
            <div style={{
                fontSize: 11,
                fontWeight: 700,
                letterSpacing: "0.12em",
                textTransform: "uppercase",
                color: "#c9a84c",
                marginBottom: 10,
            }}>
                {emoji} {title}
            </div>
            <div style={{ fontSize: 14, color: "#c9d1d9", lineHeight: 1.8 }}>
                {children}
            </div>
        </div>
    );
}

function ActionItem({ number, text }) {
    return (
        <div style={{
            display: "flex",
            gap: 14,
            padding: "12px 0",
            borderBottom: "1px solid #21262d",
        }}>
            <div style={{
                width: 26,
                height: 26,
                borderRadius: "50%",
                background: "rgba(201,168,76,0.15)",
                border: "1px solid rgba(201,168,76,0.3)",
                color: "#c9a84c",
                fontSize: 12,
                fontWeight: 700,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
                marginTop: 2,
            }}>
                {number}
            </div>
            <div style={{ fontSize: 14, color: "#c9d1d9", lineHeight: 1.75 }}>
                {text}
            </div>
        </div>
    );
}

export default function ResultsDashboard({
                                             username, platform, phases, report: rawReport, depth, personalityJson, onNewAnalysis,
                                         }) {
    const personality = parsePersonality(personalityJson);
    const report      = parseReport(rawReport);
    const modeLabel   = depthToMode(depth) === "quick" ? "Quick" : "Deep";
    const displayName = username || "Player";
    const riskStyle   = RISK_COLORS[personality?.riskLevel] || RISK_COLORS.Medium;
    const platformLabel = platform === "chesscom" ? "Chess.com" : "Lichess";

    return (
        <div style={{ maxWidth: 760, margin: "0 auto" }}>

            {/* Header */}
            <div style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "flex-start",
                marginBottom: 24,
                flexWrap: "wrap",
                gap: 12,
            }}>
                <div>
                    <div style={{ fontSize: 20, fontWeight: 700, color: "#e8e0cc", marginBottom: 6 }}>
                        KnightLens Report
                        <span style={{ color: "#c9a84c" }}> — {displayName}</span>
                    </div>
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                        <Badge variant={modeLabel === "Quick" ? "neutral" : "blue"}>{modeLabel}</Badge>
                        <Badge variant="neutral">depth {depth}</Badge>
                        <Badge variant="neutral">{platformLabel}</Badge>
                    </div>
                </div>
                <button onClick={onNewAnalysis} style={{
                    padding: "7px 16px",
                    borderRadius: 8,
                    background: "transparent",
                    border: "1px solid #30363d",
                    color: "#8b949e",
                    cursor: "pointer",
                    fontSize: 12,
                }}>
                    ← New analysis
                </button>
            </div>

            {/* PERSONALITY CARD */}
            {personality && (
                <div style={{
                    background: "linear-gradient(135deg, #161b22 0%, #1c2128 100%)",
                    border: "1px solid #c9a84c",
                    borderRadius: 14,
                    padding: 28,
                    marginBottom: 24,
                    position: "relative",
                    overflow: "hidden",
                }}>
                    <div style={{
                        position: "absolute",
                        top: -20, right: -20,
                        fontSize: 120,
                        opacity: 0.04,
                        lineHeight: 1,
                        userSelect: "none",
                    }}>♞</div>

                    <div style={{ marginBottom: 20 }}>
                        <div style={{
                            fontSize: 10,
                            letterSpacing: "0.2em",
                            textTransform: "uppercase",
                            color: "#c9a84c",
                            marginBottom: 8,
                            fontWeight: 600,
                        }}>
                            Your Chess DNA
                        </div>
                        <div style={{
                            fontSize: 22,
                            fontWeight: 800,
                            color: "#e8e0cc",
                            marginBottom: 6,
                            letterSpacing: "-0.01em",
                        }}>
                            {personality.archetype}
                        </div>
                        <div style={{ fontSize: 14, color: "#8b949e", fontStyle: "italic" }}>
                            "{personality.tagline}"
                        </div>
                    </div>

                    <div style={{
                        display: "grid",
                        gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))",
                        gap: 12,
                        marginBottom: 20,
                    }}>
                        <div style={{ background: "#0d1117", borderRadius: 8, padding: "12px 14px", border: "1px solid #21262d" }}>
                            <div style={{ fontSize: 10, color: "#8b949e", textTransform: "uppercase", letterSpacing: "0.1em", marginBottom: 4 }}>Play Style</div>
                            <div style={{ fontSize: 13, color: "#e8e0cc", fontWeight: 600 }}>{personality.playStyle}</div>
                        </div>

                        <div style={{ background: "#0d1117", borderRadius: 8, padding: "12px 14px", border: "1px solid #21262d" }}>
                            <div style={{ fontSize: 10, color: "#3fb950", textTransform: "uppercase", letterSpacing: "0.1em", marginBottom: 4 }}>✦ Strength</div>
                            <div style={{ fontSize: 13, color: "#e8e0cc", fontWeight: 600, lineHeight: 1.4 }}>{personality.strength}</div>
                        </div>

                        <div style={{ background: "#0d1117", borderRadius: 8, padding: "12px 14px", border: "1px solid #21262d" }}>
                            <div style={{ fontSize: 10, color: "#f87171", textTransform: "uppercase", letterSpacing: "0.1em", marginBottom: 4 }}>✦ Blind Spot</div>
                            <div style={{ fontSize: 13, color: "#e8e0cc", fontWeight: 600, lineHeight: 1.4 }}>{personality.blindSpot}</div>
                        </div>

                        <div style={{ background: riskStyle.bg, borderRadius: 8, padding: "12px 14px", border: `1px solid ${riskStyle.border}` }}>
                            <div style={{ fontSize: 10, color: riskStyle.text, textTransform: "uppercase", letterSpacing: "0.1em", marginBottom: 4 }}>Risk Level</div>
                            <div style={{ fontSize: 13, color: riskStyle.text, fontWeight: 700, marginBottom: 6 }}>{personality.riskLevel}</div>
                            <div style={{ height: 4, background: "#21262d", borderRadius: 2, overflow: "hidden" }}>
                                <div style={{
                                    height: "100%",
                                    width: `${RISK_FILL[personality.riskLevel] || 50}%`,
                                    background: riskStyle.border,
                                    borderRadius: 2,
                                }} />
                            </div>
                        </div>
                    </div>

                    {personality.similarGM && (
                        <div style={{
                            background: "rgba(201,168,76,0.06)",
                            border: "1px solid rgba(201,168,76,0.2)",
                            borderRadius: 8,
                            padding: "14px 16px",
                            display: "flex",
                            gap: 14,
                            alignItems: "flex-start",
                        }}>
                            <div style={{ fontSize: 28, lineHeight: 1, flexShrink: 0 }}>♛</div>
                            <div>
                                <div style={{ fontSize: 11, color: "#c9a84c", textTransform: "uppercase", letterSpacing: "0.1em", marginBottom: 4 }}>
                                    Your Game Reminds Us Of
                                </div>
                                <div style={{ fontSize: 15, fontWeight: 700, color: "#e8e0cc", marginBottom: 4 }}>{personality.similarGM}</div>
                                <div style={{ fontSize: 13, color: "#8b949e", lineHeight: 1.6 }}>{personality.gmDescription}</div>
                            </div>
                        </div>
                    )}
                </div>
            )}

            {/* PHASE PERFORMANCE */}
            {Array.isArray(phases) && phases.length > 0 && (
                <div style={{ background: "#161b22", border: "1px solid #21262d", borderRadius: 12, padding: 24, marginBottom: 24 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "#e8e0cc", marginBottom: 16, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                        Phase Performance
                    </div>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(210px, 1fr))", gap: 14 }}>
                        {phases.map((p) => <PhaseCard key={p.phaseName} phase={p} />)}
                    </div>
                </div>
            )}

            {/* COACHING REPORT */}
            {report && (
                <div style={{ background: "#161b22", border: "1px solid #21262d", borderRadius: 12, padding: 24, marginBottom: 24 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "#e8e0cc", marginBottom: 20, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                        Coaching Report
                    </div>

                    {report.overallAssessment && (
                        <ReportSection emoji="📊" title="Overall Assessment">{report.overallAssessment}</ReportSection>
                    )}
                    {report.biggestStrength && (
                        <ReportSection emoji="💪" title="Biggest Strength">{report.biggestStrength}</ReportSection>
                    )}
                    {report.primaryWeakness && (
                        <ReportSection emoji="🎯" title="Primary Weakness">{report.primaryWeakness}</ReportSection>
                    )}

                    {Array.isArray(report.actionPlan) && report.actionPlan.length > 0 && (
                        <div style={{ background: "#0d1117", border: "1px solid #21262d", borderRadius: 10, padding: "18px 20px", marginBottom: 12 }}>
                            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.12em", textTransform: "uppercase", color: "#c9a84c", marginBottom: 14 }}>
                                📋 Action Plan
                            </div>
                            {report.actionPlan.map((item, i) => <ActionItem key={i} number={i + 1} text={item} />)}
                        </div>
                    )}

                    {report.thisWeekFocus && (
                        <div style={{ background: "rgba(201,168,76,0.05)", border: "1px solid rgba(201,168,76,0.2)", borderRadius: 10, padding: "18px 20px" }}>
                            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.12em", textTransform: "uppercase", color: "#c9a84c", marginBottom: 16 }}>
                                🗓 This Week's Focus
                            </div>
                            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                                {report.thisWeekFocus.drill && (
                                    <div style={{ display: "flex", gap: 12 }}>
                                        <span style={{ fontSize: 16, flexShrink: 0 }}>⚙️</span>
                                        <div>
                                            <div style={{ fontSize: 11, color: "#8b949e", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 3 }}>Daily Drill</div>
                                            <div style={{ fontSize: 14, color: "#c9d1d9", lineHeight: 1.7 }}>{report.thisWeekFocus.drill}</div>
                                        </div>
                                    </div>
                                )}
                                {report.thisWeekFocus.studyThis && (
                                    <div style={{ display: "flex", gap: 12 }}>
                                        <span style={{ fontSize: 16, flexShrink: 0 }}>📖</span>
                                        <div>
                                            <div style={{ fontSize: 11, color: "#8b949e", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 3 }}>Study This</div>
                                            <div style={{ fontSize: 14, color: "#c9d1d9", lineHeight: 1.7 }}>{report.thisWeekFocus.studyThis}</div>
                                        </div>
                                    </div>
                                )}
                                {report.thisWeekFocus.inYourNextGame && (
                                    <div style={{ display: "flex", gap: 12 }}>
                                        <span style={{ fontSize: 16, flexShrink: 0 }}>♟</span>
                                        <div>
                                            <div style={{ fontSize: 11, color: "#8b949e", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 3 }}>In Your Next Game</div>
                                            <div style={{ fontSize: 14, color: "#c9d1d9", lineHeight: 1.7 }}>{report.thisWeekFocus.inYourNextGame}</div>
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            )}

        </div>
    );
}
