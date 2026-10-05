package net.runelite.client.plugins.microbot.chatbot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;

import javax.inject.Inject;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class ChatbotScript extends Script {

    private final ChatbotPlugin plugin;
    private final ChatbotConfig config;

    /**
     * Queue of incoming chat messages that need a response.
     */
    private final ConcurrentLinkedQueue<IncomingMessage> messageQueue = new ConcurrentLinkedQueue<>();

    /**
     * Conversation history for context.
     */
    private final LinkedList<OpenAiService.ChatMessage> conversationHistory = new LinkedList<>();

    /**
     * Timestamp of last response sent (for cooldown).
     */
    private volatile long lastResponseTime = 0;
    private volatile long currentCooldownMs = 0;

    /**
     * State tracking for overlay & logging.
     */
    private volatile int messagesReceived = 0;
    private volatile int responsesSent = 0;
    private volatile String lastStatus = "Idle";
    private volatile Color statusColor = Color.GRAY;
    private volatile long thinkingStartTime = 0;
    private volatile String currentQuery = "";
    private volatile String lastResponse = "";
    private volatile String lastReasoning = "";
    private volatile String activeModel = "";

    @Inject
    public ChatbotScript(ChatbotPlugin plugin, ChatbotConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Simple container for an incoming message with receipt timestamp.
     */
    public static class IncomingMessage {
        final String sender;
        final String message;
        final String chatType;
        final long timestamp;

        public IncomingMessage(String sender, String message, String chatType) {
            this.sender = sender;
            this.message = message;
            this.chatType = chatType;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Enqueue a message to be processed.
     */
    /**
     * Enqueue a message to be processed.
     */
    public void enqueueMessage(String sender, String message, String chatType) {
        String cleanSender = sender != null ? sender.replace('\u00A0', ' ').trim() : "";
        String cleanMessage = message != null ? message.replace('\u00A0', ' ').trim() : "";

        if (cleanSender.isEmpty() || cleanMessage.isEmpty()) {
            return;
        }

        // Record all observed chat messages to local dataset for language analysis
        if (config.logChatToFile()) {
            ChatbotDatasetLogger.logMessage(cleanSender, cleanMessage, chatType);
        }

        // Apply filters before queueing for bot response
        if (!passesFilters(cleanSender, cleanMessage, chatType)) {
            return;
        }
        messagesReceived++;
        messageQueue.offer(new IncomingMessage(cleanSender, cleanMessage, chatType));
        log.info("[Chatbot] Queued message from {} ({}): {}", cleanSender, chatType, cleanMessage);
    }

    private boolean passesFilters(String sender, String message, String chatType) {
        // Ignore self
        if (config.ignoreSelf() && Microbot.isLoggedIn() && Microbot.getClient() != null) {
            if (Microbot.getClient().getLocalPlayer() != null) {
                String rawLocal = Microbot.getClient().getLocalPlayer().getName();
                if (rawLocal != null) {
                    String localName = rawLocal.replace('\u00A0', ' ').trim();
                    if (localName.equalsIgnoreCase(sender)) {
                        log.debug("[Chatbot] Ignored message from self ('{}')", sender);
                        return false;
                    }
                }
            }
        }

        // 1. Filter GE trade, gambling, and giveaway spam
        if (config.filterGeTradeSpam() && isTradeOrGamblingSpam(message)) {
            log.debug("[Chatbot] Ignored trade/gambling spam from {}: {}", sender, message);
            return false;
        }

        // 2. Only respond to specific names
        String onlyNames = config.onlyRespondToNames().trim();
        if (!onlyNames.isEmpty()) {
            Set<String> allowed = Arrays.stream(onlyNames.split("[,;]"))
                .map(String::trim)
                .map(s -> s.replace('\u00A0', ' '))
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
            if (!allowed.contains(sender.toLowerCase())) {
                return false;
            }
        }

        // 3. Ignore specific names
        String ignoreList = config.ignoreNames().trim();
        if (!ignoreList.isEmpty()) {
            Set<String> ignored = Arrays.stream(ignoreList.split("[,;]"))
                .map(String::trim)
                .map(s -> s.replace('\u00A0', ' '))
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
            if (ignored.contains(sender.toLowerCase())) {
                return false;
            }
        }

        // 4. Max distance filter
        if (config.maxDistanceTiles() > 0 && !isWithinDistance(sender, config.maxDistanceTiles())) {
            return false;
        }

        // 5. Trigger words and "only when addressed" work as OR logic:
        //    If EITHER the message contains a trigger word OR the player is directly addressed, it passes.
        //    If BOTH are disabled/empty, all messages pass this stage.
        String triggerStr = config.triggerKeyword().trim();
        boolean hasTriggerWords = !triggerStr.isEmpty();
        boolean hasAddressedFilter = config.onlyRespondWhenAddressed();

        if (hasTriggerWords || hasAddressedFilter) {
            boolean triggerMatch = false;
            boolean addressedMatch = false;

            // Check trigger words
            if (hasTriggerWords) {
                String[] keywords = triggerStr.split("[,;]");
                String lowerMsg = message.toLowerCase();
                for (String kw : keywords) {
                    String cleanKw = kw.trim().replace("\"", "").replace("'", "").toLowerCase();
                    if (!cleanKw.isEmpty() && lowerMsg.contains(cleanKw)) {
                        triggerMatch = true;
                        log.info("[Chatbot] Trigger word '{}' matched in message from {}: {}", cleanKw, sender, message);
                        break;
                    }
                }
            }

            // Check if directly addressed
            if (hasAddressedFilter) {
                addressedMatch = ChatbotAddressService.isDirectlyAddressed(sender, message, chatType, config);
                if (addressedMatch) {
                    log.info("[Chatbot] Direct address matched from {}: {}", sender, message);
                }
            }

            // OR logic: pass if either trigger word matched or player was addressed
            // If only one filter is active, only that one needs to match
            boolean passes;
            if (hasTriggerWords && hasAddressedFilter) {
                passes = triggerMatch || addressedMatch;
            } else if (hasTriggerWords) {
                passes = triggerMatch;
            } else {
                passes = addressedMatch;
            }

            if (!passes) {
                log.debug("[Chatbot] Filtered message from {} (no trigger/address match): {}", sender, message);
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if the message matches common GE spam patterns (merchant ads, gambling, dicing, giveaways).
     */
    public static boolean isTradeOrGamblingSpam(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase().trim();

        // Gambling, rolls, dicing
        if (lower.matches(".*\\b(5[4-6]x2|roll|dicing|dice|cashing|cashed|win\\s*double)\\b.*")
            || lower.contains("55x2") || lower.contains("54x2") || lower.contains("legit host")) {
            return true;
        }

        // Trade shouts (buying / selling)
        if (lower.startsWith("buying ") || lower.startsWith("selling ") || lower.startsWith("buy ") || lower.startsWith("sell ")) {
            return true;
        }
        if (lower.matches(".*\\b(buying|selling|buy|sell)\\b.*\\b(ea|gp|k|m|b|all|cmp|bulk|overpay|under\\s*ge)\\b.*")) {
            return true;
        }

        // Giveaways, phishing, socials, quitting
        if (lower.contains("youtube") || lower.contains("twitch") || lower.contains("kick.com")
            || lower.contains(".com") || lower.contains(".net") || lower.contains(".org")
            || lower.contains("quitting") || lower.contains("giving away") || lower.contains("leaving osrs")
            || lower.contains("doubling") || lower.contains("double gp") || lower.contains("free gp")) {
            return true;
        }

        return false;
    }

    private boolean isWithinDistance(String sender, int maxTiles) {
        if (maxTiles <= 0) {
            return true;
        }
        try {
            Rs2PlayerModel localPlayer = Rs2Player.getLocalPlayer();
            Rs2PlayerModel senderPlayer = Rs2Player.getPlayer(sender);
            if (localPlayer == null || senderPlayer == null) {
                return true;
            }
            WorldPoint myLoc = localPlayer.getWorldLocation();
            WorldPoint sLoc = senderPlayer.getWorldLocation();
            if (myLoc != null && sLoc != null) {
                int dist = myLoc.distanceTo(sLoc);
                if (dist > maxTiles) {
                    log.debug("[Chatbot] Sender {} is {} tiles away (max: {})", sender, dist, maxTiles);
                    return false;
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private boolean isDirectlyAddressed(String sender, String message, String chatType) {
        return ChatbotAddressService.isDirectlyAddressed(sender, message, chatType, config);
    }

    public static boolean mentionsPlayerName(String message, String playerName) {
        return ChatbotAddressService.mentionsPlayerName(message, playerName, "");
    }

    public static boolean isDirectGreetingOrQuestion(String message) {
        return ChatbotAddressService.isDirectGreetingOrQuestion(message);
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        lastStatus = "Listening...";
        statusColor = Color.GRAY;
        messagesReceived = 0;
        responsesSent = 0;
        conversationHistory.clear();
        messageQueue.clear();
        thinkingStartTime = 0;
        currentQuery = "";
        lastResponse = "";
        lastReasoning = "";
        activeModel = ChatbotModel.getModelName(config);
        currentCooldownMs = 0;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                // Check API key is configured (unless custom provider where key may be optional)
                String apiKey = config.openAiApiKey().trim();
                if (apiKey.isEmpty() && ChatbotModel.getEffectiveProvider(config) != ChatbotProvider.CUSTOM) {
                    lastStatus = "No API Key set!";
                    statusColor = Color.RED;
                    return;
                }

                // Cooldown check: strictly enforce delay between sent messages (3-15 secs)
                long now = System.currentTimeMillis();
                long elapsed = now - lastResponseTime;
                if (lastResponseTime > 0 && elapsed < currentCooldownMs) {
                    long remainingSec = (currentCooldownMs - elapsed + 999) / 1000;
                    lastStatus = "Cooldown (" + remainingSec + "s)";
                    statusColor = new Color(180, 180, 180);
                    // Prevent backlog accumulation during cooldown: keep only newest message
                    while (messageQueue.size() > 1) {
                        messageQueue.poll();
                    }
                    return;
                }

                // Process next message from queue
                IncomingMessage incoming = messageQueue.poll();
                if (incoming == null) {
                    if (lastResponseTime > 0 && elapsed < currentCooldownMs + 2000) {
                        lastStatus = "Ready";
                        statusColor = Color.GREEN;
                    } else {
                        lastStatus = "Listening...";
                        statusColor = Color.GRAY;
                    }
                    return;
                }

                // Drop stale messages waiting longer than 30s
                if (now - incoming.timestamp > 30000) {
                    log.info("[Chatbot] Discarded stale message from {} (waited {}s): {}",
                            incoming.sender, (now - incoming.timestamp) / 1000, incoming.message);
                    return;
                }

                // Entering Thinking / Reasoning state
                lastStatus = "Thinking / Reasoning...";
                statusColor = Color.CYAN;
                thinkingStartTime = System.currentTimeMillis();
                currentQuery = incoming.sender + ": " + incoming.message;
                log.info("[Chatbot] Processing message from {}: {}", incoming.sender, incoming.message);

                // Add user message to conversation history
                String userContent = incoming.sender + ": " + incoming.message;
                conversationHistory.add(new OpenAiService.ChatMessage("user", userContent));

                // Trim conversation history
                while (conversationHistory.size() > config.conversationMemory()) {
                    conversationHistory.removeFirst();
                }

                // Build messages list for AI with live game context
                List<OpenAiService.ChatMessage> apiMessages = new ArrayList<>();
                String systemPrompt = config.systemPrompt();
                if (config.enableGameContext()) {
                    String contextBlock = ChatbotContextService.buildContextPrompt(config);
                    if (!contextBlock.isEmpty()) {
                        systemPrompt = systemPrompt + "\n" + contextBlock;
                    }
                }
                apiMessages.add(new OpenAiService.ChatMessage("system", systemPrompt));
                apiMessages.addAll(conversationHistory);

                // Parse temperature
                double temp;
                try {
                    temp = Double.parseDouble(config.temperature());
                } catch (NumberFormatException e) {
                    temp = 0.7;
                }

                // Call AI
                OpenAiService.CompletionResult result = OpenAiService.getCompletionResult(
                    ChatbotModel.getEffectiveProvider(config),
                    apiKey,
                    ChatbotModel.getModelName(config),
                    config.customApiUrl(),
                    apiMessages,
                    config.maxTokens(),
                    temp
                );

                thinkingStartTime = 0;

                if (result == null || result.getContent() == null || result.getContent().isEmpty()) {
                    lastStatus = "API Error";
                    statusColor = Color.RED;
                    log.warn("[Chatbot] Empty or failed response from AI provider");
                    return;
                }

                String response = result.getContent();
                lastReasoning = result.getReasoning() != null ? result.getReasoning() : "";
                activeModel = result.getModelUsed() + (result.isFallbackUsed() ? " (fallback)" : "");

                // Humanize response: OSRS lowercase slang, strip periods, simulate typos
                int maxLen = Math.max(20, config.maxResponseLength());
                response = ChatbotSpeechHumanizer.humanize(response, config, maxLen);
                lastResponse = response;

                // Add assistant response to history
                conversationHistory.add(new OpenAiService.ChatMessage("assistant", response));

                // Human-like typing delay before speaking
                int delayMin = Math.max(500, config.responseDelayMin());
                int delayMax = Math.max(delayMin, config.responseDelayMax());
                int delay = delayMin + (int) (Math.random() * (delayMax - delayMin));
                lastStatus = "Typing...";
                statusColor = Color.ORANGE;
                sleep(delay);

                // Send the response in-game
                if ("clan".equalsIgnoreCase(incoming.chatType)) {
                    sendClanMessage(response);
                } else if ("friends".equalsIgnoreCase(incoming.chatType)) {
                    sendFriendsChatMessage(response);
                } else if (config.respondViaPublic()) {
                    sendPublicMessage(response);
                }

                lastResponseTime = System.currentTimeMillis();
                responsesSent++;
                ChatbotAddressService.recordConversationReply(incoming.sender);
                lastStatus = "Sent response";
                statusColor = Color.GREEN;

                // Calculate next randomized cooldown between 3 and 15 seconds
                int minSec = Math.max(1, config.cooldownMinSeconds());
                int maxSec = Math.max(minSec, config.cooldownMaxSeconds());
                currentCooldownMs = (minSec + (long) (Math.random() * (maxSec - minSec + 1))) * 1000L;

                log.info("[Chatbot] Responded: {}. Next cooldown: {}s", response, currentCooldownMs / 1000);

            } catch (Exception ex) {
                thinkingStartTime = 0;
                log.error("[Chatbot] Exception in main loop: ", ex);
                lastStatus = "Error: " + ex.getMessage();
                statusColor = Color.RED;
            }
        }, 0, 400, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Types and sends a message in the public chatbox.
     */
    private void sendPublicMessage(String message) {
        clearChatboxInput();
        Rs2Keyboard.typeString(message);
        sleep(200, 400);
        if (config.pressEnterToSend()) {
            Rs2Keyboard.enter();
        } else {
            log.info("[Chatbot] [TEST MODE - NO ENTER] Typed into chatbox: {}", message);
            sleep(1800, 2200);
            backspaceCharacters(message.length());
            log.info("[Chatbot] [TEST MODE] Backspaced and cleared test message from chatbox");
        }
    }

    /**
     * Sends a clan chat message.
     */
    private void sendClanMessage(String message) {
        clearChatboxInput();
        String clanCommand = "/c " + message;
        Rs2Keyboard.typeString(clanCommand);
        sleep(200, 400);
        if (config.pressEnterToSend()) {
            Rs2Keyboard.enter();
        } else {
            log.info("[Chatbot] [TEST MODE - NO ENTER] Typed into clan chat: {}", message);
            sleep(1800, 2200);
            backspaceCharacters(clanCommand.length());
            log.info("[Chatbot] [TEST MODE] Backspaced and cleared test message from clan chat");
        }
    }

    /**
     * Sends a friends chat message.
     */
    private void sendFriendsChatMessage(String message) {
        clearChatboxInput();
        String fcCommand = "/ " + message;
        Rs2Keyboard.typeString(fcCommand);
        sleep(200, 400);
        if (config.pressEnterToSend()) {
            Rs2Keyboard.enter();
        } else {
            log.info("[Chatbot] [TEST MODE - NO ENTER] Typed into friends chat: {}", message);
            sleep(1800, 2200);
            backspaceCharacters(fcCommand.length());
            log.info("[Chatbot] [TEST MODE] Backspaced and cleared test message from friends chat");
        }
    }

    /**
     * Backspaces the specified number of characters, or until the chatbox is empty.
     */
    private void backspaceCharacters(int count) {
        int total = Math.max(count, 0);
        try {
            if (Microbot.getClient() != null) {
                String typed = Microbot.getClient().getVarcStrValue(net.runelite.api.VarClientStr.CHATBOX_TYPED_TEXT);
                if (typed != null && !typed.isEmpty()) {
                    total = Math.max(total, typed.length());
                }
            }
        } catch (Exception ignored) {
        }
        total = Math.min(total + 5, 85);
        for (int i = 0; i < total; i++) {
            Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_BACK_SPACE);
            if (i % 8 == 0) {
                sleep(2, 5);
            }
        }
        sleep(30, 60);
    }

    /**
     * Clears any existing text in the chatbox input before typing a new message.
     */
    private void clearChatboxInput() {
        backspaceCharacters(80);
    }

    public int getMessagesReceived() {
        return messagesReceived;
    }

    public int getResponsesSent() {
        return responsesSent;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public String getDisplayStatus() {
        if (thinkingStartTime > 0) {
            long sec = (System.currentTimeMillis() - thinkingStartTime) / 1000;
            return "Thinking (" + sec + "s)...";
        }
        return lastStatus;
    }

    public Color getStatusColor() {
        return statusColor;
    }

    public String getCurrentQuery() {
        return currentQuery;
    }

    public String getLastResponse() {
        return lastResponse;
    }

    public String getLastReasoning() {
        return lastReasoning;
    }

    public String getActiveModelDisplay() {
        return activeModel;
    }



    @Override
    public void shutdown() {
        super.shutdown();
        messageQueue.clear();
        conversationHistory.clear();
        ChatbotAddressService.clearConversationThread();
        if (!config.pressEnterToSend()) {
            clearChatboxInput();
        }
        lastStatus = "Stopped";
        statusColor = Color.GRAY;
        thinkingStartTime = 0;
        currentQuery = "";
        lastResponse = "";
        lastReasoning = "";
    }
}