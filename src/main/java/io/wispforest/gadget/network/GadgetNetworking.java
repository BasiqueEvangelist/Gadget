package io.wispforest.gadget.network;

import io.wispforest.endec.impl.ReflectiveEndecBuilder;
import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.desc.FieldObjects;
import io.wispforest.gadget.desc.edit.PrimitiveEditType;
import io.wispforest.gadget.desc.edit.PrimitiveEditTypes;
import io.wispforest.gadget.network.packet.c2s.*;
import io.wispforest.gadget.network.packet.s2c.*;
import io.wispforest.gadget.path.EnumMapPathStepType;
import io.wispforest.gadget.path.SimpleMapPathStepType;
import io.wispforest.gadget.util.ResourceUtil;
import io.wispforest.owo.network.OwoNetChannel;
import io.wispforest.owo.serialization.format.nbt.NbtEndec;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class GadgetNetworking {
    public static final OwoNetChannel CHANNEL = OwoNetChannel.createOptional(Gadget.id("data"))
        .addEndecs(GadgetNetworking::registerEndecs);

    private GadgetNetworking() {

    }

    public static void init() {
        PrimitiveEditTypes.init();

        CHANNEL.registerServerbound(OpenFieldDataScreenC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.inspect", 4)) {
                access.player().sendOverlayMessage(Component.translatable("message.gadget.fail.permissions"));
                return;
            }


            Object target = packet.target().resolve(access.player().level());

            if (target == null) {
                access.player().sendOverlayMessage(Component.translatable("message.gadget.fail.notfound"));
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
                MutableComponent errText = Component.translatable("message.gadget.fail.permissions");
                CHANNEL.serverHandle(access.player()).send(packet.replyWithError(errText));
                access.player().sendOverlayMessage(errText);
                return;
            }

            try {
                Object target = packet.target().resolve(access.player().level());

                if (target == null) {
                    MutableComponent errText = Component.translatable("message.gadget.fail.notfound");
                    CHANNEL.serverHandle(access.player()).send(packet.replyWithError(errText));
                    access.player().sendOverlayMessage(errText);
                    return;
                }

                Object[] real = packet.path().toRealPath(target);

                var fields = FieldObjects.getData(real[real.length - 1], new ReferenceOpenHashSet<>(real), packet.from(), packet.limit());

                CHANNEL.serverHandle(access.player()).send(new FieldDataResponseS2CPacket(packet.target(), packet.path(), fields));
            } catch (Exception e) {
                MutableComponent errText = Component.literal(e.toString());
                CHANNEL.serverHandle(access.player()).send(packet.replyWithError(errText));
                Gadget.LOGGER.error("Encountered error while gathering field data for {}.{}", packet.target(), packet.path(), e);
                access.player().sendOverlayMessage(errText);
            }
        });

        CHANNEL.registerServerbound(FieldDataSetPrimitiveC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.inspect", 4)) {
                access.player().sendOverlayMessage(Component.translatable("message.gadget.fail.permissions"));
                return;
            }


            Object target = packet.target().resolve(access.player().level());

            if (target == null) {
                access.player().sendOverlayMessage(Component.translatable("message.gadget.fail.notfound"));
                return;
            }

            packet.path().set(target, packet.data().toObject());
        });

        CHANNEL.registerServerbound(FieldDataSetNbtCompoundC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.inspect", 4)) {
                access.player().sendOverlayMessage(Component.translatable("message.gadget.fail.permissions"));
                return;
            }

            Object target = packet.target().resolve(access.player().level());

            if (target == null) {
                access.player().sendOverlayMessage(Component.translatable("message.gadget.fail.notfound"));
                return;
            }

            packet.path().set(target, packet.data());
        });

        CHANNEL.registerServerbound(ReplaceStackC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.replaceStack", 4)) {
                access.player().sendSystemMessage(Component.translatable("message.gadget.fail.permissions"));
                return;
            }

            AbstractContainerMenu screenHandler = access.player().containerMenu;

            if (screenHandler == null)
                return;

            screenHandler.slots.get(packet.slotId()).setByPlayer(packet.stack());
        });

        CHANNEL.registerServerbound(ListResourcesC2SPacket.class, (packet, access) -> {
            if (!Permissions.check(access.player(), "gadget.requestServerData", 4)) {
                access.player().sendOverlayMessage(Component.translatable("message.gadget.fail.permissions"));
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
                access.player().sendOverlayMessage(Component.translatable("message.gadget.fail.permissions"));
                return;
            }

            var resources = access.runtime().getResourceManager().getResourceStack(packet.id());

            try {
                CHANNEL.serverHandle(access.player()).send(
                    new ResourceDataS2CPacket(packet.id(), resources.get(packet.index()).open().readAllBytes()));
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
        builder.register(NbtEndec.COMPOUND, CompoundTag.class);
    }
}
