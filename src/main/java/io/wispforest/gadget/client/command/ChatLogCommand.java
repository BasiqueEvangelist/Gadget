package io.wispforest.gadget.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wispforest.gadget.client.log.ChatLogAppender;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class ChatLogCommand {
    private ChatLogCommand() {

    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("gadget")
                .then(literal("chat-log")
                    .then(literal("enable")
                        .then(argument("logger-name", StringArgumentType.greedyString())
                            .suggests(ChatLogCommand::activeLoggerNames)
                            .executes(ChatLogCommand::enable)))
                    .then(literal("disable")
                        .then(argument("logger-name", StringArgumentType.greedyString())
                            .suggests(ChatLogCommand::enabledLoggerNames)
                            .executes(ChatLogCommand::disable)))));
    }

    private static CompletableFuture<Suggestions> activeLoggerNames(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        Set<String> allLoggerNames = new HashSet<>();

        for (var logger : LogManager.getContext(false).getLoggerRegistry().getLoggers()) {
            allLoggerNames.add(logger.getName());
        }

        return SharedSuggestionProvider.suggest(allLoggerNames, builder);
    }

    private static CompletableFuture<Suggestions> enabledLoggerNames(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ChatLogAppender.INSTANCE.allowedLoggerNames(), builder);
    }

    private static int enable(CommandContext<FabricClientCommandSource> ctx) {
        String loggerName = StringArgumentType.getString(ctx, "logger-name");

        ChatLogAppender.INSTANCE.allowedLoggerNames().add(loggerName);

        ctx.getSource().sendFeedback(Component.translatable("commands.gadget.chat-log.enable.success", loggerName));

        return 0;
    }

    private static int disable(CommandContext<FabricClientCommandSource> ctx) {
        String loggerName = StringArgumentType.getString(ctx, "logger-name");

        ChatLogAppender.INSTANCE.allowedLoggerNames().remove(loggerName);

        ctx.getSource().sendFeedback(Component.translatable("commands.gadget.chat-log.disable.success", loggerName));

        return 0;
    }
}
