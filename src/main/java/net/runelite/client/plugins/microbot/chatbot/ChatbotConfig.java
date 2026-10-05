package net.runelite.client.plugins.microbot.chatbot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ChatbotConfig.configGroup)
public interface ChatbotConfig extends Config {
	String configGroup = "micro-chatbot";

	// ── Sections ───────────────────────────────────────────────────────

	@ConfigSection(
		name = "AI Provider Settings",
		description = "AI service and API configuration",
		position = 0
	)
	String openAiSection = "openAi";

	@ConfigSection(
		name = "Chat Sources",
		description = "Which chat types to listen to",
		position = 1
	)
	String chatSourceSection = "chatSources";

	@ConfigSection(
		name = "Response Settings",
		description = "How the bot responds",
		position = 2
	)
	String responseSection = "response";

	@ConfigSection(
		name = "Filtering",
		description = "Message filtering options",
		position = 3
	)
	String filterSection = "filtering";

	@ConfigSection(
		name = "OSRS Slang & Context",
		description = "Natural RS player speech style, typos, and in-game context awareness",
		position = 4
	)
	String speechSection = "speech";

	// ── AI Provider Settings ───────────────────────────────────────────

	@ConfigItem(
		keyName = "aiProvider",
		name = "AI Provider",
		description = "Choose which AI service to connect to (Google Gemini, OpenCode, Groq, OpenRouter, OpenAI, or Custom)",
		position = 0,
		section = openAiSection
	)
	default ChatbotProvider aiProvider() {
		return ChatbotProvider.GEMINI;
	}

	@ConfigItem(
		keyName = "openAiApiKey",
		name = "API Key",
		description = "Your API key (Google Gemini, OpenCode, Groq, OpenRouter, or OpenAI key)",
		position = 1,
		section = openAiSection,
		secret = true
	)
	default String openAiApiKey() {
		return "";
	}

	@ConfigItem(
		keyName = "aiModel",
		name = "Model",
		description = "Select AI model. Gemini 3.5 Flash Lite is fastest with a generous free tier. Gemini 3.8 Flash offers higher reasoning.",
		position = 2,
		section = openAiSection
	)
	default ChatbotModel aiModel() {
		return ChatbotModel.GEMINI_FLASH_LITE_LATEST;
	}

	@ConfigItem(
		keyName = "customModelName",
		name = "Custom Model Name",
		description = "Specify model name if 'Custom / Other' is selected in Model dropdown",
		position = 3,
		section = openAiSection
	)
	default String customModelName() {
		return "";
	}

	@ConfigItem(
		keyName = "customApiUrl",
		name = "Custom Endpoint URL",
		description = "Endpoint URL if using Custom/Local provider (e.g. http://localhost:11434/v1/chat/completions)",
		position = 4,
		section = openAiSection
	)
	default String customApiUrl() {
		return "http://localhost:11434/v1/chat/completions";
	}

	@ConfigItem(
		keyName = "systemPrompt",
		name = "System Prompt",
		description = "The system prompt that defines the chatbot's personality and behavior.",
		position = 5,
		section = openAiSection
	)
	default String systemPrompt() {
		return "You are a real player in Old School RuneScape (OSRS). "
			+ "You speak exactly how real RS players speak: casual lowercase, no formal AI tone, NO trailing periods. "
			+ "Use common OSRS slang and abbreviations (u, ur, r, lvl, wc, fm, ge, gp, gzz, ty, yw, gl, tbh, idk, lmao). "
			+ "Keep replies concise (under 70 characters) so they fit the chatbox cleanly. "
			+ "If asked about your level, location, or activity, use the provided LIVE OSRS GAME CONTEXT. "
			+ "If accused of being a bot, casually laugh it off like a real human player.";
	}

	@ConfigItem(
		keyName = "maxTokens",
		name = "Max Tokens",
		description = "Maximum tokens for each AI response (use 600+ for reasoning models)",
		position = 6,
		section = openAiSection
	)
	default int maxTokens() {
		return 600;
	}

	@ConfigItem(
		keyName = "temperature",
		name = "Temperature",
		description = "Creativity of responses (0.0 = deterministic, 2.0 = very creative). Use values like 0.7",
		position = 7,
		section = openAiSection
	)
	default String temperature() {
		return "0.7";
	}

	// ── Chat Sources ───────────────────────────────────────────────────

	@ConfigItem(
		keyName = "listenPublicChat",
		name = "Public Chat",
		description = "Listen and respond to public chat messages",
		position = 0,
		section = chatSourceSection
	)
	default boolean listenPublicChat() {
		return true;
	}

	@ConfigItem(
		keyName = "listenClanChat",
		name = "Clan Chat",
		description = "Listen and respond to clan chat messages",
		position = 1,
		section = chatSourceSection
	)
	default boolean listenClanChat() {
		return false;
	}

	@ConfigItem(
		keyName = "listenFriendsChat",
		name = "Friends Chat",
		description = "Listen and respond to friends chat messages",
		position = 2,
		section = chatSourceSection
	)
	default boolean listenFriendsChat() {
		return false;
	}

	// ── Response Settings ──────────────────────────────────────────────

	@ConfigItem(
		keyName = "responseDelayMin",
		name = "Min Response Delay (ms)",
		description = "Minimum delay before responding (milliseconds) to appear more human",
		position = 0,
		section = responseSection
	)
	default int responseDelayMin() {
		return 2000;
	}

	@ConfigItem(
		keyName = "responseDelayMax",
		name = "Max Response Delay (ms)",
		description = "Maximum delay before responding (milliseconds)",
		position = 1,
		section = responseSection
	)
	default int responseDelayMax() {
		return 5000;
	}

	@ConfigItem(
		keyName = "respondViaPublic",
		name = "Respond in Public",
		description = "Send responses via public chat (typed in chatbox)",
		position = 2,
		section = responseSection
	)
	default boolean respondViaPublic() {
		return true;
	}

	@ConfigItem(
		keyName = "maxResponseLength",
		name = "Max Response Length",
		description = "Maximum character length for responses (OSRS chat limit is 80)",
		position = 3,
		section = responseSection
	)
	default int maxResponseLength() {
		return 80;
	}

	@ConfigItem(
		keyName = "conversationMemory",
		name = "Conversation Memory",
		description = "Number of recent messages to include as context for the AI",
		position = 4,
		section = responseSection
	)
	default int conversationMemory() {
		return 10;
	}

	@ConfigItem(
		keyName = "cooldownMinSeconds",
		name = "Min Cooldown (s)",
		description = "Minimum seconds to wait between sending messages (default 3s)",
		position = 5,
		section = responseSection
	)
	default int cooldownMinSeconds() {
		return 3;
	}

	@ConfigItem(
		keyName = "cooldownMaxSeconds",
		name = "Max Cooldown (s)",
		description = "Maximum seconds to wait between sending messages (default 15s)",
		position = 6,
		section = responseSection
	)
	default int cooldownMaxSeconds() {
		return 15;
	}

	@ConfigItem(
		keyName = "pressEnterToSend",
		name = "Press Enter to Send",
		description = "If disabled, types response in the chatbox but does NOT press Enter (test mode - prevents accidental public shouting/spamming).",
		position = 7,
		section = responseSection
	)
	default boolean pressEnterToSend() {
		return false;
	}

	// ── Filtering ──────────────────────────────────────────────────────

	@ConfigItem(
		keyName = "onlyRespondToNames",
		name = "Only Respond To (names)",
		description = "Comma-separated list of player names to respond to. Leave empty to respond to everyone.",
		position = 0,
		section = filterSection
	)
	default String onlyRespondToNames() {
		return "";
	}

	@ConfigItem(
		keyName = "ignoreNames",
		name = "Ignore Names",
		description = "Comma-separated list of player names to ignore.",
		position = 1,
		section = filterSection
	)
	default String ignoreNames() {
		return "";
	}

	@ConfigItem(
		keyName = "triggerKeyword",
		name = "Trigger Words",
		description = "Comma-separated list of trigger words (e.g. bot, hey, hello). Only responds if message contains one. Empty = responds to all.",
		position = 2,
		section = filterSection
	)
	default String triggerKeyword() {
		return "";
	}

	@ConfigItem(
		keyName = "ignoreSelf",
		name = "Ignore Own Messages",
		description = "Don't respond to your own messages. Disable this if you want to test the bot yourself.",
		position = 3,
		section = filterSection
	)
	default boolean ignoreSelf() {
		return true;
	}

	@ConfigItem(
		keyName = "cooldownSeconds",
		name = "Cooldown (seconds)",
		description = "Minimum seconds between responses to avoid spamming",
		position = 4,
		section = filterSection
	)
	default int cooldownSeconds() {
		return 5;
	}

	@ConfigItem(
		keyName = "onlyRespondWhenAddressed",
		name = "Only When Addressed",
		description = "Only respond if the player is directly talking to you (mentions your name, asks a direct question nearby, or interacts with you). Prevents talking all the time at crowded places.",
		position = 5,
		section = filterSection
	)
	default boolean onlyRespondWhenAddressed() {
		return true;
	}

	@ConfigItem(
		keyName = "filterGeTradeSpam",
		name = "Filter GE & Trade Spam",
		description = "Ignore Grand Exchange shouting, buying/selling offers, gambling bots, doubling, and giveaway ads.",
		position = 6,
		section = filterSection
	)
	default boolean filterGeTradeSpam() {
		return true;
	}

	@ConfigItem(
		keyName = "maxDistanceTiles",
		name = "Max Distance (tiles)",
		description = "Ignore players standing farther than this number of tiles away (set to 0 to disable distance check).",
		position = 7,
		section = filterSection
	)
	default int maxDistanceTiles() {
		return 7;
	}

	@ConfigItem(
		keyName = "customNicknames",
		name = "Custom Nicknames / Aliases",
		description = "Comma-separated nicknames or aliases you go by (e.g. 'dave, davy'). Automatically combines with your active in-game character name.",
		position = 8,
		section = filterSection
	)
	default String customNicknames() {
		return "";
	}

	// ── OSRS Slang & Context ───────────────────────────────────────────

	@ConfigItem(
		keyName = "enableGameContext",
		name = "Game Context Awareness",
		description = "Informs the AI of your current location (e.g. GE, Wintertodt), active skill/activity, and levels",
		position = 0,
		section = speechSection
	)
	default boolean enableGameContext() {
		return true;
	}

	@ConfigItem(
		keyName = "osrsSlangStyle",
		name = "OSRS Slang & Lowercase",
		description = "Speaks like real players: lowercase, slang (u, ur, r, lvl, wc, ge, gzz, etc.), no formal periods",
		position = 1,
		section = speechSection
	)
	default boolean osrsSlangStyle() {
		return true;
	}

	@ConfigItem(
		keyName = "simulateTypos",
		name = "Simulate Typing Mistakes",
		description = "Occasionally drops/jumps letters (e.g. 'choppin', 'fir', 'jsut', 'gzz') like humans typing quickly",
		position = 2,
		section = speechSection
	)
	default boolean simulateTypos() {
		return true;
	}

	@ConfigItem(
		keyName = "logChatToFile",
		name = "Log Chat for Dataset Training",
		description = "Continuously logs overheard player chat to .runelite/chatbot-dataset for language learning (opt-in)",
		position = 3,
		section = speechSection
	)
	default boolean logChatToFile() {
		return false;
	}
}
