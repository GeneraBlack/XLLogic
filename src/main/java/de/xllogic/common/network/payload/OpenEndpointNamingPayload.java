package de.xllogic.common.network.payload;

import de.xllogic.XLLogicMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenEndpointNamingPayload(BlockPos endpointPos,
                                        String endpointName,
                                        String endpointType,
                                        String summary,
                                        boolean supportsSideNaming,
                                        String downAlias,
                                        String upAlias,
                                        String northAlias,
                                        String southAlias,
                                        String westAlias,
                                        String eastAlias) implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_SUMMARY_LENGTH = 256;
    public static final Type<OpenEndpointNamingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "open_endpoint_naming"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenEndpointNamingPayload> STREAM_CODEC = StreamCodec.of(
            OpenEndpointNamingPayload::encode,
            OpenEndpointNamingPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public String sideAlias(final Direction direction) {
        return switch (direction) {
            case DOWN -> this.downAlias;
            case UP -> this.upAlias;
            case NORTH -> this.northAlias;
            case SOUTH -> this.southAlias;
            case WEST -> this.westAlias;
            case EAST -> this.eastAlias;
        };
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final OpenEndpointNamingPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.endpointPos());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.endpointName());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.endpointType());
        ByteBufCodecs.stringUtf8(MAX_SUMMARY_LENGTH).encode(buffer, payload.summary());
        ByteBufCodecs.BOOL.encode(buffer, payload.supportsSideNaming());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.downAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.upAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.northAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.southAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.westAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.eastAlias());
    }

    private static OpenEndpointNamingPayload decode(final RegistryFriendlyByteBuf buffer) {
        return new OpenEndpointNamingPayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_SUMMARY_LENGTH).decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer)
        );
    }
}