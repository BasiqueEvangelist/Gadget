package io.wispforest.gadget.dump.write;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.util.NetworkUtil;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPOutputStream;

public class PacketDumpWriter implements AutoCloseable {
    public static final int VERSION = 1;

    protected @Nullable OutputStream output;
    protected @Nullable Thread onExitThread;
    protected final Path path;
    protected final boolean flushAfterWrite;

    public PacketDumpWriter(Path path) throws IOException {
        this(
            path,
            Gadget.CONFIG.dumpSafety().createExitHook(),
            Gadget.CONFIG.dumpSafety().flushAfterWrite()
        );
    }

    public PacketDumpWriter(Path path, boolean createExitHook, boolean flushAfterWrite) throws IOException {
        this.path = path;
        this.output = new GZIPOutputStream(Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE), true);
        this.flushAfterWrite = flushAfterWrite;

        ByteBuf headerBuf = Unpooled.buffer(15);
        headerBuf.writeBytes("gadget:dump".getBytes(StandardCharsets.UTF_8));
        headerBuf.writeInt(VERSION);
        headerBuf.getBytes(headerBuf.readerIndex(), output, headerBuf.readableBytes());

        if (createExitHook) {
            onExitThread = new Thread(this::onVmStop, "Exit hook thread for " + this);
            Runtime.getRuntime().addShutdownHook(onExitThread);
        }
    }

    public Path path() {
        return path;
    }

    public void write(Packet<?> packet, ProtocolInfo<?> state) {
        if (output == null) return;

        FriendlyByteBuf buf = FriendlyByteBufs.create();

        short flags = 0;

        if (state.flow() == PacketFlow.SERVERBOUND)
            flags |= 0b00000001;

        switch (state.id()) {
            case HANDSHAKING -> { }
            case STATUS -> flags |= 0b0100;
            case LOGIN -> flags |= 0b0110;
            case CONFIGURATION -> flags |= 0b1110;
            case PLAY -> flags |= 0b0010;
        }

        try (var ignored = NetworkUtil.writeByteLength(buf)) {
            buf.writeShort(flags);

            buf.writeLong(System.currentTimeMillis());

            PacketDumping.writePacket(buf, packet, state);
        }

        synchronized (this) {
            var out = output;

            if (out == null) return;

            try {
                buf.getBytes(buf.readerIndex(), out, buf.readableBytes());

                if (flushAfterWrite)
                    out.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean isClosed() {
        return output == null;
    }

    public void flush() {
        synchronized (this) {
            var out = output;

            if (out == null) return;

            try {
                output.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void onVmStop() {
        try {
            if (output == null) return;

            output.close();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't close packet dump on VM exit", e);
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (output == null) return;

            output.close();
            output = null;

            Runtime.getRuntime().removeShutdownHook(onExitThread);
            onExitThread = null;
        }
    }

    @Override
    public String toString() {
        return "PacketDumpWriter[" + path + ']';
    }
}
