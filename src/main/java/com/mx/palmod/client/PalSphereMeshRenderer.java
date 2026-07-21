package com.mx.palmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mx.palmod.entity.PalSphereProjectile;
import com.mx.palmod.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the thrown Pal Sphere as its 3D voxel mesh (Level1/2/3 by tier) instead
 * of the flat vanilla thrown-item billboard. The OBJ is baked via Forge's obj loader
 * (registered as an additional model in {@link PalSphereMeshClient}); its palette
 * texture is stitched into the block atlas, so we draw it with a block-atlas cutout
 * render type.
 *
 * The three constants below are the tuning knobs — adjust on a real client if the
 * ball is the wrong size, off-center, or spins too fast.
 */
public class PalSphereMeshRenderer extends EntityRenderer<PalSphereProjectile> {

    private static final float SCALE = 0.5f;            // overall ball size
    private static final float Y_CENTER = -0.6f;        // mesh sits on y 0..1.2 → lift center to origin
    private static final float SPIN_DEG_PER_TICK = 6f;  // idle spin

    static final ModelResourceLocation LEVEL1 =
            new ModelResourceLocation(new ResourceLocation("palmod", "entity/pal_sphere/level1"), "standalone");
    static final ModelResourceLocation LEVEL2 =
            new ModelResourceLocation(new ResourceLocation("palmod", "entity/pal_sphere/level2"), "standalone");
    static final ModelResourceLocation LEVEL3 =
            new ModelResourceLocation(new ResourceLocation("palmod", "entity/pal_sphere/level3"), "standalone");

    public PalSphereMeshRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(PalSphereProjectile entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public void render(PalSphereProjectile entity, float yaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int light) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(mrlFor(entity));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * SPIN_DEG_PER_TICK));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(0.0, Y_CENTER, 0.0);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        Minecraft.getInstance().getItemRenderer().renderModelLists(
                model, ItemStack.EMPTY, light, OverlayTexture.NO_OVERLAY, pose, vc);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffer, light);
    }

    private ModelResourceLocation mrlFor(PalSphereProjectile entity) {
        Item item = entity.getItem().getItem();
        if (item == ModRegistries.PAL_SPHERE_ADVANCED.get()) return LEVEL3;
        if (item == ModRegistries.PAL_SPHERE_MID.get()) return LEVEL2;
        return LEVEL1;
    }
}
