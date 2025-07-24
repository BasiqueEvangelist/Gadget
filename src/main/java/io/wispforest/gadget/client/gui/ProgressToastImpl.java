package io.wispforest.gadget.client.gui;

import io.wispforest.gadget.util.ProgressToast;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;

import java.util.function.LongSupplier;

public class ProgressToastImpl implements Toast, ProgressToast {
    private OwoUIAdapter<FlowLayout> adapter;
    private final MinecraftClient client = MinecraftClient.getInstance();
    private boolean attached = false;

    private LabelComponent stepLabel;
    private BoxComponent progressBox;
    private long stopTime = 0;
    private LongSupplier following = null;
    private long followingTotal = 0;

    private Visibility visibility = Visibility.HIDE;

    public ProgressToastImpl(Text headText) {
        this.adapter = OwoUIAdapter.createWithoutScreen(0, 0, 160, 32, Containers::verticalFlow);

        var root = this.adapter.rootComponent;

        root
            .child(Components.label(headText)
                .maxWidth(160)
                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                .margins(Insets.bottom(0)))
            .child(stepLabel = Components.label(Text.empty())
                .maxWidth(160)
                .horizontalTextAlignment(HorizontalAlignment.CENTER))
            .child((progressBox = Components.box(Sizing.fixed(0), Sizing.fixed(3)))
                .color(Color.WHITE)
                .fill(true)
                .positioning(Positioning.absolute(0, 15)))
            .surface(Surface.VANILLA_TRANSLUCENT.and(Surface.outline(0xFF5800FF)))
            .allowOverflow(true)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)
            .padding(Insets.of(10));

        this.adapter.inflateAndMount();
    }

    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
        long value = following == null ? -1 : following.getAsLong();

        if (value < 0) {
            progressBox.horizontalSizing(Sizing.fixed(0));
            following = null;
        } else {
            progressBox.horizontalSizing(Sizing.fixed((int) (value * 140 / followingTotal)));
        }

        this.adapter.render(context, 0, 0, client.getRenderTickCounter().getTickDelta(false));
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (stopTime == -1) {
            stopTime = time + 1;
        }  else if (stopTime == -2) {
            this.visibility = Visibility.HIDE;
            return;
        }

        if (stopTime == 0) {
            this.visibility = Visibility.SHOW;
        } else {
            this.visibility = time - stopTime > 2500 ? Visibility.HIDE : Visibility.SHOW;
        }
    }

    @Override
    public Visibility getVisibility() {
        return this.visibility;
    }

    @Override
    public void step(Text text) {
        MinecraftClient.getInstance().execute(() -> {
            if (!attached) {
                MinecraftClient.getInstance().getToastManager().add(this);
                attached = true;
            }

            this.stepLabel.text(text);
            this.following = null;
        });

    }

    @Override
    public void followProgress(LongSupplier following, long total) {
        MinecraftClient.getInstance().execute(() -> {
            this.following = following;
            this.followingTotal = total;
        });
    }

    @Override
    public void force() {
        MinecraftClient.getInstance().execute(() -> {
            if (!attached) {
                MinecraftClient.getInstance().getToastManager().add(this);
                attached = true;
            }
        });
    }

    @Override
    public void finish(Text text, boolean hideImmediately) {
        MinecraftClient.getInstance().execute(() -> {
            this.stepLabel.text(text);
            this.following = null;
            stopTime = hideImmediately ? -2 : -1;
        });
    }

    public void oom(OutOfMemoryError oom) {
        adapter.rootComponent.clearChildren();
        client.currentScreen.removed();
        client.currentScreen = null;

        following = null;
        adapter = null;
        stepLabel = null;
        progressBox = null;

        client.execute(() -> {
            client.getToastManager().clear();

            throw oom;
        });
    }
}
