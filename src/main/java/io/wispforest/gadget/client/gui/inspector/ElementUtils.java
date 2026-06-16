package io.wispforest.gadget.client.gui.inspector;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class ElementUtils {
    private record ElementSupportEntry(Class<?> type, ElementSupport<?> support) {}

    private static final List<ElementSupportEntry> ELEMENT_SUPPORTS = new ArrayList<>();
    private static final List<BiConsumer<Screen, List<ContainerEventHandler>>> ROOT_LISTERS = new ArrayList<>();

    static {
        VanillaSupport.init();
        // TODO: re-enable once REI ships an unobfuscated 26.1 build.
//        if (FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) {
//            REISupport.init();
//        }
    }

    @SuppressWarnings("unchecked")
    private static int getThroughSupport(GuiEventListener element, ElementSupportGetter getter) {
        for (var pair : ELEMENT_SUPPORTS) {
            if (pair.type().isInstance(element)) {
                ElementSupport<GuiEventListener> support = (ElementSupport<GuiEventListener>) pair.support();
                int val = getter.get(support, element);

                if (val != -1)
                    return val;
            }
        }

        return -1;
    }

    public static <T extends GuiEventListener> void registerElementSupport(Class<T> klass, ElementSupport<T> support) {
        ELEMENT_SUPPORTS.add(new ElementSupportEntry(klass, support));
    }

    public static void registerRootLister(BiConsumer<Screen, List<ContainerEventHandler>> rootLister) {
        ROOT_LISTERS.add(rootLister);
    }

    public static List<ContainerEventHandler> listRootElements(Screen screen) {
        List<ContainerEventHandler> parents = new ArrayList<>();

        for (var rootLister : ROOT_LISTERS) {
            rootLister.accept(screen, parents);
        }

        return parents;
    }

    public static int x(GuiEventListener element) {
        return getThroughSupport(element, ElementSupport::getX);
    }

    public static int y(GuiEventListener element) {
        return getThroughSupport(element, ElementSupport::getY);
    }

    public static int width(GuiEventListener element) {
        return getThroughSupport(element, ElementSupport::getWidth);
    }

    public static int height(GuiEventListener element) {
        return getThroughSupport(element, ElementSupport::getHeight);
    }

    public static boolean isVisible(GuiEventListener element) {
        if (element instanceof AbstractWidget widget)
            return widget.visible;
        else
            return true;
    }

    public static boolean inBoundingBox(GuiEventListener e, int x, int y) {
        if (x(e) == -1) return false;

        return x >= x(e)
            && y >= y(e)
            && x < (x(e) + width(e))
            && y < (y(e) + height(e));
    }

    public static void collectChildren(ContainerEventHandler root, List<GuiEventListener> children) {
        for (var child : root.children()) {
            if (child instanceof ContainerEventHandler parent)
                collectChildren(parent, children);

            children.add(child);
        }
    }

    private interface ElementSupportGetter {
        <T extends GuiEventListener> int get(ElementSupport<T> support, T element);
    }
}
