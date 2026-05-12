package de.xllogic.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.xllogic.common.block.AbstractDeviceBlock;
import de.xllogic.common.blockentity.ComputerBlockEntity;
import de.xllogic.common.network.NamedNetworkEndpointBlockEntity;
import de.xllogic.common.registry.XLBlocks;
import de.xllogic.runtime.debug.XLRuntimeDebugger;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class NetworkActivityBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final long PULSE_PERIOD_MILLIS = 1800L;
    private static final float HALO_EXPANSION = 0.018F;
    private static final float HALO_Z = 1.002F;
    private static final float CORE_Z = 1.004F;
    private static final AnimationProfile COMPUTER_PROFILE = new AnimationProfile(0xD44AD9C3,
            List.of(rect(3.0F, 2.0F, 13.0F, 5.0F), rect(2.0F, 7.0F, 4.0F, 11.0F), rect(12.0F, 7.0F, 14.0F, 11.0F), rect(5.0F, 8.0F, 11.0F, 10.0F)));
    private static final AnimationProfile MATERIAL_IO_PROFILE = new AnimationProfile(0xD4E6B35C,
            List.of(rect(3.0F, 3.0F, 6.0F, 7.0F), rect(10.0F, 3.0F, 13.0F, 7.0F), rect(4.0F, 10.0F, 12.0F, 12.0F)));
    private static final AnimationProfile CRAFTING_IO_PROFILE = new AnimationProfile(0xD4F0AA5A,
            List.of(rect(3.0F, 3.0F, 5.0F, 5.0F), rect(7.0F, 3.0F, 9.0F, 5.0F), rect(11.0F, 3.0F, 13.0F, 5.0F),
                    rect(5.0F, 7.0F, 11.0F, 9.0F), rect(3.0F, 11.0F, 5.0F, 13.0F), rect(7.0F, 11.0F, 9.0F, 13.0F), rect(11.0F, 11.0F, 13.0F, 13.0F)));
    private static final AnimationProfile CRAFTING_CPU_PROFILE = new AnimationProfile(0xD45CD9FF,
            List.of(rect(5.0F, 5.0F, 11.0F, 11.0F), rect(2.0F, 7.0F, 5.0F, 9.0F), rect(11.0F, 7.0F, 14.0F, 9.0F), rect(7.0F, 2.0F, 9.0F, 5.0F), rect(7.0F, 11.0F, 9.0F, 14.0F)));
    private static final AnimationProfile REDSTONE_IO_PROFILE = new AnimationProfile(0xD4FF6363,
            List.of(rect(7.0F, 2.0F, 9.0F, 14.0F), rect(2.0F, 7.0F, 14.0F, 9.0F)));
    private static final AnimationProfile XLAPI_PROFILE = new AnimationProfile(0xD47ED0FF,
            List.of(rect(6.0F, 6.0F, 10.0F, 10.0F), rect(3.0F, 3.0F, 5.0F, 5.0F), rect(11.0F, 3.0F, 13.0F, 5.0F), rect(3.0F, 11.0F, 5.0F, 13.0F), rect(11.0F, 11.0F, 13.0F, 13.0F)));
    private static final AnimationProfile CLOCK_PROFILE = new AnimationProfile(0xD468E1D6,
            List.of(rect(7.0F, 2.0F, 9.0F, 4.0F), rect(11.0F, 7.0F, 13.0F, 9.0F), rect(7.0F, 12.0F, 9.0F, 14.0F), rect(3.0F, 7.0F, 5.0F, 9.0F), rect(7.0F, 7.0F, 8.0F, 11.0F)));
    private static final AnimationProfile LIGHT_SENSOR_PROFILE = new AnimationProfile(0xD4F0D884,
            List.of(rect(4.0F, 4.0F, 12.0F, 12.0F), rect(7.0F, 2.0F, 9.0F, 4.0F), rect(7.0F, 12.0F, 9.0F, 14.0F)));
    private static final AnimationProfile RAIN_SENSOR_PROFILE = new AnimationProfile(0xD474C6FF,
            List.of(rect(4.0F, 4.0F, 6.0F, 7.0F), rect(7.0F, 3.0F, 9.0F, 8.0F), rect(10.0F, 4.0F, 12.0F, 7.0F), rect(6.0F, 9.0F, 8.0F, 13.0F), rect(9.0F, 10.0F, 11.0F, 14.0F)));

    public NetworkActivityBlockEntityRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(final T blockEntity, final float partialTick, final PoseStack poseStack, final MultiBufferSource bufferSource,
                       final int packedLight, final int packedOverlay) {
        final long debugStartedAt = XLRuntimeDebugger.beginSection("client.render.networkActivityBlockEntity");
        try {
            if (!isAnimationActive(blockEntity)) {
                return;
            }

            final BlockState state = blockEntity.getBlockState();
            if (!state.hasProperty(AbstractDeviceBlock.FACING)) {
                return;
            }

            final AnimationProfile profile = resolveProfile(state.getBlock());
            if (profile == null) {
                return;
            }

            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(rotationDegrees(state.getValue(AbstractDeviceBlock.FACING))));
            poseStack.translate(-0.5F, -0.5F, -0.5F);

            final int color = animateColor(profile.color(), blockEntity.getBlockPos().asLong());
            final int haloColor = withScaledAlpha(color, 0.35F);
            final PoseStack.Pose pose = poseStack.last();
            for (final GlowRect rect : profile.rects()) {
                this.renderRect(bufferSource, pose, rect.expand(HALO_EXPANSION), HALO_Z, haloColor);
                this.renderRect(bufferSource, pose, rect, CORE_Z, color);
            }

            poseStack.popPose();
        } finally {
            XLRuntimeDebugger.endSection("client.render.networkActivityBlockEntity", debugStartedAt);
        }
    }

    private void renderRect(final MultiBufferSource bufferSource, final PoseStack.Pose pose, final GlowRect rect, final float z, final int color) {
        RenderQuadHelper.drawTranslucentQuad(bufferSource, pose, rect.left(), rect.top(), rect.right(), rect.bottom(), z, color, LightTexture.FULL_BRIGHT, 0);
    }

    private static boolean isAnimationActive(final BlockEntity blockEntity) {
        if (blockEntity instanceof NamedNetworkEndpointBlockEntity endpoint) {
            return endpoint.isNetworkAnimationActive();
        }
        if (blockEntity instanceof ComputerBlockEntity computer) {
            return computer.isNetworkAnimationActive();
        }
        return false;
    }

    private static AnimationProfile resolveProfile(final Block block) {
        if (block == XLBlocks.COMPUTER.get()) {
            return COMPUTER_PROFILE;
        }
        if (block == XLBlocks.MATERIAL_IO.get()) {
            return MATERIAL_IO_PROFILE;
        }
        if (block == XLBlocks.CRAFTING_IO.get()) {
            return CRAFTING_IO_PROFILE;
        }
        if (block == XLBlocks.CRAFTING_CPU.get()) {
            return CRAFTING_CPU_PROFILE;
        }
        if (block == XLBlocks.REDSTONE_IO.get()) {
            return REDSTONE_IO_PROFILE;
        }
        if (block == XLBlocks.XLAPI_BLOCK.get()) {
            return XLAPI_PROFILE;
        }
        if (block == XLBlocks.CLOCK.get()) {
            return CLOCK_PROFILE;
        }
        if (block == XLBlocks.LIGHT_SENSOR.get()) {
            return LIGHT_SENSOR_PROFILE;
        }
        if (block == XLBlocks.RAIN_SENSOR.get()) {
            return RAIN_SENSOR_PROFILE;
        }
        return null;
    }

    private static int animateColor(final int baseColor, final long seed) {
        return scaleColor(baseColor, pulseIntensity(seed));
    }

    private static float pulseIntensity(final long seed) {
        final long elapsedMillis = Util.getMillis() + Math.floorMod(seed, PULSE_PERIOD_MILLIS);
        final float phase = (elapsedMillis % PULSE_PERIOD_MILLIS) / (float) PULSE_PERIOD_MILLIS;
        final float wave = 0.5F + 0.5F * Mth.sin(phase * Mth.TWO_PI);
        return 0.45F + 0.55F * wave;
    }

    private static int scaleColor(final int color, final float intensity) {
        final float clampedIntensity = Mth.clamp(intensity, 0.0F, 1.0F);
        final int alpha = color >>> 24 & 0xFF;
        final int red = Math.round((color >>> 16 & 0xFF) * clampedIntensity);
        final int green = Math.round((color >>> 8 & 0xFF) * clampedIntensity);
        final int blue = Math.round((color & 0xFF) * clampedIntensity);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int withScaledAlpha(final int color, final float alphaScale) {
        final int alpha = Math.round((color >>> 24 & 0xFF) * Mth.clamp(alphaScale, 0.0F, 1.0F));
        return alpha << 24 | color & 0x00FFFFFF;
    }

    private static float rotationDegrees(final Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
    }

    private static GlowRect rect(final float left, final float top, final float right, final float bottom) {
        return new GlowRect(left / 16.0F, 1.0F - top / 16.0F, right / 16.0F, 1.0F - bottom / 16.0F);
    }

    private record AnimationProfile(int color, List<GlowRect> rects) {
    }

    private record GlowRect(float left, float top, float right, float bottom) {
        private GlowRect expand(final float amount) {
            return new GlowRect(
                    Mth.clamp(this.left - amount, 0.0F, 1.0F),
                    Mth.clamp(this.top + amount, 0.0F, 1.0F),
                    Mth.clamp(this.right + amount, 0.0F, 1.0F),
                    Mth.clamp(this.bottom - amount, 0.0F, 1.0F));
        }
    }
}