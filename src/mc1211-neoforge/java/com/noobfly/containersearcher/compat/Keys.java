package com.noobfly.containersearcher.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.KeyMapping;

public final class Keys {
	private Keys() {
	}

	public static KeyMapping register(String translationKey, int glfwKey) {
		KeyMapping mapping = new KeyMapping(
			translationKey,
			InputConstants.Type.KEYSYM,
			glfwKey,
			"key.category.noobs_container_searcher.controls"
		);
		Platform.modBus().addListener((RegisterKeyMappingsEvent event) -> event.register(mapping));
		return mapping;
	}
}
