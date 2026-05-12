package de.xllogic.runtime;

import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record PythonPeripheralBinding(String apiName,
                                      String displayName,
                                      String type,
                                      BlockPos blockPos,
                                      String position,
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
	public PythonPeripheralBinding {
		blockPos = blockPos == null ? BlockPos.ZERO : blockPos.immutable();
		displayName = displayName == null ? "" : displayName;
		type = type == null ? "unknown" : type;
		position = position == null ? blockPos.toShortString() : position;
		networkScope = networkScope == null ? "local" : networkScope;
		bridgeEndpointName = bridgeEndpointName == null ? "" : bridgeEndpointName;
		downAlias = normalizeAlias(downAlias);
		upAlias = normalizeAlias(upAlias);
		northAlias = normalizeAlias(northAlias);
		southAlias = normalizeAlias(southAlias);
		westAlias = normalizeAlias(westAlias);
		eastAlias = normalizeAlias(eastAlias);
	}

	public boolean isLocal() {
		return !this.isBridged();
	}

	public boolean isBridged() {
		return "bridged".equals(this.networkScope);
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

	private static String normalizeAlias(final String alias) {
		return alias == null ? "" : alias;
	}
}
