package com.mx.palmod.pal;

import com.mx.palmod.behavior.PalBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Rideable pals: the owner mounts with a plain right-click and STEERS BY
 * LOOKING — each tick the mount swims/walks toward where the rider looks.
 * (Client movement input isn't available server-side for unsaddled mobs,
 * so rider-look steering is the whole control scheme. Sneak dismounts.)
 *
 * While mounted in water the rider gets Water Breathing — an orca taxi
 * shouldn't drown its passenger.
 */
public final class PalMountHandler {

    private PalMountHandler() {}

    /** Called from onLivingTick (every 20 ticks would be too coarse for steering —
     *  this is invoked EVERY tick via the mount's own tick when ridden). */
    public static void steer(Mob mob, PalBehavior behavior) {
        Entity passenger = mob.getFirstPassenger();
        if (!(passenger instanceof ServerPlayer rider)) return;
        if (!mob.getPersistentData().hasUUID("PalOwner")
                || !mob.getPersistentData().getUUID("PalOwner").equals(rider.getUUID())) return;

        mob.getNavigation().stop();
        Vec3 look = rider.getLookAngle();
        double speed = behavior.getRideSpeed() * 0.3;

        if (mob.isInWater()) {
            // Full 3D swim toward the rider's look
            mob.setDeltaMovement(look.scale(speed));
            mob.setYRot(rider.getYRot());
            rider.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false));
        } else {
            // On land: amble toward the look direction (mounts are for water,
            // but don't strand the rider on shore)
            Vec3 current = mob.getDeltaMovement();
            mob.setDeltaMovement(look.x * speed * 0.5, current.y, look.z * speed * 0.5);
            mob.setYRot(rider.getYRot());
        }
    }
}
