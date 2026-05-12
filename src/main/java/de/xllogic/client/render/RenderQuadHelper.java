package de.xllogic.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.xllogic.XLLogicMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

final class RenderQuadHelper {
    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "textures/misc/white.png");

    private RenderQuadHelper() {
    }

        static void drawSolidQuad(final MultiBufferSource bufferSource, final PoseStack.Pose pose, final float left, final float top, final float right,
                                                          final float bottom, final float z, final int color, final int packedLight, final int packedOverlay) {
                drawQuad(bufferSource.getBuffer(RenderType.entitySolid(WHITE_TEXTURE)), pose, left, top, right, bottom, z, color, packedLight, packedOverlay);
        }

        static void drawTranslucentQuad(final MultiBufferSource bufferSource, final PoseStack.Pose pose, final float left, final float top, final float right,
                                                                        final float bottom, final float z, final int color, final int packedLight, final int packedOverlay) {
                drawQuad(bufferSource.getBuffer(RenderType.entityTranslucentCull(WHITE_TEXTURE)), pose, left, top, right, bottom, z, color, packedLight, packedOverlay);
        }

        private static void drawQuad(final VertexConsumer consumer, final PoseStack.Pose pose, final float left, final float top, final float right,
                                                                 final float bottom, final float z, final int color, final int packedLight, final int packedOverlay) {
        final int alpha = color >>> 24 & 0xFF;
        final int red = color >>> 16 & 0xFF;
        final int green = color >>> 8 & 0xFF;
        final int blue = color & 0xFF;
        consumer.addVertex(pose, left, bottom, z).setColor(red, green, blue, alpha).setUv(0.0F, 1.0F).setOverlay(packedOverlay).setLight(packedLight)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, right, bottom, z).setColor(red, green, blue, alpha).setUv(1.0F, 1.0F).setOverlay(packedOverlay).setLight(packedLight)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, right, top, z).setColor(red, green, blue, alpha).setUv(1.0F, 0.0F).setOverlay(packedOverlay).setLight(packedLight)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, left, top, z).setColor(red, green, blue, alpha).setUv(0.0F, 0.0F).setOverlay(packedOverlay).setLight(packedLight)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}