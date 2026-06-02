package io.wispforest.gadget.client.gui.inspector;

// TODO: REI has no unobfuscated 26.1 build yet. Restore this integration (and the
//  dependency in build.gradle + the call in ElementUtils) once REI updates.
public class REISupport {
    private REISupport() {

    }

    public static void init() {
//        ElementUtils.registerRootLister((screen, list) -> {
//            var overlay = REIRuntime.getInstance().getOverlay();
//
//            if (!REIRuntime.getInstance().isOverlayVisible()) return;
//            if (overlay.isEmpty()) return;
//            if (screen != Minecraft.getInstance().screen) return;
//
//            boolean succeeded = false;
//            for (OverlayDecider decider : ScreenRegistry.getInstance().getDeciders(screen)) {
//                InteractionResult result = decider.shouldScreenBeOverlaid(screen);
//
//                if (result == InteractionResult.FAIL) {
//                    return;
//                } else if (result == InteractionResult.SUCCESS) {
//                    succeeded = true;
//                    break;
//                }
//            }
//
//            if (!succeeded) return;
//
//            list.add(overlay.get());
//        });
    }
}
