package io.wispforest.gadget.dump.fake;

import io.wispforest.gadget.dump.read.unwrapped.UnwrappedPacket;
import io.wispforest.gadget.util.ThrowableUtil;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.PacketByteBuf;

public record GadgetWriteErrorPacket(int packetId, String exceptionText) implements FakeGadgetPacket {
    public static final int ID = -1;

    public static GadgetWriteErrorPacket fromThrowable(int packetId, Throwable t) {
        return new GadgetWriteErrorPacket(packetId, ThrowableUtil.throwableToString(t));
    }

    public static GadgetWriteErrorPacket read(PacketByteBuf buf, NetworkState<?> state) {
        return new GadgetWriteErrorPacket(buf.readVarInt(), buf.readString());
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void writeToDump(PacketByteBuf buf, NetworkState<?> state) {
        buf.writeVarInt(packetId);
        buf.writeString(exceptionText);
    }

    @Override
    public UnwrappedPacket unwrapGadget() {
        // Don't render anything.
        return UnwrappedPacket.NULL;
    }
}
