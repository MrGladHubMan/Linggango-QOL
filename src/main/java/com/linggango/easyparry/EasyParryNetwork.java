package com.linggango.easyparry;

import com.linggango.easyparry.client.EasyParryClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class EasyParryNetwork {
    // Bump whenever a packet layout changes. Clients without the mod may still join (acceptMissingOr),
    // but mismatched versions are refused with a clear message instead of corrupting packets.
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EasyParryMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
    );

    public static final Map<UUID, PlayerSettings> PLAYER_SETTINGS = new ConcurrentHashMap<>();

    public static void init() {
        CHANNEL.registerMessage(
                0,
                PlayerSettings.class,
                PlayerSettings::encode,
                PlayerSettings::decode,
                PlayerSettings::handle
        );
        CHANNEL.registerMessage(
                1,
                ReturnByDeathPacket.class,
                ReturnByDeathPacket::encode,
                ReturnByDeathPacket::decode,
                ReturnByDeathPacket::handle
        );
        CHANNEL.registerMessage(
                2,
                HakariJackpotPacket.class,
                HakariJackpotPacket::encode,
                HakariJackpotPacket::decode,
                HakariJackpotPacket::handle
        );
    }

    public static void sendLocalSettingsToServer() {
        try {
            CHANNEL.sendToServer(PlayerSettings.fromLocalConfig());
        } catch (Throwable ignored) {
        }
    }

    public static void sendReturnByDeathToClient(ServerPlayer player, int rewindSeconds) {
        try {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ReturnByDeathPacket(rewindSeconds));
        } catch (Throwable ignored) {
        }
    }

    public static void sendHakariJackpotToClient(ServerPlayer player, int durationSeconds) {
        try {
            // Send to the player AND everyone tracking them, so friends can see the Jackpot dance too
            CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new HakariJackpotPacket(player.m_20148_(), durationSeconds)
            );
        } catch (Throwable ignored) {
        }
    }

    public static class HakariJackpotPacket {
        public final UUID playerId;
        public final int durationSeconds;

        public HakariJackpotPacket(UUID playerId, int durationSeconds) {
            this.playerId = playerId;
            this.durationSeconds = durationSeconds;
        }

        public static void encode(HakariJackpotPacket msg, FriendlyByteBuf buf) {
            buf.m_130077_(msg.playerId);
            buf.writeInt(msg.durationSeconds);
        }

        public static HakariJackpotPacket decode(FriendlyByteBuf buf) {
            UUID id = buf.m_130259_();
            return new HakariJackpotPacket(id, buf.readInt());
        }

        public static void handle(HakariJackpotPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            if (ctx.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                ctx.enqueueWork(() -> {
                    try {
                        EasyParryClient.onPlayerHakariJackpot(msg.playerId, msg.durationSeconds);
                    } catch (Throwable ignored) {
                    }
                });
            }
            ctx.setPacketHandled(true);
        }
    }

    public static class ReturnByDeathPacket {
        public final int rewindSeconds;

        public ReturnByDeathPacket(int rewindSeconds) {
            this.rewindSeconds = rewindSeconds;
        }

        public static void encode(ReturnByDeathPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.rewindSeconds);
        }

        public static ReturnByDeathPacket decode(FriendlyByteBuf buf) {
            return new ReturnByDeathPacket(buf.readInt());
        }

        public static void handle(ReturnByDeathPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            if (ctx.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                ctx.enqueueWork(() -> {
                    try {
                        EasyParryClient.triggerReturnByDeathEffect(msg.rewindSeconds);
                    } catch (Throwable ignored) {
                    }
                });
            }
            ctx.setPacketHandled(true);
        }
    }

    public static class PlayerSettings {
        public final boolean masterEnabled;
        public final boolean autoParry;
        public final int perfectParryChance;
        public final int parryWindowTicks;
        public final boolean parryEverything;
        public final boolean noMissCooldown;
        public final int bonusParryHeal;
        public final int extraAccessorySlots;
        public final boolean unlimitedAccessoryStacking;
        public final boolean easierWorldTweaks;
        public final boolean returnByDeath;
        public final int returnByDeathSeconds;
        public final boolean itemMagnet;
        public final int itemMagnetRange;
        public final boolean hakariJackpotMode;
        public final int gamblerJackpotChance;
        public final int hakariRegenHalfHearts;
        public final int hakariDurationSeconds;

        public PlayerSettings(
                boolean masterEnabled,
                boolean autoParry,
                int perfectParryChance,
                int parryWindowTicks,
                boolean parryEverything,
                boolean noMissCooldown,
                int bonusParryHeal,
                int extraAccessorySlots,
                boolean unlimitedAccessoryStacking,
                boolean easierWorldTweaks,
                boolean returnByDeath,
                int returnByDeathSeconds,
                boolean itemMagnet,
                int itemMagnetRange,
                boolean hakariJackpotMode,
                int gamblerJackpotChance,
                int hakariRegenHalfHearts,
                int hakariDurationSeconds
        ) {
            this.masterEnabled = masterEnabled;
            this.autoParry = autoParry;
            this.perfectParryChance = perfectParryChance;
            this.parryWindowTicks = parryWindowTicks;
            this.parryEverything = parryEverything;
            this.noMissCooldown = noMissCooldown;
            this.bonusParryHeal = bonusParryHeal;
            this.extraAccessorySlots = extraAccessorySlots;
            this.unlimitedAccessoryStacking = unlimitedAccessoryStacking;
            this.easierWorldTweaks = easierWorldTweaks;
            this.returnByDeath = returnByDeath;
            this.returnByDeathSeconds = returnByDeathSeconds;
            this.itemMagnet = itemMagnet;
            this.itemMagnetRange = itemMagnetRange;
            this.hakariJackpotMode = hakariJackpotMode;
            this.gamblerJackpotChance = gamblerJackpotChance;
            this.hakariRegenHalfHearts = hakariRegenHalfHearts;
            this.hakariDurationSeconds = hakariDurationSeconds;
        }

        public static PlayerSettings fromLocalConfig() {
            return new PlayerSettings(
                    EasyParryConfig.MASTER_ENABLED.get(),
                    EasyParryConfig.AUTO_PARRY.get(),
                    EasyParryConfig.PERFECT_PARRY_CHANCE.get(),
                    EasyParryConfig.PARRY_WINDOW_TICKS.get(),
                    EasyParryConfig.PARRY_EVERYTHING.get(),
                    EasyParryConfig.NO_MISS_COOLDOWN.get(),
                    EasyParryConfig.BONUS_PARRY_HEAL.get(),
                    EasyParryConfig.EXTRA_ACCESSORY_SLOTS.get(),
                    EasyParryConfig.UNLIMITED_ACCESSORY_STACKING.get(),
                    EasyParryConfig.EASIER_WORLD_TWEAKS.get(),
                    EasyParryConfig.RETURN_BY_DEATH.get(),
                    EasyParryConfig.RETURN_BY_DEATH_SECONDS.get(),
                    EasyParryConfig.ITEM_MAGNET.get(),
                    EasyParryConfig.ITEM_MAGNET_RANGE.get(),
                    EasyParryConfig.HAKARI_JACKPOT_MODE.get(),
                    EasyParryConfig.GAMBLER_JACKPOT_CHANCE.get(),
                    EasyParryConfig.HAKARI_REGEN_HALF_HEARTS.get(),
                    EasyParryConfig.HAKARI_DURATION_SECONDS.get()
            );
        }

        public static void encode(PlayerSettings msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.masterEnabled);
            buf.writeBoolean(msg.autoParry);
            buf.writeInt(msg.perfectParryChance);
            buf.writeInt(msg.parryWindowTicks);
            buf.writeBoolean(msg.parryEverything);
            buf.writeBoolean(msg.noMissCooldown);
            buf.writeInt(msg.bonusParryHeal);
            buf.writeInt(msg.extraAccessorySlots);
            buf.writeBoolean(msg.unlimitedAccessoryStacking);
            buf.writeBoolean(msg.easierWorldTweaks);
            buf.writeBoolean(msg.returnByDeath);
            buf.writeInt(msg.returnByDeathSeconds);
            buf.writeBoolean(msg.itemMagnet);
            buf.writeInt(msg.itemMagnetRange);
            buf.writeBoolean(msg.hakariJackpotMode);
            buf.writeInt(msg.gamblerJackpotChance);
            buf.writeInt(msg.hakariRegenHalfHearts);
            buf.writeInt(msg.hakariDurationSeconds);
        }

        public static PlayerSettings decode(FriendlyByteBuf buf) {
            return new PlayerSettings(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
            );
        }

        public static void handle(PlayerSettings msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer sender = ctx.getSender();
                if (sender != null) {
                    PLAYER_SETTINGS.put(sender.m_20148_(), msg);
                }
            });
            ctx.setPacketHandled(true);
        }
    }
}
