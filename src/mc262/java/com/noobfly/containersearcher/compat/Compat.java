package com.noobfly.containersearcher.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
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
		return client.gui.screen();
	}

	// asks before opening a link in the browser
	public static void openLink(Screen parent, String url) {
		ConfirmLinkScreen.confirmLinkNow(parent, java.net.URI.create(url));
	}

	public static void setScreen(Minecraft client, Screen screen) {
		client.gui.setScreen(screen);
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

	// 26.4 snapshots dropped the client biome manager (and the hashed seed with it)
	public static Object biomeManager(net.minecraft.client.multiplayer.ClientLevel level) {
		return level.getBiomeManager();
	}

	public static void createFireworks(net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z,
		java.util.List<net.minecraft.world.item.component.FireworkExplosion> explosions) {
		level.createFireworks(x, y, z, 0.0D, 0.0D, 0.0D, explosions);
	}
}
