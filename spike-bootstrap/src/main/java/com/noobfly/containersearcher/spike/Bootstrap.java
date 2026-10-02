package com.noobfly.containersearcher.spike;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.spongepowered.asm.mixin.Mixins;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Bootstrap implements PreLaunchEntrypoint, ClientModInitializer {
	private static final Logger LOGGER = Logger.getLogger("noobs-container-searcher-spike");
	private static final String REAL_ENTRYPOINT = "com.noobfly.containersearcher.ContainerSearcherClient";
	private static final String REAL_MIXIN_CONFIG = "noobs_container_searcher.client.mixins.json";

	private static final Map<String, String> IMPL_BY_VERSION = Map.of(
		"1.21.1", "/impl/mc1211.jar",
		"1.21.4", "/impl/mc1214.jar",
		"1.21.8", "/impl/mc1218.jar",
		"1.21.10", "/impl/mc1210.jar",
		"1.21.11", "/impl/mc121.jar",
		"26.1", "/impl/mc261.jar",
		"26.1.2", "/impl/mc2612.jar",
		"26.2", "/impl/mc262.jar"
	);

	private static ClassLoader targetClassLoader;

	@Override
	public void onPreLaunch() {
		try {
			String mcVersion = FabricLoader.getInstance()
				.getModContainer("minecraft")
				.orElseThrow(() -> new IllegalStateException("minecraft mod container not found"))
				.getMetadata().getVersion().getFriendlyString();
			LOGGER.info("[spike] detected minecraft version: " + mcVersion);

			String implResource = IMPL_BY_VERSION.get(mcVersion);
			if (implResource == null) {
				throw new IllegalStateException("unsupported minecraft version: " + mcVersion);
			}

			Path tempJar = Files.createTempFile("ncs-spike-impl-", ".jar");
			tempJar.toFile().deleteOnExit();
			try (InputStream in = Bootstrap.class.getResourceAsStream(implResource)) {
				if (in == null) {
					throw new IllegalStateException("embedded impl jar not found: " + implResource);
				}
				Files.copy(in, tempJar, StandardCopyOption.REPLACE_EXISTING);
			}
			LOGGER.info("[spike] extracted " + implResource + " to " + tempJar);

			FabricLauncher launcher = FabricLauncherBase.getLauncher();
			launcher.addToClassPath(tempJar);
			targetClassLoader = launcher.getTargetClassLoader();
			LOGGER.info("[spike] added impl jar to classpath");

			Mixins.addConfiguration(REAL_MIXIN_CONFIG);
			LOGGER.info("[spike] registered late mixin config: " + REAL_MIXIN_CONFIG);
		} catch (Exception exception) {
			LOGGER.log(Level.SEVERE, "[spike] bootstrap preLaunch failed", exception);
			throw new RuntimeException("Spike bootstrap failed", exception);
		}
	}

	@Override
	public void onInitializeClient() {
		try {
			if (targetClassLoader == null) {
				throw new IllegalStateException("preLaunch did not run before client init");
			}
			Class<?> realEntrypoint = Class.forName(REAL_ENTRYPOINT, true, targetClassLoader);
			Object instance = realEntrypoint.getDeclaredConstructor().newInstance();
			realEntrypoint.getMethod("onInitializeClient").invoke(instance);
			LOGGER.info("[spike] invoked real ContainerSearcherClient.onInitializeClient() successfully");
		} catch (InvocationTargetException exception) {
			LOGGER.log(Level.SEVERE, "[spike] real entrypoint threw", exception.getCause());
			throw new RuntimeException("Spike client init failed", exception.getCause());
		} catch (Exception exception) {
			LOGGER.log(Level.SEVERE, "[spike] client init failed", exception);
			throw new RuntimeException("Spike client init failed", exception);
		}
	}
}
