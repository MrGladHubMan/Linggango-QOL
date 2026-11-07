package com.linggango.easyparry;

import com.misanthropy.linggango.linggango_tweaks.config.TweaksConfig;
import com.misanthropy.linggango.linggango_tweaks.tweak.balance.AccessoryLimitsConfig;
import com.misanthropy.linggango.linggango_tweaks.tweak.balance.AccessoryStacking;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EasyParryConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue MASTER_ENABLED;
    public static final ForgeConfigSpec.BooleanValue AUTO_PARRY;
    public static final ForgeConfigSpec.IntValue PERFECT_PARRY_CHANCE;
    public static final ForgeConfigSpec.IntValue PARRY_WINDOW_TICKS;
    public static final ForgeConfigSpec.BooleanValue PARRY_EVERYTHING;
    public static final ForgeConfigSpec.BooleanValue NO_MISS_COOLDOWN;
    public static final ForgeConfigSpec.IntValue BONUS_PARRY_HEAL;
    public static final ForgeConfigSpec.IntValue EXTRA_ACCESSORY_SLOTS;
    public static final ForgeConfigSpec.BooleanValue UNLIMITED_ACCESSORY_STACKING;
    public static final ForgeConfigSpec.BooleanValue RETURN_BY_DEATH;
    public static final ForgeConfigSpec.IntValue RETURN_BY_DEATH_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ITEM_MAGNET;
    public static final ForgeConfigSpec.IntValue ITEM_MAGNET_RANGE;
    public static final ForgeConfigSpec.BooleanValue HAKARI_JACKPOT_MODE;
    public static final ForgeConfigSpec.IntValue GAMBLER_JACKPOT_CHANCE;
    public static final ForgeConfigSpec.BooleanValue HAKARI_IDLE_DANCE;
    public static final ForgeConfigSpec.IntValue HAKARI_MUSIC_VOLUME;
    public static final ForgeConfigSpec.IntValue HAKARI_REGEN_HALF_HEARTS;
    public static final ForgeConfigSpec.IntValue HAKARI_DURATION_SECONDS;
    public static final ForgeConfigSpec.BooleanValue EASIER_WORLD_TWEAKS;
    public static final ForgeConfigSpec.BooleanValue APPLY_TO_FRIENDS;
    public static final ForgeConfigSpec.BooleanValue SHOW_HUD_OVERLAY;
    public static final ForgeConfigSpec.BooleanValue HAKARI_DANCE_PREVIEW;
    public static final ForgeConfigSpec.BooleanValue AUTO_UPDATE;
    public static final ForgeConfigSpec.ConfigValue<String> UPDATE_MANIFEST_URL;

    private static Map<Object, Object> backupStackingById = null;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("easy_parry");

        MASTER_ENABLED = b
                .comment("Master switch to turn the entire Easy Parry & Menu system ON or OFF.")
                .define("master_enabled", true);

        AUTO_PARRY = b
                .comment("When ON, incoming attacks are automatically parried without needing to press the Parry key.")
                .define("auto_parry", false);

        PERFECT_PARRY_CHANCE = b
                .comment("Percentage chance (0% - 100%) for any successful parry (manual or auto) to become a Tier 3 Perfect Parry.")
                .defineInRange("perfect_parry_chance", 100, 0, 100);

        PARRY_WINDOW_TICKS = b
                .comment("Active parry time window in ticks (20 ticks = 1.0 second; default pack value is 3 ticks).")
                .defineInRange("parry_window_ticks", 20, 1, 60);

        PARRY_EVERYTHING = b
                .comment("Allow parrying Red Heavy Attacks, Magic, Fire, Explosions, Projectiles, 360 degrees around you, and Bare-handed.")
                .define("parry_everything", true);

        NO_MISS_COOLDOWN = b
                .comment("Remove the lockout cooldown when a manual parry misses so you can parry again immediately.")
                .define("no_miss_cooldown", true);

        BONUS_PARRY_HEAL = b
                .comment("Health healed on every successful parry (2 HP = 1 Heart).")
                .defineInRange("bonus_parry_heal", 4, 0, 20);

        EXTRA_ACCESSORY_SLOTS = b
                .comment("Number of extra universal 'curio' accessory slots added to your Curios inventory (0 - 128, default 64). Accepts ANY accessory type.")
                .defineInRange("extra_accessory_slots", 64, 0, 128);

        UNLIMITED_ACCESSORY_STACKING = b
                .comment("Remove the 3-accessory set limit, duplicate suppression (red X), and accessory damage/lifesteal caps so all equipped accessories work at 100% power.")
                .define("unlimited_accessory_stacking", true);

        RETURN_BY_DEATH = b
                .comment("Re:Zero Subaru 'Return by Death' Mode (Players Only): When you take fatal damage, prevents the Death Screen, plays the Subaru Return by Death SFX, fades the screen, and rewinds you back in time before death! (Quick toggle key: B)")
                .define("return_by_death", true);

        RETURN_BY_DEATH_SECONDS = b
                .comment("How many seconds before death to rewind the player when Return by Death triggers (3 - 30 seconds, default 10).")
                .defineInRange("return_by_death_seconds", 10, 3, 30);

        ITEM_MAGNET = b
                .comment("Gentle Item & XP Magnet (QoL): Smoothly pulls nearby dropped items and XP orbs toward you. Hold Shift (Sneak) to pause the magnet when dropping items for friends.")
                .define("item_magnet", true);

        ITEM_MAGNET_RANGE = b
                .comment("Radius in blocks for the Item & XP Magnet (3 - 16 blocks, default 8).")
                .defineInRange("item_magnet_range", 8, 3, 16);

        HAKARI_JACKPOT_MODE = b
                .comment("Gambler Class Hakari Jackpot Mode: When Gambler hits Jackpot on Roll the Dice, grants rapid healing (1 Heart / 0.15s), Emerald Green Cursed Energy Aura, Idle Dance, and plays Hakari Jackpot music!")
                .define("hakari_jackpot_mode", true);

        GAMBLER_JACKPOT_CHANCE = b
                .comment("Percentage chance (1% - 100%) for Class Gambler to hit Jackpot (default 15%, vanilla is 10%).")
                .defineInRange("gambler_jackpot_chance", 15, 1, 100);

        HAKARI_IDLE_DANCE = b
                .comment("Play the custom 4-phase Kinji Hakari Idle Dance animation loop whenever standing still in Jackpot mode.")
                .define("hakari_idle_dance", true);

        HAKARI_MUSIC_VOLUME = b
                .comment("Volume percentage (0% - 100%) for the Hakari Jackpot music (default 80%).")
                .defineInRange("hakari_music_volume", 80, 0, 100);

        HAKARI_REGEN_HALF_HEARTS = b
                .comment("Health regenerated every 0.15 seconds (3 ticks) during Hakari Jackpot (2 HP = 1.0 Heart, 4 HP = 2.0 Hearts; default 2).")
                .defineInRange("hakari_regen_half_hearts", 2, 1, 20);

        HAKARI_DURATION_SECONDS = b
                .comment("Duration in seconds of Hakari Jackpot mode (default 251 seconds = 4 minutes 11 seconds).")
                .defineInRange("hakari_duration_seconds", 251, 10, 600);

        EASIER_WORLD_TWEAKS = b
                .comment("Automatically apply easier Co-op & World Tweaks (unlock equipment gates, disable cave/darkness hazards, disable boss HP reset & damage floor, faster Roll/Dash).")
                .define("easier_world_tweaks", true);

        APPLY_TO_FRIENDS = b
                .comment("When hosting LAN / Essential / Server, apply these Easy Parry & Extra Accessory benefits to all connected friends/players.")
                .define("apply_to_friends", true);

        SHOW_HUD_OVERLAY = b
                .comment("Show or hide the compact Easy Parry status pill in the top-left corner of the HUD.")
                .define("show_hud_overlay", false);

        HAKARI_DANCE_PREVIEW = b
                .comment("Preview the Hakari idle dance on yourself without needing a Jackpot (stand still in 3rd person, F5). Only you see the preview.")
                .define("hakari_dance_preview", false);

        b.pop();

        b.push("auto_update");

        AUTO_UPDATE = b
                .comment("Automatically check for a newer Easy Parry jar when the game starts, download it in the background,",
                        "and install it the next time the game closes. Turn off to stay on your current version.")
                .define("auto_update", true);

        UPDATE_MANIFEST_URL = b
                .comment("HTTPS address of the update manifest (update.json). Leave empty to use the address built into the mod.")
                .define("update_manifest_url", "");

        b.pop();
        SPEC = b.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void syncAccessoryStacking() {
        try {
            Field byIdField = AccessoryStacking.class.getDeclaredField("BY_ID");
            byIdField.setAccessible(true);
            Map byIdMap = (Map) byIdField.get(null);

            Field resolvedField = AccessoryStacking.class.getDeclaredField("resolved");
            resolvedField.setAccessible(true);

            if (backupStackingById == null && byIdMap != null && !byIdMap.isEmpty()) {
                backupStackingById = new HashMap<>(byIdMap);
            }

            if (UNLIMITED_ACCESSORY_STACKING.get()) {
                TweaksConfig.STACKING_SECOND_FACTOR.set(1.0D);
                TweaksConfig.STACKING_THIRD_FACTOR.set(1.0D);
                AccessoryLimitsConfig.HIT_KNEE.set(0.0D);
                AccessoryLimitsConfig.LIFESTEAL_PER_HIT.set(0.0D);
                AccessoryLimitsConfig.ZEROED_HIT_FLOOR.set(false);

                if (byIdMap != null) {
                    byIdMap.clear();
                }
                resolvedField.set(null, Collections.emptyMap());
            } else {
                TweaksConfig.STACKING_SECOND_FACTOR.set(0.5D);
                TweaksConfig.STACKING_THIRD_FACTOR.set(0.25D);
                if (byIdMap != null && backupStackingById != null && byIdMap.isEmpty()) {
                    byIdMap.putAll(backupStackingById);
                    resolvedField.set(null, null);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void syncToLinggangoTweaks() {
        try {
            syncAccessoryStacking();

            if (EASIER_WORLD_TWEAKS.get()) {
                TweaksConfig.GATES_ENABLED.set(false);
                TweaksConfig.MOB_STAGES_ENABLED.set(false);
                TweaksConfig.DARKNESS.set(false);
                TweaksConfig.STALACTITES.set(false);
                TweaksConfig.MINING_NOISE.set(false);
                TweaksConfig.GAS_POCKETS.set(false);
                TweaksConfig.CAVE_STALKERS.set(false);
                TweaksConfig.WILDS_PREDATORS.set(false);
                TweaksConfig.WILDS_STALKERS.set(false);
                TweaksConfig.WILDS_SUNPROOF_CHANCE.set(0.0D);
                TweaksConfig.STONE_AGE_LOGS_NEED_AXE.set(false);
                TweaksConfig.STONE_AGE_TOOL_LIMITS.set(false);
                TweaksConfig.INVASION_RANDOM_CHANCE.set(0.0D);
                TweaksConfig.ERASURE_CHANCE.set(0.0D);
                TweaksConfig.DAMAGE_FLOOR_FRACTION.set(0.13D);
                TweaksConfig.PERCENTAGE_RESTORED.set(0);
                TweaksConfig.BOSS_INTAKE_SECONDS.set(0);
                TweaksConfig.LADDER_LEVELS_AT_FULL.set(200);
                TweaksConfig.LADDER_HARDMODE_LEVELS.set(30);
                TweaksConfig.ROLL_COOLDOWN_SECONDS.set(1.2D);
                TweaksConfig.ROLL_INVULNERABLE_TICKS.set(6);
                TweaksConfig.ROLL_EXHAUSTION.set(0.0D);
                TweaksConfig.ROLL_FOOD_REQUIRED.set(0);
                TweaksConfig.ROLL_DODGE_REFUND_TICKS.set(6);
                TweaksConfig.DASH_INVULNERABLE_TICKS.set(5);
                TweaksConfig.DASH_COOLDOWN_SECONDS.set(0.6D);
            }

            if (!MASTER_ENABLED.get()) {
                // master off: back to pack-like parry, just without the long hitstop/lockout
                TweaksConfig.PARRY_ACTIVE_WINDOW.set(5);
                TweaksConfig.PARRY_PERFECT_TICKS.set(2);
                TweaksConfig.PARRY_PERFECT_SHARE.set(0.6D);
                TweaksConfig.PARRY_SUCCESS_TICKS.set(2);
                TweaksConfig.PARRY_RECOVERY.set(2);
                TweaksConfig.PARRY_HITSTOP_TICKS.set(0);
                TweaksConfig.PARRY_COOLDOWN.set(8);
                TweaksConfig.PARRY_MISS_LOCKOUT_MAX.set(30);
                TweaksConfig.PARRY_ARC_DEGREES.set(160.0D);
                TweaksConfig.PARRY_REQUIRE_WEAPON.set(false);
                TweaksConfig.PARRY_PARTIAL_ENABLED.set(true);
                return;
            }

            // the client window stays short so you can re-press quickly;
            // the long window (parry_window_ticks) is enforced server side in EasyParryServerHandler
            TweaksConfig.PARRY_ACTIVE_WINDOW.set(5);
            TweaksConfig.PARRY_PERFECT_TICKS.set(5);
            TweaksConfig.PARRY_PERFECT_SHARE.set(1.0D);
            TweaksConfig.PARRY_SUCCESS_TICKS.set(2);
            TweaksConfig.PARRY_RECOVERY.set(2);
            TweaksConfig.PARRY_HITSTOP_TICKS.set(0);
            TweaksConfig.PARRY_HEAL_AMOUNT.set((double) BONUS_PARRY_HEAL.get());

            if (PARRY_EVERYTHING.get()) {
                TweaksConfig.PARRY_ARC_DEGREES.set(360.0D);
                TweaksConfig.PARRY_REQUIRE_WEAPON.set(false);
                TweaksConfig.PARRY_PARTIAL_ENABLED.set(false);
            } else {
                TweaksConfig.PARRY_ARC_DEGREES.set(160.0D);
                TweaksConfig.PARRY_REQUIRE_WEAPON.set(false);
                TweaksConfig.PARRY_PARTIAL_ENABLED.set(true);
            }

            if (NO_MISS_COOLDOWN.get()) {
                TweaksConfig.PARRY_COOLDOWN.set(1);
                TweaksConfig.PARRY_MISS_LOCKOUT_MAX.set(1);
            } else {
                TweaksConfig.PARRY_COOLDOWN.set(8);
                TweaksConfig.PARRY_MISS_LOCKOUT_MAX.set(30);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void saveAndSync() {
        try {
            SPEC.save();
        } catch (Throwable ignored) {
        }
        syncToLinggangoTweaks();
        EasyParryNetwork.sendLocalSettingsToServer();
    }
}
