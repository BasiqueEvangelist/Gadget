package io.wispforest.gadget.dump.read.handler;

import io.wispforest.gadget.dump.read.unwrapped.LinesUnwrappedPacket;
import io.wispforest.gadget.util.ErrorSink;
import net.fabricmc.fabric.impl.networking.CommonRegisterPayload;
import net.fabricmc.fabric.impl.networking.CommonVersionPayload;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.fabricmc.fabric.impl.recipe.ingredient.CustomIngredientPayloadC2S;
import net.fabricmc.fabric.impl.recipe.ingredient.CustomIngredientPayloadS2C;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public final class FapiSupport {
    private FapiSupport() {

    }

    public static void init() {
        PacketUnwrapper.EVENT.register((packet, errSink) -> {
            if (!(packet.customPayload() instanceof RegistrationPayload payload)) return null;

            return new MinecraftRegisterPacket(payload);
        });

        PacketUnwrapper.EVENT.register((packet, errSink) -> {
            if (!(packet.customPayload() instanceof CommonVersionPayload payload)) return null;

            return new CommonVersionPacket(payload);
        });

        PacketUnwrapper.EVENT.register((packet, errSink) -> {
            if (!(packet.customPayload() instanceof CommonRegisterPayload payload)) return null;

            return new CommonRegisterPacket(payload);
        });

        PacketUnwrapper.EVENT.register((packet, errSink) -> {
            if (!(packet.customPayload() instanceof CustomIngredientPayloadS2C payload)) return null;

            return new CustomIngredientS2CPacket(payload);
        });

        PacketUnwrapper.EVENT.register((packet, errSink) -> {
            if (!(packet.customPayload() instanceof CustomIngredientPayloadC2S payload)) return null;

            return new CustomIngredientC2SPacket(payload);
        });
    }

    public record MinecraftRegisterPacket(RegistrationPayload payload) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Text> out, ErrorSink errSink) {
            Text header = !(payload.id() == RegistrationPayload.UNREGISTER)
                ? Text.literal("+ ")
                .formatted(Formatting.GREEN)
                : Text.literal("- ")
                .formatted(Formatting.RED);

            for (Identifier channel : payload.channels()) {
                out.accept(
                    Text.literal("")
                        .append(header)
                        .append(Text.literal(channel.toString())
                            .formatted(Formatting.GRAY)));
            }
        }
    }

    public record CommonVersionPacket(CommonVersionPayload payload) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Text> out, ErrorSink errSink) {
            out.accept(
                Text.literal("versions")
                    .append(Text.literal(" = " + Arrays.stream(payload.versions())
                            .mapToObj(Integer::toString)
                            .collect(Collectors.joining(", ")))
                        .formatted(Formatting.GRAY)));
        }
    }

    public record CommonRegisterPacket(CommonRegisterPayload payload) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Text> out, ErrorSink errSink) {
            out.accept(Text.literal("version")
                .append(Text.literal(" = " + payload.version())
                    .formatted(Formatting.GRAY)));

            out.accept(Text.literal("phase")
                .append(Text.literal(" = " + payload.phase())
                    .formatted(Formatting.GRAY)));

            for (Identifier channel : payload.channels()) {
                out.accept(
                    Text.literal("+ ")
                        .formatted(Formatting.GREEN)
                        .append(Text.literal(channel.toString())
                            .formatted(Formatting.GRAY)));
            }
        }
    }

    public record CustomIngredientS2CPacket(CustomIngredientPayloadS2C payload) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Text> out, ErrorSink errSink) {
            out.accept(Text.literal("protocolVersion")
                .append(Text.literal(" = " + payload.protocolVersion())
                    .formatted(Formatting.GRAY)));
        }
    }

    public record CustomIngredientC2SPacket(CustomIngredientPayloadC2S payload) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Text> out, ErrorSink errSink) {
            out.accept(Text.literal("protocolVersion")
                .append(Text.literal(" = " + payload.protocolVersion())
                    .formatted(Formatting.GRAY)));

            for (Identifier serializer : payload.registeredSerializers()) {
                out.accept(
                    Text.literal("+ ")
                        .formatted(Formatting.GREEN)
                        .append(Text.literal(serializer.toString())
                            .formatted(Formatting.GRAY)));
            }
        }
    }
}
