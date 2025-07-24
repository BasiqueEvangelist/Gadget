package io.wispforest.gadget.mixin.client;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Shadow @Final private MinecraftClient client;

    @Shadow protected abstract boolean processF3(int key);

    /*@Inject(method = "method_1454(ILnet/minecraft/client/gui/screen/Screen;[ZIII)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;keyPressed(III)Z", shift = At.Shift.BY, by = 2))
    private static void afterKeyPressed(int code, Screen screen, boolean[] resultHack, int key, int scancode, int modifiers, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();

        if (resultHack[0]) return;
        if (!Gadget.CONFIG.debugKeysInScreens()) return;
        if (!InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_F3)) return;

        resultHack[0] = ((KeyboardMixin)(Object) client.keyboard).processF3(key);
    }*/

    @Inject(method = "processF3", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;player:Lnet/minecraft/client/network/ClientPlayerEntity;"), cancellable = true)
    private void leaveIfPlayer(int key, CallbackInfoReturnable<Boolean> cir) {
        if (client.player == null)
            cir.setReturnValue(false);
    }

    @Inject(method = "processF3", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasPermissionLevel(I)Z"), cancellable = true)
    private void leaveOnGameModeSelection(int key, CallbackInfoReturnable<Boolean> cir) {
        if (client.player == null)
            cir.setReturnValue(false);
    }
}
