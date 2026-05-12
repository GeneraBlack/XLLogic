package de.xllogic.common.registry;

import net.neoforged.bus.api.IEventBus;

public final class XLRegistries {
    private XLRegistries() {
    }

    public static void register(final IEventBus modEventBus) {
        XLBlocks.BLOCKS.register(modEventBus);
        XLItems.ITEMS.register(modEventBus);
        XLBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        XLCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
    }
}