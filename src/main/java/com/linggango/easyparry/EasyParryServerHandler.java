package com.linggango.easyparry;

import com.misanthropy.linggango.class_api.ClassApi;
import com.misanthropy.linggango.linggango_tweaks.parry.HeavyAttackBridge;
import com.misanthropy.linggango.linggango_tweaks.parry.ParryServerHandler;
import com.misanthropy.linggango.linggango_tweaks.parry.ParryTuning;
import com.misanthropy.linggango.linggango_tweaks.skill.DeflectorClass;
import com.misanthropy.linggango.linggango_tweaks.skill.GamblerClass;
import com.misanthropy.linggango.linggango_tweaks.skill.SkillManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioEquipEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class EasyParryServerHandler {
    private static final UUID EXTRA_CURIO_SLOTS_UUID = UUID.fromString("d8a3e712-4b9c-4e1a-9f2d-64c8b1e2a3f4");
    public static final int HAKARI_JACKPOT_SECONDS = 251; // 4 minutes 11 seconds
    public static final long HAKARI_JACKPOT_TICKS = 251L * 20L; // 5020 ticks

    private static final DustParticleOptions HAKARI_GREEN_PRIMARY = new DustParticleOptions(new Vector3f(0.05F, 1.0F, 0.38F), 1.65F);
    private static final DustParticleOptions HAKARI_GREEN_BRIGHT = new DustParticleOptions(new Vector3f(0.45F, 1.0F, 0.65F), 1.25F);

    private static Method executeParrySuccessMethod;
    private static boolean reflectionInitialized = false;
    private static Method gamblerOutcomeMethod;
    private static boolean gamblerOutcomeChecked = false;
    private static Method getPersistentDataMethod;
    private static boolean persistentDataMethodChecked = false;
    private static Field deadFlagField;
    private static boolean deadFieldInitialized = false;
    public static volatile UUID localHostUuid = null;

    private static final Map<UUID, Deque<PlayerTimeSnapshot>> TIME_HISTORY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RBD_INVULN_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_RBD_TRIGGER_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> HAKARI_JACKPOT_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CONSUMED_PARRY_TICK = new ConcurrentHashMap<>();

    public static class PlayerTimeSnapshot {
        public final long gameTime;
        public final ResourceKey<Level> dimension;
        public final double x;
        public final double y;
        public final double z;
        public final float yRot;
        public final float xRot;
        public final float health;

        public PlayerTimeSnapshot(
                long gameTime,
                ResourceKey<Level> dimension,
                double x,
                double y,
                double z,
                float yRot,
                float xRot,
                float health
        ) {
            this.gameTime = gameTime;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.health = health;
        }
    }

    private static void clearDeadFlag(LivingEntity entity) {
        if (!deadFieldInitialized) {
            deadFieldInitialized = true;
            try {
                Field f = LivingEntity.class.getDeclaredField("f_20890_");
                f.setAccessible(true);
                deadFlagField = f;
            } catch (Throwable ignored) {
            }
        }
        if (deadFlagField != null) {
            try {
                deadFlagField.setBoolean(entity, false);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void revivePlayerState(ServerPlayer player) {
        try {
            clearDeadFlag(player);
            player.f_20919_ = 0; // deathTime = 0
            player.f_20916_ = 0; // hurtTime = 0
            player.m_21153_(player.m_21233_()); // setHealth(getMaxHealth())
            if (player.m_20089_() == Pose.DYING) {
                player.m_20124_(Pose.STANDING);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Method getExecuteParrySuccessMethod() {
        if (!reflectionInitialized) {
            reflectionInitialized = true;
            try {
                Method m = ParryServerHandler.class.getDeclaredMethod(
                        "executeParrySuccess",
                        ServerPlayer.class,
                        ParryServerHandler.PlayerParryData.class,
                        Entity.class,
                        Entity.class,
                        float.class,
                        int.class,
                        boolean.class,
                        boolean.class
                );
                m.setAccessible(true);
                executeParrySuccessMethod = m;
            } catch (Throwable ignored) {
            }
        }
        return executeParrySuccessMethod;
    }

    private static EasyParryNetwork.PlayerSettings getEffectiveSettings(ServerPlayer player) {
        UUID uuid = player.m_20148_();
        UUID hostId = localHostUuid;
        if (hostId != null && hostId.equals(uuid)) {
            return EasyParryNetwork.PlayerSettings.fromLocalConfig();
        }
        EasyParryNetwork.PlayerSettings clientPersonal = EasyParryNetwork.PLAYER_SETTINGS.get(uuid);
        if (clientPersonal != null) {
            return clientPersonal;
        }
        if (EasyParryConfig.APPLY_TO_FRIENDS.get()) {
            return EasyParryNetwork.PlayerSettings.fromLocalConfig();
        }
        return null;
    }

    private static boolean isHakariImmortal(ServerPlayer player, long now) {
        Long until = HAKARI_JACKPOT_UNTIL.get(player.m_20148_());
        if (until == null) {
            return false;
        }
        if (now < until) {
            return true;
        }
        HAKARI_JACKPOT_UNTIL.remove(player.m_20148_());
        return false;
    }

    private static CompoundTag getPlayerPersistentData(ServerPlayer player) {
        if (!persistentDataMethodChecked) {
            persistentDataMethodChecked = true;
            try {
                Method m = Entity.class.getMethod("getPersistentData");
                m.setAccessible(true);
                getPersistentDataMethod = m;
            } catch (Throwable ignored) {
            }
        }
        if (getPersistentDataMethod != null) {
            try {
                return (CompoundTag) getPersistentDataMethod.invoke(player);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void invokeGamblerOutcome(ServerPlayer player, int index, boolean playSound) {
        if (!gamblerOutcomeChecked) {
            gamblerOutcomeChecked = true;
            try {
                Method m = GamblerClass.class.getDeclaredMethod("outcome", ServerPlayer.class, int.class, boolean.class);
                m.setAccessible(true);
                gamblerOutcomeMethod = m;
            } catch (Throwable ignored) {
            }
        }
        if (gamblerOutcomeMethod != null) {
            try {
                gamblerOutcomeMethod.invoke(null, player, index, playSound);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void triggerHakariJackpot(ServerPlayer player, long now, EasyParryNetwork.PlayerSettings settings) {
        UUID uuid = player.m_20148_();
        int durationSec = settings != null ? Math.max(10, Math.min(600, settings.hakariDurationSeconds)) : HAKARI_JACKPOT_SECONDS;
        long durationTicks = (long) durationSec * 20L;
        HAKARI_JACKPOT_UNTIL.put(uuid, now + durationTicks);

        // jackpot = every Roll the Dice outcome at once
        for (int i = 0; i < 6; i++) {
            invokeGamblerOutcome(player, i, false);
        }

        if (player.m_9236_() instanceof ServerLevel) {
            ServerLevel sLevel = (ServerLevel) player.m_9236_();
            double px = player.m_20185_();
            double py = player.m_20186_() + 1.0D;
            double pz = player.m_20189_();
            sLevel.m_8767_(HAKARI_GREEN_PRIMARY, px, py, pz, 45, 0.65D, 1.0D, 0.65D, 0.08D);
            sLevel.m_8767_(HAKARI_GREEN_BRIGHT, px, py, pz, 30, 0.55D, 0.9D, 0.55D, 0.08D);
            sLevel.m_8767_(ParticleTypes.f_123748_, px, py, pz, 25, 0.6D, 1.0D, 0.6D, 0.1D);
            sLevel.m_8767_(ParticleTypes.f_123809_, px, py, pz, 35, 0.5D, 0.9D, 0.5D, 0.25D);
        }

        int mins = durationSec / 60;
        int secs = durationSec % 60;
        player.m_5661_(
                Component.m_237113_(String.format("★ JACKPOT!! IDLE DEATH GAMBLE (%d:%02d RAPID HEAL) ★", mins, secs))
                        .m_130940_(ChatFormatting.GREEN)
                        .m_130940_(ChatFormatting.BOLD),
                true
        );

        EasyParryNetwork.sendHakariJackpotToClient(player, durationSec);
    }

    private static void tickHakariJackpot(ServerPlayer player, long now, EasyParryNetwork.PlayerSettings settings) {
        if (!isHakariImmortal(player, now)) {
            return;
        }
        if (player.m_21224_() || player.m_21223_() <= 0.0F) {
            return;
        }

        // heal every 3 ticks
        float healHp = settings != null ? Math.max(1.0F, (float) settings.hakariRegenHalfHearts) : 2.0F;
        if (player.f_19797_ % 3 == 0 && player.m_21223_() < player.m_21233_()) {
            player.m_5634_(healHp);
        }

        // green aura
        if (player.f_19797_ % 2 == 0 && player.m_9236_() instanceof ServerLevel) {
            ServerLevel sLevel = (ServerLevel) player.m_9236_();
            double px = player.m_20185_();
            double py = player.m_20186_() + 0.95D;
            double pz = player.m_20189_();
            sLevel.m_8767_(HAKARI_GREEN_PRIMARY, px, py, pz, 7, 0.42D, 0.85D, 0.42D, 0.02D);
            sLevel.m_8767_(HAKARI_GREEN_BRIGHT, px, py, pz, 4, 0.35D, 0.75D, 0.35D, 0.02D);
            sLevel.m_8767_(ParticleTypes.f_123748_, px, py, pz, 3, 0.45D, 0.85D, 0.45D, 0.05D);
            if (player.f_19797_ % 6 == 0) {
                sLevel.m_8767_(ParticleTypes.f_123809_, px, py, pz, 3, 0.35D, 0.7D, 0.35D, 0.12D);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.isCanceled()) {
            return;
        }

        LivingEntity living = event.getEntity();
        if (!(living instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) living;

        EasyParryNetwork.PlayerSettings settings = getEffectiveSettings(player);
        if (settings == null || !settings.masterEnabled) {
            return;
        }

        UUID uuid = player.m_20148_();
        long gameTime = player.m_9236_().m_46467_();

        // short invuln window after a rewind
        Long invulnUntil = RBD_INVULN_UNTIL.get(uuid);
        if (invulnUntil != null && gameTime < invulnUntil) {
            event.setCanceled(true);
            return;
        }

        DamageSource source = event.getSource();
        Entity attacker = source.m_7639_();
        Entity directEntity = source.m_7640_();

        // void / /kill never reach LivingDamage, so catch them here
        if (settings.returnByDeath && source.m_269533_(DamageTypeTags.f_268738_) && event.getAmount() >= player.m_21223_()) {
            event.setCanceled(true);
            triggerReturnByDeath(player, settings);
            return;
        }

        if (player.m_21224_() || player.m_21223_() <= 0.0F) {
            return;
        }

        // only parry hits from an entity or projectile
        if (attacker == null && directEntity == null) {
            return;
        }

        boolean parryEverything = settings.parryEverything;
        if (!parryEverything && !DeflectorClass.unbound(player)) {
            if (HeavyAttackBridge.incoming(player)) {
                return;
            }
        }

        ParryServerHandler.PlayerParryData data = ParryServerHandler.playerDataMap.computeIfAbsent(
                uuid,
                k -> new ParryServerHandler.PlayerParryData()
        );

        if (data.applyingLeak) {
            return;
        }

        long landTick = HeavyAttackBridge.landTick(player);
        long effectiveTick = landTick >= 0L ? landTick : gameTime;

        boolean autoParry = settings.autoParry;
        boolean manualActive = false;

        long lastParryTick = Math.max(data.parryStartTick, data.lastInputTick);
        long lastConsumed = LAST_CONSUMED_PARRY_TICK.getOrDefault(uuid, -1L);
        int startup = ParryTuning.startupTicks();
        int window = Math.max(settings.parryWindowTicks, ParryTuning.activeWindow(player));
        if (lastParryTick > 0L && lastParryTick > lastConsumed) {
            long elapsedEffective = effectiveTick - lastParryTick;
            long elapsedNow = gameTime - lastParryTick;
            if ((elapsedEffective >= -2L && elapsedEffective <= (long) (startup + window))
                    || (elapsedNow >= 0L && elapsedNow <= (long) (startup + window))) {
                manualActive = true;
            }
        }

        if (!autoParry && !manualActive) {
            return;
        }

        Method execMethod = getExecuteParrySuccessMethod();
        if (execMethod == null) {
            return;
        }

        event.setCanceled(true);
        if (lastParryTick > 0L) {
            LAST_CONSUMED_PARRY_TICK.put(uuid, lastParryTick);
        }
        data.hasActiveParry = false;
        data.parriedThisWindow = true;
        data.parryStartTick = 0L;
        data.lastInputTick = 0L;
        data.graceUntilTick = 0L;
        data.cooldownEndTick = 0L;
        data.missStreak = 0;
        data.missStreakUntil = 0L;

        int perfectPct = settings.perfectParryChance;
        int tier;
        if (perfectPct >= 100) {
            tier = 3;
        } else if (perfectPct <= 0) {
            tier = 2;
        } else {
            tier = ThreadLocalRandom.current().nextInt(100) < perfectPct ? 3 : 2;
        }

        boolean isProjectile = source.m_269533_(DamageTypeTags.f_268490_);
        Entity effectiveAttacker = attacker != null ? attacker : directEntity;

        try {
            execMethod.invoke(
                    null,
                    player,
                    data,
                    effectiveAttacker,
                    directEntity,
                    event.getAmount(),
                    tier,
                    false,
                    isProjectile
            );
            // one press = one parry, otherwise the next mob hit gets parried for free
            data.hasActiveParry = false;
            data.parriedThisWindow = true;
            data.graceUntilTick = 0L;
            data.cooldownEndTick = 0L;
            data.missStreak = 0;
            data.missStreakUntil = 0L;
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity living = event.getEntity();
        if (!(living instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) living;
        EasyParryNetwork.PlayerSettings settings = getEffectiveSettings(player);
        if (settings == null || !settings.masterEnabled || !settings.returnByDeath) {
            return;
        }

        float currentHealth = player.m_21223_() + player.m_6103_();
        if (event.getAmount() >= currentHealth) {
            event.setAmount(0.0F);
            event.setCanceled(true);
            triggerReturnByDeath(player, settings);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (!(living instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) living;
        EasyParryNetwork.PlayerSettings settings = getEffectiveSettings(player);
        if (settings == null || !settings.masterEnabled || !settings.returnByDeath) {
            if (HAKARI_JACKPOT_UNTIL.remove(player.m_20148_()) != null) {
                EasyParryNetwork.sendHakariJackpotToClient(player, 0);
            }
            return;
        }

        event.setCanceled(true);
        revivePlayerState(player);
        triggerReturnByDeath(player, settings);
    }

    private static void triggerReturnByDeath(ServerPlayer player, EasyParryNetwork.PlayerSettings settings) {
        UUID uuid = player.m_20148_();
        long now = player.m_9236_().m_46467_();

        revivePlayerState(player);

        Long lastTrigger = LAST_RBD_TRIGGER_TICK.get(uuid);
        if (lastTrigger != null && now - lastTrigger < 10L) {
            return;
        }
        LAST_RBD_TRIGGER_TICK.put(uuid, now);
        RBD_INVULN_UNTIL.put(uuid, now + 20L);

        int rewindSec = Math.max(3, Math.min(30, settings.returnByDeathSeconds));
        long targetTick = now - (long) rewindSec * 20L;
        ResourceKey<Level> currentDim = player.m_9236_().m_46472_();

        PlayerTimeSnapshot chosen = null;
        Deque<PlayerTimeSnapshot> history = TIME_HISTORY.get(uuid);
        if (history != null) {
            synchronized (history) {
                for (PlayerTimeSnapshot snap : history) {
                    if (snap.dimension.equals(currentDim)) {
                        if (chosen == null) {
                            chosen = snap;
                        }
                        if (snap.gameTime <= targetTick) {
                            chosen = snap;
                        } else {
                            break;
                        }
                    }
                }
                if (chosen != null) {
                    final long cutoff = chosen.gameTime;
                    history.removeIf(s -> s.gameTime > cutoff);
                }
            }
        }

        // full heal, food, no fire, no fall damage
        revivePlayerState(player);
        player.f_19789_ = 0.0F;
        player.m_20095_();
        player.m_20256_(Vec3.f_82478_);
        player.f_19802_ = 20;

        try {
            player.m_36324_().m_38705_(20);
            player.m_36324_().m_38717_(20.0F);
        } catch (Throwable ignored) {
        }

        // drop harmful effects so you don't die again right away
        try {
            List<MobEffect> toRemove = new ArrayList<>();
            for (MobEffectInstance inst : player.m_21220_()) {
                if (inst != null && inst.m_19544_() != null && !inst.m_19544_().m_19486_()) {
                    toRemove.add(inst.m_19544_());
                }
            }
            for (MobEffect eff : toRemove) {
                player.m_21195_(eff);
            }
        } catch (Throwable ignored) {
        }

        // rewind position
        if (chosen != null) {
            try {
                player.m_6021_(chosen.x, chosen.y, chosen.z);
                if (player.f_8906_ != null) {
                    player.f_8906_.m_9774_(chosen.x, chosen.y, chosen.z, chosen.yRot, chosen.xRot);
                }
            } catch (Throwable ignored) {
            }
        }

        // client plays the sfx + fade
        EasyParryNetwork.sendReturnByDeathToClient(player, rewindSec);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.player;
        UUID uuid = player.m_20148_();
        long now = player.m_9236_().m_46467_();

        EasyParryNetwork.PlayerSettings settings = getEffectiveSettings(player);
        boolean enabledForPlayer = settings != null && settings.masterEnabled;

        // jackpot heal + aura
        if (enabledForPlayer && settings.hakariJackpotMode) {
            tickHakariJackpot(player, now, settings);
        }

        // take over the last tick of Gambler's Roll the Dice to apply our own jackpot chance
        if (enabledForPlayer && (settings.hakariJackpotMode || settings.gamblerJackpotChance != 10)) {
            try {
                CompoundTag pData = getPlayerPersistentData(player);
                if (pData != null && pData.m_128451_("lt_gambler_roll_timer") == 1) {
                    pData.m_128405_("lt_gambler_roll_timer", 0);
                    try {
                        ClassApi.skill(player).startCooldown();
                    } catch (Throwable ignored) {
                    }
                    int chance = Math.max(1, Math.min(100, settings.gamblerJackpotChance));
                    if (ThreadLocalRandom.current().nextInt(100) < chance) {
                        if (settings.hakariJackpotMode) {
                            triggerHakariJackpot(player, now, settings);
                        } else {
                            for (int i = 0; i < 6; i++) {
                                invokeGamblerOutcome(player, i, false);
                            }
                            player.m_5661_(
                                    Component.m_237113_("JACKPOT! All outcomes triggered!")
                                            .m_130940_(ChatFormatting.GOLD)
                                            .m_130940_(ChatFormatting.BOLD),
                                    true
                            );
                        }
                    } else {
                        invokeGamblerOutcome(player, ThreadLocalRandom.current().nextInt(6), true);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // failsafe: something killed us without going through the events above
        if (enabledForPlayer && settings.returnByDeath) {
            if (player.m_21224_() || player.m_21223_() <= 0.0F || player.f_20919_ > 0 || player.m_20089_() == Pose.DYING) {
                revivePlayerState(player);
                triggerReturnByDeath(player, settings);
                return;
            }
        }

        // position history for return by death, every 5 ticks
        if (player.f_19797_ % 5 == 0 && !player.m_21224_() && player.m_21223_() > 0.0F) {
            if (player.m_20186_() > (double) (player.m_9236_().m_141937_() + 2)) {
                Deque<PlayerTimeSnapshot> history = TIME_HISTORY.computeIfAbsent(uuid, k -> new ArrayDeque<>());
                synchronized (history) {
                    history.addLast(new PlayerTimeSnapshot(
                            now,
                            player.m_9236_().m_46472_(),
                            player.m_20185_(),
                            player.m_20186_(),
                            player.m_20189_(),
                            player.m_146908_(),
                            player.m_146909_(),
                            player.m_21223_()
                    ));
                    long minKeepTick = now - 700L; // 35s
                    while (!history.isEmpty() && history.peekFirst().gameTime < minKeepTick) {
                        history.pollFirst();
                    }
                }
            }
        }

        if (enabledForPlayer && settings.noMissCooldown) {
            ParryServerHandler.PlayerParryData data = ParryServerHandler.playerDataMap.get(uuid);
            if (data != null) {
                data.cooldownEndTick = 0L;
                data.missStreak = 0;
                data.missStreakUntil = 0L;
                data.parriedThisWindow = true;
            }
        }

        // item + xp magnet, paused while sneaking
        if (enabledForPlayer && settings.itemMagnet && player.f_19797_ % 2 == 0) {
            if (!player.m_21224_() && player.m_21223_() > 0.0F && !player.m_6144_()) {
                tickItemAndXpMagnet(player, settings.itemMagnetRange);
            }
        }

        // extra curio slots ignore the master switch, turning it off must never drop your accessories
        if (player.f_19797_ % 10 == 0) {
            int targetExtraSlots = settings != null ? settings.extraAccessorySlots : EasyParryConfig.EXTRA_ACCESSORY_SLOTS.get();
            syncExtraAccessorySlots(player, targetExtraSlots);
        }
    }

    private static void tickItemAndXpMagnet(ServerPlayer player, int rawRange) {
        int range = Math.max(3, Math.min(16, rawRange));
        double maxDistSq = (double) (range * range);
        double targetX = player.m_20185_();
        double targetY = player.m_20186_() + 0.5D;
        double targetZ = player.m_20189_();

        try {
            AABB box = player.m_20191_().m_82400_((double) range);
            Level level = player.m_9236_();

            List<ItemEntity> items = level.m_45976_(ItemEntity.class, box);
            for (ItemEntity item : items) {
                if (item == null || !item.m_6084_() || item.m_32063_()) {
                    continue;
                }
                pullEntityToward(item, targetX, targetY, targetZ, maxDistSq);
            }

            List<ExperienceOrb> orbs = level.m_45976_(ExperienceOrb.class, box);
            for (ExperienceOrb orb : orbs) {
                if (orb == null || !orb.m_6084_()) {
                    continue;
                }
                pullEntityToward(orb, targetX, targetY, targetZ, maxDistSq);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void pullEntityToward(Entity entity, double targetX, double targetY, double targetZ, double maxDistSq) {
        double dx = targetX - entity.m_20185_();
        double dy = targetY - entity.m_20186_();
        double dz = targetZ - entity.m_20189_();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 0.04D || distSq > maxDistSq) {
            return;
        }
        double dist = Math.sqrt(distSq);
        double strength = 0.14D;
        Vec3 cur = entity.m_20184_();
        double vx = cur.f_82479_ * 0.7D + (dx / dist) * strength;
        double vy = cur.f_82480_ * 0.7D + (dy / dist) * strength + 0.04D;
        double vz = cur.f_82481_ * 0.7D + (dz / dist) * strength;
        entity.m_20256_(new Vec3(vx, vy, vz));
        entity.f_19864_ = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCurioEquip(CurioEquipEvent event) {
        ItemStack stack = event.getStack();
        SlotContext slotContext = event.getSlotContext();
        if (stack == null || stack.m_41619_() || slotContext == null) {
            return;
        }
        if (!EasyParryMod.isAnyAccessory(stack)) {
            return;
        }

        LivingEntity entity = slotContext.entity();
        int extraSlots;
        boolean unlimitedStacking;
        boolean easierWorldTweaks;

        if (entity instanceof ServerPlayer) {
            EasyParryNetwork.PlayerSettings settings = getEffectiveSettings((ServerPlayer) entity);
            if (settings != null) {
                extraSlots = settings.extraAccessorySlots;
                unlimitedStacking = settings.unlimitedAccessoryStacking;
                easierWorldTweaks = settings.easierWorldTweaks;
            } else {
                extraSlots = EasyParryConfig.EXTRA_ACCESSORY_SLOTS.get();
                unlimitedStacking = EasyParryConfig.UNLIMITED_ACCESSORY_STACKING.get();
                easierWorldTweaks = EasyParryConfig.EASIER_WORLD_TWEAKS.get();
            }
        } else {
            extraSlots = EasyParryConfig.EXTRA_ACCESSORY_SLOTS.get();
            unlimitedStacking = EasyParryConfig.UNLIMITED_ACCESSORY_STACKING.get();
            easierWorldTweaks = EasyParryConfig.EASIER_WORLD_TWEAKS.get();
        }

        if (event.getResult() == Event.Result.DENY && !easierWorldTweaks) {
            return;
        }

        String id = slotContext.identifier();
        if ("curio".equals(id) && (extraSlots > 0 || unlimitedStacking)) {
            event.setResult(Event.Result.ALLOW);
        } else if (unlimitedStacking) {
            try {
                if (CuriosApi.isStackValid(slotContext, stack)) {
                    event.setResult(Event.Result.ALLOW);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void syncExtraAccessorySlots(ServerPlayer player, int targetSlots) {
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                handler.getStacksHandler("curio").ifPresent(stacksHandler -> {
                    AttributeModifier existing = stacksHandler.getModifiers().get(EXTRA_CURIO_SLOTS_UUID);
                    int currentSlots = existing != null ? (int) Math.round(existing.m_22218_()) : 0;
                    if (currentSlots != targetSlots) {
                        if (existing != null) {
                            handler.removeSlotModifier("curio", EXTRA_CURIO_SLOTS_UUID);
                        }
                        if (targetSlots > 0) {
                            handler.addPermanentSlotModifier(
                                    "curio",
                                    EXTRA_CURIO_SLOTS_UUID,
                                    "EasyParryExtraCurios",
                                    (double) targetSlots,
                                    AttributeModifier.Operation.ADDITION
                            );
                        }
                    }
                });
            });
        } catch (Throwable ignored) {
        }
    }
}
