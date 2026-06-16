package io.wispforest.gadget.client.gui;

import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

public class NotificationToast implements Toast {
    private final OwoUIAdapter<FlowLayout> adapter;
    private final Minecraft client = Minecraft.getInstance();
    private Visibility visibility = Visibility.SHOW;

    public NotificationToast(Component headText, Component messageText) {
        this.adapter = OwoUIAdapter.createWithoutScreen(0, 0, 160, 32, UIContainers::verticalFlow);

        var root = this.adapter.rootComponent;

        root
            .child(UIComponents.label(headText)
                .maxWidth(160)
                .horizontalTextAlignment(HorizontalAlignment.CENTER))
            .surface(Surface.VANILLA_TRANSLUCENT.and(Surface.outline(0xFF5800FF)))
            .allowOverflow(true)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)
            .padding(Insets.of(5));

        if (messageText != null)
            root.child(UIComponents.label(messageText));

        this.adapter.inflateAndMount();

        // TODO: fix toasts.
    }

    public void register() {
        if (!client.isSameThread()) {
            client.execute(this::register);
            return;
        }

        client.gui.toastManager().addToast(this);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, Font textRenderer, long startTime) {
        this.adapter.extractRenderState(ctx, 0, 0, client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    @Override
    public void update(ToastManager manager, long time) {
        this.visibility = time > 5000 ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }
}
