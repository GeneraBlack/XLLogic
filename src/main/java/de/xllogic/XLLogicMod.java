package de.xllogic;

import com.mojang.logging.LogUtils;
import de.xllogic.client.XLLogicClient;
import de.xllogic.common.blockentity.XLApiBlockEntity;
import de.xllogic.common.config.XLServerConfig;
import de.xllogic.gametest.NetworkGameTests;
import de.xllogic.common.network.XLNetworking;
import de.xllogic.common.registry.XLRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
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
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> XLApiBlockEntity.clearRelaysForLevel(event.getLevel() instanceof net.minecraft.world.level.Level level ? level : null));
        if (FMLEnvironment.dist.isClient()) {
            XLLogicClient.register(modEventBus);
        }
    }
}
