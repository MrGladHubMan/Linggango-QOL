package com.linggango.easyparry.client;

import com.linggango.easyparry.EasyParryConfig;
import com.misanthropy.linggango.linggango_tweaks.client.screen.OptionListScreen;
import com.misanthropy.linggango.linggango_tweaks.config.DisplayClientConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EasyParryScreen extends OptionListScreen {
    private static final Component SUBTITLE = Component.m_237113_(
            "Auto-Parry, Perfect %, Return by Death (Subaru Mode), +64 Accessories & Co-op (Shortcut: O)"
    );

    private final Screen lastScreen;

    public EasyParryScreen(Screen lastScreen) {
        super(Component.m_237113_("Easy Parry & Gameplay Menu"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected Component subtitle() {
        return SUBTITLE;
    }

    @Override
    protected void buildRows() {
        this.header("Easy Parry System");

        this.toggle(
                "Master Enable Easy Parry",
                EasyParryConfig.MASTER_ENABLED,
                "Turns the entire Easy Parry & Extra Accessories system On or Off. When Off, reverts to standard modpack behavior."
        );

        this.toggle(
                "Auto-Parry Incoming Attacks",
                EasyParryConfig.AUTO_PARRY,
                "Automatically parries all incoming melee and projectile attacks without needing to press F."
        );

        this.slider(
                "Perfect Parry Chance",
                EasyParryConfig.PERFECT_PARRY_CHANCE,
                0,
                100,
                "Chance (0% - 100%) for any successful parry (manual or auto) to become a Tier 3 Perfect Parry, even without Cold Read unlocked.",
                v -> v + "%"
        );

        this.slider(
                "Parry Active Window",
                EasyParryConfig.PARRY_WINDOW_TICKS,
                1,
                60,
                "How long your manual parry stays active in ticks (20 ticks = 1.0 second; default modpack value is 3 ticks).",
                v -> v + "t (" + String.format("%.2fs", v / 20.0F) + ")"
        );

        this.header("Extra Accessories & Stacking");

        this.slider(
                "Extra Accessory Slots (Curio Channel)",
                EasyParryConfig.EXTRA_ACCESSORY_SLOTS,
                0,
                128,
                "Adds universal 'Curio' accessory slots (default +64) to your Curios inventory that accept ANY accessory type (Rings, Charms, Belts, Necklaces, Hands, etc.).",
                v -> "+" + v + " Slots"
        );

        this.toggle(
                "Unlimited Accessory Stacking (No 3-Set Limit)",
                EasyParryConfig.UNLIMITED_ACCESSORY_STACKING,
                "Removes the 3-accessory set limit, duplicate suppression (red X), and accessory damage/lifesteal caps so all equipped accessories work at 100% power."
        );

        this.header("Return by Death (Re:Zero Subaru Mode)");

        this.toggle(
                "Return by Death Mode (Players Only)",
                EasyParryConfig.RETURN_BY_DEATH,
                "When you take lethal damage, blocks the Death Screen, plays the Subaru Return by Death SFX, fades to black, and rewinds you back before death with full HP!"
        );

        this.slider(
                "Return by Death Rewind Time",
                EasyParryConfig.RETURN_BY_DEATH_SECONDS,
                3,
                30,
                "How many seconds before death to rewind your position when Return by Death triggers (default: 10 seconds).",
                v -> v + " Seconds"
        );

        this.header("Gambler Class — Hakari Jackpot (4m 11s)");

        this.toggle(
                "Hakari Jackpot Mode (Green Aura + Music + Rapid Heal)",
                EasyParryConfig.HAKARI_JACKPOT_MODE,
                "When Class Gambler hits Jackpot on Roll the Dice, surrounds you with Emerald Green Cursed Energy, plays Hakari Jackpot music, and rapidly heals you for 4 minutes 11 seconds!"
        );

        this.slider(
                "Gambler Jackpot Chance",
                EasyParryConfig.GAMBLER_JACKPOT_CHANCE,
                1,
                100,
                "Percentage chance (1% - 100%) to hit Jackpot when using Gambler's Roll the Dice skill & passive crit (default: 15%, vanilla is 10%).",
                v -> v + "%"
        );

        this.toggle(
                "Hakari Idle Dance Animation",
                EasyParryConfig.HAKARI_IDLE_DANCE,
                "Plays the Kinji Hakari Jackpot dance whenever you (or a friend) stand still during Jackpot mode. View it in 3rd person (F5)."
        );

        this.toggle(
                "Preview Hakari Dance (No Jackpot Needed)",
                EasyParryConfig.HAKARI_DANCE_PREVIEW,
                "Test the dance anytime: stand still and press F5 to see it on yourself. Only you see the preview. Turn this off when done."
        );

        this.slider(
                "Hakari Jackpot Music Volume",
                EasyParryConfig.HAKARI_MUSIC_VOLUME,
                0,
                100,
                "Volume of the Hakari Jackpot music (0% = Muted, 100% = Max Volume; default: 80%). Updates live even while music is playing!",
                v -> v == 0 ? "Muted (0%)" : v + "%"
        );

        this.slider(
                "Hakari Rapid Heal (per 0.15s)",
                EasyParryConfig.HAKARI_REGEN_HALF_HEARTS,
                1,
                20,
                "Health regenerated every 0.15 seconds (3 ticks) during Hakari Jackpot (default: 2 HP = 1.0 Heart every 0.15s).",
                v -> v + " HP (" + String.format("%.1f Hearts", v / 2.0F) + " / 0.15s)"
        );

        this.slider(
                "Hakari Jackpot Duration",
                EasyParryConfig.HAKARI_DURATION_SECONDS,
                10,
                600,
                "Duration of Hakari Jackpot mode in seconds (default: 251s = 4 minutes 11 seconds).",
                v -> String.format("%dm %02ds (%ds)", v / 60, v % 60, v)
        );

        this.header("Quality of Life (Item & XP Magnet)");

        this.toggle(
                "Gentle Item & XP Magnet",
                EasyParryConfig.ITEM_MAGNET,
                "Smoothly pulls nearby dropped items and XP orbs toward you. Hold Shift (Sneak) to pause the magnet, and newly dropped items (Q) won't be sucked back immediately."
        );

        this.slider(
                "Magnet Pull Range",
                EasyParryConfig.ITEM_MAGNET_RANGE,
                3,
                16,
                "Radius in blocks for the Gentle Item & XP Magnet (default: 8 blocks).",
                v -> v + " Blocks"
        );

        this.header("Parry Rules & Bonuses");

        this.toggle(
                "Parry Everything (Heavy / 360 / Bare-Hand)",
                EasyParryConfig.PARRY_EVERYTHING,
                "Allows parrying unblockable Red Heavy Attacks, Magic, Fire, Explosions, 360 degrees around you, and Bare-handed without a weapon."
        );

        this.toggle(
                "No Miss Cooldown",
                EasyParryConfig.NO_MISS_COOLDOWN,
                "Removes the lockout cooldown penalty when a manual parry misses so you can press Parry again immediately."
        );

        this.slider(
                "Heal on Successful Parry",
                EasyParryConfig.BONUS_PARRY_HEAL,
                0,
                20,
                "Health restored on every successful parry (2 HP = 1 Heart).",
                v -> v + " HP (" + String.format("%.1f Hearts", v / 2.0F) + ")"
        );

        this.header("Co-op & World Tweaks");

        this.toggle(
                "Easier World, Boss & Progression Tweaks",
                EasyParryConfig.EASIER_WORLD_TWEAKS,
                "Unlocks boss-gated equipment, disables cave/darkness hazards, disables boss HP reset & armor-pierce floor, and buffs Roll/Dash."
        );

        this.toggle(
                "Apply to All Friends on Server / LAN",
                EasyParryConfig.APPLY_TO_FRIENDS,
                "When hosting LAN / Essential / Server, applies your settings as the default for friends who join (friends with this mod can customize their own in O!)."
        );

        this.header("Crosshair & Cooldown Readout");

        this.toggle(
                "Icon beside the crosshair",
                DisplayClientConfig.PARRY_CROSSHAIR_ICON,
                "Puts a small parry icon next to the crosshair. Turn this off to use the panel in the corner of the screen."
        );

        this.toggle(
                "Show cooldown as a bar",
                DisplayClientConfig.PARRY_COOLDOWN_BAR,
                "On draws a bar under the icon that drains as the lockout runs down."
        );

        this.header("Auto Update (v" + EasyParryUpdater.currentVersion() + ")");

        this.toggle(
                "Auto-Update Easy Parry",
                EasyParryConfig.AUTO_UPDATE,
                "Checks for a newer version on game start, downloads it in the background, and installs it when you close the game. "
                        + "Status: " + EasyParryUpdater.statusText()
        );
    }

    @Override
    public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
        if (EasyParryClient.MENU_KEY.m_90832_(keyCode, scanCode)) {
            this.m_7379_();
            return true;
        }
        return super.m_7933_(keyCode, scanCode, modifiers);
    }

    @Override
    public void m_7379_() {
        EasyParryConfig.saveAndSync();
        try {
            DisplayClientConfig.SPEC.save();
        } catch (Throwable ignored) {
        }
        if (this.f_96541_ != null) {
            this.f_96541_.m_91152_(this.lastScreen);
        }
    }
}
