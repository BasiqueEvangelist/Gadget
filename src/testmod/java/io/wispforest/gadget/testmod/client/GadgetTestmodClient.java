package io.wispforest.gadget.testmod.client;

import io.wispforest.owo.serialization.CodecUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class GadgetTestmodClient implements ClientModInitializer {
    public static final FunnyItem FUNNY_ITEM = new FunnyItem();

    @Override
    public void onInitializeClient() {
        Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath("gadget-testmod", "funny"), FUNNY_ITEM);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("gadget-testmod")
                .then(literal("epic")
                    .executes(ctx -> {
                        ClientPlayNetworking.send(new EpicPacket("cringe"));
                        return 1;
                    })));
        });

        PayloadTypeRegistry.serverboundPlay().register(EpicPacket.TYPE, CodecUtils.toPacketCodec(EpicPacket.ENDEC));
        ServerPlayNetworking.registerGlobalReceiver(EpicPacket.TYPE, (pkt, ctx) -> {
            // Do nothing.
        });
    }
}
