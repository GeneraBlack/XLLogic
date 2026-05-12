package de.xllogic.common.network.payload;

import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum ComputerSessionStatus {
    ACTIVE,
    TARGET_UNAVAILABLE;

    private static final int MAX_SERIALIZED_NAME_LENGTH = 32;

    public static final StreamCodec<ByteBuf, ComputerSessionStatus> STREAM_CODEC = StreamCodec.of(
            (buffer, status) -> ByteBufCodecs.stringUtf8(MAX_SERIALIZED_NAME_LENGTH).encode(buffer, status.name()),
            buffer -> fromSerializedName(ByteBufCodecs.stringUtf8(MAX_SERIALIZED_NAME_LENGTH).decode(buffer))
    );

    public boolean targetAvailable() {
        return this == ACTIVE;
    }

    public static ComputerSessionStatus fromSerializedName(final String serializedName) {
        if (serializedName == null || serializedName.isBlank()) {
            return ACTIVE;
        }

        try {
            return valueOf(serializedName.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return ACTIVE;
        }
    }
}