package io.wispforest.gadget.decompile.handle;

import io.wispforest.gadget.util.ThrowableUtil;
import net.minecraft.network.chat.Component;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

public class GadgetFernflowerLogger extends IFernflowerLogger {
    private final QuiltflowerHandlerImpl handler;

    public GadgetFernflowerLogger(QuiltflowerHandlerImpl handler) {
        this.handler = handler;
    }

    @Override
    public void writeMessage(String message, Severity severity) {
        if (severity == Severity.INFO) {
            handler.logConsumer.accept(Component.translatable("text.gadget.quiltflower_log.info", message));
        } else if (severity == Severity.WARN) {
            handler.logConsumer.accept(Component.translatable("text.gadget.quiltflower_log.warn", message));
        } else if (severity == Severity.ERROR) {
            handler.logConsumer.accept(Component.translatable("text.gadget.quiltflower_log.error", message));
        }
    }

    @Override
    public void writeMessage(String message, Severity severity, Throwable t) {
        String fullExceptionText = ThrowableUtil.throwableToString(t);

        if (severity == Severity.INFO) {
            handler.logConsumer.accept(
                Component.translatable("text.gadget.quiltflower_log.info.with_error", message, fullExceptionText));
        } else if (severity == Severity.WARN) {
            handler.logConsumer.accept(
                Component.translatable("text.gadget.quiltflower_log.warn.with_error", message, fullExceptionText));
        } else if (severity == Severity.ERROR) {
            handler.logConsumer.accept(
                Component.translatable("text.gadget.quiltflower_log.error.with_error", message, fullExceptionText));
        }
    }
}
