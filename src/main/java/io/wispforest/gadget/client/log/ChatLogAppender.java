package io.wispforest.gadget.client.log;

import io.wispforest.gadget.mixin.client.ChatComponentAccessor;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class ChatLogAppender extends AbstractAppender {
    public static final ChatLogAppender INSTANCE = new ChatLogAppender();
    public static final GuiMessageTag MESSAGE_TAG = new GuiMessageTag(
        0x0096FF,
        null,
        Component.translatable("chat.tag.gadget.loud_logging"),
        "Why are you seeing this?"
    );
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final Layout<String> layout = PatternLayout.newBuilder()
        .withPattern("[%d{HH:mm:ss} %level] (%logger{1}) %msg{nolookups}")
        .build();
    private final Set<String> allowedLoggerNames = new HashSet<>();

    protected ChatLogAppender() {
        super("gadget:chat_log", null, null, false, null);
    }

    public static void init() {
        INSTANCE.start();
        ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(INSTANCE);
    }

    public Set<String> allowedLoggerNames() {
        return allowedLoggerNames;
    }

    private String shortenLoggerName(String orig) {
        var split = orig.split("\\.");

        for (int j = 0; j < split.length; j++) {
            String part = split[j];

            for (int i = 0; i < part.length(); ++i) {
                if (!Character.isLetterOrDigit(part.charAt(i))) {
                    return orig;
                }
            }

            if (j < split.length - 1) {
                split[j] = String.valueOf(part.charAt(0));
            }
        }

        return String.join(".", split);
    }

    private Component fromLogEvent(LogEvent event) {
        var formatDate = TIME_FORMATTER.format(
            Instant.ofEpochMilli(event.getInstant().getEpochMillisecond())
                .atZone(ZoneId.systemDefault())
        );

        return Component.translatable(
            "text.gadget.log_entry",
            formatDate,
            Component.translatable("text.gadget.log_level." + event.getLevel().getStandardLevel().name().toLowerCase(Locale.ROOT)),
            shortenLoggerName(event.getLoggerName()),
            event.getMessage().getFormattedMessage()
        );
    }

    @Override
    public void append(LogEvent event) {
        if (!allowedLoggerNames.contains(event.getLoggerName()))
            return;

        var text = fromLogEvent(event);
        var client = Minecraft.getInstance();

        client.execute(() -> {
            if (client.player == null) return;

            ((ChatComponentAccessor) client.gui.hud.getChat())
                .callAddMessage(
                    text,
                    null,
                    GuiMessageSource.SYSTEM_CLIENT,
                        MESSAGE_TAG
                );
        });
    }
}
