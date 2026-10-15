package com.noobfly.containersearcher.spike;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads the game version from the version.json that sits in the Minecraft jar,
// so it works without touching any loader API (those differ between NeoForge builds).
final class McVersion {
	// minecraft version -> package tag the NeoForge classes were relocated to
	static final Map<String, String> TAGS = Map.of(
		"1.21.1", "v1_21_1",
		"1.21.4", "v1_21_4",
		"1.21.8", "v1_21_8",
		"1.21.10", "v1_21_10",
		"1.21.11", "v1_21_11",
		"26.1", "v26_1",
		"26.1.2", "v26_1_2",
		"26.2", "v26_2",
		"26.3", "v26_3"
	);

	private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

	private McVersion() {
	}

	static String tag(ClassLoader loader) {
		String id = detect(loader);
		String tag = TAGS.get(id);
		if (tag == null) {
			throw new IllegalStateException("Noob's Container Searcher does not support Minecraft " + id);
		}
		return tag;
	}

	private static String detect(ClassLoader loader) {
		try (InputStream in = loader.getResourceAsStream("version.json")) {
			if (in == null) {
				throw new IllegalStateException("version.json not found, can't tell the Minecraft version");
			}
			Matcher matcher = ID.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			if (!matcher.find()) {
				throw new IllegalStateException("no id in version.json");
			}
			return matcher.group(1);
		} catch (java.io.IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
