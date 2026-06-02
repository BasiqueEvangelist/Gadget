package io.wispforest.gadget.dump.read;

import io.wispforest.gadget.dump.fake.FakeGadgetPacket;
import io.wispforest.gadget.dump.fake.GadgetDynamicRegistriesPacket;
import io.wispforest.gadget.dump.write.PacketDumping;
import io.wispforest.gadget.util.NetworkUtil;
import io.wispforest.gadget.util.ProgressToast;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.zip.GZIPInputStream;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.status.StatusProtocols;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.Nullable;

public class PacketDumpDeserializer {
    private PacketDumpDeserializer() {

    }

    public static ReadPacketDump readFrom(ProgressToast toast, Path path) throws IOException {
        try (var is = toast.loadWithProgress(path)) {
            return PacketDumpDeserializer.readNew(is);
        }
    }

    public static ReadPacketDump readNew(InputStream is) throws IOException {
        try (BufferedInputStream dis = new BufferedInputStream(new GZIPInputStream(is))) {
            var magic = dis.readNBytes(11);

            if (!Arrays.equals(magic, "gadget:dump".getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalStateException("Invalid gdump file!");
            }

            var version = readInt(dis, false).orElseThrow();

            if (version == 1)
                return readV1(dis);
            else
                throw new IllegalStateException("Invalid gdump version " + version);
        }
    }

    private static ReadPacketDump readV1(InputStream is) {
        List<DumpedPacket> list = new ArrayList<>();

        FriendlyByteBuf buf = FriendlyByteBufs.create();

        Int2ObjectMap<Identifier> loginQueryChannels = new Int2ObjectOpenHashMap<>();
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        try {
            while (true) {
                OptionalInt len = readInt(is, true);

                if (len.isEmpty())
                    return new ReadPacketDump(list, null);

                buf.readerIndex(0);
                buf.writerIndex(0);

                buf.writeBytes(is.readNBytes(len.getAsInt()));

                short flags = buf.readShort();
                boolean outbound = (flags & 1) != 0;
                ConnectionProtocol phase = switch (flags & 0b1110) {
                    case 0b0000 -> ConnectionProtocol.HANDSHAKING;
                    case 0b0100 -> ConnectionProtocol.STATUS;
                    case 0b0110 -> ConnectionProtocol.LOGIN;
                    case 0b1110 -> ConnectionProtocol.CONFIGURATION;
                    case 0b0010 -> ConnectionProtocol.PLAY;
                    default -> throw new IllegalStateException();
                };
                long sentAt = buf.readLong();
                int size = buf.readableBytes();

                // todo: actually gather DRM info
                ProtocolInfo<?> state = createState(phase, outbound ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND, registries);

                Packet<?> packet = PacketDumping.readPacket(buf, state);
                Identifier channelId = NetworkUtil.getChannelOrNull(packet);

                if (packet instanceof ClientboundCustomQueryPacket req) {
                    loginQueryChannels.put(req.transactionId(), req.payload().id());
                } else if (packet instanceof ServerboundCustomQueryAnswerPacket res) {
                    channelId = loginQueryChannels.get(res.transactionId());
                } else if (packet instanceof GadgetDynamicRegistriesPacket dyn) {
                    var clientRegistries = new RegistryDataCollector();

                    dyn.elements().forEach(clientRegistries::appendContents);
                    clientRegistries.appendTags(dyn.tags());

                    registries = clientRegistries.collectGameRegistries(
                        ResourceProvider.EMPTY,
                        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                        false
                    );
                }

                if (packet instanceof FakeGadgetPacket fake && fake.isVirtual()) continue;

                list.add(new DumpedPacket(outbound, state.id(), packet, channelId, sentAt, size));
            }
        } catch (IOException e) {
            return new ReadPacketDump(list, e);
        }
    }

    private static ProtocolInfo<?> createState(ConnectionProtocol phase, PacketFlow side, RegistryAccess registries) {
        return switch (phase) {
            case HANDSHAKING ->
                switch (side) {
                    case SERVERBOUND -> HandshakeProtocols.SERVERBOUND;
                    case CLIENTBOUND -> throw new IllegalStateException();
                };

            case STATUS ->
                switch (side) {
                    case SERVERBOUND -> StatusProtocols.SERVERBOUND;
                    case CLIENTBOUND -> StatusProtocols.CLIENTBOUND;
                };

            case LOGIN ->
                switch (side) {
                    case SERVERBOUND -> LoginProtocols.SERVERBOUND;
                    case CLIENTBOUND -> LoginProtocols.CLIENTBOUND;
                };

            case CONFIGURATION ->
                switch (side) {
                    case SERVERBOUND -> ConfigurationProtocols.SERVERBOUND;
                    case CLIENTBOUND -> ConfigurationProtocols.CLIENTBOUND;
                };

            case PLAY ->
                switch (side) {
                    case SERVERBOUND -> GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries), () -> true);
                    case CLIENTBOUND -> GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries));
                };
        };
    }

    // I hate DataInputStream.
    private static OptionalInt readInt(InputStream is, boolean gracefulEof) throws IOException {
        int ch1 = is.read();
        int ch2 = is.read();
        int ch3 = is.read();
        int ch4 = is.read();

        if (gracefulEof && ch1 < 0)
            return OptionalInt.empty();
        else if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();

        return OptionalInt.of(((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4));
    }

    public record ReadPacketDump(List<DumpedPacket> packets, @Nullable IOException finalError) {

    }
}
