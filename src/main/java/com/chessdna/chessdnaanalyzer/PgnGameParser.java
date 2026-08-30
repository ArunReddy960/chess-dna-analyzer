package com.chessdna.chessdnaanalyzer;

import chesspresso.game.Game;
import chesspresso.pgn.PGNReader;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PgnGameParser {

    private static final Pattern GAME_BOUNDARY = Pattern.compile("(?m)(?=^\\[Event\\s)");

    public List<String> splitGames(String rawPgn) {
        if (rawPgn == null || rawPgn.isBlank()) {
            return List.of();
        }

        List<String> games = new ArrayList<>();
        for (String candidate : GAME_BOUNDARY.split(rawPgn.trim())) {
            if (!candidate.isBlank()) {
                games.add(candidate.trim());
            }
        }
        return games;
    }

    public List<String> extractFens(String pgnText) {
        List<String> fens = new ArrayList<>();
        try {
            // Chess.com PGNs include clock/evaluation comments that Chesspresso can
            // silently treat as the end of the main line. The analysis only needs
            // moves, so remove comments and numeric annotation glyphs first.
            String normalizedPgn = pgnText
                    .replaceAll("\\{[^}]*}", " ")
                    .replaceAll("(?m)^\\s*;.*$", " ")
                    .replaceAll("\\$\\d+", " ");

            PGNReader reader = new PGNReader(new StringReader(normalizedPgn), "game");
            Game game = reader.parseGame();
            game.gotoStart();
            while (game.hasNextMove()) {
                game.goForward();
                fens.add(game.getPosition().getFEN());
            }
            return fens;
        } catch (Exception e) {
            throw new GameProviderException("Failed to parse a game returned by the chess platform.", e);
        }
    }

    public ChessGame toGame(String username, ChessPlatform platform, String pgn, String fallbackUrl) {
        String white = tag(pgn, "White");
        String black = tag(pgn, "Black");
        PlayerColor color;
        if (username.equalsIgnoreCase(white)) {
            color = PlayerColor.WHITE;
        } else if (username.equalsIgnoreCase(black)) {
            color = PlayerColor.BLACK;
        } else {
            throw new GameProviderException("The returned PGN does not contain player '" + username + "'.");
        }

        String sourceUrl = firstNonBlank(fallbackUrl, tag(pgn, "Site"));
        String id = sourceUrl == null ? sha256(pgn) : sourceUrl;
        return new ChessGame(id, platform, sourceUrl, pgn, color);
    }

    String tag(String pgn, String name) {
        Pattern pattern = Pattern.compile("(?mi)^\\[" + Pattern.quote(name) + "\\s+\"([^\"]*)\"\\]$");
        Matcher matcher = pattern.matcher(pgn);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
