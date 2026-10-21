package com.noobfly.containersearcher.mixin;

import com.noobfly.containersearcher.ContainerChanges;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// AbstractContainerScreen has no mouseScrolled of its own on this version, so catch the wheel before it reaches the screen
@Mixin(MouseHandler.class)
public abstract class MouseScrollMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private double xpos;

	@Shadow
	private double ypos;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void noobsContainerSearcher$scrollChanges(long window, double xOffset, double yOffset, CallbackInfo callbackInfo) {
		if (window != minecraft.getWindow().getWindow() || !(minecraft.screen instanceof AbstractContainerScreen<?>)) {
			return;
		}
		double mouseX = xpos * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
		double mouseY = ypos * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
		if (ContainerChanges.scroll(mouseX, mouseY, Math.signum(yOffset))) {
			callbackInfo.cancel();
		}
	}
}
