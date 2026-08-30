package com.chessdna.chessdnaanalyzer;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String platform;
    private int gameCount;
    private String status;
    private String stage;
    private int depth;
    private int completedGames;
    private int cacheHits;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(columnDefinition = "TEXT")
    private String coachingReport;
    @Column(columnDefinition = "TEXT")
    private String personalityJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPlatform() { return platform == null ? ChessPlatform.LICHESS.apiValue() : platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public int getGameCount() { return gameCount; }
    public void setGameCount(int gameCount) { this.gameCount = gameCount; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public int getCompletedGames() { return completedGames; }
    public void setCompletedGames(int completedGames) { this.completedGames = completedGames; }
    public int getCacheHits() { return cacheHits; }
    public void setCacheHits(int cacheHits) { this.cacheHits = cacheHits; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getCoachingReport() { return coachingReport; }
    public void setCoachingReport(String coachingReport) { this.coachingReport = coachingReport; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getPersonalityJson() { return personalityJson; }
    public void setPersonalityJson(String personalityJson) { this.personalityJson = personalityJson; }
}
