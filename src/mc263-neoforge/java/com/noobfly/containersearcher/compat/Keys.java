package com.noobfly.containersearcher.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class Keys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
		Identifier.parse("noobs_container_searcher:controls")
	);

	private Keys() {
	}

	public static KeyMapping register(String translationKey, int glfwKey) {
		KeyMapping mapping = new KeyMapping(
			translationKey,
			InputConstants.Type.KEYBOARD,
			glfwKey,
			CATEGORY
		);
		Platform.modBus().addListener((RegisterKeyMappingsEvent event) -> event.register(mapping));
		return mapping;
	}
}
