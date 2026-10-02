package com.noobfly.containersearcher.mixin;

import com.noobfly.containersearcher.ContainerSearcherClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void noobsContainerSearcher$applyVillagerOutline(
		Entity entity,
		EntityRenderState renderState,
		float partialTick,
		CallbackInfo callbackInfo
	) {
		if (entity instanceof Villager) {
			entity.setGlowingTag(ContainerSearcherClient.isVillagerHighlighted(entity));
		}
	}
}
