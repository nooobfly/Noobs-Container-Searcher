package com.noobfly.containersearcher.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class Keys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
		Identifier.parse("noobs_container_searcher:controls")
	);

	private Keys() {
	}

	public static KeyMapping register(String translationKey, int glfwKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
			translationKey,
			InputConstants.Type.KEYSYM,
			glfwKey,
			CATEGORY
		));
	}
}
