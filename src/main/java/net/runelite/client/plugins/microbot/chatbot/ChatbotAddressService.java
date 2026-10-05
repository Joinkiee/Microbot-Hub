package net.runelite.client.plugins.microbot.chatbot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.util.Text;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Dynamically resolves player identity and determines whether in-game chat
 * messages are directly addressed to the local player, regardless of what username
 * the user has on Old School RuneScape when downloading from GitHub.
 */
@Slf4j
public class ChatbotAddressService {

    private static volatile String cachedLocalPlayerName = null;
    private static volatile Set<NameToken> cachedTokens = null;
    private static volatile String lastTokenPlayerName = null;
    private static volatile String lastTokenConfigNicknames = null;
    private static final Object tokenLock = new Object();

    // Active conversation thread tracking: keeps 2-way dialogue flowing without needing repeated name mentions
    private static volatile String activeConversationPartner = null;
    private static volatile long activeConversationExpiry = 0;

    /**
     * Common titles, account type prefixes, and decorative fluff in OSRS usernames.
     */
    private static final String[] COMMON_PREFIX_TITLES = {
            "iron", "hcfe", "hc", "gim", "uim", "fe", "im",
            "pure", "zerk", "zerker", "tank", "skiller",
            "sir", "mr", "mrs", "ms", "dr", "lord", "king", "queen", "capt", "captain", "prof", "saint", "master", "chief",
            "the", "its", "real", "og", "lil", "big", "el", "da", "dj", "mc",
            "xxx", "xx", "ooo", "oo", "iii", "ii",
            "ttv", "tv", "yt", "osrs", "rs"
    };

    /**
     * Suffixes commonly appended to OSRS usernames.
     */
    private static final String[] COMMON_SUFFIXES = {
            "_ttv", "_tv", "_yt", "_pk", "_pvm", "_pvp", "_rs", "_osrs", "_07",
            "_fe", "_im", "_iron", "_hc", "_gim", "_uim",
            "xxx", "xx", "x"
    };

    /**
     * Common RuneScape terms and dictionary words that may appear in a player's username
     * (e.g. "Iron Arthur", "Pure Mage", "Gold Hunter", "Wood Guy", "Fish King").
     * When a sub-token matches a guarded word, it is ONLY recognized as addressing the player
     * if accompanied by direct address syntax (e.g. "hey iron", "@iron", "iron, hop", "iron u there?"),
     * preventing false triggers from general game banter or trade shouts ("buying iron ore").
     */
    private static final Set<String> GUARDED_WORDS = new HashSet<>(Arrays.asList(
            "iron", "pure", "gold", "wood", "fish", "rune", "fire", "food", "pot", "drop",
            "trade", "buy", "sell", "bot", "lvl", "level", "world", "wc", "mine", "mining", "ore",
            "bar", "smith", "smithing", "cook", "cooking", "bake", "farm", "farming", "agility", "combat", "str", "strength", "def", "defence",
            "att", "attack", "pray", "prayer", "mage", "magic", "range", "ranged", "arrow", "bow", "sword",
            "shield", "cape", "helm", "plate", "legs", "boots", "ring", "ammy", "gem",
            "seed", "herb", "herblore", "potion", "coal", "bronze", "steel", "mith", "addy", "adam",
            "dragon", "black", "red", "green", "blue", "white", "dark", "light", "afk",
            "slayer", "slay", "hunter", "hunt", "thieving", "thief", "thieve", "crafting", "craft", "fletching", "fletch",
            "construction", "con", "hitpoints", "hp", "runecraft", "rc",
            "noob", "max", "main", "alt", "ironman", "hc", "gim", "uim", "the", "its",
            "real", "big", "lil", "mr", "sir", "dr", "king", "lord", "god", "man", "boy",
            "guy", "girl", "dude", "bro", "pro", "all", "and", "you", "for", "are", "not",
            "have", "with", "this", "that", "from", "they", "will", "what", "how", "why",
            "when", "where", "who", "can", "good", "bad", "fast", "slow", "free", "dead",
            "cash", "dice", "roll", "clan", "bank", "item", "game", "scape", "runescape"
    ));

    /**
     * Represents a single name token with its sensitivity classification.
     */
    public static class NameToken {
        private final String token;
        private final boolean guarded;

        public NameToken(String token, boolean guarded) {
            this.token = token.toLowerCase();
            this.guarded = guarded;
        }

        public String getToken() {
            return token;
        }

        public boolean isGuarded() {
            return guarded;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NameToken nameToken = (NameToken) o;
            return token.equals(nameToken.token);
        }

        @Override
        public int hashCode() {
            return token.hashCode();
        }

        @Override
        public String toString() {
            return token + (guarded ? "(guarded)" : "");
        }
    }

    /**
     * Dynamically resolves the active player's username from live game state.
     * Detects account switches and world hops automatically.
     */
    public static String getLocalPlayerName() {
        String currentName = null;
        try {
            Rs2PlayerModel local = Rs2Player.getLocalPlayer();
            if (local != null && local.getName() != null) {
                String clean = Text.removeTags(local.getName()).replace('\u00A0', ' ').trim();
                if (!clean.isEmpty()) {
                    currentName = clean;
                }
            }
            if (currentName == null && Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null) {
                String name = Microbot.getClient().getLocalPlayer().getName();
                if (name != null) {
                    String clean = Text.removeTags(name).replace('\u00A0', ' ').trim();
                    if (!clean.isEmpty()) {
                        currentName = clean;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (currentName != null && !currentName.equalsIgnoreCase(cachedLocalPlayerName)) {
            log.info("[Chatbot] Dynamic player identity updated: '{}' (was: '{}')", currentName, cachedLocalPlayerName);
            cachedLocalPlayerName = currentName;
            synchronized (tokenLock) {
                cachedTokens = null;
                lastTokenConfigNicknames = null;
            }
        }

        return cachedLocalPlayerName != null ? cachedLocalPlayerName : "";
    }

    /**
     * Dynamically generates all name tokens, variations, leet-speak forms, root stems,
     * and custom nicknames for any OSRS username.
     */
    public static Set<NameToken> generateNameTokens(String rawPlayerName, String customNicknames) {
        Set<NameToken> tokens = new HashSet<>();
        if (rawPlayerName == null || rawPlayerName.trim().isEmpty()) {
            addCustomNicknames(tokens, customNicknames);
            return tokens;
        }

        String clean = rawPlayerName.replace('\u00A0', ' ').trim().toLowerCase();

        // 1. Full raw name
        addToken(tokens, clean, clean.contains(" ") || !isGuarded(clean));

        // 2. Full name with spaces and underscores removed
        String noSpaces = clean.replace(" ", "").replace("_", "");
        if (!noSpaces.isEmpty()) {
            addToken(tokens, noSpaces, !isGuarded(noSpaces));
        }

        // 3. Name stripped of numbers/symbols
        String alphaOnly = clean.replaceAll("[^a-z]", "");
        if (alphaOnly.length() >= 2) {
            addToken(tokens, alphaOnly, !isGuarded(alphaOnly));
        }

        // 4. Space / Underscore / Dash separated chunks
        String[] splitWords = clean.split("[\\s_.-]+");
        for (String w : splitWords) {
            String cleanW = w.replaceAll("[^a-z]", "");
            if (cleanW.length() >= 2) {
                addToken(tokens, cleanW, !isGuarded(cleanW));
            }
        }

        // 5. CamelCase decomposition (e.g. BestiFunnyPa -> besti, funny, pa)
        String[] camelParts = rawPlayerName.split("(?=[A-Z])|[\\s_\\d.-]+");
        for (String part : camelParts) {
            String p = part.trim().toLowerCase().replaceAll("[^a-z]", "");
            if (p.length() >= 2) {
                addToken(tokens, p, !isGuarded(p));
            }
        }

        // 6. Common prefix/title removal (e.g. 'Iron Arthur' -> 'arthur', 'xX_Slayer_Xx' -> 'slayer')
        for (String word : splitWords) {
            for (String prefix : COMMON_PREFIX_TITLES) {
                if (word.startsWith(prefix) && word.length() > prefix.length()) {
                    String remainder = word.substring(prefix.length()).replaceAll("[^a-z]", "");
                    if (remainder.length() >= 2) {
                        addToken(tokens, remainder, !isGuarded(remainder));
                    }
                }
            }
        }

        // 7. Common suffix removal (e.g. 'Arthur99' -> 'arthur', 'Slayer_TTV' -> 'slayer')
        for (String word : splitWords) {
            for (String suffix : COMMON_SUFFIXES) {
                if (word.endsWith(suffix) && word.length() > suffix.length()) {
                    String remainder = word.substring(0, word.length() - suffix.length()).replaceAll("[^a-z]", "");
                    if (remainder.length() >= 2) {
                        addToken(tokens, remainder, !isGuarded(remainder));
                    }
                }
            }
            // Strip trailing digits
            String noDigits = word.replaceAll("\\d+$", "");
            if (noDigits.length() >= 2 && !noDigits.equals(word)) {
                addToken(tokens, noDigits, !isGuarded(noDigits));
            }
        }

        // 8. Leet-speak normalization (e.g. 'B0aty' -> 'boaty', 'El33t' -> 'eleet', 'Sn1p3r' -> 'sniper')
        String leetNormalized = clean
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('7', 't')
                .replace('8', 'b');
        String leetAlpha = leetNormalized.replaceAll("[^a-z]", "");
        if (leetAlpha.length() >= 2 && !leetAlpha.equals(alphaOnly)) {
            addToken(tokens, leetAlpha, !isGuarded(leetAlpha));
        }

        // 9. Root stem / abbreviation for longer words (e.g. 'Zezima' -> 'zez', 'Nightmarer' -> 'night')
        for (String word : splitWords) {
            String cleanW = word.replaceAll("[^a-z]", "");
            if (cleanW.length() >= 6) {
                String stem3 = cleanW.substring(0, 3);
                String stem4 = cleanW.substring(0, 4);
                addToken(tokens, stem3, !isGuarded(stem3));
                addToken(tokens, stem4, !isGuarded(stem4));
            }
        }

        // 10. Custom user-defined nicknames from settings
        addCustomNicknames(tokens, customNicknames);

        return tokens;
    }

    private static boolean isGuarded(String word) {
        if (word == null) return false;
        return GUARDED_WORDS.contains(word.toLowerCase().trim());
    }

    private static void addToken(Set<NameToken> tokens, String text, boolean isSafe) {
        if (text == null) return;
        String clean = text.trim().toLowerCase();
        if (clean.length() < 2) return;
        boolean guarded = !isSafe || isGuarded(clean);
        tokens.add(new NameToken(clean, guarded));
    }

    private static void addCustomNicknames(Set<NameToken> tokens, String customNicknames) {
        if (customNicknames != null && !customNicknames.trim().isEmpty()) {
            for (String nick : customNicknames.split("[,;]")) {
                String clean = nick.trim().toLowerCase().replaceAll("[^a-z0-9 ]", "");
                if (clean.length() >= 2) {
                    tokens.add(new NameToken(clean, isGuarded(clean)));
                }
            }
        }
    }

    /**
     * Checks if the message mentions the player by any dynamic tokens or nicknames.
     */
    public static boolean mentionsPlayerName(String message, String rawPlayerName, String customNicknames) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String lowerMsg = message.toLowerCase();
        Set<NameToken> tokens;

        synchronized (tokenLock) {
            if (cachedTokens != null
                    && Objects.equals(lastTokenPlayerName, rawPlayerName)
                    && Objects.equals(lastTokenConfigNicknames, customNicknames)) {
                tokens = cachedTokens;
            } else {
                tokens = generateNameTokens(rawPlayerName, customNicknames);
                cachedTokens = tokens;
                lastTokenPlayerName = rawPlayerName;
                lastTokenConfigNicknames = customNicknames;
            }
        }

        for (NameToken tokenObj : tokens) {
            String token = tokenObj.getToken();
            if (token.isEmpty()) continue;

            if (tokenObj.isGuarded()) {
                // Guarded words must be in a direct-address pattern
                if (matchesGuardedAddress(lowerMsg, token)) {
                    return true;
                }
            } else {
                // Multi-word compound tokens (e.g. "iron arthur", "besti funny")
                if (token.contains(" ")) {
                    if (lowerMsg.matches(".*\\b" + Pattern.quote(token) + "\\b.*")) {
                        return true;
                    }
                } else {
                    // Safe unique tokens match on word boundary anywhere in message
                    if (lowerMsg.matches(".*\\b" + Pattern.quote(token) + "\\b.*")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Determines whether a guarded word (e.g. "iron", "gold", "wood", "fish") is used
     * in a direct-address syntax targeting the player, rather than general item/game discussion.
     */
    public static boolean matchesGuardedAddress(String lowerMsg, String token) {
        String quoted = Pattern.quote(token);

        // 0. If preceded by an inquiry or requirement prefix (e.g. 'what slayer lvl', 'which iron ore'),
        //    it is a game mechanics/item discussion, not a direct player address.
        if (lowerMsg.matches(".*\\b(what|wat|which|any|req|reqs|required|requirement)\\s+" + quoted + "\\b.*")) {
            return false;
        }

        // 1. Preceded by direct greeting (e.g. 'hey iron', 'yo gold', 'sup fish')
        if (lowerMsg.matches(".*\\b(hey|hi|yo|sup|hello|oi|aye|gz|grats|gzz)\\s+" + quoted + "\\b.*")) {
            return true;
        }

        // 2. Preceded by mention symbol (e.g. '@iron', '/god')
        if (lowerMsg.matches(".*[@/]" + quoted + "\\b.*")) {
            return true;
        }

        // 3. Preceded by title / direct pronoun (e.g. 'bro iron', 'dude gold')
        if (lowerMsg.matches(".*\\b(bro|dude|mate|man|lad|homie|sir)\\s+" + quoted + "\\b.*")) {
            return true;
        }

        // 4. Followed by address punctuation (e.g. 'iron,', 'iron:', 'iron?', 'iron!')
        if (lowerMsg.matches(".*\\b" + quoted + "[,:?!].*")) {
            return true;
        }

        // 5. Followed by direct address pronoun, question, or command
        // (e.g. 'iron u there', 'iron can u hop', 'iron wc lvl', 'iron hop pls')
        if (lowerMsg.matches(".*\\b" + quoted + "\\s+(u|you|ur|your|can|are|is|did|do|have|what|wat|how|why|wc|fish|mine|lvl|level|bot|pls|plz|hop|there|alive|stop)\\b.*")) {
            return true;
        }

        // 6. Message starts with the token followed by whitespace and a pronoun/question
        if (lowerMsg.matches("^" + quoted + "\\s+(u|you|ur|can|what|wat|how|why|wc|lvl|pls|plz|hop)\\b.*")) {
            return true;
        }

        // 7. Message is solely the token itself (or with punctuation)
        if (lowerMsg.matches("^" + quoted + "[!?.]*$")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the message represents a direct question or greeting common in OSRS.
     */
    public static boolean isDirectGreetingOrQuestion(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase().trim();

        // 1. Skill / Level questions
        if (lower.matches(".*\\b(lvl|level|wc|fish|mine|fm|fletch|cb|combat|total|xp|stats|att|str|def|range|mage|magic)\\b.*\\?*")
                || lower.contains("wc lvl") || lower.contains("fish lvl") || lower.contains("what lvl") || lower.contains("wat lvl")) {
            return true;
        }

        // 2. Bot checks / AFK checks
        if (lower.matches(".*\\b(bot|botting|afk|u there|you there|u alive|human|macro|script|reported|report)\\b.*\\?*")
                || lower.contains("bot?") || lower.contains("u bot") || lower.contains("you bot")) {
            return true;
        }

        // 3. World / Hop requests
        if (lower.matches(".*\\b(hop|can u hop|can you hop|plz hop|pls hop|world|leave pls)\\b.*\\?*")
                || lower.contains("hop pls") || lower.contains("pls hop")) {
            return true;
        }

        // 4. Time / Location questions
        if (lower.contains("how long u been") || lower.contains("how long have you been")) {
            return true;
        }

        // 5. Compliments
        if (lower.contains("nice cape") || lower.contains("nice pet") || lower.contains("nice stats")
                || lower.contains("nice gear") || lower.contains("gz") || lower.contains("gzz") || lower.contains("grats")) {
            return true;
        }

        // 6. Direct greetings
        if (lower.matches("^(hey|hi|yo|sup|hello|gm|gn|howdy|morning)[!?. ]*$")) {
            return true;
        }

        // 7. Starting with direct address pronouns (e.g. 'bro wc lvl', 'dude u there')
        if (lower.matches("^(bro|dude|mate|man|lad|homie|sir|fam|cuz)[, ]+.*")) {
            return true;
        }

        // 8. General questions ending with '?'
        if (lower.endsWith("?") && (lower.startsWith("why ") || lower.startsWith("how ") || lower.startsWith("what ") || lower.startsWith("wat ") || lower.startsWith("who ") || lower.startsWith("where ") || lower.startsWith("can u ") || lower.startsWith("can you "))) {
            return true;
        }

        return false;
    }

    /**
     * Records that the chatbot sent a reply to the specified player.
     * Establishes an active 45-second conversation thread.
     */
    public static void recordConversationReply(String partnerName) {
        if (partnerName != null && !partnerName.trim().isEmpty()) {
            activeConversationPartner = partnerName.trim();
            activeConversationExpiry = System.currentTimeMillis() + 45000L;
            log.debug("[Chatbot] Active conversation thread set with '{}' for 45s", activeConversationPartner);
        }
    }

    /**
     * Checks if the sender is the active conversation partner within the 45-second window.
     */
    public static boolean isConversationPartner(String sender) {
        if (sender == null || activeConversationPartner == null) {
            return false;
        }
        if (System.currentTimeMillis() > activeConversationExpiry) {
            activeConversationPartner = null;
            return false;
        }
        return activeConversationPartner.equalsIgnoreCase(sender.trim());
    }

    /**
     * Clears any active conversation thread.
     */
    public static void clearConversationThread() {
        activeConversationPartner = null;
        activeConversationExpiry = 0;
    }

    /**
     * Determines whether an incoming message is directly addressed to the player.
     * Fully dynamic for ANY player name on GitHub.
     */
    public static boolean isDirectlyAddressed(String sender, String message, String chatType, ChatbotConfig config) {
        // Private messages are always directly addressed
        if ("private".equalsIgnoreCase(chatType)) {
            return true;
        }

        String localPlayerName = getLocalPlayerName();

        // Ignore messages from self
        if (!localPlayerName.isEmpty() && localPlayerName.equalsIgnoreCase(sender)) {
            return false;
        }

        // 1. Dynamic name mention check (handles all tokens, roots, camelCase, prefixes, nicknames)
        String customNicknames = config != null ? config.customNicknames() : "";
        if (mentionsPlayerName(message, localPlayerName, customNicknames)) {
            return true;
        }

        // 2. Active conversation continuity check (natural 2-way dialogue thread)
        if (isConversationPartner(sender)) {
            try {
                Rs2PlayerModel localPlayer = Rs2Player.getLocalPlayer();
                Rs2PlayerModel senderPlayer = Rs2Player.getPlayer(sender);
                if (localPlayer != null && senderPlayer != null) {
                    WorldPoint myLoc = localPlayer.getWorldLocation();
                    WorldPoint sLoc = senderPlayer.getWorldLocation();
                    int maxDist = config != null ? config.maxDistanceTiles() : 7;
                    if (myLoc != null && sLoc != null && (maxDist <= 0 || myLoc.distanceTo(sLoc) <= maxDist)) {
                        return true;
                    }
                } else {
                    return true;
                }
            } catch (Exception ignored) {
                return true;
            }
        }

        // 3. Active in-game player interaction check (trade, follow, combat)
        try {
            Rs2PlayerModel localPlayer = Rs2Player.getLocalPlayer();
            Rs2PlayerModel senderPlayer = Rs2Player.getPlayer(sender);
            if (localPlayer != null && senderPlayer != null) {
                Actor senderInteracting = senderPlayer.getInteracting();
                Actor myInteracting = localPlayer.getInteracting();
                if (senderInteracting == localPlayer || myInteracting == senderPlayer) {
                    return true;
                }

                // 4. Proximity 1-on-1 direct question check (<= 3 tiles in low-density area)
                WorldPoint myLoc = localPlayer.getWorldLocation();
                WorldPoint sLoc = senderPlayer.getWorldLocation();
                if (myLoc != null && sLoc != null) {
                    int dist = myLoc.distanceTo(sLoc);
                    if (dist <= 3 && isDirectGreetingOrQuestion(message)) {
                        // Count players in proximity to distinguish 1-on-1 interaction from crowd cluster
                        long nearbyOthers = Rs2Player.getPlayers(p -> {
                            if (p == null || p.getWorldLocation() == null) return false;
                            return p.getWorldLocation().distanceTo(myLoc) <= 4;
                        }).count();

                        // In a 1-on-1 or small group (<= 3 players total), a nearby direct question is addressed to you!
                        // At crowded locations like the Grand Exchange (> 3 players), this returns false to prevent noise.
                        if (nearbyOthers <= 3) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[Chatbot] Error checking dynamic player interaction: {}", e.getMessage());
        }

        return false;
    }
}
