package de.xllogic.common.network;

import de.xllogic.runtime.debug.XLRuntimeDebugger;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.Locale;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class NamedNetworkEndpointBlockEntity extends BlockEntity {
    private static final String TAG_NETWORK_ANIMATION_ACTIVE = "NetworkAnimationActive";
    private static final String TAG_SIDE_ALIASES = "SideAliases";
    private static final int MAX_CUSTOM_NAME_LENGTH = 64;
    private static final int NETWORK_VALIDATION_INTERVAL_TICKS = 20;

    private String endpointName = "";
    private final String[] sideAliases = new String[Direction.values().length];
    private boolean networkAnimationActive;
    private long lastNetworkValidationTick = Long.MIN_VALUE;

    protected NamedNetworkEndpointBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState blockState) {
        super(type, pos, blockState);
        Arrays.fill(this.sideAliases, "");
    }

    public String getEndpointName() {
        if (!this.endpointName.isBlank()) {
            return this.endpointName;
        }
        return defaultEndpointName();
    }

    public String setEndpointName(final String rawName) {
        this.endpointName = sanitizeOrFallback(rawName, defaultEndpointName());
        this.markStateChanged();
        return this.endpointName;
    }

    public boolean supportsSideNaming() {
        return false;
    }

    public String getSideAlias(final Direction side) {
        if (side == null) {
            return "";
        }
        final String alias = this.sideAliases[side.ordinal()];
        return alias == null ? "" : alias;
    }

    public String setSideAlias(final Direction side, final String rawAlias) {
        if (side == null) {
            return "";
        }

        final String normalizedAlias = this.supportsSideNaming() ? normalizeCustomName(rawAlias) : "";
        if (Objects.equals(this.sideAliases[side.ordinal()], normalizedAlias)) {
            return normalizedAlias;
        }

        this.sideAliases[side.ordinal()] = normalizedAlias;
        this.markStateChanged();
        return normalizedAlias;
    }

    public boolean applyNamingConfiguration(final String rawEndpointName, final CompoundTag sideAliasesTag) {
        boolean changed = false;
        final String normalizedEndpointName = sanitizeOrFallback(rawEndpointName, defaultEndpointName());
        if (!Objects.equals(this.endpointName, normalizedEndpointName)) {
            this.endpointName = normalizedEndpointName;
            changed = true;
        }

        for (final Direction direction : Direction.values()) {
            final String requestedAlias = sideAliasesTag == null ? "" : sideAliasesTag.getString(direction.getSerializedName());
            final String normalizedAlias = this.supportsSideNaming() ? normalizeCustomName(requestedAlias) : "";
            if (!Objects.equals(this.sideAliases[direction.ordinal()], normalizedAlias)) {
                this.sideAliases[direction.ordinal()] = normalizedAlias;
                changed = true;
            }
        }

        if (changed) {
            this.markStateChanged();
        }
        return changed;
    }

    public Direction resolveNamedSide(final String rawSide) {
        final Direction directSide = rawSide == null ? null : Direction.byName(rawSide.toLowerCase(Locale.ROOT));
        if (directSide != null) {
            return directSide;
        }
        if (!this.supportsSideNaming()) {
            return null;
        }

        final String normalized = normalizeCustomName(rawSide);
        if (normalized.isBlank()) {
            return null;
        }

        for (final Direction direction : Direction.values()) {
            if (normalized.equals(this.sideAliases[direction.ordinal()])) {
                return direction;
            }
        }
        return null;
    }

    public String sideAliasSummary() {
        if (!this.supportsSideNaming()) {
            return "";
        }

        final StringJoiner joiner = new StringJoiner(", ");
        for (final Direction direction : Direction.values()) {
            final String alias = this.getSideAlias(direction);
            if (!alias.isBlank()) {
                joiner.add(direction.getSerializedName() + "=" + alias);
            }
        }
        return joiner.toString();
    }

    public static String normalizeCustomName(final String rawName) {
        final String limited = limitName(rawName == null ? "" : rawName.toLowerCase(Locale.ROOT));
        return limited
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_", "")
                .replaceAll("_$", "");
    }

    public String getEndpointType() {
        final ResourceLocation key = BuiltInRegistries.BLOCK.getKey(this.getBlockState().getBlock());
        return key == null ? "unknown" : key.getPath();
    }

    public boolean allowsNetworkPassthrough() {
        return false;
    }

    public boolean isNetworkAnimationActive() {
        return this.networkAnimationActive;
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        final long now = this.level.getGameTime();
        if (this.lastNetworkValidationTick != Long.MIN_VALUE && now - this.lastNetworkValidationTick < NETWORK_VALIDATION_INTERVAL_TICKS) {
            return;
        }

        this.lastNetworkValidationTick = now;
        this.refreshNetworkAnimationState();
    }

    public void refreshNetworkAnimationState() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        final long debugStartedAt = XLRuntimeDebugger.beginSection("server.endpoint.refreshNetworkAnimationState");
        try {
            this.setNetworkAnimationActive(XLNetworkResolver.hasValidAnimationNetwork(this.level, this.worldPosition));
        } finally {
            XLRuntimeDebugger.endSection("server.endpoint.refreshNetworkAnimationState", debugStartedAt);
        }
    }

    protected final void setNetworkAnimationActive(final boolean networkAnimationActive) {
        if (this.networkAnimationActive == networkAnimationActive) {
            return;
        }

        this.networkAnimationActive = networkAnimationActive;
        this.markStateChanged();
    }

    protected String defaultEndpointName() {
        final String suffix = Long.toHexString(this.worldPosition.asLong());
        final String shortSuffix = suffix.substring(Math.max(0, suffix.length() - 6));
        return sanitizeOrFallback(this.getEndpointType() + "_" + shortSuffix, "endpoint");
    }

    protected final void markStateChanged() {
        this.setChanged();
        if (this.level != null) {
            final BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.endpointName = tag.getString("EndpointName");
        for (final Direction direction : Direction.values()) {
            this.sideAliases[direction.ordinal()] = "";
        }
        if (tag.contains(TAG_SIDE_ALIASES, CompoundTag.TAG_COMPOUND)) {
            final CompoundTag sideAliasesTag = tag.getCompound(TAG_SIDE_ALIASES);
            for (final Direction direction : Direction.values()) {
                this.sideAliases[direction.ordinal()] = this.supportsSideNaming()
                        ? normalizeCustomName(sideAliasesTag.getString(direction.getSerializedName()))
                        : "";
            }
        }
        this.networkAnimationActive = tag.getBoolean(TAG_NETWORK_ANIMATION_ACTIVE);
        this.lastNetworkValidationTick = Long.MIN_VALUE;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.endpointName.isBlank()) {
            tag.putString("EndpointName", this.endpointName);
        }
        if (this.supportsSideNaming()) {
            final CompoundTag sideAliasesTag = new CompoundTag();
            for (final Direction direction : Direction.values()) {
                final String alias = this.getSideAlias(direction);
                if (!alias.isBlank()) {
                    sideAliasesTag.putString(direction.getSerializedName(), alias);
                }
            }
            if (!sideAliasesTag.isEmpty()) {
                tag.put(TAG_SIDE_ALIASES, sideAliasesTag);
            }
        }
        if (this.networkAnimationActive) {
            tag.putBoolean(TAG_NETWORK_ANIMATION_ACTIVE, true);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    private static String sanitizeOrFallback(final String rawName, final String fallback) {
        final String normalized = normalizeCustomName(rawName);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String limitName(final String value) {
        return value.length() <= MAX_CUSTOM_NAME_LENGTH ? value : value.substring(0, MAX_CUSTOM_NAME_LENGTH);
    }
}
