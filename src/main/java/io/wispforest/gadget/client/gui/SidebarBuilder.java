package io.wispforest.gadget.client.gui;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.util.UISounds;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class SidebarBuilder {
    private final FlowLayout layout = Containers.verticalFlow(Sizing.content(), Sizing.content());

    {
        layout
            .positioning(Positioning.absolute(0, 0))
            .padding(Insets.of(2));
    }

    public FlowLayout layout() {
        return layout;
    }

    public void button(String translationKeyBase, OnPressHandler handler) {
        button(Text.translatable(translationKeyBase), Text.translatable(translationKeyBase + ".tooltip"), handler);
    }

    public void button(Text icon, @Nullable Text tooltip, OnPressHandler handler) {
        Button button = new Button(icon, tooltip);

        button.mouseDown().subscribe((mouseX, mouseY, mouseButton) -> {
            if (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

            UISounds.playInteractionSound();

            handler.onPress((int) mouseX, (int) mouseY);

            return true;
        });

        layout.child(button);
    }

    public static class Button extends FlowLayout {
        private final LabelComponent iconLabel;

        public Button(Text icon, Text tooltip) {
            super(Sizing.fixed(16), Sizing.fixed(16), Algorithm.VERTICAL);

            child((iconLabel = Components.label(icon))
                .verticalTextAlignment(VerticalAlignment.CENTER)
                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                .positioning(Positioning.absolute(5, 4))
                .cursorStyle(CursorStyle.HAND)
            );
            cursorStyle(CursorStyle.HAND);

            if (tooltip != null)
                tooltip(tooltip);

            mouseEnter().subscribe(
                () -> surface(Surface.flat(0x80ffffff)));

            mouseLeave().subscribe(
                () -> surface(Surface.BLANK));
        }

        public Button icon(Text icon) {
            iconLabel.text(icon);

            return this;
        }
    }

    public interface OnPressHandler {
        void onPress(int mouseX, int mouseY);
    }
}
