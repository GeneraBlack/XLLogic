package de.xllogic.common.blockentity;

import de.xllogic.common.network.NamedNetworkEndpointBlockEntity;
import de.xllogic.common.registry.XLBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class ClockBlockEntity extends NamedNetworkEndpointBlockEntity {
    public ClockBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(XLBlockEntities.CLOCK.get(), pos, blockState);
    }
}
