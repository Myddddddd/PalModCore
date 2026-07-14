package com.mx.palmod.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

public class PalAttackOwnerTargetGoal extends TargetGoal {
    private final Mob mob;
    private LivingEntity owner;
    private LivingEntity target;
    private int timestamp;

    public PalAttackOwnerTargetGoal(Mob mob) {
        super(mob, false);
        this.mob = mob;
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
        } else {
            this.owner = player;
            LivingEntity livingentity = player.getLastHurtMob();
            if (livingentity != null && livingentity != this.mob && this.canAttack(livingentity, TargetingConditions.DEFAULT)) {
                this.target = livingentity;
                return true;
            }
            return false;
        }
    }

    @Override
    public void start() {
        this.mob.setTarget(this.target);
        if (this.owner != null) {
            this.timestamp = this.owner.getLastHurtMobTimestamp();
        }
        super.start();
    }
}
