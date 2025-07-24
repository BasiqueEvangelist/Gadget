package io.wispforest.gadget.dump.read.handler;

import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.dump.fake.FakeGadgetPacket;
import io.wispforest.gadget.dump.read.UnwrappedPacketData;
import io.wispforest.gadget.dump.read.unwrapped.CustomPayloadUnwrappedPacket;
import io.wispforest.gadget.dump.read.unwrapped.VanillaUnwrappedPacket;
import io.wispforest.gadget.util.ReflectionUtil;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.util.Identifier;

public final class PacketHandlers {
    public static final Identifier FIRST_PHASE = Gadget.id("first");
    public static final Identifier LAST_PHASE = Gadget.id("last");


    private PacketHandlers() {

    }

    public static void init() {
        OwoSupport.init();
        FapiSupport.init();

        PacketUnwrapper.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LAST_PHASE);
        PacketUnwrapper.EVENT.register(LAST_PHASE, (packet, errSink) -> {
            if (packet.packet() instanceof FakeGadgetPacket fake) {
                return fake.unwrapGadget();
            }

            if (packet.channelId() != null) {
                return new CustomPayloadUnwrappedPacket(packet.customPayload());
            } else {
                return new VanillaUnwrappedPacket(packet.packet());
            }
        });

        SearchTextGatherer.EVENT.addPhaseOrdering(FIRST_PHASE, Event.DEFAULT_PHASE);
        SearchTextGatherer.EVENT.register(FIRST_PHASE, (packet, searchText, errSink) -> {
            searchText.append(ReflectionUtil.nameWithoutPackage(packet.packet().getClass()));

            if (packet.channelId() != null)
                searchText.append(" ").append(packet.channelId());
        });

        SearchTextGatherer.EVENT.register((packet, out, errSink) -> {
            var unwrapped = packet.get(UnwrappedPacketData.KEY).unwrap();

            if (unwrapped == null) return;

            unwrapped.gatherSearchText(out, errSink);
        });

        PlainTextPacketDumper.EVENT.register((packet, out, indent, errSink) -> {
            UnwrappedPacketData data = packet.get(UnwrappedPacketData.KEY);

            var unwrapped = data.unwrap();
            data.copyErrors(errSink);

            if (unwrapped == null) return;

            unwrapped.dumpAsPlainText(out, indent, errSink);
        });
    }
}
