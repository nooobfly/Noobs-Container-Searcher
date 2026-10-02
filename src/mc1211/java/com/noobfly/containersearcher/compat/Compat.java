package com.noobfly.containersearcher.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

public final class Compat {
	private Compat() {
	}

	public static Screen screen(Minecraft client) {
		return client.screen;
	}

	public static void setScreen(Minecraft client, Screen screen) {
		client.setScreen(screen);
	}

	public static void message(LocalPlayer player, Component message) {
		player.displayClientMessage(message, false);
	}

	public static void interact(Minecraft client, Entity target, InteractionHand hand) {
		InteractionResult result = client.gameMode.interactAt(
			client.player,
			target,
			new EntityHitResult(target),
			hand
		);
		if (!result.consumesAction()) {
			client.gameMode.interact(client.player, target, hand);
		}
	}
}
