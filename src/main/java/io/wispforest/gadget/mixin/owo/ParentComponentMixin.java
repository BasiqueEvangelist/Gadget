package io.wispforest.gadget.mixin.owo;

import io.wispforest.gadget.client.gui.BasedVerticalFlowLayout;
import io.wispforest.owo.ui.core.AnimatableProperty;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.util.ScissorStack;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ParentComponent.class, remap = false)
public interface ParentComponentMixin extends Component {
    @Shadow boolean allowOverflow();

    @Shadow AnimatableProperty<Insets> padding();

    @Inject(method = "update", at = @At("HEAD"))
    private void mald1(float delta, int mouseX, int mouseY, CallbackInfo ci) {
        if (!this.allowOverflow()) {
            var padding = this.padding().get();
            ScissorStack.push(this.x() + padding.left(), this.y() + padding.top(), this.width() - padding.horizontal(), this.height() - padding.vertical(), (MatrixStack) null);
        }
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lio/wispforest/owo/ui/core/Component;update(FII)V"))
    private void mald2(Component instance, float delta, int mouseX, int mouseY) {
        if (this instanceof BasedVerticalFlowLayout) {
            if (!ScissorStack.isVisible(instance, null))
                return;
        }

        instance.update(delta, mouseX, mouseY);
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void mald3(float delta, int mouseX, int mouseY, CallbackInfo ci) {
        if (!this.allowOverflow()) {
            ScissorStack.pop();
        }
    }
}
