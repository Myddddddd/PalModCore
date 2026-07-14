package com.mx.palmod.pal;

import com.mx.palmod.behavior.PalBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * Entry point for a pal's passive per-tick powers (ticked from onLivingTick, every
 * 20 ticks). The powers themselves — XP collector, item magnet, greedy boom, and
 * anything a third-party mod adds — live in {@link PalAbilityRegistry}.
 */
public final class PalPassives {

    public static final String KEY_STORED_XP = "PalStoredXp";

    private PalPassives() {}

    public static void tick(Mob mob, PalBehavior behavior) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        for (PalAbilityRegistry.PalAbility ability : PalAbilityRegistry.applicable(behavior)) {
            ability.tick(mob, behavior, level);
        }
    }

    /** Sneak-click with an empty hand: pop the stored XP out as bottles o' enchanting. */
    public static void extractXpBottles(ServerPlayer player, Mob mob) {
        net.minecraft.nbt.CompoundTag data = mob.getPersistentData();
        int stored = data.getInt(KEY_STORED_XP);
        int bottles = stored / 8; // a bottle averages ~7-8 xp
        if (bottles <= 0) {
            player.displayClientMessage(Component.literal(
                    mob.getName().getString() + " has no XP stored yet (" + stored + "/8)."), true);
            return;
        }
        data.putInt(KEY_STORED_XP, stored - bottles * 8);
        net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player,
                new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, bottles));
        player.displayClientMessage(Component.literal(
                mob.getName().getString() + " condensed " + bottles + " bottle(s) o' enchanting."), true);
        mob.level().playSound(null, mob.blockPosition(),
                SoundEvents.EXPERIENCE_BOTTLE_THROW, SoundSource.NEUTRAL, 0.8F, 1.2F);
    }
}
