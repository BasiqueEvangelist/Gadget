package io.wispforest.gadget.testmod.client;

import io.wispforest.owo.serialization.CodecUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class GadgetTestmodClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("gadget-testmod")
                .then(literal("epic")
                    .executes(ctx -> {
                        ClientPlayNetworking.send(new EpicPacket("cringe"));
                        return 1;
                    })));
        });

        PayloadTypeRegistry.playC2S().register(EpicPacket.ID, CodecUtils.toPacketCodec(EpicPacket.ENDEC));
        ServerPlayNetworking.registerGlobalReceiver(EpicPacket.ID, (pkt, ctx) -> {
            // Do nothing.
        });
    }
}
