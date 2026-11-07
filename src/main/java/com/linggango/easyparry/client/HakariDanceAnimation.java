package com.linggango.easyparry.client;

import com.linggango.easyparry.EasyParryConfig;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

/**
 * Hakari's jackpot dance, done procedurally on top of playerAnimator.
 *
 * Things worth knowing before touching the numbers:
 *  - AnimationStack only calls tick() on layers that already report isActive(), so isActive()
 *    can't rely on state that tick() sets.
 *  - The pack has no bendy-lib, so BEND (elbows/knees) does nothing. Poses use straight limbs.
 *  - Limb values are model space: pixels for pivots, radians for rotation, Y down, -Z forward.
 *    Arm xRot -PI = straight up. +zRot moves the right arm/leg outward, -zRot the left one.
 *  - "body" POSITION is in blocks and gets flipped by the renderer, hence the negated X/Y.
 */
public class HakariDanceAnimation implements IAnimation {
    private static final double TICKS_PER_BEAT = 10.0D; // 120 BPM
    private static final double LOOP_BEATS = 32.0D;
    private static final double FADE_IN_PER_TICK = 1.0D / 8.0D;
    private static final double FADE_OUT_PER_TICK = 1.0D / 5.0D;

    private final AbstractClientPlayer player;
    private double danceTicks = 0.0D;
    private double weight = 0.0D;
    private double prevWeight = 0.0D;

    public HakariDanceAnimation(AbstractClientPlayer player) {
        this.player = player;
    }

    public static void register() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, animationStack) ->
                animationStack.addAnimLayer(500, new HakariDanceAnimation(player)));
    }
    private boolean shouldDance() {
        if (this.player == null || !this.player.m_6084_() || this.player.m_21224_()) {
            return false;
        }
        if (!EasyParryConfig.HAKARI_IDLE_DANCE.get()) {
            return false;
        }
        boolean isLocal = isLocalPlayer();
        if (isLocal && !EasyParryConfig.MASTER_ENABLED.get()) {
            return false;
        }
        if (!EasyParryClient.isPlayerInHakariJackpot(this.player.m_20148_())) {
            return false;
        }
        // Pause when sneaking, riding, sleeping, swimming, elytra-flying, swinging or using an item
        if (this.player.m_6144_()
                || this.player.m_20159_()
                || this.player.m_5803_()
                || this.player.m_6069_()
                || this.player.m_21255_()
                || this.player.f_20911_
                || this.player.m_21212_() > 0
                || !this.player.m_20096_()) {
            return false;
        }
        if (this.player instanceof LocalPlayer) {
            LocalPlayer lp = (LocalPlayer) this.player;
            if (lp.f_108618_ != null) {
                if (Math.abs(lp.f_108618_.f_108566_) > 0.05F
                        || Math.abs(lp.f_108618_.f_108567_) > 0.05F
                        || lp.f_108618_.f_108568_) {
                    return false;
                }
            }
        }
        double dx = this.player.m_20185_() - this.player.f_19854_;
        double dz = this.player.m_20189_() - this.player.f_19856_;
        return (dx * dx + dz * dz) < 0.0025D;
    }

    private boolean isLocalPlayer() {
        Minecraft mc = Minecraft.m_91087_();
        return mc.f_91074_ != null && mc.f_91074_.m_20148_().equals(this.player.m_20148_());
    }

    @Override
    public void tick() {
        this.prevWeight = this.weight;
        if (shouldDance()) {
            this.weight = Math.min(1.0D, this.weight + FADE_IN_PER_TICK);
            this.danceTicks += 1.0D;
        } else {
            this.weight = Math.max(0.0D, this.weight - FADE_OUT_PER_TICK);
            if (this.weight <= 0.0D) {
                this.danceTicks = 0.0D;
            } else {
                this.danceTicks += 1.0D;
            }
        }
    }

    @Override
    public boolean isActive() {
        // can't depend on tick(): the stack only ticks layers that are already active
        return this.weight > 0.0D || shouldDance();
    }

    @Override
    public void setupAnim(float tickDelta) {
    }

    @Override
    public FirstPersonMode getFirstPersonMode(float tickDelta) {
        return FirstPersonMode.NONE;
    }
    private static double clamp01(double t) {
        return Math.max(0.0D, Math.min(1.0D, t));
    }

    private static double smooth(double t) {
        t = clamp01(t);
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D); // smootherstep
    }

    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Sharp dip that lands exactly on every beat and recovers before the next one. */
    private static double beatHit(double beats) {
        double frac = beats - Math.floor(beats);
        double d = 1.0D - frac;
        return d * d * d * d;
    }

    /** Side-to-side groove, one full L/R cycle every 2 beats, slightly snappier than a sine. */
    private static double groove(double beats) {
        double s = Math.sin(Math.PI * beats);
        return Math.signum(s) * Math.pow(Math.abs(s), 0.75D);
    }
    private static final class Pose {
        // whole-body offset in model pixels (Y down) and rotation in radians
        double bodyX, bodyY, bodyZ;
        double bodyRx, bodyRy, bodyRz;
        // head offsets added on top of where the player is looking
        double headRx, headRy, headRz;
        double torsoRx, torsoRy, torsoRz;
        double rArmRx, rArmRy, rArmRz;
        double lArmRx, lArmRy, lArmRz;
        double rLegRx, rLegRy, rLegRz;
        double lLegRx, lLegRy, lLegRz;
        // shoulder lift in pixels (negative = up, shrug)
        double rShoulderY, lShoulderY;

        Pose copy() {
            Pose p = new Pose();
            p.bodyX = bodyX; p.bodyY = bodyY; p.bodyZ = bodyZ;
            p.bodyRx = bodyRx; p.bodyRy = bodyRy; p.bodyRz = bodyRz;
            p.headRx = headRx; p.headRy = headRy; p.headRz = headRz;
            p.torsoRx = torsoRx; p.torsoRy = torsoRy; p.torsoRz = torsoRz;
            p.rArmRx = rArmRx; p.rArmRy = rArmRy; p.rArmRz = rArmRz;
            p.lArmRx = lArmRx; p.lArmRy = lArmRy; p.lArmRz = lArmRz;
            p.rLegRx = rLegRx; p.rLegRy = rLegRy; p.rLegRz = rLegRz;
            p.lLegRx = lLegRx; p.lLegRy = lLegRy; p.lLegRz = lLegRz;
            p.rShoulderY = rShoulderY; p.lShoulderY = lShoulderY;
            return p;
        }
    }

    private static Pose blend(Pose a, Pose b, double t) {
        Pose p = new Pose();
        p.bodyX = mix(a.bodyX, b.bodyX, t);
        p.bodyY = mix(a.bodyY, b.bodyY, t);
        p.bodyZ = mix(a.bodyZ, b.bodyZ, t);
        p.bodyRx = mix(a.bodyRx, b.bodyRx, t);
        p.bodyRy = mix(a.bodyRy, b.bodyRy, t);
        p.bodyRz = mix(a.bodyRz, b.bodyRz, t);
        p.headRx = mix(a.headRx, b.headRx, t);
        p.headRy = mix(a.headRy, b.headRy, t);
        p.headRz = mix(a.headRz, b.headRz, t);
        p.torsoRx = mix(a.torsoRx, b.torsoRx, t);
        p.torsoRy = mix(a.torsoRy, b.torsoRy, t);
        p.torsoRz = mix(a.torsoRz, b.torsoRz, t);
        p.rArmRx = mix(a.rArmRx, b.rArmRx, t);
        p.rArmRy = mix(a.rArmRy, b.rArmRy, t);
        p.rArmRz = mix(a.rArmRz, b.rArmRz, t);
        p.lArmRx = mix(a.lArmRx, b.lArmRx, t);
        p.lArmRy = mix(a.lArmRy, b.lArmRy, t);
        p.lArmRz = mix(a.lArmRz, b.lArmRz, t);
        p.rLegRx = mix(a.rLegRx, b.rLegRx, t);
        p.rLegRy = mix(a.rLegRy, b.rLegRy, t);
        p.rLegRz = mix(a.rLegRz, b.rLegRz, t);
        p.lLegRx = mix(a.lLegRx, b.lLegRx, t);
        p.lLegRy = mix(a.lLegRy, b.lLegRy, t);
        p.lLegRz = mix(a.lLegRz, b.lLegRz, t);
        p.rShoulderY = mix(a.rShoulderY, b.rShoulderY, t);
        p.lShoulderY = mix(a.lShoulderY, b.lShoulderY, t);
        return p;
    }

    /**
     * Wide stance with hips swaying while both feet stay planted: both legs tilt by the same
     * angle and the whole body is shifted to cancel the foot slide.
     */
    private static void plantedHipSway(Pose p, double spread, double sway) {
        p.rLegRz = spread + sway;
        p.lLegRz = -spread + sway;
        // a common leg tilt of `sway` moves both feet by -12*sin(sway) px in X; shift body to cancel
        p.bodyX += 12.0D * Math.sin(sway);
    }

    // Phase A/B: signature Jackpot pose. One arm angled in over the top of the head, the other
    // raised up beside the head, hips rolling side to side on the groove.
    private static Pose poseJackpotSway(double beats, boolean mirrored) {
        Pose p = new Pose();
        double g = groove(beats);
        double hit = beatHit(beats);

        plantedHipSway(p, 0.20D, 0.11D * g);
        p.bodyY = 0.9D * hit;
        p.bodyRy = 0.10D * g;
        p.torsoRz = 0.09D * g;
        p.torsoRy = -0.08D * g;

        p.headRz = -0.16D * g;
        p.headRx = 0.10D * hit - 0.04D;
        p.headRy = 0.08D * g;

        // "Over-head" arm: Minecraft arms are too short to cross in front of the head without clipping,
        // so this one goes up and tips BACK past vertical, with the hand landing behind/over the crown
        double overRx = -3.50D + 0.08D * hit;
        double overRz = -0.66D - 0.05D * g;
        // "Raised" arm: up and out in a V beside the head, pumping on the beat
        double upRx = -2.92D - 0.14D * hit;
        double upRz = 0.42D + 0.06D * g;

        if (!mirrored) {
            p.rArmRx = overRx;
            p.rArmRz = overRz;
            p.lArmRx = upRx;
            p.lArmRz = -upRz;
            p.rShoulderY = -0.8D;
            p.lShoulderY = -0.4D - 0.4D * hit;
        } else {
            p.lArmRx = overRx;
            p.lArmRz = -overRz;
            p.rArmRx = upRx;
            p.rArmRz = upRz;
            p.lShoulderY = -0.8D;
            p.rShoulderY = -0.4D - 0.4D * hit;
        }

        // weight shift: the leg on the side the hips swing toward stays straight, the other steps light
        p.rLegRx = -0.08D * Math.max(0.0D, g);
        p.lLegRx = -0.08D * Math.max(0.0D, -g);
        return p;
    }

    // Phase C: shuffle turn. Body turns to show a side profile every 2 beats, arms pumping overhead.
    private static Pose poseShuffleTurn(double beats) {
        Pose p = new Pose();
        double cycle = beats / 4.0D;
        double side = Math.sin(Math.PI * 2.0D * cycle); // -1 .. 1 over 4 beats
        double hit = beatHit(beats);
        double pump = Math.sin(Math.PI * beats);

        p.bodyRy = 0.78D * side;
        p.bodyY = 1.0D * hit;
        p.torsoRx = 0.06D * hit;
        p.torsoRz = 0.06D * pump;

        p.headRy = -0.45D * side; // keep the face turned toward the camera while the body turns
        p.headRx = 0.12D * hit;
        p.headRz = -0.08D * pump;

        p.rArmRx = -3.08D + 0.18D * pump;
        p.lArmRx = -3.08D - 0.18D * pump;
        p.rArmRz = 0.36D;
        p.lArmRz = -0.36D;
        p.rShoulderY = -0.6D + 0.5D * Math.max(0.0D, pump);
        p.lShoulderY = -0.6D + 0.5D * Math.max(0.0D, -pump);

        // step: one foot forward, one back, swapping with the turn
        p.rLegRx = -0.30D * side;
        p.lLegRx = 0.30D * side;
        p.rLegRz = 0.10D;
        p.lLegRz = -0.10D;
        return p;
    }

    // Phase D: low wide "fever" groove. Deep wide stance, hips rolling, arms swinging low and
    // opposite each other like a groove step, shoulders bouncing on the beat.
    private static Pose poseLowFever(double beats) {
        Pose p = new Pose();
        double g = groove(beats);
        double hit = beatHit(beats);
        double swing = Math.sin(Math.PI * beats);

        plantedHipSway(p, 0.40D, 0.08D * g);
        p.bodyY = 1.0D * hit;
        p.bodyRy = 0.16D * g;
        p.torsoRx = 0.10D + 0.08D * hit;
        p.torsoRz = 0.05D * g;

        p.headRx = 0.18D * hit;
        p.headRz = -0.12D * g;

        p.rArmRx = -0.75D * swing - 0.15D;
        p.lArmRx = 0.75D * swing - 0.15D;
        p.rArmRz = 0.38D;
        p.lArmRz = -0.38D;
        p.rShoulderY = -0.7D * hit;
        p.lShoulderY = -0.7D * hit;

        p.rLegRx = -0.06D;
        p.lLegRx = -0.06D;
        return p;
    }

    // Phase E: "Jackpot grin" - arms thrown open wide with a cocky head tilt and shoulder shimmy.
    private static Pose poseJackpotGrin(double beats) {
        Pose p = new Pose();
        double shimmy = Math.sin(Math.PI * 2.0D * beats); // 1 cycle per beat
        double hit = beatHit(beats);

        plantedHipSway(p, 0.26D, 0.0D);
        p.bodyY = 0.4D * hit;
        p.bodyRy = 0.10D * shimmy;
        p.torsoRy = -0.14D * shimmy;

        p.headRx = -0.30D + 0.05D * hit; // chin up
        p.headRz = 0.14D;
        p.headRy = 0.06D * shimmy;

        p.rArmRx = -0.85D;
        p.lArmRx = -0.85D;
        p.rArmRz = 0.85D;
        p.lArmRz = -0.85D;
        p.rArmRy = 0.25D * shimmy;
        p.lArmRy = 0.25D * shimmy;
        p.rShoulderY = -0.7D * Math.max(0.0D, shimmy);
        p.lShoulderY = -0.7D * Math.max(0.0D, -shimmy);
        return p;
    }

    // Phase F: overhead clap on the beat, then back into the Jackpot pose.
    private static Pose poseOverheadClap(double beats) {
        Pose p = new Pose();
        double hit = beatHit(beats);
        double g = groove(beats);

        plantedHipSway(p, 0.18D, 0.06D * g);
        p.bodyY = 1.1D * hit;
        p.headRx = 0.14D * hit - 0.10D;
        p.headRz = -0.08D * g;

        // hands meet above the head exactly on the beat, open up between beats
        double close = hit;
        p.rArmRx = -3.32D;
        p.lArmRx = -3.32D;
        p.rArmRz = mix(0.50D, -0.34D, close);
        p.lArmRz = -mix(0.50D, -0.34D, close);
        p.rShoulderY = -1.0D;
        p.lShoulderY = -1.0D;
        return p;
    }

    private static Pose evaluate(double ticks) {
        double beats = (ticks / TICKS_PER_BEAT) % LOOP_BEATS;
        double blendLen = 0.75D;

        Pose current;
        if (beats < 8.0D) {
            current = blendInto(beats, 0.0D, blendLen, poseOverheadClap(beats), poseJackpotSway(beats, false));
        } else if (beats < 12.0D) {
            current = blendInto(beats, 8.0D, blendLen, poseJackpotSway(beats, false), poseJackpotSway(beats, true));
        } else if (beats < 16.0D) {
            current = poseJackpotSway(beats, true);
        } else if (beats < 22.0D) {
            current = blendInto(beats, 16.0D, blendLen, poseJackpotSway(beats, true), poseShuffleTurn(beats));
        } else if (beats < 26.0D) {
            current = blendInto(beats, 22.0D, blendLen, poseShuffleTurn(beats), poseLowFever(beats));
        } else if (beats < 29.0D) {
            current = blendInto(beats, 26.0D, blendLen, poseLowFever(beats), poseJackpotGrin(beats));
        } else {
            current = blendInto(beats, 29.0D, blendLen, poseJackpotGrin(beats), poseOverheadClap(beats));
        }
        return groundFeet(current);
    }

    private static Pose blendInto(double beats, double start, double len, Pose from, Pose to) {
        double t = (beats - start) / len;
        if (t >= 1.0D) {
            return to;
        }
        return blend(from, to, smooth(t));
    }

    /** Drops the body so the lowest foot touches the ground when the legs are splayed. */
    private static Pose groundFeet(Pose p) {
        Pose out = p.copy();
        double rReach = 12.0D * Math.cos(p.rLegRx) * Math.cos(p.rLegRz);
        double lReach = 12.0D * Math.cos(p.lLegRx) * Math.cos(p.lLegRz);
        double drop = 12.0D - Math.max(rReach, lReach);
        out.bodyY += Math.max(0.0D, drop);
        return out;
    }
    private static float lerpF(float base, double target, double w) {
        return (float) (base + (target - base) * w);
    }

    private static Vec3f lerpVec(Vec3f base, double x, double y, double z, double w) {
        return new Vec3f(lerpF(base.getX(), x, w), lerpF(base.getY(), y, w), lerpF(base.getZ(), z, w));
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
        double w = smooth(mix(this.prevWeight, this.weight, tickDelta));
        if (w <= 0.0D) {
            return value0;
        }
        Pose pose = evaluate(this.danceTicks + (double) tickDelta);

        switch (modelName) {
            case "body":
                if (type == TransformType.POSITION) {
                    return lerpVec(value0, -pose.bodyX / 16.0D, -pose.bodyY / 16.0D, pose.bodyZ / 16.0D, w);
                } else if (type == TransformType.ROTATION) {
                    return lerpVec(value0, pose.bodyRx, pose.bodyRy, pose.bodyRz, w);
                }
                return value0;
            case "torso":
                if (type == TransformType.ROTATION) {
                    return lerpVec(value0, pose.torsoRx, pose.torsoRy, pose.torsoRz, w);
                }
                return value0;
            case "head":
                if (type == TransformType.ROTATION) {
                    // keep following the camera a little so the dance still feels alive
                    double lookX = value0.getX() * 0.35D + pose.headRx;
                    double lookY = value0.getY() * 0.35D + pose.headRy;
                    return lerpVec(value0, lookX, lookY, pose.headRz, w);
                }
                return value0;
            case "rightArm":
                if (type == TransformType.POSITION) {
                    return lerpVec(value0, value0.getX(), value0.getY() + pose.rShoulderY, value0.getZ(), w);
                } else if (type == TransformType.ROTATION) {
                    return lerpVec(value0, pose.rArmRx, pose.rArmRy, pose.rArmRz, w);
                }
                return value0;
            case "leftArm":
                if (type == TransformType.POSITION) {
                    return lerpVec(value0, value0.getX(), value0.getY() + pose.lShoulderY, value0.getZ(), w);
                } else if (type == TransformType.ROTATION) {
                    return lerpVec(value0, pose.lArmRx, pose.lArmRy, pose.lArmRz, w);
                }
                return value0;
            case "rightLeg":
                if (type == TransformType.ROTATION) {
                    return lerpVec(value0, pose.rLegRx, pose.rLegRy, pose.rLegRz, w);
                }
                return value0;
            case "leftLeg":
                if (type == TransformType.ROTATION) {
                    return lerpVec(value0, pose.lLegRx, pose.lLegRy, pose.lLegRz, w);
                }
                return value0;
            default:
                return value0;
        }
    }
}
