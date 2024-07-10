package io.wispforest.gadget.client.gui.inspector;

import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.gui.widgets.WidgetWithBounds;
import me.shedaniel.rei.api.client.registry.screen.OverlayDecider;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

public class REISupport {
    private REISupport() {

    }

    public static void init() {
        ElementUtils.registerElementSupport(WidgetWithBounds.class, ElementSupport.fromLambda(
            w -> w.getBounds().x,
            w -> w.getBounds().y,
            w -> w.getBounds().width,
            w -> w.getBounds().height
        ));

        ElementUtils.registerRootLister((screen, list) -> {
            var overlay = REIRuntime.getInstance().getOverlay();

            if (!REIRuntime.getInstance().isOverlayVisible()) return;
            if (overlay.isEmpty()) return;
            if (screen != Minecraft.getInstance().screen) return;

            boolean succeeded = false;
            for (OverlayDecider decider : ScreenRegistry.getInstance().getDeciders(screen)) {
                InteractionResult result = decider.shouldScreenBeOverlaid(screen);

                if (result == InteractionResult.FAIL) {
                    return;
                } else if (result == InteractionResult.SUCCESS) {
                    succeeded = true;
                    break;
                }
            }

            if (!succeeded) return;

            list.add(overlay.get());
        });
    }
}
