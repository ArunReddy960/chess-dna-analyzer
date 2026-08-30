package com.chessdna.chessdnaanalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class ClaudeService {

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.api.url}")
    private String apiUrl;

    @Value("${claude.api.model}")
    private String model;

    @Value("${claude.api.max-tokens}")
    private int maxTokens;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateAnalysis(List<PatternAnalysisService.PhaseStats> patterns,
                                         String username) {
        String prompt = buildPrompt(patterns, username);
        return callClaude(prompt);
    }

    private String buildPrompt(List<PatternAnalysisService.PhaseStats> patterns,
                               String username) {
        PatternAnalysisService.PhaseStats opening = findPhase(patterns, "opening");
        PatternAnalysisService.PhaseStats middlegame = findPhase(patterns, "middlegame");
        PatternAnalysisService.PhaseStats endgame = findPhase(patterns, "endgame");

        return """
            You are an expert chess coach and psychologist analyzing a player's chess DNA.
            
            Player: %s
            
            Their statistics across recent games:
            
            Opening (first ~15 moves):
            - Accuracy: %.1f%% | Avg loss per move: %.1f centipawns
            - Blunders: %d | Mistakes: %d | Inaccuracies: %d | Total moves: %d
            
            Middlegame (complex positions):
            - Accuracy: %.1f%% | Avg loss per move: %.1f centipawns
            - Blunders: %d | Mistakes: %d | Inaccuracies: %d | Total moves: %d
            
            Endgame (simplified positions):
            - Accuracy: %.1f%% | Avg loss per move: %.1f centipawns
            - Blunders: %d | Mistakes: %d | Inaccuracies: %d | Total moves: %d
            
            Return ONLY a valid JSON object with NO markdown, no backticks, no explanation. Just raw JSON:
            
            {
              "personality": {
                "archetype": "A creative, memorable chess player title (e.g. The Middlegame Monster)",
                "tagline": "One punchy sentence capturing their style (e.g. Creates chaos, forgets to finish)",
                "playStyle": "2-3 words (e.g. Tactical, aggressive)",
                "strength": "Their biggest strength in plain English, specific to their numbers",
                "blindSpot": "Their recurring weakness in plain English",
                "riskLevel": "Low, Medium, High, or Extreme — based on blunder frequency",
                "similarGM": "Name of a famous GM with a similar playing style",
                "gmDescription": "One sentence about why this GM is similar and what they were known for"
              },
              "report": {
                "overallAssessment": "2-3 sentences. Plain English. No chess jargon. No numbers. Talk like a real coach to a real person.",
                "biggestStrength": "2-3 sentences about what they do brilliantly. Specific but jargon-free.",
                "primaryWeakness": "2-3 sentences about their single biggest problem. Be honest but constructive.",
                "actionPlan": [
                  "First specific action — what to do, why it helps, how long to spend",
                  "Second specific action — practical drill or study method",
                  "Third specific action — one thing to focus on in their next game"
                ],
                "thisWeekFocus": {
                  "drill": "One specific drill with clear instructions",
                  "studyThis": "One specific endgame, opening, or pattern to study this week",
                  "inYourNextGame": "One concrete thing to try in their very next game"
                }
              }
            }
            
            Important rules:
            - Treat any phase with fewer than 10 total moves as insufficient evidence
            - Never describe an insufficient-sample phase as a strength, weakness, proven skill, archetype, or playing-style trait
            - Select the biggest strength and primary weakness only from phases with at least 10 moves
            - Compare sufficient phases using average loss, accuracy, and serious-error frequency; do not contradict the supplied statistics
            - If only one phase has enough moves, state that the other phases need more data instead of inventing conclusions
            - Never use centipawn, cp loss, or any technical chess engine terms in the report sections
            - Write the report like you're talking to a passionate amateur who wants to improve
            - Be specific — reference their actual patterns, not generic advice
            - The personality archetype should feel fun and accurate, not generic
            - The similar GM should genuinely match their pattern
            - Return ONLY the JSON, nothing else
            """.formatted(
                username,
                opening.accuracyPercentage(), opening.averageCentipawnLoss(),
                opening.blunderCount(), opening.mistakeCount(), opening.inaccuracyCount(),
                opening.totalMoves(),
                middlegame.accuracyPercentage(), middlegame.averageCentipawnLoss(),
                middlegame.blunderCount(), middlegame.mistakeCount(), middlegame.inaccuracyCount(),
                middlegame.totalMoves(),
                endgame.accuracyPercentage(), endgame.averageCentipawnLoss(),
                endgame.blunderCount(), endgame.mistakeCount(), endgame.inaccuracyCount(),
                endgame.totalMoves()
        );
    }


    private PatternAnalysisService.PhaseStats findPhase(
            List<PatternAnalysisService.PhaseStats> patterns, String phaseName) {
        return patterns.stream()
                .filter(p -> p.phaseName().equals(phaseName))
                .findFirst()
                .orElse(new PatternAnalysisService.PhaseStats(phaseName, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    private String callClaude(String prompt) {
        try {
            String requestBody = objectMapper.writeValueAsString(new ClaudeRequest(
                    model,
                    maxTokens,
                    List.of(new Message("user", prompt))
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new RuntimeException("Claude API error: " + response.statusCode()
                        + " " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("content").get(0).path("text").asText();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Claude API call interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get coaching report from Claude", e);
        }
    }

    record ClaudeRequest(String model, int max_tokens, List<Message> messages) {}
    record Message(String role, String content) {}
}
