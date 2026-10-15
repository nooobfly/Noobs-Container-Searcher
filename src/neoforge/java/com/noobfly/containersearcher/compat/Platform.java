package com.noobfly.containersearcher.compat;

import com.noobfly.containersearcher.ContainerSearcherClient;
import com.noobfly.containersearcher.SearchController;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.nio.file.Path;

// NeoForge half of Platform, the Fabric one is in src/fabric
public final class Platform {
	private static final SearchController.CommandMessenger<net.minecraft.commands.CommandSourceStack> MESSENGER =
		new SearchController.CommandMessenger<>() {
			@Override
			public void feedback(net.minecraft.commands.CommandSourceStack source, Component message) {
				Compat.message(net.minecraft.client.Minecraft.getInstance().player, message);
			}

			@Override
			public void error(net.minecraft.commands.CommandSourceStack source, Component message) {
				Compat.message(
					net.minecraft.client.Minecraft.getInstance().player,
					message.copy().withStyle(net.minecraft.ChatFormatting.RED)
				);
			}
		};

	private static IEventBus modBus;

	private Platform() {
	}

	// has to be called first thing from the @Mod constructor
	public static void init(IEventBus bus) {
		modBus = bus;
	}

	// mod bus, key mappings get registered on it
	public static IEventBus modBus() {
		return modBus;
	}

	public static Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	public static void registerCommands(SearchController controller) {
		NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
			controller.registerCommands(event.getDispatcher(), MESSENGER));
	}

	public static void registerEvents(ContainerSearcherClient client) {
		NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
			if (!event.getLevel().isClientSide()) {
				return;
			}
			InteractionResult result = client.onUseBlock(event.getLevel(), event.getHitVec());
			if (result != InteractionResult.PASS) {
				event.setCancellationResult(result);
				event.setCanceled(true);
			}
		});
		NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
			if (!event.getLevel().isClientSide()) {
				return;
			}
			InteractionResult result = client.onUseEntity(event.getEntity(), event.getLevel(), event.getTarget());
			if (result != InteractionResult.PASS) {
				event.setCancellationResult(result);
				event.setCanceled(true);
			}
		});
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
			client.onClientTick(net.minecraft.client.Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing event) -> {
			if (event.getScreen() instanceof AbstractContainerScreen<?>) {
				client.onContainerScreenClosed();
			}
		});
	}
}
