package de.xllogic.common.network;

import io.netty.buffer.ByteBuf;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record XLNetworkEndpointSnapshot(String endpointName,
                                        String endpointType,
                                        BlockPos pos,
                                        int distance,
                                        String networkScope,
                                        String bridgeEndpointName,
                                        int bridgeUplinkGroup,
                                        String downAlias,
                                        String upAlias,
                                        String northAlias,
                                        String southAlias,
                                        String westAlias,
                                        String eastAlias) {
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_SCOPE_LENGTH = 16;
    private static final String SCOPE_LOCAL = "local";
    private static final String SCOPE_BRIDGED = "bridged";

    public static final StreamCodec<ByteBuf, XLNetworkEndpointSnapshot> STREAM_CODEC = StreamCodec.of(
        XLNetworkEndpointSnapshot::encode,
        XLNetworkEndpointSnapshot::decode
    );

    public XLNetworkEndpointSnapshot {
        endpointName = limit(endpointName, MAX_NAME_LENGTH);
        endpointType = limit(endpointType, MAX_NAME_LENGTH);
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        networkScope = normalizeScope(networkScope);
        bridgeEndpointName = limitOptional(bridgeEndpointName, MAX_NAME_LENGTH);
        bridgeUplinkGroup = SCOPE_BRIDGED.equals(networkScope) ? Math.max(0, bridgeUplinkGroup) : -1;
        distance = Math.max(distance, 0);
        downAlias = sanitizeAlias(downAlias);
        upAlias = sanitizeAlias(upAlias);
        northAlias = sanitizeAlias(northAlias);
        southAlias = sanitizeAlias(southAlias);
        westAlias = sanitizeAlias(westAlias);
        eastAlias = sanitizeAlias(eastAlias);
    }

    public static XLNetworkEndpointSnapshot local(final NamedNetworkEndpointBlockEntity endpoint, final BlockPos pos, final int distance) {
        return fromEndpoint(endpoint, pos, distance, SCOPE_LOCAL, "", -1);
    }

    public static XLNetworkEndpointSnapshot bridged(final XLNetworkEndpointSnapshot endpoint,
                                                    final int distance,
                                                    final String bridgeEndpointName,
                                                    final int bridgeUplinkGroup) {
        if (endpoint == null) {
            return new XLNetworkEndpointSnapshot("unknown", "unknown", BlockPos.ZERO, distance, SCOPE_BRIDGED, bridgeEndpointName, bridgeUplinkGroup,
                    "", "", "", "", "", "");
        }
        return new XLNetworkEndpointSnapshot(
                endpoint.endpointName(),
                endpoint.endpointType(),
                endpoint.pos(),
                distance,
                SCOPE_BRIDGED,
                bridgeEndpointName,
                bridgeUplinkGroup,
                endpoint.downAlias(),
                endpoint.upAlias(),
                endpoint.northAlias(),
                endpoint.southAlias(),
                endpoint.westAlias(),
                endpoint.eastAlias()
        );
    }

    public boolean isLocal() {
        return SCOPE_LOCAL.equals(this.networkScope);
    }

    public boolean isBridged() {
        return SCOPE_BRIDGED.equals(this.networkScope);
    }

    public String summary() {
        if (this.isBridged()) {
            return this.endpointName + " [" + this.endpointType + "] @ " + this.pos.toShortString() + " via " + this.bridgeEndpointName + " (group " + this.bridgeUplinkGroup + ")";
        }
        return this.endpointName + " [" + this.endpointType + "] @ " + this.pos.toShortString();
    }

    public String sideAlias(final Direction direction) {
        if (direction == null) {
            return "";
        }
        return switch (direction) {
            case DOWN -> this.downAlias;
            case UP -> this.upAlias;
            case NORTH -> this.northAlias;
            case SOUTH -> this.southAlias;
            case WEST -> this.westAlias;
            case EAST -> this.eastAlias;
        };
    }

    public boolean hasSideAliases() {
        return !this.downAlias.isBlank()
                || !this.upAlias.isBlank()
                || !this.northAlias.isBlank()
                || !this.southAlias.isBlank()
                || !this.westAlias.isBlank()
                || !this.eastAlias.isBlank();
    }

    public List<String> sideNames() {
        final LinkedHashSet<String> names = new LinkedHashSet<>();
        for (final Direction direction : Direction.values()) {
            names.add(direction.getSerializedName());
            final String alias = this.sideAlias(direction);
            if (!alias.isBlank()) {
                names.add(alias);
            }
        }
        return List.copyOf(names);
    }

    private static String limit(final String value, final int maxLength) {
        final String safeValue = value == null ? "unknown" : value;
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    private static String limitOptional(final String value, final int maxLength) {
        final String safeValue = value == null ? "" : value;
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    private static String normalizeScope(final String value) {
        return SCOPE_BRIDGED.equals(value) ? SCOPE_BRIDGED : SCOPE_LOCAL;
    }

    private static String sanitizeAlias(final String value) {
        return limitOptional(NamedNetworkEndpointBlockEntity.normalizeCustomName(value), MAX_NAME_LENGTH);
    }

    private static XLNetworkEndpointSnapshot fromEndpoint(final NamedNetworkEndpointBlockEntity endpoint,
                                                          final BlockPos pos,
                                                          final int distance,
                                                          final String networkScope,
                                                          final String bridgeEndpointName,
                                                          final int bridgeUplinkGroup) {
        if (endpoint == null) {
            return new XLNetworkEndpointSnapshot("unknown", "unknown", pos, distance, networkScope, bridgeEndpointName, bridgeUplinkGroup,
                    "", "", "", "", "", "");
        }
        return new XLNetworkEndpointSnapshot(
                endpoint.getEndpointName(),
                endpoint.getEndpointType(),
                pos,
                distance,
                networkScope,
                bridgeEndpointName,
                bridgeUplinkGroup,
                endpoint.getSideAlias(Direction.DOWN),
                endpoint.getSideAlias(Direction.UP),
                endpoint.getSideAlias(Direction.NORTH),
                endpoint.getSideAlias(Direction.SOUTH),
                endpoint.getSideAlias(Direction.WEST),
                endpoint.getSideAlias(Direction.EAST)
        );
    }

    private static void encode(final ByteBuf buffer, final XLNetworkEndpointSnapshot snapshot) {
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, snapshot.endpointName());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, snapshot.endpointType());
        BlockPos.STREAM_CODEC.encode(buffer, snapshot.pos());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.distance());
        ByteBufCodecs.stringUtf8(MAX_SCOPE_LENGTH).encode(buffer, snapshot.networkScope());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, snapshot.bridgeEndpointName());
        ByteBufCodecs.VAR_INT.encode(buffer, snapshot.bridgeUplinkGroup());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, snapshot.downAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, snapshot.upAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, snapshot.northAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, snapshot.southAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, snapshot.westAlias());
        ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buffer, snapshot.eastAlias());
    }

    private static XLNetworkEndpointSnapshot decode(final ByteBuf buffer) {
        return new XLNetworkEndpointSnapshot(
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                BlockPos.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_SCOPE_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buffer)
        );
    }
}
