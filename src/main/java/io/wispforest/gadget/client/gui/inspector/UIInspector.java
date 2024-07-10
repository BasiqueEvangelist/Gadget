package io.wispforest.gadget.client.gui.inspector;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.util.ReflectionUtil;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

public class UIInspector {
    private static final Map<Screen, UIInspector> ALL = new WeakHashMap<>();

    private boolean enabled;
    private boolean onlyHovered = true;

    private int childAtOffset = 0;

    private UIInspector() {

    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean enabled() {
        return Gadget.CONFIG.uiInspector() && enabled;
    }

    public static UIInspector get(Screen screen) {
        return ALL.get(screen);
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            UIInspector inspector = ALL.computeIfAbsent(screen, unused -> new UIInspector());

            ScreenEvents.afterRender(screen).register(inspector::drawInspector);
            ScreenKeyboardEvents.afterKeyPress(screen).register(inspector::keyPressed);
            ScreenEvents.remove(screen).register(ALL::remove);
            ScreenMouseEvents.allowMouseScroll(screen).register(inspector::mouseScrolled);
        });
    }

    private boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!enabled()) return true;
        if (!Screen.hasShiftDown()) return true;

        childAtOffset += verticalAmount;
        if (childAtOffset < 0) childAtOffset = 0;

        return false;
    }

    public static void dumpWidgetTree(Screen screen) {
        StringBuilder sb = new StringBuilder();
        for (var parent : ElementUtils.listRootElements(screen))
            writeWidgetTree(parent, 0, sb);
        Gadget.LOGGER.info("Widget tree for screen:\n{}", sb);
    }

    private static void writeWidgetTree(GuiEventListener element, int indent, StringBuilder sb) {
        sb.append(" ".repeat(indent));
        sb.append(ReflectionUtil.nameWithoutPackage(element.getClass()));
        sb.append(" ");
        sb.append(ElementUtils.x(element));
        sb.append(",");
        sb.append(ElementUtils.y(element));
        sb.append(" (");
        sb.append(ElementUtils.width(element));
        sb.append(",");
        sb.append(ElementUtils.height(element));
        sb.append(")\n");

        if (element instanceof ContainerEventHandler parent) {
            for (var child : parent.children()) {
                writeWidgetTree(child, indent + 1, sb);
            }
        }
    }

    public void keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                enabled = !enabled;
            } else if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) {
                onlyHovered = !onlyHovered;
            }
        }
    }

    // Mostly copied (and modified) from Drawer$DebugDrawer#drawInspector
    public void drawInspector(Screen screen, GuiGraphics ctxIn, int mouseX, int mouseY, float tickDelta) {
        if (!enabled()) return;

        OwoUIDrawContext ctx = OwoUIDrawContext.of(ctxIn);

        RenderSystem.disableDepthTest();
        var client = Minecraft.getInstance();
        var textRenderer = client.font;

        var parents = ElementUtils.listRootElements(screen);
        var children = new ArrayList<GuiEventListener>();

        for (var parent : parents) {
            ElementUtils.collectChildren(parent, children);
        }

        if (onlyHovered) {
            children.removeIf(el -> !ElementUtils.inBoundingBox(el, mouseX, mouseY));

            childAtOffset = Math.min(childAtOffset, children.size() - 1);

            if (!children.isEmpty()) {
                var selected = children.get(childAtOffset);
                children.clear();
                children.add(selected);
            }
        }

        if (children.isEmpty())
            childAtOffset = 0;

        for (var child : children) {
            if (!ElementUtils.isVisible(child)) continue;
            if (ElementUtils.x(child) == -1) continue;

            ctx.pose().translate(0, 0, 1000);

            ctx.drawRectOutline(ElementUtils.x(child), ElementUtils.y(child), ElementUtils.width(child), ElementUtils.height(child), 0xFF3AB0FF);

            if (onlyHovered) {

                int inspectorX = ElementUtils.x(child) + 1;
                int inspectorY = ElementUtils.y(child) + ElementUtils.height(child) + 1;
                int inspectorHeight = textRenderer.lineHeight * 2 + 4;

                if (inspectorY > client.getWindow().getGuiScaledHeight() - inspectorHeight) {
                    inspectorY -= ElementUtils.height(child) + inspectorHeight + 1;
                    if (inspectorY < 0) inspectorY = 1;
                }

                final var nameText = Component.nullToEmpty(ReflectionUtil.nameWithoutPackage(child.getClass()));
                final var descriptor = Component.literal(ElementUtils.x(child) + "," + ElementUtils.y(child) + " (" + ElementUtils.width(child) + "," + ElementUtils.height(child) + ")");

                int width = Math.max(textRenderer.width(nameText), textRenderer.width(descriptor));
                ctx.fill(inspectorX, inspectorY, inspectorX + width + 3, inspectorY + inspectorHeight, 0xA7000000);
                ctx.drawRectOutline(inspectorX, inspectorY, width + 3, inspectorHeight, 0xA7000000);

                ctx.drawString(textRenderer, nameText, inspectorX + 2, inspectorY + 2, 0xFFFFFF, false);
                ctx.drawString(textRenderer, descriptor, inspectorX + 2, inspectorY + textRenderer.lineHeight + 2, 0xFFFFFF, false);
            }
            ctx.pose().translate(0, 0, -1000);
        }

        RenderSystem.enableDepthTest();
    }
}
