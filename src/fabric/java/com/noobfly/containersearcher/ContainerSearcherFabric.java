package com.noobfly.containersearcher;

import net.fabricmc.api.ClientModInitializer;

public final class ContainerSearcherFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		new ContainerSearcherClient().onInitializeClient();
	}
}
