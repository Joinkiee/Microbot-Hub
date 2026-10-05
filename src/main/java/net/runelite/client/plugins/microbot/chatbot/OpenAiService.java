package net.runelite.client.plugins.microbot.chatbot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Service for communicating with OpenAI, Google Gemini, and custom OpenAI-compatible LLM endpoints.
 */
@Slf4j
public class OpenAiService {

    public static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    public static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    public static final String OPENCODE_ZEN_URL = "https://opencode.ai/zen/v1/chat/completions";
    public static final String OPENCODE_INFERENCE_URL = "https://opencode.ai/inference/openai/v1/chat/completions";
    public static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    public static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final Gson GSON = new Gson();

    /**
     * Represents a single message in the conversation.
     */
    public static class ChatMessage {
        private final String role;
        private final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * Detailed result of an AI completion, including thoughts/reasoning and fallback tracking.
     */
    public static class CompletionResult {
        private final String content;
        private final String reasoning;
        private final String modelUsed;
        private final boolean fallbackUsed;

        public CompletionResult(String content, String reasoning, String modelUsed, boolean fallbackUsed) {
            this.content = content;
            this.reasoning = reasoning;
            this.modelUsed = modelUsed;
            this.fallbackUsed = fallbackUsed;
        }

        public String getContent() {
            return content;
        }

        public String getReasoning() {
            return reasoning;
        }

        public String getModelUsed() {
            return modelUsed;
        }

        public boolean isFallbackUsed() {
            return fallbackUsed;
        }
    }

    /**
     * Backward-compatible overload for existing calls.
     */
    public static String getChatCompletion(String apiKey, String model, List<ChatMessage> messages,
                                           int maxTokens, double temperature) {
        return getChatCompletion(ChatbotProvider.OPENAI, apiKey, model, null, messages, maxTokens, temperature);
    }

    /**
     * Backward-compatible string-returning chat completion.
     */
    public static String getChatCompletion(ChatbotProvider provider, String apiKey, String model,
                                           String customUrl, List<ChatMessage> messages,
                                           int maxTokens, double temperature) {
        CompletionResult result = getCompletionResult(provider, apiKey, model, customUrl, messages, maxTokens, temperature);
        return result != null ? result.getContent() : null;
    }

    /**
     * Sends a chat completion request to the specified provider and returns the detailed result.
     * Automatically falls back to available Gemini models if rate limit (HTTP 429) or high demand (HTTP 503) occurs.
     */
    public static CompletionResult getCompletionResult(ChatbotProvider provider, String apiKey, String model,
                                                       String customUrl, List<ChatMessage> messages,
                                                       int maxTokens, double temperature) {
        String targetModel = normalizeModelName(provider, model);
        String targetUrl = resolveEndpointUrl(provider, customUrl);

        CompletionResult result = executeRequest(provider, targetUrl, apiKey, targetModel, messages, maxTokens, temperature, false);
        if (result != null) {
            return result;
        }

        // If Gemini failed (e.g. 503 high demand or 429 quota exhaustion on 3.8-flash),
        // automatically fallback to high-quota, ultra-fast flash-lite models that are active on Google's API
        if (provider == ChatbotProvider.GEMINI) {
            // First priority fallback: gemini-3.1-flash-lite (fastest ~1s, lowest latency, zero 503s)
            if (!targetModel.equals("gemini-3.1-flash-lite")) {
                log.info("[Chatbot] Request with '{}' busy/quota-limited. Rapid failover to 'gemini-3.1-flash-lite'...", targetModel);
                result = executeRequest(provider, targetUrl, apiKey, "gemini-3.1-flash-lite", messages, maxTokens, temperature, true);
                if (result != null) {
                    return result;
                }
            }
            // Second priority fallback: gemini-flash-lite-latest
            if (!targetModel.equals("gemini-flash-lite-latest")) {
                log.info("[Chatbot] Retrying with 'gemini-flash-lite-latest'...");
                result = executeRequest(provider, targetUrl, apiKey, "gemini-flash-lite-latest", messages, maxTokens, temperature, true);
                if (result != null) {
                    return result;
                }
            }
            // Third priority fallback: gemini-3.5-flash-lite
            if (!targetModel.equals("gemini-3.5-flash-lite")) {
                log.info("[Chatbot] Retrying with 'gemini-3.5-flash-lite'...");
                result = executeRequest(provider, targetUrl, apiKey, "gemini-3.5-flash-lite", messages, maxTokens, temperature, true);
                if (result != null) {
                    return result;
                }
            }
        }

        // If OpenCode failed on primary Zen endpoint, try inference endpoint
        if (provider == ChatbotProvider.OPENCODE) {
            if (!targetUrl.equals(OPENCODE_INFERENCE_URL)) {
                log.info("[Chatbot] Request to OpenCode Zen failed, trying OpenCode Inference endpoint...");
                result = executeRequest(provider, OPENCODE_INFERENCE_URL, apiKey, targetModel, messages, maxTokens, temperature, true);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private static String normalizeModelName(ChatbotProvider provider, String model) {
        if (provider == null) {
            provider = ChatbotProvider.GEMINI;
        }
        if (model == null || model.trim().isEmpty()) {
            switch (provider) {
                case GEMINI: return "gemini-flash-lite-latest";
                case OPENCODE: return "deepseek-chat";
                case GROQ: return "llama-3.3-70b-versatile";
                case OPENROUTER: return "deepseek/deepseek-chat:free";
                case OPENAI: return "gpt-4o-mini";
                default: return "custom";
            }
        }
        String clean = model.trim();
        if (provider == ChatbotProvider.GEMINI) {
            // Automatically upgrade deprecated models if user had them saved in RuneLite config
            if (clean.startsWith("gpt-") || clean.equalsIgnoreCase("default") ||
                clean.equals("gemini-2.0-flash") || clean.equals("gemini-1.5-flash") ||
                clean.equalsIgnoreCase("2.0 flash") || clean.equalsIgnoreCase("1.5 flash")) {
                return "gemini-flash-lite-latest";
            }
            if (clean.equalsIgnoreCase("3.8 flash") || clean.equalsIgnoreCase("3.8-flash") || clean.equalsIgnoreCase("gemini 3.8 flash") || clean.equalsIgnoreCase("gemini_3_8_flash")) {
                return "gemini-3.8-flash";
            }
            if (clean.equalsIgnoreCase("3.5 flash") || clean.equalsIgnoreCase("3.5-flash") || clean.equalsIgnoreCase("gemini 3.5 flash") || clean.equalsIgnoreCase("gemini_3_5_flash")) {
                return "gemini-3.5-flash";
            }
            if (clean.equalsIgnoreCase("3.5 flash lite") || clean.equalsIgnoreCase("3.5-flash-lite") || clean.equalsIgnoreCase("gemini 3.5 flash lite") || clean.equalsIgnoreCase("gemini_3_5_flash_lite")) {
                return "gemini-3.5-flash-lite";
            }
            if (clean.equalsIgnoreCase("3.1 flash lite") || clean.equalsIgnoreCase("3.1-flash-lite") || clean.equalsIgnoreCase("gemini 3.1 flash lite") || clean.equalsIgnoreCase("gemini_3_1_flash_lite")) {
                return "gemini-3.1-flash-lite";
            }
            if (clean.equalsIgnoreCase("2.5 flash lite") || clean.equalsIgnoreCase("2.5-flash-lite") || clean.equalsIgnoreCase("gemini 2.5 flash lite") || clean.equalsIgnoreCase("gemini_2_5_flash_lite")) {
                return "gemini-2.5-flash-lite";
            }
            if (clean.equalsIgnoreCase("flash lite") || clean.equalsIgnoreCase("flash-lite") || clean.equalsIgnoreCase("gemini-flash-lite") || clean.equalsIgnoreCase("gemini_flash_lite_latest")) {
                return "gemini-flash-lite-latest";
            }
            if (!clean.startsWith("gemini-") && !clean.contains("/")) {
                clean = "gemini-" + clean.replace(" ", "-").replace("_", "-").toLowerCase();
            }
        }
        return clean;
    }

    private static String resolveEndpointUrl(ChatbotProvider provider, String customUrl) {
        if (provider == null) {
            return GEMINI_API_URL;
        }
        switch (provider) {
            case GEMINI:
                return GEMINI_API_URL;
            case OPENCODE:
                return OPENCODE_ZEN_URL;
            case GROQ:
                return GROQ_API_URL;
            case OPENROUTER:
                return OPENROUTER_API_URL;
            case CUSTOM:
                if (customUrl != null && !customUrl.trim().isEmpty()) {
                    return customUrl.trim();
                }
                return "http://localhost:11434/v1/chat/completions";
            case OPENAI:
            default:
                return OPENAI_API_URL;
        }
    }

    private static String sanitize(String text, String apiKey) {
        if (text == null) {
            return "";
        }
        if (apiKey != null && !apiKey.trim().isEmpty() && apiKey.length() > 4) {
            return text.replace(apiKey.trim(), "[REDACTED_KEY]");
        }
        return text;
    }

    private static CompletionResult executeRequest(ChatbotProvider provider, String endpointUrl, String apiKey,
                                                 String model, List<ChatMessage> messages,
                                                 int maxTokens, double temperature, boolean isFallback) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            int effectiveTokens = Math.max(maxTokens, 800);
            requestBody.addProperty("max_tokens", effectiveTokens);
            requestBody.addProperty("temperature", temperature);

            JsonArray messagesArray = new JsonArray();
            for (ChatMessage msg : messages) {
                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("role", msg.getRole());
                msgObj.addProperty("content", msg.getContent());
                messagesArray.add(msgObj);
            }
            requestBody.add("messages", messagesArray);

            String jsonBody = GSON.toJson(requestBody);

            URL url = new URL(endpointUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            }
            if (provider == ChatbotProvider.OPENROUTER || endpointUrl.contains("openrouter")) {
                conn.setRequestProperty("HTTP-Referer", "https://github.com/chsami/Microbot-Hub");
                conn.setRequestProperty("X-Title", "Microbot Chatbot");
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(),
                                StandardCharsets.UTF_8))) {
                    StringBuilder errorBody = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorBody.append(line);
                    }
                    if (responseCode == 503 || responseCode == 429) {
                        log.warn("[Chatbot] Model '{}' is busy (HTTP {}), executing automatic failover...", model, responseCode);
                    } else {
                        log.error("[Chatbot] API error from {} for model '{}' (HTTP {}): {}",
                                endpointUrl, model, responseCode, sanitize(errorBody.toString(), apiKey));
                    }
                }
                return null;
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder responseBody = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    responseBody.append(line);
                }

                JsonObject response = new JsonParser().parse(responseBody.toString()).getAsJsonObject();

                String content = null;
                String reasoning = null;

                // Standard OpenAI / OpenAI-compatible Gemini format
                if (response.has("choices")) {
                    JsonArray choices = response.getAsJsonArray("choices");
                    if (choices != null && choices.size() > 0) {
                        JsonObject firstChoice = choices.get(0).getAsJsonObject();
                        if (firstChoice.has("message")) {
                            JsonObject message = firstChoice.getAsJsonObject("message");
                            if (message.has("content") && !message.get("content").isJsonNull()) {
                                content = message.get("content").getAsString().trim();
                            }
                            if (message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()) {
                                reasoning = message.get("reasoning_content").getAsString().trim();
                            } else if (message.has("thought") && !message.get("thought").isJsonNull()) {
                                reasoning = message.get("thought").getAsString().trim();
                            }
                        }
                    }
                }

                // Native Gemini format fallback
                if (content == null && response.has("candidates")) {
                    JsonArray candidates = response.getAsJsonArray("candidates");
                    if (candidates != null && candidates.size() > 0) {
                        JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                        if (firstCandidate.has("content")) {
                            JsonObject candContent = firstCandidate.getAsJsonObject("content");
                            if (candContent.has("parts")) {
                                JsonArray parts = candContent.getAsJsonArray("parts");
                                for (int i = 0; i < parts.size(); i++) {
                                    JsonObject part = parts.get(i).getAsJsonObject();
                                    if (part.has("thought") && part.get("thought").getAsBoolean()) {
                                        if (part.has("text") && !part.get("text").isJsonNull()) {
                                            reasoning = part.get("text").getAsString().trim();
                                        }
                                    } else if (part.has("text") && !part.get("text").isJsonNull()) {
                                        content = part.get("text").getAsString().trim();
                                    }
                                }
                            }
                        }
                    }
                }

                if (content != null && !content.isEmpty()) {
                    return new CompletionResult(content, reasoning, model, isFallback);
                }
            }

            return null;
        } catch (Exception e) {
            log.error("[Chatbot] Failed to call AI endpoint ({}) for model '{}': {}",
                    endpointUrl, model, sanitize(e.getMessage(), apiKey));
            return null;
        }
    }
}
