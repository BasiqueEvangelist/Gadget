package io.wispforest.gadget.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.client.gui.GadgetScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(
        method = "createPauseMenu",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V")
    )
    private void gadget$addMenuButton(CallbackInfo ci, @Local LinearLayout iconButtonRow) {
        if (!Gadget.CONFIG.menuButtonEnabled()) return;

        iconButtonRow.addChild(Button.builder(
                Component.translatable("text.gadget.menu_button"),
                button -> Minecraft.getInstance().setScreenAndShow(new GadgetScreen((PauseScreen) (Object) this)))
            .width(20)
            .build());
    }
}
