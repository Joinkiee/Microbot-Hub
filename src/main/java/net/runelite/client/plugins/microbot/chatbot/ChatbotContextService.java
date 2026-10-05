package net.runelite.client.plugins.microbot.chatbot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Collection;

/**
 * Provides live in-game context (location, activity, skills, active scripts)
 * to ground the Chatbot AI with real-time game awareness.
 */
@Slf4j
public class ChatbotContextService {

    /**
     * Identifies the current player location based on coordinates.
     */
    public static String getLocationName() {
        if (!Microbot.isLoggedIn()) {
            return "Not logged in";
        }
        try {
            WorldPoint wp = Rs2Player.getWorldLocation();
            if (wp == null) {
                return "Gielinor";
            }
            int x = wp.getX();
            int y = wp.getY();

            // Grand Exchange
            if (x >= 3140 && x <= 3190 && y >= 3470 && y <= 3515) return "Grand Exchange (GE)";
            // Varrock East / West / Center
            if (x >= 3180 && x <= 3280 && y >= 3380 && y <= 3450) return "Varrock";
            // Edgeville
            if (x >= 3070 && x <= 3120 && y >= 3460 && y <= 3520) return "Edgeville";
            // Lumbridge
            if (x >= 3200 && x <= 3245 && y >= 3200 && y <= 3255) return "Lumbridge";
            // Wintertodt
            if (x >= 1610 && x <= 1655 && y >= 3930 && y <= 4005) return "Wintertodt";
            // Woodcutting Guild
            if (x >= 1560 && x <= 1610 && y >= 3470 && y <= 3520) return "Woodcutting Guild";
            // Barbarian Village
            if (x >= 3070 && x <= 3115 && y >= 3410 && y <= 3450) return "Barbarian Village";
            // Falador
            if (x >= 2940 && x <= 3060 && y >= 3320 && y <= 3400) return "Falador";
            // Seers' Village / Camelot
            if (x >= 2690 && x <= 2760 && y >= 3450 && y <= 3510) return "Seers' Village";
            // Catherby
            if (x >= 2790 && x <= 2860 && y >= 3420 && y <= 3460) return "Catherby";
            // Ardougne
            if (x >= 2560 && x <= 2680 && y >= 3260 && y <= 3340) return "Ardougne";
            // Draynor Village
            if (x >= 3070 && x <= 3130 && y >= 3220 && y <= 3280) return "Draynor Village";
            // Al Kharid
            if (x >= 3260 && x <= 3320 && y >= 3140 && y <= 3310) return "Al Kharid";
            // Ferox Enclave
            if (x >= 3125 && x <= 3165 && y >= 3625 && y <= 3655) return "Ferox Enclave";
            // Wilderness
            if (y >= 3525) {
                int lvl = (y - 3520) / 8 + 1;
                return "Wilderness (Level " + lvl + ")";
            }
            // Motherlode Mine
            if (x >= 3720 && x <= 3775 && y >= 5640 && y <= 5695) return "Motherlode Mine";
            // Blast Furnace
            if (x >= 1930 && x <= 1965 && y >= 4950 && y <= 4985) return "Blast Furnace";
            // Guardians of the Rift
            if (x >= 3590 && x <= 3645 && y >= 9480 && y <= 9535) return "Guardians of the Rift";
            // Prifddinas
            if (x >= 3200 && x <= 3300 && y >= 6000 && y <= 6150) return "Prifddinas";
            // Barrows
            if (x >= 3550 && x <= 3585 && y >= 3270 && y <= 3310) return "Barrows";

            return "World " + Rs2Player.getWorld() + " near coordinates " + x + ", " + y;
        } catch (Exception e) {
            return "Gielinor";
        }
    }

    /**
     * Determines the current activity/skill the player is actively engaged in.
     */
    public static String getActivityDescription() {
        if (!Microbot.isLoggedIn()) {
            return "offline";
        }
        try {
            int anim = Rs2Player.getAnimation();

            // Woodcutting animations (axes)
            if (anim == 879 || anim == 877 || anim == 875 || anim == 873 || anim == 871 || anim == 869
                    || anim == 867 || anim == 865 || anim == 2846 || anim == 7264 || anim == 8324) {
                int wc = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
                return "woodcutting / chopping trees (Woodcutting lvl " + wc + ")";
            }

            // Fishing animations
            if (anim == 621 || anim == 622 || anim == 623 || anim == 7401 || anim == 7402) {
                int fish = Rs2Player.getRealSkillLevel(Skill.FISHING);
                return "fishing (Fishing lvl " + fish + ")";
            }

            // Mining animations (pickaxes)
            if (anim == 624 || anim == 628 || anim == 629 || anim == 6753 || anim == 7139 || anim == 8344) {
                int mine = Rs2Player.getRealSkillLevel(Skill.MINING);
                return "mining rocks (Mining lvl " + mine + ")";
            }

            // Cooking animations
            if (anim == 896 || anim == 897) {
                int cook = Rs2Player.getRealSkillLevel(Skill.COOKING);
                return "cooking food (Cooking lvl " + cook + ")";
            }

            // Firemaking animation
            if (anim == 733) {
                int fm = Rs2Player.getRealSkillLevel(Skill.FIREMAKING);
                return "burning logs / firemaking (Firemaking lvl " + fm + ")";
            }

            // Attacking / in combat
            Actor target = Rs2Player.getInteracting();
            if (target != null && target.getName() != null && !target.getName().trim().isEmpty()) {
                return "fighting " + target.getName().trim() + " (Combat lvl " + Rs2Player.getCombatLevel() + ")";
            }

            // Check running gameplay activity
            String activeActivity = getActiveGameplayActivity();
            if (activeActivity != null && !activeActivity.isEmpty()) {
                return activeActivity;
            }

            // Moving vs standing idle
            if (Rs2Player.isMoving()) {
                return "walking / running around";
            }

            return "bankstanding / chilling";
        } catch (Exception e) {
            return "chilling";
        }
    }

    /**
     * Checks if a recognized skilling or minigame plugin is running and returns a natural player activity.
     * All development, testing, automation utility, and background plugins are strictly excluded.
     */
    public static String getActiveGameplayActivity() {
        try {
            PluginManager pm = Microbot.getPluginManager();
            if (pm == null) return null;
            Collection<Plugin> plugins = pm.getPlugins();
            if (plugins == null) return null;

            for (Plugin p : plugins) {
                if (!pm.isPluginEnabled(p)) continue;
                String className = p.getClass().getSimpleName();
                String lower = className.toLowerCase();

                // Strict filter: Exclude all utility, test, harness, helper, dev, recorder, antiban, server plugins
                if (lower.contains("test") || lower.contains("runner") || lower.contains("example")
                        || lower.contains("harness") || lower.contains("statemachine") || lower.contains("replay")
                        || lower.contains("recorder") || lower.contains("macro") || lower.contains("helper")
                        || lower.contains("shortestpath") || lower.contains("antiban") || lower.contains("dashboard")
                        || lower.contains("autologin") || lower.contains("selector") || lower.contains("organizer")
                        || lower.contains("seller") || lower.contains("dismiss") || lower.contains("breakhandler")
                        || lower.contains("agentserver") || lower.contains("qol") || lower.contains("chatbot")
                        || lower.contains("microbot") || lower.contains("inventorysetups")) {
                    continue;
                }

                // Map recognized skilling & minigame plugins to natural player activities
                if (lower.contains("woodcutting")) return "chopping trees / woodcutting";
                if (lower.contains("fishing") || lower.contains("fisher")) return "fishing";
                if (lower.contains("mining") || lower.contains("miner")) return "mining rocks";
                if (lower.contains("cooking") || lower.contains("cooker")) return "cooking food";
                if (lower.contains("firemaking") || lower.contains("firemaker")) return "burning logs / firemaking";
                if (lower.contains("magic")) return "training magic";
                if (lower.contains("fighter") || lower.contains("combat")) return "training combat / fighting";
                if (lower.contains("agility")) return "running agility laps";
                if (lower.contains("thieving") || lower.contains("thiever")) return "training thieving";
                if (lower.contains("fletching")) return "fletching";
                if (lower.contains("crafting")) return "crafting";
                if (lower.contains("smithing") || lower.contains("smelting")) return "smithing / smelting";
                if (lower.contains("herblore") || lower.contains("cleaner")) return "herblore";
                if (lower.contains("farming") || lower.contains("tree")) return "farming run";
                if (lower.contains("hunter")) return "training hunter";
                if (lower.contains("runecraft")) return "runecrafting";
                if (lower.contains("wintertodt")) return "doing wintertodt";
                if (lower.contains("tempoross")) return "doing tempoross";
                if (lower.contains("barrows")) return "doing barrows runs";
                if (lower.contains("gauntlet")) return "doing gauntlet";
                if (lower.contains("zulrah")) return "killing zulrah";
                if (lower.contains("vorkath")) return "killing vorkath";
                if (lower.contains("flipper")) return "flipping items at the GE";
                if (lower.contains("buyer")) return "buying items at the GE";
                if (lower.contains("bankstander")) return "bankstanding at the GE";
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Summarizes relevant player skills.
     */
    public static String getSkillsSummary() {
        if (!Microbot.isLoggedIn()) {
            return "";
        }
        try {
            Client client = Microbot.getClient();
            int total = client != null ? client.getTotalLevel() : 0;
            return String.format(
                    "Combat: %d, Total Lvl: %d | Woodcutting: %d, Fishing: %d, Mining: %d, Firemaking: %d, Cooking: %d, Magic: %d, Ranged: %d, Strength: %d, Attack: %d, Defence: %d",
                    Rs2Player.getCombatLevel(),
                    total,
                    Rs2Player.getRealSkillLevel(Skill.WOODCUTTING),
                    Rs2Player.getRealSkillLevel(Skill.FISHING),
                    Rs2Player.getRealSkillLevel(Skill.MINING),
                    Rs2Player.getRealSkillLevel(Skill.FIREMAKING),
                    Rs2Player.getRealSkillLevel(Skill.COOKING),
                    Rs2Player.getRealSkillLevel(Skill.MAGIC),
                    Rs2Player.getRealSkillLevel(Skill.RANGED),
                    Rs2Player.getRealSkillLevel(Skill.STRENGTH),
                    Rs2Player.getRealSkillLevel(Skill.ATTACK),
                    Rs2Player.getRealSkillLevel(Skill.DEFENCE)
            );
        } catch (Exception e) {
            return "Combat: " + Rs2Player.getCombatLevel();
        }
    }

    /**
     * Builds a comprehensive context prompt block for LLM completion.
     */
    public static String buildContextPrompt(ChatbotConfig config) {
        if (config == null || !config.enableGameContext() || !Microbot.isLoggedIn()) {
            return "";
        }
        try {
            String localName = "me";
            if (Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null) {
                String n = Microbot.getClient().getLocalPlayer().getName();
                if (n != null && !n.isEmpty()) {
                    localName = n.replace('\u00A0', ' ').trim();
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\nLIVE OSRS GAME CONTEXT (Use this to answer questions accurately and naturally):\n");
            sb.append("- Current Location: ").append(getLocationName()).append(" (World ").append(Rs2Player.getWorld()).append(")\n");
            sb.append("- Current In-Game Activity: ").append(getActivityDescription()).append("\n");
            sb.append("- Your Stats: ").append(getSkillsSummary()).append("\n");
            sb.append("- Instructions: If someone asks 'wc lvl?', 'what u doing?', 'where at?', etc., reply casually based on these exact live stats and location.\n");
            sb.append("- SAFETY MANDATE: NEVER use words like 'test', 'runner', 'testrunner', 'script', 'bot', 'plugin', or 'program'. Always act like a real player chilling.\n");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
