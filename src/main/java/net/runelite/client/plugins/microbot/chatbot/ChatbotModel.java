package net.runelite.client.plugins.microbot.chatbot;

import java.util.ArrayList;
import java.util.List;

/**
 * Available AI models selectable via dropdown in the Chatbot configuration.
 * All display labels are intentionally kept concise (under 22 characters)
 * to prevent text clipping and horizontal overflow in RuneLite's sidebar.
 */
public enum ChatbotModel {
    GEMINI_FLASH_LITE_LATEST("gemini-3.1-flash-lite", "Gemini 3.5 Flash Lite", ChatbotProvider.GEMINI),
    GEMINI_3_8_FLASH("gemini-3.8-flash", "Gemini 3.8 Flash", ChatbotProvider.GEMINI),
    GEMINI_3_5_FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", ChatbotProvider.GEMINI),
    GEMINI_3_1_FLASH_LITE("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", ChatbotProvider.GEMINI),
    GEMINI_2_5_FLASH_LITE("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", ChatbotProvider.GEMINI),
    GEMINI_3_5_FLASH("gemini-3.5-flash", "Gemini 3.5 Flash", ChatbotProvider.GEMINI),
    GEMINI_2_0_FLASH("gemini-flash-lite-latest", "Gemini 2.0 Flash", ChatbotProvider.GEMINI),
    GEMINI_1_5_FLASH("gemini-flash-lite-latest", "Gemini 1.5 Flash", ChatbotProvider.GEMINI),
    OPENCODE_FREE("opencode/free", "OpenCode Free", ChatbotProvider.OPENCODE),
    OPENCODE_DEEPSEEK("deepseek-chat", "DeepSeek Chat", ChatbotProvider.OPENCODE),
    OPENCODE_LLAMA("llama-3.3-70b", "Llama 3.3 70B", ChatbotProvider.OPENCODE),
    OPENCODE_CUSTOM("opencode-custom", "Custom OpenCode", ChatbotProvider.OPENCODE),
    GROQ_LLAMA_3_3_70B("llama-3.3-70b-versatile", "Llama 3.3 70B", ChatbotProvider.GROQ),
    GROQ_LLAMA_3_1_8B("llama-3.1-8b-instant", "Llama 3.1 8B", ChatbotProvider.GROQ),
    OPENROUTER_DEEPSEEK_FREE("deepseek/deepseek-chat:free", "DeepSeek (Free)", ChatbotProvider.OPENROUTER),
    OPENROUTER_LLAMA_FREE("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 (Free)", ChatbotProvider.OPENROUTER),
    GPT_4O_MINI("gpt-4o-mini", "GPT-4o Mini", ChatbotProvider.OPENAI),
    GPT_4O("gpt-4o", "GPT-4o", ChatbotProvider.OPENAI),
    CUSTOM("custom", "Custom Model", ChatbotProvider.CUSTOM);

    private final String modelId;
    private final String label;
    private final ChatbotProvider defaultProvider;

    ChatbotModel(String modelId, String label, ChatbotProvider defaultProvider) {
        this.modelId = modelId;
        this.label = label;
        this.defaultProvider = defaultProvider;
    }

    public String getModelId() {
        return modelId;
    }

    public ChatbotProvider getDefaultProvider() {
        return defaultProvider;
    }

    /**
     * Deprecated models that should not be shown in the UI dropdown.
     */
    public boolean isDeprecated() {
        return this == GEMINI_3_5_FLASH_LITE
                || this == GEMINI_3_1_FLASH_LITE
                || this == GEMINI_2_5_FLASH_LITE
                || this == GEMINI_3_5_FLASH
                || this == GEMINI_2_0_FLASH
                || this == GEMINI_1_5_FLASH;
    }

    /**
     * Returns an array of models relevant to the given provider for UI dropdown display.
     */
    public static ChatbotModel[] getModelsForProvider(ChatbotProvider provider) {
        if (provider == null) {
            provider = ChatbotProvider.GEMINI;
        }
        List<ChatbotModel> list = new ArrayList<>();
        for (ChatbotModel m : values()) {
            if (m.isDeprecated()) {
                continue;
            }
            if (m.getDefaultProvider() == provider) {
                list.add(m);
            }
        }
        return list.toArray(new ChatbotModel[0]);
    }

    /**
     * Resolves the default recommended model for a given provider.
     */
    public static ChatbotModel getDefaultModelForProvider(ChatbotProvider provider) {
        if (provider == null) {
            return GEMINI_FLASH_LITE_LATEST;
        }
        switch (provider) {
            case OPENCODE:
                return OPENCODE_FREE;
            case GROQ:
                return GROQ_LLAMA_3_3_70B;
            case OPENROUTER:
                return OPENROUTER_DEEPSEEK_FREE;
            case OPENAI:
                return GPT_4O_MINI;
            case CUSTOM:
                return CUSTOM;
            case GEMINI:
            default:
                return GEMINI_FLASH_LITE_LATEST;
        }
    }

    @Override
    public String toString() {
        return label;
    }

    /**
     * Resolves the effective provider based on user configuration.
     */
    public static ChatbotProvider getEffectiveProvider(ChatbotConfig config) {
        if (config == null) {
            return ChatbotProvider.GEMINI;
        }
        try {
            ChatbotProvider prov = config.aiProvider();
            if (prov == ChatbotProvider.CUSTOM) {
                return ChatbotProvider.CUSTOM;
            }
            ChatbotModel model = config.aiModel();
            if (model != null && model.getDefaultProvider() != null && model != ChatbotModel.CUSTOM) {
                return model.getDefaultProvider();
            }
            return prov != null ? prov : ChatbotProvider.GEMINI;
        } catch (Exception e) {
            return ChatbotProvider.GEMINI;
        }
    }

    /**
     * Resolves the model string identifier based on user configuration.
     */
    public static String getModelName(ChatbotConfig config) {
        if (config == null) {
            return "gemini-flash-lite-latest";
        }
        try {
            ChatbotProvider provider = config.aiProvider();
            ChatbotModel model = config.aiModel();
            if (model == null || (model.getDefaultProvider() != null && model.getDefaultProvider() != provider && model != ChatbotModel.CUSTOM)) {
                model = getDefaultModelForProvider(provider);
            }

            if (model == ChatbotModel.CUSTOM || model == ChatbotModel.OPENCODE_CUSTOM) {
                String custom = config.customModelName();
                if (custom != null && !custom.trim().isEmpty()) {
                    return custom.trim();
                }
                return model == ChatbotModel.OPENCODE_CUSTOM ? "deepseek-chat" : "gemini-flash-lite-latest";
            }
            return model != null ? model.getModelId() : "gemini-flash-lite-latest";
        } catch (Exception e) {
            return "gemini-flash-lite-latest";
        }
    }
}
