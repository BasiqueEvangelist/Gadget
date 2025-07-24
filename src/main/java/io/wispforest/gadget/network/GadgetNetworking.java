package io.wispforest.gadget.network;

import io.wispforest.endec.impl.ReflectiveEndecBuilder;
import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.desc.FieldObjects;
import io.wispforest.gadget.desc.edit.PrimitiveEditType;
import io.wispforest.gadget.desc.edit.PrimitiveEditTypes;
import io.wispforest.gadget.network.packet.c2s.FieldDataRequestC2SPacket;
import io.wispforest.gadget.network.packet.c2s.FieldDataSetNbtCompoundC2SPacket;
import io.wispforest.gadget.network.packet.c2s.FieldDataSetPrimitiveC2SPacket;
import io.wispforest.gadget.network.packet.c2s.ListResourcesC2SPacket;
import io.wispforest.gadget.network.packet.c2s.OpenFieldDataScreenC2SPacket;
import io.wispforest.gadget.network.packet.c2s.ReplaceStackC2SPacket;
import io.wispforest.gadget.network.packet.c2s.RequestResourceC2SPacket;
import io.wispforest.gadget.network.packet.s2c.AnnounceS2CPacket;
import io.wispforest.gadget.network.packet.s2c.FieldDataErrorS2CPacket;
import io.wispforest.gadget.network.packet.s2c.FieldDataResponseS2CPacket;
import io.wispforest.gadget.network.packet.s2c.OpenFieldDataScreenS2CPacket;
import io.wispforest.gadget.network.packet.s2c.ResourceDataS2CPacket;
import io.wispforest.gadget.network.packet.s2c.ResourceListS2CPacket;
import io.wispforest.gadget.path.EnumMapPathStepType;
import io.wispforest.gadget.path.SimpleMapPathStepType;
import io.wispforest.gadget.util.ResourceUtil;
import io.wispforest.owo.network.OwoNetChannel;
import io.wispforest.owo.serialization.format.nbt.NbtEndec;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

public final class GadgetNetworking {
    public static final OwoNetChannel CHANNEL = OwoNetChannel.createOptional(Gadget.id("data"))
        .addEndecs(GadgetNetworking::registerEndecs);

    private GadgetNetworking() {

    }

    public static void init() {
        PrimitiveEditTypes.init();

        CHANNEL.registerServerbound(OpenFieldDataScreenC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.inspect", 4)) {
                access.player().sendMessage(Text.translatable("message.gadget.fail.permissions"), true);
                return;
            }


            Object target = packet.target().resolve(access.player().getWorld());

            if (target == null) {
                access.player().sendMessage(Text.translatable("message.gadget.fail.notfound"), true);
                return;
            }

            var data = FieldObjects.fromObject(target, Set.of());
            var fields = FieldObjects.getData(target, ReferenceSets.singleton(target), 0, -1);

            CHANNEL.serverHandle(access.player()).send(new OpenFieldDataScreenS2CPacket(
                packet.target(),
                new FieldData(data, false, true),
                fields
            ));
        });

        CHANNEL.registerServerbound(FieldDataRequestC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.inspect", 4)) {
                MutableText errText = Text.translatable("message.gadget.fail.permissions");
                CHANNEL.serverHandle(access.player()).send(packet.replyWithError(errText));
                access.player().sendMessage(errText, true);
                return;
            }

            try {
                Object target = packet.target().resolve(access.player().getWorld());

                if (target == null) {
                    MutableText errText = Text.translatable("message.gadget.fail.notfound");
                    CHANNEL.serverHandle(access.player()).send(packet.replyWithError(errText));
                    access.player().sendMessage(errText, true);
                    return;
                }

                Object[] real = packet.path().toRealPath(target);

                var fields = FieldObjects.getData(real[real.length - 1], new ReferenceOpenHashSet<>(real), packet.from(), packet.limit());

                CHANNEL.serverHandle(access.player()).send(new FieldDataResponseS2CPacket(packet.target(), packet.path(), fields));
            } catch (Exception e) {
                MutableText errText = Text.literal(e.toString());
                CHANNEL.serverHandle(access.player()).send(packet.replyWithError(errText));
                Gadget.LOGGER.error("Encountered error while gathering field data for {}.{}", packet.target(), packet.path(), e);
                access.player().sendMessage(errText, true);
            }
        });

        CHANNEL.registerServerbound(FieldDataSetPrimitiveC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.inspect", 4)) {
                access.player().sendMessage(Text.translatable("message.gadget.fail.permissions"), true);
                return;
            }


            Object target = packet.target().resolve(access.player().getWorld());

            if (target == null) {
                access.player().sendMessage(Text.translatable("message.gadget.fail.notfound"), true);
                return;
            }

            packet.path().set(target, packet.data().toObject());
        });

        CHANNEL.registerServerbound(FieldDataSetNbtCompoundC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.inspect", 4)) {
                access.player().sendMessage(Text.translatable("message.gadget.fail.permissions"), true);
                return;
            }

            Object target = packet.target().resolve(access.player().getWorld());

            if (target == null) {
                access.player().sendMessage(Text.translatable("message.gadget.fail.notfound"), true);
                return;
            }

            packet.path().set(target, packet.data());
        });

        CHANNEL.registerServerbound(ReplaceStackC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.replaceStack", 4)) {
                access.player().sendMessage(Text.translatable("message.gadget.fail.permissions"));
                return;
            }

            ScreenHandler screenHandler = access.player().currentScreenHandler;

            if (screenHandler == null)
                return;

            screenHandler.slots.get(packet.slotId()).setStack(packet.stack());
        });

        CHANNEL.registerServerbound(ListResourcesC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.requestServerData", 4)) {
                access.player().sendMessage(Text.translatable("message.gadget.fail.permissions"), true);
                return;
            }

            var resources =
                ResourceUtil.collectAllResources(access.runtime().getResourceManager());
            var network = new HashMap<Identifier, Integer>();

            for (var entry : resources.entrySet())
                network.put(entry.getKey(), entry.getValue().size());

            CHANNEL.serverHandle(access.player()).send(new ResourceListS2CPacket(network));
        });

        CHANNEL.registerServerbound(RequestResourceC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.requestServerData", 4)) {
                access.player().sendMessage(Text.translatable("message.gadget.fail.permissions"), true);
                return;
            }

            var resources = access.runtime().getResourceManager().getAllResources(packet.id());

            try {
                CHANNEL.serverHandle(access.player()).send(
                    new ResourceDataS2CPacket(packet.id(), resources.get(packet.index()).getInputStream().readAllBytes()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        CHANNEL.registerClientboundDeferred(OpenFieldDataScreenS2CPacket.class);
        CHANNEL.registerClientboundDeferred(FieldDataResponseS2CPacket.class);
        CHANNEL.registerClientboundDeferred(FieldDataErrorS2CPacket.class);
        CHANNEL.registerClientboundDeferred(AnnounceS2CPacket.class);
        CHANNEL.registerClientboundDeferred(ResourceListS2CPacket.class);
        CHANNEL.registerClientboundDeferred(ResourceDataS2CPacket.class);
    }

    @SuppressWarnings("unchecked")
    public static void registerEndecs(ReflectiveEndecBuilder builder) {
        builder.register(EnumMapPathStepType.ENDEC, EnumMapPathStepType.class);
        builder.register(SimpleMapPathStepType.ENDEC, SimpleMapPathStepType.class);
        builder.register(PrimitiveEditType.ENDEC, (Class<PrimitiveEditType<?>>)(Object) PrimitiveEditType.class);
        builder.register(NbtEndec.COMPOUND, NbtCompound.class);
    }
}
