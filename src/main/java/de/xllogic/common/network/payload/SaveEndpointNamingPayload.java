package de.xllogic.common.network.payload;

import de.xllogic.XLLogicMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveEndpointNamingPayload(BlockPos endpointPos,
                                        String endpointName,
                                        String downAlias,
                                        String upAlias,
                                        String northAlias,
                                        String southAlias,
                                        String westAlias,
                                        String eastAlias) implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 64;
    public static final Type<SaveEndpointNamingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "save_endpoint_naming"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveEndpointNamingPayload> STREAM_CODEC = StreamCodec.of(
            SaveEndpointNamingPayload::encode,
            SaveEndpointNamingPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public CompoundTag sideAliasesTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putString(Direction.DOWN.getSerializedName(), this.downAlias());
        tag.putString(Direction.UP.getSerializedName(), this.upAlias());
        tag.putString(Direction.NORTH.getSerializedName(), this.northAlias());
        tag.putString(Direction.SOUTH.getSerializedName(), this.southAlias());
        tag.putString(Direction.WEST.getSerializedName(), this.westAlias());
        tag.putString(Direction.EAST.getSerializedName(), this.eastAlias());
        return tag;
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final SaveEndpointNamingPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.endpointPos());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.endpointName());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.downAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.upAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.northAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.southAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.westAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, payload.eastAlias());
    }

    private static SaveEndpointNamingPayload decode(final RegistryFriendlyByteBuf buffer) {
        return new SaveEndpointNamingPayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer)
        );
    }
}