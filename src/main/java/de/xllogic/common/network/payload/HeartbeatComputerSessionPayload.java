package de.xllogic.common.network.payload;

import de.xllogic.XLLogicMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HeartbeatComputerSessionPayload(BlockPos computerPos) implements CustomPacketPayload {
    public static final Type<HeartbeatComputerSessionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "heartbeat_computer_session"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HeartbeatComputerSessionPayload> STREAM_CODEC = StreamCodec.of(
            HeartbeatComputerSessionPayload::encode,
            HeartbeatComputerSessionPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final HeartbeatComputerSessionPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.computerPos());
    }

    private static HeartbeatComputerSessionPayload decode(final RegistryFriendlyByteBuf buffer) {
        return new HeartbeatComputerSessionPayload(BlockPos.STREAM_CODEC.decode(buffer));
    }
}