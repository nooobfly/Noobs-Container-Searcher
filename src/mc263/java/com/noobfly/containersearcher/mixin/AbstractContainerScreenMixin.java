package com.noobfly.containersearcher.mixin;

import com.noobfly.containersearcher.SearchController;
import com.noobfly.containersearcher.compat.Gfx;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void noobsContainerSearcher$renderMatchingSlot(
		GuiGraphicsExtractor graphics,
		Slot slot,
		int mouseX,
		int mouseY,
		CallbackInfo callbackInfo
	) {
		SearchController.renderMatchingSlot(new Gfx(graphics), slot);
	}
}
