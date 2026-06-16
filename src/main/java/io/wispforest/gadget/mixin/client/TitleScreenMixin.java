package io.wispforest.gadget.mixin.client;

import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.client.gui.GadgetScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    private int getHorizontalPosition(int currentButton, int numberOfButtons, int buttonWidth) {
        throw new AssertionError();
    }

    @ModifyArg(
        method = "init",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;getHorizontalPosition(III)I"),
        index = 1
    )
    private int gadget$reserveMenuButtonSlot(int numberOfButtons) {
        return Gadget.CONFIG.menuButtonEnabled() ? numberOfButtons + 1 : numberOfButtons;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gadget$addMenuButton(CallbackInfo ci) {
        if (!Gadget.CONFIG.menuButtonEnabled()) return;

        int count = 0;
        int rowY = 0;
        for (var child : this.children()) {
            if (child instanceof SpriteIconButton icon) {
                count++;
                rowY = icon.getY();
            }
        }
        if (count == 0) return;

        this.addRenderableWidget(Button.builder(
                Component.translatable("text.gadget.menu_button"),
                button -> Minecraft.getInstance().setScreenAndShow(new GadgetScreen((TitleScreen) (Object) this)))
            .bounds(this.getHorizontalPosition(count + 1, count + 1, 20), rowY, 20, 20)
            .build());
    }
}
