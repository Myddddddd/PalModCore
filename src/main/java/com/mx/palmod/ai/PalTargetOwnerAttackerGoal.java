package com.mx.palmod.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Targets whatever last hurt the Pal's owner.
 *
 * In guard-only mode ("bodyguard"), the goal is strictly reactive:
 *  - Edge-triggered: it only fires when the owner's last-hurt timestamp CHANGES,
 *    so it never re-locks an old attacker after the fight is over.
 *  - Leashed: attackers farther than guardLeash from the owner are ignored,
 *    and an engaged target is dropped as soon as it leaves that radius.
 */
public class PalTargetOwnerAttackerGoal extends TargetGoal {
    private final Mob mob;
    private final boolean guardOnly;
    private final float guardLeash;
    private LivingEntity owner;
    private LivingEntity attacker;
    private int timestamp;

    public PalTargetOwnerAttackerGoal(Mob mob) {
        this(mob, false, 0.0F);
    }

    public PalTargetOwnerAttackerGoal(Mob mob, boolean guardOnly, float guardLeash) {
        super(mob, false);
        this.mob = mob;
        this.guardOnly = guardOnly;
        this.guardLeash = guardLeash;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!this.mob.getPersistentData().contains("PalOwner")) {
            return false;
        }
        UUID ownerUUID = this.mob.getPersistentData().getUUID("PalOwner");
        Player player = this.mob.level().getPlayerByUUID(ownerUUID);
        if (player == null) {
            return false;
        }
        this.owner = player;
        LivingEntity livingentity = player.getLastHurtByMob();
        if (livingentity == null || livingentity == this.mob) {
            return false;
        }
        // Edge-trigger: only react to a NEW attack on the owner (vanilla
        // OwnerHurtByTargetGoal semantics) — never re-lock a finished fight.
        if (player.getLastHurtByMobTimestamp() == this.timestamp) {
            return false;
        }
        if (guardOnly) {
            // Leash: ignore attackers too far from the owner
            if (livingentity.distanceToSqr(player) > (double) (guardLeash * guardLeash)) {
                return false;
            }
        }
        if (this.canAttack(livingentity, TargetingConditions.DEFAULT)) {
            this.attacker = livingentity;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (guardOnly) {
            LivingEntity target = this.mob.getTarget();
            if (target == null || this.owner == null) {
                return false;
            }
            // Disengage once the attacker leaves the guard radius around the owner
            if (target.distanceToSqr(this.owner) > (double) (guardLeash * guardLeash)) {
                return false;
            }
        }
        return super.canContinueToUse();
    }

    @Override
    public void start() {
        this.mob.setTarget(this.attacker);
        if (this.owner != null) {
            this.timestamp = this.owner.getLastHurtByMobTimestamp();
        }
        super.start();
    }
}
