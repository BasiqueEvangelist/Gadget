package io.wispforest.gadget.client.gui;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;

public class NotificationToast implements Toast {
    private final OwoUIAdapter<FlowLayout> adapter;
    private final MinecraftClient client = MinecraftClient.getInstance();

    private Visibility visibility = Visibility.HIDE;

    public NotificationToast(Text headText, Text messageText) {
        this.adapter = OwoUIAdapter.createWithoutScreen(0, 0, 160, 32, Containers::verticalFlow);

        var root = this.adapter.rootComponent;

        root
            .child(Components.label(headText)
                .maxWidth(160)
                .horizontalTextAlignment(HorizontalAlignment.CENTER))
            .surface(Surface.VANILLA_TRANSLUCENT.and(Surface.outline(0xFF5800FF)))
            .allowOverflow(true)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)
            .padding(Insets.of(5));

        if (messageText != null)
            root.child(Components.label(messageText));

        this.adapter.inflateAndMount();
    }

    public void register() {
        if (!client.isOnThread()) {
            client.execute(this::register);
            return;
        }

        client.getToastManager().add(this);
    }

    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
        this.adapter.render(context, 0, 0, client.getRenderTickCounter().getTickDelta(false));
    }

    @Override
    public void update(ToastManager manager, long time) {
        this.visibility = time > 5000 ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public Visibility getVisibility() {
        return this.visibility;
    }
}
