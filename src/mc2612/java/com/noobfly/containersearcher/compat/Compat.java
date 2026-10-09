package com.noobfly.containersearcher.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
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
		player.sendSystemMessage(message);
	}

	public static void interact(Minecraft client, Entity target, InteractionHand hand) {
		client.gameMode.interact(client.player, target, new EntityHitResult(target), hand);
	}

	public static void swing(LocalPlayer player) {
		player.swing(InteractionHand.MAIN_HAND);
	}

	public static boolean isLeftButton(int b) {
		return b == 0;
	}
}
