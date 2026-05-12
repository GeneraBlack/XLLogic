package de.xllogic.common.network.payload;

import de.xllogic.XLLogicMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExecuteComputerScriptPayload(BlockPos computerPos, String script) implements CustomPacketPayload {
    private static final int MAX_SCRIPT_LENGTH = 16384;

    public static final Type<ExecuteComputerScriptPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "execute_computer_script"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExecuteComputerScriptPayload> STREAM_CODEC = StreamCodec.of(
        ExecuteComputerScriptPayload::encode,
        ExecuteComputerScriptPayload::decode
    );

    public ExecuteComputerScriptPayload {
        script = limit(script == null ? "" : script, MAX_SCRIPT_LENGTH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String limit(final String value, final int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final ExecuteComputerScriptPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.computerPos());
        ByteBufCodecs.stringUtf8(MAX_SCRIPT_LENGTH).encode(buffer, payload.script());
    }

    private static ExecuteComputerScriptPayload decode(final RegistryFriendlyByteBuf buffer) {
        return new ExecuteComputerScriptPayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_SCRIPT_LENGTH).decode(buffer)
        );
    }
}