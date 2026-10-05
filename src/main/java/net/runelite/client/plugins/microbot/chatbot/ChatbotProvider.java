package net.runelite.client.plugins.microbot.chatbot;

public enum ChatbotProvider {
    GEMINI("Google Gemini"),
    OPENCODE("OpenCode"),
    GROQ("GroqCloud"),
    OPENROUTER("OpenRouter"),
    OPENAI("OpenAI"),
    CUSTOM("Custom / Local");

    private final String label;

    ChatbotProvider(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
