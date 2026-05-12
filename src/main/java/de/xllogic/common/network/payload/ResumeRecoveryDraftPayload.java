package de.xllogic.common.network.payload;

import de.xllogic.XLLogicMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ResumeRecoveryDraftPayload(BlockPos computerPos, String script, boolean autoStartOnLoad, boolean forceOverwrite) implements CustomPacketPayload {
    private static final int MAX_SCRIPT_LENGTH = 16384;

    public static final Type<ResumeRecoveryDraftPayload> PAYLOAD_TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "resume_recovery_draft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResumeRecoveryDraftPayload> STREAM_CODEC = StreamCodec.of(
            ResumeRecoveryDraftPayload::encode,
            ResumeRecoveryDraftPayload::decode
    );

    public ResumeRecoveryDraftPayload {
        script = limit(script == null ? "" : script, MAX_SCRIPT_LENGTH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PAYLOAD_TYPE;
    }

    private static String limit(final String value, final int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final ResumeRecoveryDraftPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.computerPos());
        ByteBufCodecs.stringUtf8(MAX_SCRIPT_LENGTH).encode(buffer, payload.script());
        ByteBufCodecs.BOOL.encode(buffer, payload.autoStartOnLoad());
        ByteBufCodecs.BOOL.encode(buffer, payload.forceOverwrite());
    }

    private static ResumeRecoveryDraftPayload decode(final RegistryFriendlyByteBuf buffer) {
        return new ResumeRecoveryDraftPayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_SCRIPT_LENGTH).decode(buffer),
            ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer)
        );
    }
}