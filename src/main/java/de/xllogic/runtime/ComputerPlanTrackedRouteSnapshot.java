package de.xllogic.runtime;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ComputerPlanTrackedRouteSnapshot(String routeName, String itemId, int expectedCount) {
    private static final int MAX_STRING_LENGTH = 128;

    public static final StreamCodec<ByteBuf, ComputerPlanTrackedRouteSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH),
            ComputerPlanTrackedRouteSnapshot::routeName,
            ByteBufCodecs.stringUtf8(MAX_STRING_LENGTH),
            ComputerPlanTrackedRouteSnapshot::itemId,
            ByteBufCodecs.VAR_INT,
            ComputerPlanTrackedRouteSnapshot::expectedCount,
            ComputerPlanTrackedRouteSnapshot::new
    );

    public ComputerPlanTrackedRouteSnapshot {
        routeName = limit(routeName);
        itemId = limit(itemId);
        expectedCount = Math.max(0, expectedCount);
    }

    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("RouteName", this.routeName);
        tag.putString("ItemId", this.itemId);
        tag.putInt("ExpectedCount", this.expectedCount);
        return tag;
    }

    public static ComputerPlanTrackedRouteSnapshot fromTag(final CompoundTag tag) {
        return new ComputerPlanTrackedRouteSnapshot(
                tag.getString("RouteName"),
                tag.getString("ItemId"),
                tag.getInt("ExpectedCount")
        );
    }

    public boolean hasRouteName() {
        return !this.routeName.isBlank();
    }

    public boolean hasItemId() {
        return !this.itemId.isBlank();
    }

    private static String limit(final String value) {
        final String safeValue = value == null ? "" : value;
        return safeValue.length() <= MAX_STRING_LENGTH ? safeValue : safeValue.substring(0, MAX_STRING_LENGTH);
    }
}