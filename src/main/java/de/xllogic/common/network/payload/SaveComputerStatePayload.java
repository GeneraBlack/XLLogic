package de.xllogic.common.network.payload;

import de.xllogic.XLLogicMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveComputerStatePayload(BlockPos computerPos, String script, boolean autoStartOnLoad) implements CustomPacketPayload {
    private static final int MAX_SCRIPT_LENGTH = 16384;

    public static final Type<SaveComputerStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "save_computer_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveComputerStatePayload> STREAM_CODEC = StreamCodec.of(
        SaveComputerStatePayload::encode,
        SaveComputerStatePayload::decode
    );

    public SaveComputerStatePayload {
        script = limit(script == null ? "" : script, MAX_SCRIPT_LENGTH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String limit(final String value, final int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final SaveComputerStatePayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.computerPos());
        ByteBufCodecs.stringUtf8(MAX_SCRIPT_LENGTH).encode(buffer, payload.script());
        ByteBufCodecs.BOOL.encode(buffer, payload.autoStartOnLoad());
    }

    private static SaveComputerStatePayload decode(final RegistryFriendlyByteBuf buffer) {
        return new SaveComputerStatePayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_SCRIPT_LENGTH).decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer)
        );
    }
}
