package io.wispforest.gadget.client.command;

import com.google.common.io.MoreFiles;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.wispforest.gadget.mappings.MappingsManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ReloadMappingsCommand {
    private ReloadMappingsCommand() {

    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("gadget")
                .then(literal("reload_mappings")
                    .executes(ReloadMappingsCommand::reload)));
    }

    private static int reload(CommandContext<FabricClientCommandSource> ctx) {
        Path mappingsDir = FabricLoader.getInstance().getGameDir().resolve("gadget").resolve("mappings");

        if (Files.exists(mappingsDir)) {
            try {
                MoreFiles.deleteRecursively(mappingsDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        MappingsManager.reloadMappings();

        ctx.getSource().sendFeedback(Text.translatable("commands.gadget.reload_mappings.success"));

        return 1;
    }
}
