package com.noobfly.containersearcher.compat;

import com.noobfly.containersearcher.ContainerSearcherClient;
import com.noobfly.containersearcher.SearchController;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

// Fabric half of Platform, the NeoForge one is in src/neoforge
public final class Platform {
	private static final SearchController.CommandMessenger<FabricClientCommandSource> MESSENGER =
		new SearchController.CommandMessenger<>() {
			@Override
			public void feedback(FabricClientCommandSource source, Component message) {
				source.sendFeedback(message);
			}

			@Override
			public void error(FabricClientCommandSource source, Component message) {
				source.sendError(message);
			}
		};

	private Platform() {
	}

	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	public static void registerCommands(SearchController controller) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			controller.registerCommands(dispatcher, MESSENGER));
	}

	public static void registerEvents(ContainerSearcherClient client) {
		com.noobfly.containersearcher.Analytics.start();
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> client.onUseBlock(world, hitResult));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
			client.onUseEntity(player, world, entity));
		ClientTickEvents.END_CLIENT_TICK.register(client::onClientTick);
		ScreenEvents.AFTER_INIT.register((minecraft, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenEvents.remove(screen).register(removedScreen -> client.onContainerScreenClosed());
			}
		});
	}
}
