package de.xllogic.common.blockentity;

import de.xllogic.common.network.NamedNetworkEndpointBlockEntity;
import de.xllogic.common.registry.XLBlockEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class XLApiBlockEntity extends NamedNetworkEndpointBlockEntity {
    private static final String TAG_UPLINK_GROUP = "UplinkGroup";
    private static final String TAG_RELAY_ENABLED = "RelayEnabled";
    private static final String TAG_FORWARDED_MESSAGES = "ForwardedMessages";
    private static final Map<String, Map<Integer, Set<BlockPos>>> ACTIVE_RELAYS = new HashMap<>();

    private int uplinkGroup;
    private boolean relayEnabled = true;
    private int forwardedMessages;

    public XLApiBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(XLBlockEntities.XLAPI_BLOCK.get(), pos, blockState);
    }

    public int getUplinkGroup() {
        return this.uplinkGroup;
    }

    public void cycleUplinkGroup() {
        this.setUplinkGroup(this.uplinkGroup + 1);
    }

    public void setUplinkGroup(final int uplinkGroup) {
        final int normalizedGroup = Math.floorMod(uplinkGroup, 16);
        if (this.uplinkGroup == normalizedGroup) {
            return;
        }

        this.uplinkGroup = normalizedGroup;
        this.markStateChanged();
        this.syncRelayRegistration();
    }

    public boolean isRelayEnabled() {
        return this.relayEnabled;
    }

    public int getForwardedMessages() {
        return this.forwardedMessages;
    }

    public void toggleRelayEnabled() {
        this.setRelayEnabled(!this.relayEnabled);
    }

    public void setRelayEnabled(final boolean relayEnabled) {
        if (this.relayEnabled == relayEnabled) {
            return;
        }

        this.relayEnabled = relayEnabled;
        this.markStateChanged();
        this.syncRelayRegistration();
    }

    public String describeState() {
        return "Endpoint: " + this.getEndpointName() + " | XLAPI relay: " + this.relayEnabled + " | uplink group: " + this.uplinkGroup + " | forwarded messages: " + this.forwardedMessages;
    }

    public int recordForwardedMessages(final int count) {
        final int appliedCount = Math.max(0, count);
        if (appliedCount <= 0) {
            return this.forwardedMessages;
        }

        this.forwardedMessages = Math.min(Integer.MAX_VALUE, this.forwardedMessages + appliedCount);
        this.markStateChanged();
        return this.forwardedMessages;
    }

    public static List<BlockPos> findActiveRelayPeers(final Level level, final int uplinkGroup, final BlockPos excludedPos) {
        if (level == null) {
            return List.of();
        }

        final Map<Integer, Set<BlockPos>> relaysByGroup = ACTIVE_RELAYS.get(level.dimension().location().toString());
        if (relaysByGroup == null || relaysByGroup.isEmpty()) {
            return List.of();
        }

        final Set<BlockPos> relayPositions = relaysByGroup.get(Math.floorMod(uplinkGroup, 16));
        if (relayPositions == null || relayPositions.isEmpty()) {
            return List.of();
        }

        final ArrayList<BlockPos> resolved = new ArrayList<>(relayPositions.size());
        final ArrayList<BlockPos> stale = new ArrayList<>();
        for (final BlockPos relayPos : relayPositions) {
            if (relayPos.equals(excludedPos) || !level.isLoaded(relayPos)) {
                continue;
            }
            if (level.getBlockEntity(relayPos) instanceof XLApiBlockEntity xlApi && xlApi.isRelayEnabled() && xlApi.getUplinkGroup() == Math.floorMod(uplinkGroup, 16)) {
                resolved.add(relayPos.immutable());
            } else {
                stale.add(relayPos.immutable());
            }
        }

        if (!stale.isEmpty()) {
            relayPositions.removeAll(stale);
        }

        resolved.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        return List.copyOf(resolved);
    }

    @Override
    public boolean allowsNetworkPassthrough() {
        return this.relayEnabled;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.syncRelayRegistration();
    }

    @Override
    public void setRemoved() {
        this.unregisterRelay();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        this.syncRelayRegistration();
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.uplinkGroup = Math.max(0, tag.getInt(TAG_UPLINK_GROUP));
        this.relayEnabled = !tag.contains(TAG_RELAY_ENABLED) || tag.getBoolean(TAG_RELAY_ENABLED);
        this.forwardedMessages = Math.max(0, tag.getInt(TAG_FORWARDED_MESSAGES));
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_UPLINK_GROUP, this.uplinkGroup);
        tag.putBoolean(TAG_RELAY_ENABLED, this.relayEnabled);
        tag.putInt(TAG_FORWARDED_MESSAGES, this.forwardedMessages);
    }

    private void syncRelayRegistration() {
        this.unregisterRelay();
        if (this.level == null || this.isRemoved() || !this.relayEnabled) {
            return;
        }

        final Map<Integer, Set<BlockPos>> relaysByGroup = ACTIVE_RELAYS.computeIfAbsent(this.level.dimension().location().toString(), key -> new HashMap<>());
        final Set<BlockPos> relayPositions = relaysByGroup.computeIfAbsent(this.uplinkGroup, key -> new LinkedHashSet<>());
        relayPositions.add(this.worldPosition.immutable());
    }

    private void unregisterRelay() {
        if (this.level == null) {
            return;
        }

        final String levelKey = this.level.dimension().location().toString();
        final Map<Integer, Set<BlockPos>> relaysByGroup = ACTIVE_RELAYS.get(levelKey);
        if (relaysByGroup == null || relaysByGroup.isEmpty()) {
            return;
        }

        final ArrayList<Integer> emptyGroups = new ArrayList<>();
        for (final Map.Entry<Integer, Set<BlockPos>> entry : relaysByGroup.entrySet()) {
            entry.getValue().remove(this.worldPosition);
            if (entry.getValue().isEmpty()) {
                emptyGroups.add(entry.getKey());
            }
        }
        for (final Integer emptyGroup : emptyGroups) {
            relaysByGroup.remove(emptyGroup);
        }
        if (relaysByGroup.isEmpty()) {
            ACTIVE_RELAYS.remove(levelKey);
        }
    }
}
