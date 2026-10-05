package net.runelite.client.plugins.microbot.chatbot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Continuously logs overheard in-game player chat messages to a dedicated dataset file
 * for dialect learning and 5-hour language synthesis.
 */
@Slf4j
public class ChatbotDatasetLogger {

    private static final Gson GSON = new Gson();
    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ChatbotDatasetLogger");
        t.setDaemon(true);
        return t;
    });

    private static File getDatasetDir() {
        File dir = new File(RuneLite.RUNELITE_DIR, "chatbot-dataset");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Asynchronously records an observed chat message with game context to disk.
     */
    public static void logMessage(String sender, String message, String chatType) {
        if (sender == null || message == null || message.trim().isEmpty()) {
            return;
        }

        final String cleanSender = sender.trim();
        final String cleanMsg = message.trim();
        final String location = ChatbotContextService.getLocationName();
        final String activity = ChatbotContextService.getActivityDescription();
        final int world = Rs2Player.getWorld();
        final String timestamp = Instant.now().toString();

        LOG_EXECUTOR.submit(() -> {
            try {
                File dir = getDatasetDir();
                File jsonlFile = new File(dir, "osrs_chat_log.jsonl");
                File textFile = new File(dir, "osrs_chat_log.txt");

                // 1. JSONL Entry
                JsonObject obj = new JsonObject();
                obj.addProperty("timestamp", timestamp);
                obj.addProperty("sender", cleanSender);
                obj.addProperty("chatType", chatType != null ? chatType : "public");
                obj.addProperty("message", cleanMsg);
                obj.addProperty("location", location);
                obj.addProperty("world", world);
                obj.addProperty("playerActivity", activity);

                try (BufferedWriter bw = new BufferedWriter(new FileWriter(jsonlFile, StandardCharsets.UTF_8, true))) {
                    bw.write(GSON.toJson(obj));
                    bw.newLine();
                }

                // 2. Human-readable text log
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(textFile, StandardCharsets.UTF_8, true))) {
                    String line = String.format("[%s] [W%d] [%s] [%s] (%s): %s",
                            timestamp, world, location, cleanSender, chatType != null ? chatType : "public", cleanMsg);
                    bw.write(line);
                    bw.newLine();
                }
            } catch (Exception e) {
                log.debug("[Chatbot] Could not write to dataset log: {}", e.getMessage());
            }
        });
    }
}
