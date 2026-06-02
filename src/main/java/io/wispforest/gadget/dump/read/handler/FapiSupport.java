package io.wispforest.gadget.dump.read.handler;

import io.wispforest.gadget.dump.read.unwrapped.LinesUnwrappedPacket;
import io.wispforest.gadget.util.ErrorSink;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.fabric.impl.networking.CommonRegisterPayload;
import net.fabricmc.fabric.impl.networking.CommonVersionPayload;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.fabricmc.fabric.impl.recipe.ingredient.ServerboundCustomIngredientPayload;
import net.fabricmc.fabric.impl.recipe.ingredient.ClientboundCustomIngredientPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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
            if (!(packet.customPayload() instanceof ClientboundCustomIngredientPayload payload)) return null;

            return new CustomIngredientS2CPacket(payload);
        });

        PacketUnwrapper.EVENT.register((packet, errSink) -> {
            if (!(packet.customPayload() instanceof ServerboundCustomIngredientPayload payload)) return null;

            return new CustomIngredientC2SPacket(payload);
        });
    }

    public record MinecraftRegisterPacket(RegistrationPayload payload) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Component> out, ErrorSink errSink) {
            Component header = !(payload.type() == RegistrationPayload.UNREGISTER)
                ? Component.literal("+ ")
                .withStyle(ChatFormatting.GREEN)
                : Component.literal("- ")
                .withStyle(ChatFormatting.RED);

            for (Identifier channel : payload.channels()) {
                out.accept(
                    Component.literal("")
                        .append(header)
                        .append(Component.literal(channel.toString())
                            .withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    public record CommonVersionPacket(CommonVersionPayload payload) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Component> out, ErrorSink errSink) {
            out.accept(
                Component.literal("versions")
                    .append(Component.literal(" = " + Arrays.stream(payload.versions())
                            .mapToObj(Integer::toString)
                            .collect(Collectors.joining(", ")))
                        .withStyle(ChatFormatting.GRAY)));
        }
    }

    public record CommonRegisterPacket(CommonRegisterPayload payload) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Component> out, ErrorSink errSink) {
            out.accept(Component.literal("version")
                .append(Component.literal(" = " + payload.version())
                    .withStyle(ChatFormatting.GRAY)));

            out.accept(Component.literal("phase")
                .append(Component.literal(" = " + payload.protocol())
                    .withStyle(ChatFormatting.GRAY)));

            for (Identifier channel : payload.channels()) {
                out.accept(
                    Component.literal("+ ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(channel.toString())
                            .withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    public record CustomIngredientS2CPacket(ClientboundCustomIngredientPayload payload) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Component> out, ErrorSink errSink) {
            out.accept(Component.literal("protocolVersion")
                .append(Component.literal(" = " + payload.protocolVersion())
                    .withStyle(ChatFormatting.GRAY)));
        }
    }

    public record CustomIngredientC2SPacket(ServerboundCustomIngredientPayload payload) implements LinesUnwrappedPacket {
        @Override
        public void render(Consumer<Component> out, ErrorSink errSink) {
            out.accept(Component.literal("protocolVersion")
                .append(Component.literal(" = " + payload.protocolVersion())
                    .withStyle(ChatFormatting.GRAY)));

            for (Identifier serializer : payload.registeredSerializers()) {
                out.accept(
                    Component.literal("+ ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(serializer.toString())
                            .withStyle(ChatFormatting.GRAY)));
            }
        }
    }
}
