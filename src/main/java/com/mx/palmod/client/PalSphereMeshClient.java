package com.mx.palmod.client;

import com.mx.palmod.Palmod;
import com.mx.palmod.registry.ModRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client wiring for the 3D Pal Sphere mesh: bakes the three OBJ models (forge:obj
 * loader) as additional models and swaps the thrown projectile's renderer to
 * {@link PalSphereMeshRenderer}. Mod bus, client dist only.
 */
@Mod.EventBusSubscriber(modid = Palmod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PalSphereMeshClient {

    private PalSphereMeshClient() {}

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(PalSphereMeshRenderer.LEVEL1);
        event.register(PalSphereMeshRenderer.LEVEL2);
        event.register(PalSphereMeshRenderer.LEVEL3);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.PAL_SPHERE_PROJECTILE.get(), PalSphereMeshRenderer::new);
    }
}
