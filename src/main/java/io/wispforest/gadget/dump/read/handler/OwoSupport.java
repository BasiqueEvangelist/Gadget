 package io.wispforest.gadget.dump.read.handler;

 import io.wispforest.gadget.dump.read.unwrapped.FieldsUnwrappedPacket;
 import io.wispforest.gadget.dump.read.unwrapped.LinesUnwrappedPacket;
 import io.wispforest.gadget.mixin.owo.HandshakeResponseAccessor;
 import io.wispforest.gadget.mixin.owo.MessagePayloadAccessor;
 import io.wispforest.gadget.mixin.owo.ParticleSystemAccessor;
 import io.wispforest.gadget.mixin.owo.ParticleSystemInstanceAccessor;
 import io.wispforest.gadget.mixin.owo.ParticleSystemPayloadAccessor;
 import io.wispforest.gadget.util.ErrorSink;
 import io.wispforest.owo.network.OwoHandshake;
 import io.wispforest.owo.particles.systems.ParticleSystemController;
 import net.minecraft.text.Text;
 import net.minecraft.util.Formatting;
 import net.minecraft.util.Identifier;
 import net.minecraft.util.math.Vec3d;
 import org.jetbrains.annotations.Nullable;

 import java.lang.reflect.InvocationTargetException;
 import java.lang.reflect.Method;
 import java.util.Map;
 import java.util.OptionalInt;
 import java.util.function.Consumer;

@SuppressWarnings("UnstableApiUsage")
public final class OwoSupport {
    private OwoSupport() {

    }

    public static void init() {
        PacketUnwrapper.EVENT.register((packet, errSink) -> {
            if (!(packet.customPayload() instanceof MessagePayloadAccessor payload)) return null;

            return new ChannelPacket(payload.getMessage());
        });

        Method instanceGetter;
        try {
            var klass = Class.forName("io.wispforest.owo.particles.systems.ParticleSystemController$ParticleSystemPayload");
            instanceGetter = klass.getMethod("instance");
            instanceGetter.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        PacketUnwrapper.EVENT.register((packet, errSink) -> {
            if (!(packet.customPayload() instanceof ParticleSystemPayloadAccessor payload)) return null;

            ParticleSystemController controller = ParticleSystemController.REGISTERED_CONTROLLERS.get(payload.getId().id());
            Vec3d pos = payload.getPos();
            ParticleSystemInstanceAccessor<?> instance;

            try {
                instance = (ParticleSystemInstanceAccessor<?>) instanceGetter.invoke(payload);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }

            int index = ((ParticleSystemAccessor) instance.getSystem()).getIndex();

            return new ParticleSystemPacket(controller, index, pos, instance.getData());
        });

        PacketUnwrapper.EVENT.register((packet, errSink) -> {
            if (!(packet.customPayload() instanceof OwoHandshake.HandshakeRequest payload)) return null;

            return new HandshakeRequest(payload);
        });

        PacketUnwrapper.EVENT.register((packet, errSink) -> {
            if (!(packet.customPayload() instanceof HandshakeResponseAccessor payload)) return null;

            return new HandshakeResponse(payload.getRequiredChannels(), payload.getRequiredControllers(), payload.getOptionalChannels());
        });
    }

    private static void drawHandshakeMap(Map<Identifier, Integer> data, Text prefix, Consumer<Text> out) {
        for (var entry : data.entrySet()) {
            out.accept(Text.literal("")
                .append(prefix)
                .append(Text.literal(entry.getKey().toString())
                    .formatted(Formatting.WHITE))
                .append(Text.literal(" = " + entry.getValue())
                    .formatted(Formatting.GRAY)));
        }
    }

    public record ParticleSystemPacket(ParticleSystemController controller, int systemId, Vec3d pos, Object data) implements FieldsUnwrappedPacket {
        @Override
        public Text headText() {
            return Text.translatable("text.gadget.particle_system", systemId, (int) pos.x, (int) pos.y, (int) pos.z);
        }

        @Override
        public @Nullable Object rawFieldsObject() {
            return data;
        }

        @Override
        public OptionalInt packetId() {
            return OptionalInt.of(systemId);
        }
    }

    public record ChannelPacket(Object packetData) implements FieldsUnwrappedPacket {
        @Override
        public @Nullable Object rawFieldsObject() {
            return packetData;
        }
    }

    public record HandshakeRequest(OwoHandshake.HandshakeRequest req) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Text> out, ErrorSink errSink) {
            drawHandshakeMap(req.optionalChannels(), Text.literal("o ").formatted(Formatting.AQUA), out);
        }
    }

    public record HandshakeResponse(Map<Identifier, Integer> requiredChannels,
                                    Map<Identifier, Integer> requiredControllers,
                                    Map<Identifier, Integer> optionalChannels) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Text> out, ErrorSink errSink) {
            drawHandshakeMap(requiredChannels, Text.literal("r ").formatted(Formatting.RED), out);
            drawHandshakeMap(requiredControllers, Text.literal("p ").formatted(Formatting.GREEN), out);
            drawHandshakeMap(optionalChannels, Text.literal("o ").formatted(Formatting.AQUA), out);
        }
    }
}
