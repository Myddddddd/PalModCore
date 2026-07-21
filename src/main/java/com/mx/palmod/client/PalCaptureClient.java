package com.mx.palmod.client;

import com.mx.palmod.Palmod;
import com.mx.palmod.entity.PalSphereProjectile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-only: renders the "shrink into the ball" part of the catch animation.
 *
 * The projectile carries its capture state ({@link PalSphereProjectile#getCaptureMobId()}
 * / {@code getCaptureProgress()}) in auto-synced entity data, so no custom packet is
 * needed. Each client tick we rebuild a {mobId → scale} map from the (few) capturing
 * spheres in the level, and {@link RenderLivingEvent.Pre} scales the matching mob down
 * toward its center — 1.0 at full size, ~0 when fully inside the ball. On a failed catch
 * the projectile drives progress back to 0, so the mob visibly grows out again.
 *
 * All client-only types are confined to this class (registered only on {@link Dist#CLIENT}),
 * so the dedicated server never loads it.
 */
@Mod.EventBusSubscriber(modid = Palmod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PalCaptureClient {

    private static final Map<Integer, Float> MOB_SCALE = new HashMap<>();

    private PalCaptureClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MOB_SCALE.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof PalSphereProjectile ball) {
                int mobId = ball.getCaptureMobId();
                if (mobId >= 0) {
                    MOB_SCALE.put(mobId, Math.max(0.0f, 1.0f - ball.getCaptureProgress()));
                }
            }
        }
    }

    // The transform is applied IN PLACE (no pushPose) and undone in Post, so a
    // third-party mod cancelling the cancelable Pre event can never leak a
    // PoseStack entry — the renderer's own pop discards our in-place scale.
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        Float scale = MOB_SCALE.get(event.getEntity().getId());
        if (scale == null) return;
        float s = Math.max(0.02f, scale);         // never exactly 0 (keep the transform valid)
        float half = event.getEntity().getBbHeight() * 0.5f;
        var pose = event.getPoseStack();
        pose.translate(0.0, half, 0.0);
        pose.scale(s, s, s);
        pose.translate(0.0, -half, 0.0);
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        Float scale = MOB_SCALE.get(event.getEntity().getId());
        if (scale == null) return;
        float s = Math.max(0.02f, scale);
        float inv = 1.0f / s;
        float half = event.getEntity().getBbHeight() * 0.5f;
        var pose = event.getPoseStack();          // inverse of the Pre transform
        pose.translate(0.0, half, 0.0);
        pose.scale(inv, inv, inv);
        pose.translate(0.0, -half, 0.0);
    }
}
