package de.xllogic.common.network.payload;

import de.xllogic.XLLogicMod;
import de.xllogic.common.blockentity.ComputerBlockEntity;
import de.xllogic.common.network.XLNetworkEndpointSnapshot;
import de.xllogic.runtime.ComputerRuntimeSnapshot;
import de.xllogic.runtime.PythonExecutionContext;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenComputerStatePayload(BlockPos computerPos, String script, ComputerRuntimeSnapshot runtimeState, List<XLNetworkEndpointSnapshot> endpoints,
                                       boolean editable, String activeEditorName, boolean autoStartOnLoad) implements CustomPacketPayload {
    private static final int MAX_SCRIPT_LENGTH = 16384;
    private static final int MAX_ENDPOINTS = 128;
    private static final int MAX_EDITOR_NAME_LENGTH = 64;
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<XLNetworkEndpointSnapshot>> ENDPOINTS_CODEC = XLNetworkEndpointSnapshot.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENDPOINTS));

    public static final Type<OpenComputerStatePayload> PAYLOAD_TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "open_computer_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenComputerStatePayload> STREAM_CODEC = StreamCodec.of(
        OpenComputerStatePayload::encode,
        OpenComputerStatePayload::decode
    );

    public OpenComputerStatePayload {
        script = limit(script == null ? "" : script, MAX_SCRIPT_LENGTH);
        runtimeState = runtimeState == null ? ComputerRuntimeSnapshot.idle() : runtimeState;
        endpoints = sanitizeEndpoints(endpoints);
        activeEditorName = limit(activeEditorName == null ? "" : activeEditorName, MAX_EDITOR_NAME_LENGTH);
    }

    public static OpenComputerStatePayload fromComputer(final ComputerBlockEntity computer, final boolean editable, final String activeEditorName) {
        return new OpenComputerStatePayload(computer.getBlockPos(), computer.getScript(), computer.getRuntimeState(), computer.getReachableEndpoints(), editable,
                activeEditorName, computer.autoStartOnLoad());
    }

    public PythonExecutionContext executionContext() {
        return PythonExecutionContext.fromSnapshots("computer_" + this.computerPos.toShortString(), this.computerPos, this.endpoints);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PAYLOAD_TYPE;
    }

    private static List<XLNetworkEndpointSnapshot> sanitizeEndpoints(final List<XLNetworkEndpointSnapshot> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return List.of();
        }
        return List.copyOf(endpoints.size() > MAX_ENDPOINTS ? endpoints.subList(0, MAX_ENDPOINTS) : endpoints);
    }

    private static String limit(final String value, final int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final OpenComputerStatePayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.computerPos());
        ByteBufCodecs.stringUtf8(MAX_SCRIPT_LENGTH).encode(buffer, payload.script());
        ComputerRuntimeSnapshot.STREAM_CODEC.encode(buffer, payload.runtimeState());
        ENDPOINTS_CODEC.encode(buffer, payload.endpoints());
        ByteBufCodecs.BOOL.encode(buffer, payload.editable());
        ByteBufCodecs.stringUtf8(MAX_EDITOR_NAME_LENGTH).encode(buffer, payload.activeEditorName());
        ByteBufCodecs.BOOL.encode(buffer, payload.autoStartOnLoad());
    }

    private static OpenComputerStatePayload decode(final RegistryFriendlyByteBuf buffer) {
        return new OpenComputerStatePayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_SCRIPT_LENGTH).decode(buffer),
                ComputerRuntimeSnapshot.STREAM_CODEC.decode(buffer),
                ENDPOINTS_CODEC.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_EDITOR_NAME_LENGTH).decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer)
        );
    }
}
