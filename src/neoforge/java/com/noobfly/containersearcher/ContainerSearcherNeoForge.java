package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Platform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = "noobs_container_searcher", dist = Dist.CLIENT)
public final class ContainerSearcherNeoForge {
	public ContainerSearcherNeoForge(IEventBus modBus) {
		Platform.init(modBus);
		new ContainerSearcherClient().onInitializeClient();
	}
}
