package de.xllogic.common.network.payload;

import de.xllogic.XLLogicMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CloseComputerSessionPayload(BlockPos computerPos) implements CustomPacketPayload {
    public static final Type<CloseComputerSessionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "close_computer_session"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CloseComputerSessionPayload> STREAM_CODEC = StreamCodec.of(
            CloseComputerSessionPayload::encode,
            CloseComputerSessionPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final CloseComputerSessionPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.computerPos());
    }

    private static CloseComputerSessionPayload decode(final RegistryFriendlyByteBuf buffer) {
        return new CloseComputerSessionPayload(BlockPos.STREAM_CODEC.decode(buffer));
    }
}