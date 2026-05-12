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

public record ComputerRuntimeStatePayload(BlockPos computerPos, ComputerRuntimeSnapshot runtimeState, List<XLNetworkEndpointSnapshot> endpoints,
                                          boolean editable, String activeEditorName, ComputerSessionStatus sessionStatus,
                                          String sessionMessage) implements CustomPacketPayload {
    private static final int MAX_ENDPOINTS = 128;
    private static final int MAX_EDITOR_NAME_LENGTH = 64;
    private static final int MAX_SESSION_MESSAGE_LENGTH = 192;
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<XLNetworkEndpointSnapshot>> ENDPOINTS_CODEC = XLNetworkEndpointSnapshot.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENDPOINTS));

    public static final Type<ComputerRuntimeStatePayload> PAYLOAD_TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(XLLogicMod.MOD_ID, "computer_runtime_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ComputerRuntimeStatePayload> STREAM_CODEC = StreamCodec.of(
        ComputerRuntimeStatePayload::encode,
        ComputerRuntimeStatePayload::decode
    );

    public ComputerRuntimeStatePayload {
        runtimeState = runtimeState == null ? ComputerRuntimeSnapshot.idle() : runtimeState;
        endpoints = sanitizeEndpoints(endpoints);
        activeEditorName = sanitizeActiveEditorName(activeEditorName);
        sessionStatus = sessionStatus == null ? ComputerSessionStatus.ACTIVE : sessionStatus;
        sessionMessage = sanitizeSessionMessage(sessionMessage);
    }

    public static ComputerRuntimeStatePayload fromComputer(final ComputerBlockEntity computer, final boolean editable, final String activeEditorName) {
        return new ComputerRuntimeStatePayload(computer.getBlockPos(), computer.getRuntimeState(), computer.getReachableEndpoints(), editable, activeEditorName,
            ComputerSessionStatus.ACTIVE, "");
        }

        public static ComputerRuntimeStatePayload fromComputer(final ComputerBlockEntity computer, final boolean editable, final String activeEditorName,
                                   final ComputerSessionStatus sessionStatus, final String sessionMessage) {
        return new ComputerRuntimeStatePayload(computer.getBlockPos(), computer.getRuntimeState(), computer.getReachableEndpoints(), editable, activeEditorName,
            sessionStatus, sessionMessage);
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

    private static void encode(final RegistryFriendlyByteBuf buffer, final ComputerRuntimeStatePayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.computerPos());
        ComputerRuntimeSnapshot.STREAM_CODEC.encode(buffer, payload.runtimeState());
        ENDPOINTS_CODEC.encode(buffer, payload.endpoints());
        ByteBufCodecs.BOOL.encode(buffer, payload.editable());
        ByteBufCodecs.stringUtf8(MAX_EDITOR_NAME_LENGTH).encode(buffer, payload.activeEditorName());
        ComputerSessionStatus.STREAM_CODEC.encode(buffer, payload.sessionStatus());
        ByteBufCodecs.stringUtf8(MAX_SESSION_MESSAGE_LENGTH).encode(buffer, payload.sessionMessage());
    }

    private static ComputerRuntimeStatePayload decode(final RegistryFriendlyByteBuf buffer) {
        return new ComputerRuntimeStatePayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                ComputerRuntimeSnapshot.STREAM_CODEC.decode(buffer),
                ENDPOINTS_CODEC.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_EDITOR_NAME_LENGTH).decode(buffer),
                ComputerSessionStatus.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.stringUtf8(MAX_SESSION_MESSAGE_LENGTH).decode(buffer)
        );
    }

    private static String sanitizeSessionMessage(final String sessionMessage) {
        if (sessionMessage == null || sessionMessage.isBlank()) {
            return "";
        }
        return sessionMessage.length() <= MAX_SESSION_MESSAGE_LENGTH
                ? sessionMessage
                : sessionMessage.substring(0, MAX_SESSION_MESSAGE_LENGTH);
    }

    private static String sanitizeActiveEditorName(final String activeEditorName) {
        if (activeEditorName == null || activeEditorName.isBlank()) {
            return "";
        }
        return activeEditorName.length() <= MAX_EDITOR_NAME_LENGTH
                ? activeEditorName
                : activeEditorName.substring(0, MAX_EDITOR_NAME_LENGTH);
    }
}