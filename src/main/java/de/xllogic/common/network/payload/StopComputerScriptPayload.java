package de.xllogic.common.network.payload;

import de.xllogic.XLLogicMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StopComputerScriptPayload(BlockPos computerPos) implements CustomPacketPayload {
    public static final Type<StopComputerScriptPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "stop_computer_script"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StopComputerScriptPayload> STREAM_CODEC = StreamCodec.of(
            StopComputerScriptPayload::encode,
            StopComputerScriptPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final StopComputerScriptPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.computerPos());
    }

    private static StopComputerScriptPayload decode(final RegistryFriendlyByteBuf buffer) {
        return new StopComputerScriptPayload(BlockPos.STREAM_CODEC.decode(buffer));
    }
}