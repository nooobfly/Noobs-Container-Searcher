package com.noobfly.containersearcher.mixin;

import com.noobfly.containersearcher.TargetArrows;
import com.noobfly.containersearcher.compat.Gfx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class GuiMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void noobsContainerSearcher$targetArrows(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
		TargetArrows.render(new Gfx(graphics));
	}
}
