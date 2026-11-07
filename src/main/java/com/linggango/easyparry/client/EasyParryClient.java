package com.linggango.easyparry.client;

import com.linggango.easyparry.EasyParryConfig;
import com.linggango.easyparry.EasyParryServerHandler;
import com.misanthropy.hit_indicator.client.WindupTracker;
import com.misanthropy.linggango.linggango_tweaks.client.screen.ParryOptionsScreen;
import com.misanthropy.linggango.linggango_tweaks.parry.client.ParryEffects;
import com.mojang.blaze3d.audio.OggAudioStream;
import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.openal.AL10;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class EasyParryClient {
    public static final String CATEGORY = "key.categories.linggango_tweaks";

    public static final KeyMapping MENU_KEY = new KeyMapping(
            "key.linggango_easy_parry.menu",
            InputConstants.Type.KEYSYM,
            79, // 'O' key
            CATEGORY
    );

    private static long lastAutoSwingTick = 0L;

    private static int rbdFadeTicks = 0;
    private static int rbdMaxFadeTicks = 55;
    private static int rbdRewindSec = 10;

    private static int alBufferId = 0;
    private static int alSourceId = 0;
    private static boolean alInitAttempted = false;

    private static int hakariJackpotTicks = 0;
    private static int hakariFlashTicks = 0;
    private static int hakariAlBufferId = 0;
    private static int hakariAlSourceId = 0;
    private static boolean hakariAlInitAttempted = false;

    public static final IGuiOverlay STATUS_HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (rbdFadeTicks <= 0 && hakariJackpotTicks <= 0) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null) {
            return;
        }
        Font font = mc.f_91062_;

        // jackpot border + countdown
        if (hakariJackpotTicks > 0 && EasyParryConfig.MASTER_ENABLED.get() && EasyParryConfig.HAKARI_JACKPOT_MODE.get()) {
            double pulse = 0.5D + 0.5D * Math.sin(((double) hakariJackpotTicks - partialTick) * 0.25D);
            int borderAlpha = 35 + (int) (pulse * 45.0D);
            int greenBorder = (borderAlpha << 24) | 0x00FF66;
            int thickness = 4;
            guiGraphics.m_280509_(0, 0, screenWidth, thickness, greenBorder);
            guiGraphics.m_280509_(0, screenHeight - thickness, screenWidth, screenHeight, greenBorder);
            guiGraphics.m_280509_(0, thickness, thickness, screenHeight - thickness, greenBorder);
            guiGraphics.m_280509_(screenWidth - thickness, thickness, screenWidth, screenHeight - thickness, greenBorder);

            if (hakariFlashTicks > 0) {
                int flashAlpha = Math.min(110, (int) ((hakariFlashTicks / 30.0F) * 110.0F));
                guiGraphics.m_280509_(0, 0, screenWidth, screenHeight, (flashAlpha << 24) | 0x10FF70);
            }

            int totalSec = Math.max(0, (hakariJackpotTicks + 19) / 20);
            int mins = totalSec / 60;
            int secs = totalSec % 60;
            String timerText = String.format("★ JACKPOT: IDLE DEATH GAMBLE — %d:%02d ★", mins, secs);
            int tw = font.m_92895_(timerText);
            int bx = (screenWidth - tw) / 2;
            int by = 8;
            guiGraphics.m_280509_(bx - 6, by - 3, bx + tw + 6, by + 11, 0x90032211);
            guiGraphics.m_280056_(font, timerText, bx, by, 0x33FF77, true);
        }

        if (rbdFadeTicks <= 0) {
            return;
        }

        // return by death fade
        float progress = (rbdFadeTicks - partialTick) / (float) rbdMaxFadeTicks;
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        int alpha;
        if (rbdFadeTicks > 38) {
            alpha = 248;
        } else {
            alpha = Math.min(248, Math.max(0, (int) (progress / 0.70F * 248.0F)));
        }
        if (alpha > 4) {
            int bgColor = (alpha << 24) | 0x06010A;
            guiGraphics.m_280509_(0, 0, screenWidth, screenHeight, bgColor);

            int textAlpha = Math.min(255, (int) (alpha * 1.03F));
            if (textAlpha > 20) {
                String title = "RETURN BY DEATH";
                String sub = "Rewound " + rbdRewindSec + " seconds before death";
                int tw = font.m_92895_(title);
                int sw = font.m_92895_(sub);
                int titleColor = (textAlpha << 24) | 0xCC3366;
                int subColor = (textAlpha << 24) | 0xE0D4E8;
                guiGraphics.m_280056_(font, title, (screenWidth - tw) / 2, screenHeight / 2 - 12, titleColor, true);
                guiGraphics.m_280056_(font, sub, (screenWidth - sw) / 2, screenHeight / 2 + 4, subColor, true);
            }
        }
    };

    public static void init(IEventBus modBus) {
        modBus.addListener(EasyParryClient::onRegisterKeyMappings);
        modBus.addListener(EasyParryClient::onRegisterGuiOverlays);
        // configs are loaded by client setup, so the update check starts there
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) -> EasyParryUpdater.startAsync());

        MinecraftForge.EVENT_BUS.addListener(EasyParryClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(EasyParryClient::onScreenOpening);

        try {
            HakariDanceAnimation.register();
        } catch (Throwable ignored) {
        }
    }

    /** Jackpot ticks left for every player we know about (self + nearby friends). */
    private static final java.util.Map<java.util.UUID, Integer> JACKPOT_PLAYERS = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isPlayerInHakariJackpot(java.util.UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        Minecraft mc = Minecraft.m_91087_();
        boolean isLocal = mc.f_91074_ != null && playerUuid.equals(mc.f_91074_.m_20148_());
        if (isLocal) {
            if (EasyParryConfig.HAKARI_DANCE_PREVIEW.get()) {
                return true;
            }
            return hakariJackpotTicks > 0;
        }
        Integer remaining = JACKPOT_PLAYERS.get(playerUuid);
        return remaining != null && remaining > 0;
    }

    /** Server tells us someone (us or a friend) hit Jackpot. duration 0 = it ended. */
    public static void onPlayerHakariJackpot(java.util.UUID playerUuid, int durationSeconds) {
        if (playerUuid == null) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        boolean isLocal = mc.f_91074_ != null && playerUuid.equals(mc.f_91074_.m_20148_());
        if (durationSeconds <= 0) {
            JACKPOT_PLAYERS.remove(playerUuid);
            return;
        }
        JACKPOT_PLAYERS.put(playerUuid, Math.max(20, durationSeconds * 20));
        if (isLocal) {
            triggerHakariJackpotEffect(durationSeconds);
        }
    }

    private static void tickRemoteJackpots() {
        if (JACKPOT_PLAYERS.isEmpty()) {
            return;
        }
        JACKPOT_PLAYERS.replaceAll((uuid, ticks) -> ticks - 1);
        JACKPOT_PLAYERS.values().removeIf(ticks -> ticks <= 0);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MENU_KEY);
    }

    private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("easy_parry_hud", STATUS_HUD);
    }

    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof ParryOptionsScreen && !(event.getNewScreen() instanceof EasyParryScreen)) {
            Minecraft mc = Minecraft.m_91087_();
            event.setNewScreen(new EasyParryScreen(mc.f_91080_));
            return;
        }
        if (event.getNewScreen() instanceof DeathScreen) {
            Minecraft mc = Minecraft.m_91087_();
            LocalPlayer player = mc.f_91074_;
            if (EasyParryConfig.MASTER_ENABLED.get() && EasyParryConfig.RETURN_BY_DEATH.get()) {
                if (rbdFadeTicks > 0 || (player != null && player.m_21223_() > 0.0F)) {
                    event.setCanceled(true);
                    return;
                }
            }
            if (hakariJackpotTicks > 0) {
                hakariJackpotTicks = 0;
                stopHakariJackpotMusic();
            }
        }
    }

    public static void triggerHakariJackpotEffect(int durationSeconds) {
        hakariJackpotTicks = Math.max(20, durationSeconds * 20);
        hakariFlashTicks = 30;
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer player = mc.f_91074_;
        if (player != null) {
            player.f_20919_ = 0;
            player.f_20916_ = 0;
            if (player.m_20089_() == Pose.DYING) {
                player.m_20124_(Pose.STANDING);
            }
        }
        if (mc.f_91080_ instanceof DeathScreen) {
            mc.m_91152_(null);
        }
        playHakariJackpotMusic();
    }

    private static float getConfiguredHakariGain() {
        try {
            int vol = EasyParryConfig.HAKARI_MUSIC_VOLUME.get();
            return Math.max(0.0F, Math.min(1.0F, vol / 100.0F));
        } catch (Throwable ignored) {
            return 0.80F;
        }
    }

    private static void playHakariJackpotMusic() {
        try {
            float gain = getConfiguredHakariGain();
            if (!hakariAlInitAttempted) {
                hakariAlInitAttempted = true;
                try (InputStream raw = EasyParryClient.class.getResourceAsStream("/assets/linggango_easy_parry/sounds/hakari_jackpot.ogg")) {
                    if (raw != null) {
                        try (OggAudioStream ogg = new OggAudioStream(new BufferedInputStream(raw))) {
                            AudioFormat fmt = ogg.m_6206_();
                            ByteBuffer pcmBuf = ogg.m_83764_();
                            if (pcmBuf != null && pcmBuf.remaining() > 0) {
                                int channels = fmt.getChannels();
                                int sampleSize = fmt.getSampleSizeInBits();
                                int alFormat;
                                if (channels == 1) {
                                    alFormat = sampleSize == 8 ? AL10.AL_FORMAT_MONO8 : AL10.AL_FORMAT_MONO16;
                                } else {
                                    alFormat = sampleSize == 8 ? AL10.AL_FORMAT_STEREO8 : AL10.AL_FORMAT_STEREO16;
                                }
                                int sampleRate = (int) fmt.getSampleRate();
                                int buf = AL10.alGenBuffers();
                                AL10.alBufferData(buf, alFormat, pcmBuf, sampleRate);
                                int src = AL10.alGenSources();
                                AL10.alSourcei(src, AL10.AL_BUFFER, buf);
                                AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                                AL10.alSourcei(src, AL10.AL_LOOPING, AL10.AL_TRUE);
                                AL10.alSourcef(src, AL10.AL_GAIN, gain);
                                hakariAlBufferId = buf;
                                hakariAlSourceId = src;
                            }
                        }
                    }
                }
            }
            if (hakariAlSourceId != 0 && hakariAlBufferId != 0) {
                AL10.alSourceStop(hakariAlSourceId);
                AL10.alSourcei(hakariAlSourceId, AL10.AL_BUFFER, hakariAlBufferId);
                AL10.alSourcei(hakariAlSourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSourcei(hakariAlSourceId, AL10.AL_LOOPING, AL10.AL_TRUE);
                AL10.alSourcef(hakariAlSourceId, AL10.AL_GAIN, gain);
                AL10.alSourcePlay(hakariAlSourceId);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void stopHakariJackpotMusic() {
        try {
            if (hakariAlSourceId != 0) {
                int state = AL10.alGetSourcei(hakariAlSourceId, AL10.AL_SOURCE_STATE);
                if (state == AL10.AL_PLAYING || state == AL10.AL_PAUSED) {
                    AL10.alSourceStop(hakariAlSourceId);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void triggerReturnByDeathEffect(int rewindSeconds) {
        rbdRewindSec = rewindSeconds;
        rbdMaxFadeTicks = 55;
        rbdFadeTicks = 55;
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer player = mc.f_91074_;
        if (player != null) {
            player.f_20919_ = 0;
            player.f_20916_ = 0;
            if (player.m_20089_() == Pose.DYING) {
                player.m_20124_(Pose.STANDING);
            }
        }
        if (mc.f_91080_ instanceof DeathScreen) {
            mc.m_91152_(null);
        }
        playReturnByDeathSfx();
    }

    private static void playReturnByDeathSfx() {
        // play through minecraft's own OpenAL context
        try {
            if (!alInitAttempted) {
                alInitAttempted = true;
                try (InputStream raw = EasyParryClient.class.getResourceAsStream("/assets/linggango_easy_parry/sounds/return_by_death.wav")) {
                    if (raw != null) {
                        byte[] wavBytes = raw.readAllBytes();
                        if (wavBytes.length > 44) {
                            int pcmLen = wavBytes.length - 44;
                            ByteBuffer pcmBuf = ByteBuffer.allocateDirect(pcmLen).order(ByteOrder.nativeOrder());
                            pcmBuf.put(wavBytes, 44, pcmLen);
                            pcmBuf.flip();

                            int buf = AL10.alGenBuffers();
                            AL10.alBufferData(buf, AL10.AL_FORMAT_STEREO16, pcmBuf, 48000);
                            int src = AL10.alGenSources();
                            AL10.alSourcei(src, AL10.AL_BUFFER, buf);
                            AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                            AL10.alSourcef(src, AL10.AL_GAIN, 1.0F);
                            alBufferId = buf;
                            alSourceId = src;
                        }
                    }
                }
            }
            if (alSourceId != 0 && alBufferId != 0) {
                AL10.alSourceStop(alSourceId);
                AL10.alSourcei(alSourceId, AL10.AL_BUFFER, alBufferId);
                AL10.alSourcei(alSourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSourcef(alSourceId, AL10.AL_GAIN, 1.0F);
                AL10.alSourcePlay(alSourceId);
                return;
            }
        } catch (Throwable ignored) {
        }

        // fallback if OpenAL isn't ready
        Thread sfxThread = new Thread(() -> {
            try (InputStream raw = EasyParryClient.class.getResourceAsStream("/assets/linggango_easy_parry/sounds/return_by_death.wav")) {
                if (raw == null) {
                    return;
                }
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(ais);
                    clip.addLineListener(ev -> {
                        if (ev.getType() == LineEvent.Type.STOP) {
                            clip.close();
                        }
                    });
                    clip.start();
                }
            } catch (Throwable ignored) {
            }
        }, "EasyParry-ReturnByDeath-SFX");
        sfxThread.setDaemon(true);
        sfxThread.start();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (rbdFadeTicks > 0) {
            rbdFadeTicks--;
        }
        if (hakariFlashTicks > 0) {
            hakariFlashTicks--;
        }

        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer player = mc.f_91074_;
        if (player == null) {
            if (hakariJackpotTicks > 0) {
                hakariJackpotTicks = 0;
                stopHakariJackpotMusic();
            }
            JACKPOT_PLAYERS.clear();
            return;
        }
        tickRemoteJackpots();
        EasyParryUpdater.tickClientNotice(player);

        if (hakariJackpotTicks > 0) {
            if (!EasyParryConfig.MASTER_ENABLED.get() || !EasyParryConfig.HAKARI_JACKPOT_MODE.get()) {
                hakariJackpotTicks = 0;
                stopHakariJackpotMusic();
            } else {
                hakariJackpotTicks--;
                if (hakariJackpotTicks <= 0) {
                    stopHakariJackpotMusic();
                } else if (hakariAlSourceId != 0) {
                    try {
                        float baseGain = getConfiguredHakariGain();
                        float effectiveGain = hakariJackpotTicks <= 60
                                ? baseGain * (hakariJackpotTicks / 60.0F)
                                : baseGain;
                        AL10.alSourcef(hakariAlSourceId, AL10.AL_GAIN, effectiveGain);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        EasyParryServerHandler.localHostUuid = player.m_20148_();
        if (player.f_19797_ == 20 || player.f_19797_ % 40 == 0) {
            com.linggango.easyparry.EasyParryNetwork.sendLocalSettingsToServer();
        }

        if (EasyParryConfig.MASTER_ENABLED.get() && EasyParryConfig.RETURN_BY_DEATH.get()) {
            if (player.f_20919_ > 0 || player.m_20089_() == Pose.DYING) {
                player.f_20919_ = 0;
                player.m_20124_(Pose.STANDING);
            }
        }

        while (MENU_KEY.m_90859_()) {
            if (mc.f_91080_ == null) {
                mc.m_91152_(new EasyParryScreen(null));
            } else if (mc.f_91080_ instanceof EasyParryScreen) {
                mc.f_91080_.m_7379_();
            }
        }

        boolean masterOn = EasyParryConfig.MASTER_ENABLED.get();
        clearClientParryCooldown(masterOn && EasyParryConfig.NO_MISS_COOLDOWN.get());

        if (!masterOn) {
            return;
        }

        // swing the parry animation when a telegraphed hit is about to land (auto-parry visuals)
        if (EasyParryConfig.AUTO_PARRY.get() && mc.f_91073_ != null) {
            tickClientAutoParryVisuals(mc.f_91073_, player);
        }
    }

    private static void clearClientParryCooldown(boolean noMissCooldown) {
        try {
            ParryEffects.StateManager sm = ParryEffects.getStateManager();
            if (sm == null) {
                return;
            }
            // don't let the client sit in COOLDOWN after the timer already ran out
            if (sm.getCurrentState() == ParryEffects.ParryState.COOLDOWN && sm.getCooldownTicks() <= 0) {
                sm.setCooldown(0);
            }
            if (noMissCooldown) {
                sm.setCooldown(-1);
                if (sm.getCooldownTicks() > 0 || sm.getCurrentState() == ParryEffects.ParryState.COOLDOWN) {
                    sm.setCooldown(0);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void tickClientAutoParryVisuals(ClientLevel level, LocalPlayer player) {
        try {
            long gameTime = level.m_46467_();
            if (gameTime - lastAutoSwingTick < 6L) {
                return;
            }
            Int2ObjectMap<WindupTracker.Windup> active = WindupTracker.active();
            if (active == null || active.isEmpty()) {
                return;
            }
            double viewTick = WindupTracker.viewTick();
            ObjectIterator<Int2ObjectMap.Entry<WindupTracker.Windup>> it = Int2ObjectMaps.fastIterator(active);
            while (it.hasNext()) {
                Int2ObjectMap.Entry<WindupTracker.Windup> entry = it.next();
                WindupTracker.Windup w = entry.getValue();
                if (w == null || w.end != 0) {
                    continue;
                }
                Entity attacker = level.m_6815_(entry.getIntKey());
                if (attacker == null || !attacker.m_6084_()) {
                    continue;
                }
                float maxDist = w.ranged ? 28.0F : 7.0F;
                if (player.m_20270_(attacker) > maxDist) {
                    continue;
                }
                double remaining = (double) w.durationTicks - WindupTracker.elapsed(w, viewTick);
                if (remaining >= 0.0D && remaining <= 2.5D) {
                    lastAutoSwingTick = gameTime;
                    ParryEffects.startParryAction();
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
