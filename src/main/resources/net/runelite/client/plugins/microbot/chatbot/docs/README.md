# Chatbot Plugin

AI-powered in-game chatbot for Microbot Hub. Listens to in-game chat and responds intelligently with human-like OSRS speech patterns, context awareness, and intelligent anti-spam filtering.

---

## Key Features

1. **Multi-Provider AI Support**:
   - **Google Gemini** (Generous free tier, fastest responses):
     - `Gemini 3.5 Flash Lite` (Recommended default)
     - `Gemini 3.8 Flash`
     - Automatic silent fallback between Gemini models if rate-limited (`HTTP 429`) or under high demand (`HTTP 503`).
   - **OpenCode** (Free models, DeepSeek, Llama 3.3 70B, Custom).
   - **GroqCloud** (Ultra-fast Llama 3.3 70B and 3.1 8B).
   - **OpenRouter** (Free community models).
   - **OpenAI** (GPT-4o Mini, GPT-4o).
   - **Custom / Local** (Ollama, LM Studio, or any OpenAI-compatible API).

2. **Natural OSRS Player Speech**:
   - All-lowercase, casual tone with common OSRS slang and abbreviations (`u`, `ur`, `r`, `lvl`, `wc`, `fm`, `ge`, `gp`, `gzz`, `ty`, `yw`, `gl`, `tbh`, `idk`, `lmao`).
   - Trailing formal periods are stripped.
   - Optional human typing mistakes/transpositions (e.g. `choppin`, `fir`, `jsut`, `gzz`).
   - Built-in bot/script leak sanitizer ensures internal automation details never appear in chat.

3. **Dynamic Player Addressing & Conversation Continuity**:
   - Automatically detects when a player addresses you by your character name or custom nicknames.
   - Dynamically remembers active conversation partners for multi-turn dialogues.
   - Detects direct nearby player questions (`wc lvl?`, `bot?`, `u there?`, `can u hop?`).

4. **GE Spam & Gambling Bot Filter**:
   - Automatically filters out Grand Exchange shout spammers, gambling bots (`55x2`, `dicing`, `roll`), fake YouTube giveaways, and doubling scams.

5. **Safe Test Mode**:
   - When **Press Enter to Send** is disabled, the bot types the response into your chatbox, leaves it visible for ~2 seconds so you can preview it, and then backspaces every character away.
   - Leaves your chatbox completely empty and clean, preventing accidental public messaging.

6. **Privacy & Hub Compliance**:
   - **Zero Secret Retention**: API keys are tagged `@ConfigItem(secret = true)` for encrypted storage, and keys are scrubbed from all logs.
   - **No Private DM Snooping**: Private chat messages (`PRIVATECHAT`) are completely ignored.
   - **Opt-In Only Logging**: Chat logging (`logChatToFile`) defaults to `false`.
   - **No Background Config Mutations**: The plugin never overwrites your configuration behind the scenes.
   - **Zero Telemetry**: Direct client-to-API communication only.

---

## Quick Start (Google Gemini - Free)

1. Get a free API key from [Google AI Studio](https://aistudio.google.com/app/apikey).
2. In RuneLite -> Settings -> **Chatbot**:
   - **AI Provider**: `Google Gemini`
   - **API Key**: Paste your key
   - **Model**: `Gemini 3.5 Flash Lite`
3. Toggle **Chatbot** ON.
4. By default, **Press Enter to Send** is OFF so you can test responses safely in the chatbox. Once satisfied, check **Press Enter to Send** to speak in-game.

---

## Configuration Reference

### AI Provider Settings
| Setting | Key | Default | Description |
| :--- | :--- | :--- | :--- |
| **AI Provider** | `aiProvider` | `Google Gemini` | Select AI service (Google Gemini, OpenCode, Groq, OpenRouter, OpenAI, Custom). |
| **API Key** | `openAiApiKey` | *(empty)* | API key for the selected service (stored securely with `secret = true`). |
| **Model** | `aiModel` | `Gemini 3.5 Flash Lite` | Active model for the selected provider. |
| **Custom Model Name** | `customModelName` | *(empty)* | Custom model name if using Custom or Local endpoint. |
| **Custom Endpoint URL** | `customApiUrl` | `http://localhost:11434/v1/chat/completions` | Custom/Local OpenAI-compatible URL. |
| **System Prompt** | `systemPrompt` | *(OSRS default)* | System prompt establishing personality, slang, and bounds. |
| **Max Tokens** | `maxTokens` | `600` | Token limit per reply. |
| **Temperature** | `temperature` | `0.7` | Creativity level (0.0 = deterministic, 2.0 = creative). |

### Chat Sources
| Setting | Key | Default | Description |
| :--- | :--- | :--- | :--- |
| **Public Chat** | `listenPublicChat` | `true` | Listen and respond to public chat. |
| **Clan Chat** | `listenClanChat` | `false` | Listen and respond to clan chat messages. |
| **Friends Chat** | `listenFriendsChat` | `false` | Listen and respond to friends chat messages. |

### Response Settings
| Setting | Key | Default | Description |
| :--- | :--- | :--- | :--- |
| **Min Response Delay (ms)** | `responseDelayMin` | `2000` | Minimum delay before typing to appear human. |
| **Max Response Delay (ms)** | `responseDelayMax` | `5000` | Maximum delay before typing. |
| **Respond in Public** | `respondViaPublic` | `true` | Send replies via public chat. |
| **Max Response Length** | `maxResponseLength` | `80` | Maximum character length (OSRS chat limit is 80). |
| **Conversation Memory** | `conversationMemory` | `10` | Recent turns kept for context memory. |
| **Min Cooldown (s)** | `cooldownMinSeconds` | `3` | Minimum seconds between sending messages. |
| **Max Cooldown (s)** | `cooldownMaxSeconds` | `15` | Maximum seconds between sending messages. |
| **Press Enter to Send** | `pressEnterToSend` | `false` | If disabled, types in chatbox and backspaces away (test mode). |

### Filtering
| Setting | Key | Default | Description |
| :--- | :--- | :--- | :--- |
| **Only When Addressed** | `onlyRespondWhenAddressed` | `true` | Only responds when addressed by name, in active dialogue, or asked a question. |
| **Filter GE & Trade Spam** | `filterGeTradeSpam` | `true` | Discards trade shouting, doubling, dicing, and scam bots. |
| **Max Distance (tiles)** | `maxDistanceTiles` | `7` | Discards messages from players further than this distance. |
| **Custom Nicknames** | `customNicknames` | *(empty)* | Additional nicknames you go by (e.g. `dave, davy`). |
| **Trigger Words** | `triggerKeyword` | *(empty)* | Optional comma-separated trigger words. |
| **Ignore Own Messages** | `ignoreSelf` | `true` | Prevents replying to yourself. |
| **Only Respond To** | `onlyRespondToNames` | *(empty)* | Comma-separated list of allowed players. |
| **Ignore Names** | `ignoreNames` | *(empty)* | Comma-separated list of ignored players. |
