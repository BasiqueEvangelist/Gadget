package io.wispforest.gadget.client.gui;

import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.util.ThrowableUtil;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.util.UISounds;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class GuiUtil {
    private GuiUtil() {

    }

    public static void hoverBlue(LabelComponent label) {
        label.mouseEnter().subscribe(
            () -> label.text(((MutableText) label.text()).formatted(Formatting.BLUE)));

        label.mouseLeave().subscribe(
            () -> label.text(((MutableText) label.text()).formatted(Formatting.WHITE)));
    }

    public static void semiButton(LabelComponent label, Runnable onPressed) {
        hoverBlue(label);
        label.cursorStyle(CursorStyle.HAND);

        label.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

            UISounds.playButtonSound();
            onPressed.run();

            return true;
        });
    }

    public static void semiButton(LabelComponent label, BiConsumer<Double, Double> onPressed) {
        hoverBlue(label);
        label.cursorStyle(CursorStyle.HAND);

        label.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

            UISounds.playButtonSound();
            onPressed.accept(mouseX, mouseY);

            return true;
        });
    }

    public static ParentComponent root(Component component) {
        ParentComponent root = component instanceof ParentComponent parent ? parent : component.parent();

        if (root == null)
            throw new IllegalStateException();

        //noinspection DataFlowIssue
        while (root.hasParent())
            root = root.parent();

        return root;
    }

    public static DropdownComponent contextMenu(Component at, double mouseX, double mouseY) {
        FlowLayout root = (FlowLayout) root(at);
        var dropdown = Components.dropdown(Sizing.content());

        dropdown
            .positioning(Positioning.absolute((int) mouseX + at.x(), (int) mouseY + at.y()))
            .zIndex(100);

        ((ParentComponent) dropdown.children().get(0)).padding(Insets.of(3));

        dropdown.focusLost().subscribe(() -> dropdown.queue(() -> root.removeChild(dropdown)));

        root.child(dropdown);
        root.focusHandler().focus(dropdown, Component.FocusSource.MOUSE_CLICK);

        return dropdown;
    }

    private static final int INVALID_COLOR = 0xEB1D36;
    private static final int VALID_COLOR = 0x28FFBF;

    public static void textFieldVerifier(TextFieldWidget textField, Predicate<String> verifier) {
        textField.setChangedListener(
            text -> textField.setEditableColor(verifier.test(text) ? VALID_COLOR : INVALID_COLOR));
    }

    public static LabelComponent showException(Throwable e) {
        return showExceptionText(ThrowableUtil.throwableToString(e));
    }

    public static LabelComponent showExceptionText(String fullExceptionText) {
        LabelComponent label = Components.label(
            Text.literal(fullExceptionText.replace("\t", "    "))
                .formatted(Formatting.RED));
        label.horizontalSizing(Sizing.fill(99));
        return label;
    }

    public static void showMonospaceText(FlowLayout container, String all) {
        var lines = all.lines().toList();
        int maxWidth = Integer.toString(lines.size() - 1).length();

        int i = 0;
        for (String line : lines) {
            container.child(Components.label(
                Text.literal("")
                    .append(Text.literal(StringUtils.leftPad(Integer.toString(i), maxWidth) + " ")
                        .formatted(Formatting.GRAY)
                        .styled(x -> x.withFont(Gadget.id("monocraft"))))
                    .append(Text.literal(line.replace("\t", "    "))
                        .styled(x -> x.withFont(Gadget.id("monocraft")))))
                .horizontalSizing(Sizing.fill(99)));

            i++;
        }
    }

    public static FlowLayout hexDump(byte[] bytes, boolean doEllipsis) {
        FlowLayout view = Containers.verticalFlow(Sizing.content(), Sizing.content());

        List<Component> expandedChildren = new ArrayList<>();

        int index = 0;
        while (index < bytes.length) {
            StringBuilder line = new StringBuilder();

            line.append(String.format("%04x  ", index));

            int i;
            for (i = 0; i < 16 && index < bytes.length; i++) {
                short b = (short) (bytes[index] & 0xff);

                line.append(String.format("%02x ", b));
                index++;
            }

            line.append("   ".repeat(Math.max(0, 16 - i)));

            for (int j = 0; j < i; j++) {
                short b = (short) (bytes[index - i + j] & 0xff);

                if (b >= 32 && b < 127)
                    line.append((char) b);
                else
                    line.append('.');
            }

            var label = Components.label(Text.literal(line.toString())
                    .styled(x -> x.withFont(Gadget.id("monocraft"))))
                .margins(Insets.bottom(3));

            if (view.children().size() > 10 && doEllipsis)
                expandedChildren.add(label);
            else
                view.child(label);
        }

        if (expandedChildren.size() > 0) {
            LabelComponent ellipsis = Components.label(Text.literal("..."));

            semiButton(ellipsis, () -> {
                view.removeChild(ellipsis);
                view.children(expandedChildren);
            });

            view.child(ellipsis);
        }

        return view;
    }
}
