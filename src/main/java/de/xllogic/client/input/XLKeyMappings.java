package de.xllogic.client.input;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class XLKeyMappings {
    public static final String CATEGORY = "key.categories.xllogic";
    public static final KeyMapping OPEN_COMPUTER = new KeyMapping("key.xllogic.open_terminal", GLFW.GLFW_KEY_P, CATEGORY);
    public static final KeyMapping TOGGLE_RUNTIME_DEBUGGER = new KeyMapping("key.xllogic.toggle_runtime_debugger", GLFW.GLFW_KEY_F8, CATEGORY);
    public static final KeyMapping DUMP_RUNTIME_DEBUGGER = new KeyMapping("key.xllogic.dump_runtime_debugger", GLFW.GLFW_KEY_F9, CATEGORY);

    private XLKeyMappings() {
    }

    public static void register(final RegisterKeyMappingsEvent event) {
        event.register(OPEN_COMPUTER);
        event.register(TOGGLE_RUNTIME_DEBUGGER);
        event.register(DUMP_RUNTIME_DEBUGGER);
    }
}
