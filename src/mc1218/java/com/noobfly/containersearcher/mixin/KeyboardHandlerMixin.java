package com.noobfly.containersearcher.mixin;

import com.noobfly.containersearcher.ContainerSearcherClient;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void noobsContainerSearcher$stopRerollOnKeyboardInput(
		long window,
		int key,
		int scancode,
		int action,
		int mods,
		CallbackInfo callbackInfo
	) {
		ContainerSearcherClient.onKeyboardInput(key, scancode, action, mods);
	}
}
