package net.runelite.client.plugins.microbot.chatbot;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms AI responses into authentic Old School RuneScape player speech:
 * casual lowercase, OSRS slang and abbreviations, removal of robotic periods,
 * and realistic minor typing imperfections (dropped letters, slips, doubled letters).
 */
public class ChatbotSpeechHumanizer {

    private static final Random RNG = new Random();

    // Word replacement patterns for OSRS slang
    private static final Object[][] SLANG_REPLACEMENTS = {
            {Pattern.compile("(?i)\\byou're\\b|\\byour\\b|\\byou are\\b"), "ur"},
            {Pattern.compile("(?i)\\byou\\b"), "u"},
            {Pattern.compile("(?i)\\bare\\b"), "r"},
            {Pattern.compile("(?i)\\bthank you\\b|\\bthanks\\b"), "ty"},
            {Pattern.compile("(?i)\\byou're welcome\\b|\\byour welcome\\b"), "yw"},
            {Pattern.compile("(?i)\\bno problem\\b"), "np"},
            {Pattern.compile("(?i)\\bgood luck\\b"), "gl"},
            {Pattern.compile("(?i)\\bgood fight\\b"), "gf"},
            {Pattern.compile("(?i)\\bcongratulations\\b|\\bcongrats\\b|\\bgrats\\b"), "gzz"},
            {Pattern.compile("(?i)\\bi don't know\\b|\\bdon't know\\b"), "idk"},
            {Pattern.compile("(?i)\\bto be honest\\b"), "tbh"},
            {Pattern.compile("(?i)\\bnever mind\\b|\\bnevermind\\b"), "nvm"},
            {Pattern.compile("(?i)\\bright now\\b"), "rn"},
            {Pattern.compile("(?i)\\bwhat about you\\b"), "wbu"},
            {Pattern.compile("(?i)\\bplease\\b"), "pls"},
            {Pattern.compile("(?i)\\bprobably\\b"), "prob"},
            {Pattern.compile("(?i)\\bdefinitely\\b"), "defs"},
            {Pattern.compile("(?i)\\bthough\\b"), "tho"},
            {Pattern.compile("(?i)\\bbecause\\b"), "cuz"},
            {Pattern.compile("(?i)\\bwoodcutting\\b"), "wc"},
            {Pattern.compile("(?i)\\bfiremaking\\b"), "fm"},
            {Pattern.compile("(?i)\\blevel\\b"), "lvl"},
            {Pattern.compile("(?i)\\bgrand exchange\\b"), "ge"},
            {Pattern.compile("(?i)\\byes\\b|\\byeah\\b"), "ye"}
    };

    /**
     * Formats and humanizes the raw AI completion according to user config.
     */
    public static String humanize(String text, ChatbotConfig config, int maxLength) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String result = text.trim();

        // 1. Remove surrounding quotes and unwanted prefix labels
        result = result.replaceAll("^[\"']+|[\"']+$", "").trim();
        result = result.replaceAll("(?i)^(player|bot|assistant|me):\\s*", "").trim();

        // 2. Strip trailing periods (real RS players never end chat with periods)
        while (result.endsWith(".") || result.endsWith(";") || result.endsWith(":")) {
            result = result.substring(0, result.length() - 1).trim();
        }

        // 3. Lowercase conversion for casual feel, while preserving acronyms
        if (config == null || config.osrsSlangStyle()) {
            result = result.toLowerCase();

            // Apply slang replacements
            for (Object[] pair : SLANG_REPLACEMENTS) {
                Pattern pat = (Pattern) pair[0];
                String rep = (String) pair[1];
                result = pat.matcher(result).replaceAll(rep);
            }
        }

        // 4. Simulate realistic typos & jumped/missing letters if enabled
        if (config != null && config.simulateTypos()) {
            result = applyRealisticTypos(result);
        }

        // 5. Safety Sanitization: Neutralize any accidental automation/test terminology
        result = sanitizeBotLeak(result);

        // 6. Clean up any leftover punctuation artifacts
        result = result.replaceAll("\\s+", " ").trim();
        while (result.endsWith(".") || result.endsWith(",")) {
            result = result.substring(0, result.length() - 1).trim();
        }

        // 7. Ensure length fits within chatbox limit without cutting off words
        if (result.length() > maxLength) {
            result = truncateSafely(result, maxLength);
        }

        return result;
    }

    /**
     * Catches and neutralizes any accidental automation/testing terminology
     * (e.g. 'testrunner', 'test runner', 'microbot', 'script', 'plugin').
     */
    public static String sanitizeBotLeak(String text) {
        if (text == null) return "";
        String s = text;
        s = s.replaceAll("(?i)\\btestrunner\\s+stuff\\b", "random stuff");
        s = s.replaceAll("(?i)\\btestrunner\\b", "stuff");
        s = s.replaceAll("(?i)\\btest\\s*runner\\b", "stuff");
        s = s.replaceAll("(?i)\\btest\\s*run\\b", "stuff");
        s = s.replaceAll("(?i)\\btestrunning\\b", "playing");
        s = s.replaceAll("(?i)\\bdoing\\s+testing\\b", "chilling");
        s = s.replaceAll("(?i)\\btest\\s*mode\\b", "chill mode");
        s = s.replaceAll("(?i)\\bmicrobot\\b", "osrs");
        s = s.replaceAll("(?i)\\bmy\\s+script\\b", "my game");
        s = s.replaceAll("(?i)\\bbot\\s*script\\b", "game");
        s = s.replaceAll("(?i)\\brating\\s+model\\b", "game");
        return s;
    }

    /**
     * Applies subtle, realistic typing slips common in fast OSRS chat.
     */
    private static String applyRealisticTypos(String input) {
        String[] words = input.split(" ");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];

            // 1. Missing 'g' on -ing words (e.g. chopping -> choppin, doing -> doin) (40% chance)
            if (word.length() > 4 && word.endsWith("ing") && RNG.nextInt(100) < 45) {
                word = word.substring(0, word.length() - 1);
            }
            // 2. Fast typing slip for "the" -> "teh", "for" -> "fir", "just" -> "jsut", "with" -> "wiht" (15% chance)
            else if (word.equals("the") && RNG.nextInt(100) < 15) {
                word = "teh";
            } else if (word.equals("for") && RNG.nextInt(100) < 15) {
                word = "fir";
            } else if (word.equals("just") && RNG.nextInt(100) < 15) {
                word = "jsut";
            } else if (word.equals("with") && RNG.nextInt(100) < 15) {
                word = "wiht";
            } else if (word.equals("what") && RNG.nextInt(100) < 20) {
                word = "wat";
            }
            // 3. Doubled letters for casual emphasis (e.g. "gz" -> "gzzz", "ye" -> "yee", "cool" -> "coool")
            else if (word.equals("gz") && RNG.nextInt(100) < 60) {
                word = RNG.nextBoolean() ? "gzz" : "gzzz";
            } else if (word.equals("lol") && RNG.nextInt(100) < 30) {
                word = "loool";
            } else if (word.equals("no") && RNG.nextInt(100) < 20) {
                word = "nooo";
            }

            sb.append(word);
            if (i < words.length - 1) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    /**
     * Truncates a string to maxLength at a word boundary, removing trailing connectors.
     */
    private static String truncateSafely(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        // Find last space before maxLength
        int cutIndex = text.lastIndexOf(' ', maxLength);
        if (cutIndex <= 0) {
            cutIndex = maxLength;
        }

        String trimmed = text.substring(0, cutIndex).trim();

        // Strip trailing dangling words like "and", "or", "but", "so", "to", "the"
        String[] dangling = {"and", "or", "but", "so", "to", "the", "a", "with", "of", "in", "is"};
        for (String d : dangling) {
            if (trimmed.toLowerCase().endsWith(" " + d)) {
                trimmed = trimmed.substring(0, trimmed.length() - d.length() - 1).trim();
            }
        }

        while (trimmed.endsWith(",") || trimmed.endsWith(".") || trimmed.endsWith("-") || trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        return trimmed;
    }
}
