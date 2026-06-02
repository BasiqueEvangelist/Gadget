package io.wispforest.gadget.client.gui;

import io.wispforest.gadget.util.ProgressToast;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

public class ProgressToastImpl implements Toast, ProgressToast {
    private OwoUIAdapter<FlowLayout> adapter;
    private final Minecraft client = Minecraft.getInstance();
    private boolean attached = false;

    private LabelComponent stepLabel;
    private BoxComponent progressBox;
    private long stopTime = 0;
    private LongSupplier following = null;
    private long followingTotal = 0;
    private Visibility visibility = Visibility.SHOW;

    public ProgressToastImpl(Component headText) {
        this.adapter = OwoUIAdapter.createWithoutScreen(0, 0, 160, 32, UIContainers::verticalFlow);

        var root = this.adapter.rootComponent;

        root
            .child(UIComponents.label(headText)
                .maxWidth(160)
                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                .margins(Insets.bottom(0)))
            .child(stepLabel = UIComponents.label(Component.empty())
                .maxWidth(160)
                .horizontalTextAlignment(HorizontalAlignment.CENTER))
            .child((progressBox = UIComponents.box(Sizing.fixed(0), Sizing.fixed(3)))
                .color(Color.WHITE)
                .fill(true)
                .positioning(Positioning.absolute(0, 15)))
            .surface(Surface.VANILLA_TRANSLUCENT.and(Surface.outline(0xFF5800FF)))
            .allowOverflow(true)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)
            .padding(Insets.of(10));

        this.adapter.inflateAndMount();

        // TODO: fix toasts.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, Font textRenderer, long startTime) {
        long value = following == null ? -1 : following.getAsLong();

        if (value < 0) {
            progressBox.horizontalSizing(Sizing.fixed(0));
            following = null;
        } else {
            progressBox.horizontalSizing(Sizing.fixed((int) (value * 140 / followingTotal)));
        }

        this.adapter.extractRenderState(ctx, 0, 0, client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (stopTime == -1)
            stopTime = time + 1;
        else if (stopTime == -2) {
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
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void step(Component text) {
        Minecraft.getInstance().execute(() -> {
            if (!attached) {
                Minecraft.getInstance().getToastManager().addToast(this);
                attached = true;
            }

            this.stepLabel.text(text);
            this.following = null;
        });

    }

    @Override
    public void followProgress(LongSupplier following, long total) {
        Minecraft.getInstance().execute(() -> {
            this.following = following;
            this.followingTotal = total;
        });
    }

    @Override
    public void force() {
        Minecraft.getInstance().execute(() -> {
            if (!attached) {
                Minecraft.getInstance().getToastManager().addToast(this);
                attached = true;
            }
        });
    }

    @Override
    public void finish(Component text, boolean hideImmediately) {
        Minecraft.getInstance().execute(() -> {
            this.stepLabel.text(text);
            this.following = null;
            stopTime = hideImmediately ? -2 : -1;
        });
    }

    public void oom(OutOfMemoryError oom) {
        adapter.rootComponent.clearChildren();
        client.screen.removed();
        client.screen = null;

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
