package com.noobfly.containersearcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.noobfly.containersearcher.compat.Platform;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

// Anonymous usage ping, at most once every 12 hours. Sends only mod version, Minecraft version,
// loader and a random install id. Turn it off with "enabled": false in config/noobs-container-searcher/analytics.json
public final class Analytics {
	private static final String URL = "https://noobfly.net/api/mc-analytics/ping";
	private static final long INTERVAL_MS = 12L * 60L * 60L * 1000L;
	private static final Gson GSON = new Gson();

	private static final class State {
		boolean enabled = true;
		String installId;
		long lastPing;
	}

	private static Path file() {
		return Platform.configDir().resolve("noobs-container-searcher").resolve("analytics.json");
	}

	public static synchronized boolean isEnabled() {
		return load(file()).enabled;
	}

	public static synchronized void setEnabled(boolean enabled) {
		State state = load(file());
		state.enabled = enabled;
		save(file(), state);
	}

	private Analytics() {
	}

	public static void start() {
		Thread thread = new Thread(Analytics::run, "container-searcher-analytics");
		thread.setDaemon(true);
		thread.start();
	}

	private static void run() {
		try {
			Path file = file();
			State state = load(file);
			if (state.installId == null) {
				state.installId = UUID.randomUUID().toString();
				save(file, state);
			}
			if (!state.enabled || System.currentTimeMillis() - state.lastPing < INTERVAL_MS) {
				return;
			}
			Properties info = new Properties();
			try (InputStream in = Analytics.class.getResourceAsStream("analytics.properties")) {
				if (in == null) {
					return;
				}
				info.load(in);
			}
			JsonObject body = new JsonObject();
			body.addProperty("id", state.installId);
			body.addProperty("v", info.getProperty("mod_version"));
			body.addProperty("mc", info.getProperty("minecraft_version"));
			body.addProperty("l", loader());
			HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
			int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
			// 429 means the server already counted this install in the last 12 hours
			if (status == 204 || status == 429) {
				synchronized (Analytics.class) {
					State latest = load(file);
					latest.lastPing = System.currentTimeMillis();
					save(file, latest);
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static String loader() {
		try {
			Class.forName("net.fabricmc.loader.api.FabricLoader");
			return "fabric";
		} catch (ClassNotFoundException e) {
			return "neoforge";
		}
	}

	private static State load(Path file) {
		try {
			if (Files.exists(file)) {
				State loaded = GSON.fromJson(Files.readString(file), State.class);
				if (loaded != null) {
					return loaded;
				}
			}
		} catch (Exception ignored) {
		}
		return new State();
	}

	private static void save(Path file, State state) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(state));
		} catch (Exception ignored) {
		}
	}
}
