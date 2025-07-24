package io.wispforest.gadget.mixin.owo;

import io.wispforest.gadget.client.gui.ComponentEventCounter;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OwoUIAdapter.class)
public abstract class OwoUIAdapterMixin implements Drawable, Element {
    /*@Inject(method = {"render", "method_25394"}, at = @At("HEAD"))
    private void reset(DrawContext ctx, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ComponentEventCounter.reset();
    }*/

    @Inject(method = {"mouseClicked", "mouseReleased", "mouseScrolled", "mouseDragged", "keyPressed", "charTyped"}, at = @At("HEAD"))
    private void reset(CallbackInfoReturnable<?> cir) {
        ComponentEventCounter.reset();
    }

    @Inject(method = {"mouseClicked", "mouseReleased", "mouseScrolled", "mouseDragged", "keyPressed", "charTyped"}, at = @At("RETURN"))
    private void tally(CallbackInfoReturnable<Boolean> cir) {
        ComponentEventCounter.tally();
    }

    /*@Inject(method = {"render", "method_25394"}, at = @At("RETURN"))
    private void tally(DrawContext ctx, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ComponentEventCounter.tally();
    }*/
}
