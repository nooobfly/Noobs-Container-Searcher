package com.noobfly.containersearcher.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;

public final class Keys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
		ResourceLocation.parse("noobs_container_searcher:controls")
	);

	private Keys() {
	}

	public static KeyMapping register(String translationKey, int glfwKey) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping(
			translationKey,
			InputConstants.Type.KEYSYM,
			glfwKey,
			CATEGORY
		));
	}
}
