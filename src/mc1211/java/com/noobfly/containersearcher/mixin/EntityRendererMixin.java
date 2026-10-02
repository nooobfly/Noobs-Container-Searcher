package com.noobfly.containersearcher.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.noobfly.containersearcher.ContainerSearcherClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void noobsContainerSearcher$applyVillagerOutline(
		Entity entity,
		float entityYaw,
		float partialTicks,
		PoseStack poseStack,
		MultiBufferSource buffer,
		int packedLight,
		CallbackInfo callbackInfo
	) {
		if (entity instanceof Villager) {
			entity.setGlowingTag(ContainerSearcherClient.isVillagerHighlighted(entity));
		}
	}
}
