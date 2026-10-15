package com.noobfly.containersearcher.spike;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

// NeoForge entry point of the universal jar. The per-version NeoForge classes are
// packed into this jar under com.noobfly.containersearcher.nf.<tag>, this just picks
// the one that matches the running game.
@Mod("noobs_container_searcher")
public final class NeoForgeBootstrap {
	public NeoForgeBootstrap(IEventBus modBus) throws ReflectiveOperationException {
		ClassLoader loader = NeoForgeBootstrap.class.getClassLoader();
		try {
			Class.forName("net.minecraft.client.Minecraft", false, loader);
		} catch (ClassNotFoundException dedicatedServer) {
			return;
		}
		String tag = McVersion.tag(loader);
		Class<?> entry = Class.forName("com.noobfly.containersearcher.nf." + tag + ".ContainerSearcherNeoForge", true, loader);
		entry.getConstructor(IEventBus.class).newInstance(modBus);
	}
}
