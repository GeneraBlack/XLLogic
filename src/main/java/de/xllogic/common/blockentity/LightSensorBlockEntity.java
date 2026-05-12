package de.xllogic.common.blockentity;

import de.xllogic.common.network.NamedNetworkEndpointBlockEntity;
import de.xllogic.common.registry.XLBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class LightSensorBlockEntity extends NamedNetworkEndpointBlockEntity {
    public LightSensorBlockEntity(final BlockPos pos, final BlockState blockState) {
        super(XLBlockEntities.LIGHT_SENSOR.get(), pos, blockState);
    }
}
