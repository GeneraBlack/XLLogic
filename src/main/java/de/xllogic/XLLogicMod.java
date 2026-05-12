package de.xllogic;

import com.mojang.logging.LogUtils;
import de.xllogic.client.XLLogicClient;
import de.xllogic.common.config.XLServerConfig;
import de.xllogic.gametest.NetworkGameTests;
import de.xllogic.common.network.XLNetworking;
import de.xllogic.common.registry.XLRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(XLLogicMod.MOD_ID)
public final class XLLogicMod {
    public static final String MOD_ID = "xllogic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public XLLogicMod(final IEventBus modEventBus, final ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, XLServerConfig.SPEC);
        XLRegistries.register(modEventBus);
        XLNetworking.register(modEventBus);
        modEventBus.addListener(NetworkGameTests::registerGameTests);
        if (FMLEnvironment.dist.isClient()) {
            XLLogicClient.register(modEventBus);
        }
    }
}
