package de.xllogic.common.network.payload;

import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum RecoveryDraftResumeStatus {
    TARGET_UNAVAILABLE,
    BLOCKED_BY_OTHER_EDITOR,
    DIVERGED,
    RESUMED;

    private static final int MAX_SERIALIZED_NAME_LENGTH = 48;

    public static final StreamCodec<ByteBuf, RecoveryDraftResumeStatus> STREAM_CODEC = StreamCodec.of(
            (buffer, status) -> ByteBufCodecs.stringUtf8(MAX_SERIALIZED_NAME_LENGTH).encode(buffer, status.name()),
            buffer -> fromSerializedName(ByteBufCodecs.stringUtf8(MAX_SERIALIZED_NAME_LENGTH).decode(buffer))
    );

    public boolean resumed() {
        return this == RESUMED;
    }

    public static RecoveryDraftResumeStatus fromSerializedName(final String serializedName) {
        if (serializedName == null || serializedName.isBlank()) {
            return TARGET_UNAVAILABLE;
        }

        try {
            return valueOf(serializedName.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return TARGET_UNAVAILABLE;
        }
    }
}