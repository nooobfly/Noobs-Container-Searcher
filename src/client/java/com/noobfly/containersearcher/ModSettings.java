package com.noobfly.containersearcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("noobs-container-searcher")
		.resolve("settings.json");

	private static volatile ModSettings instance;

	public boolean itemDisplay = true;
	public List<String> rerollSelection = new ArrayList<>();
	private transient int selectionRevision;

	private ModSettings() {
	}

	public static ModSettings get() {
		ModSettings current = instance;
		if (current == null) {
			synchronized (ModSettings.class) {
				current = instance;
				if (current == null) {
					current = load();
					instance = current;
				}
			}
		}
		return current;
	}

	public static boolean itemDisplayEnabled() {
		return get().itemDisplay;
	}

	public void setItemDisplay(boolean enabled) {
		itemDisplay = enabled;
		save();
	}

	public synchronized List<String> rerollSelection() {
		return rerollSelection == null ? List.of() : List.copyOf(rerollSelection);
	}

	public synchronized int selectionRevision() {
		return selectionRevision;
	}

	public synchronized void setRerollSelection(List<String> entries) {
		rerollSelection = new ArrayList<>(entries);
		selectionRevision++;
		save();
	}

	public synchronized void removeRerollBook(String enchantment, int level) {
		if (rerollSelection != null && rerollSelection.remove(enchantment + "|" + level)) {
			selectionRevision++;
			save();
		}
	}

	private static ModSettings load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				ModSettings loaded = GSON.fromJson(reader, ModSettings.class);
				if (loaded != null) {
					if (loaded.rerollSelection == null) {
						loaded.rerollSelection = new ArrayList<>();
					}
					return loaded;
				}
			} catch (IOException | RuntimeException ignored) {
			}
		}
		return new ModSettings();
	}

	private synchronized void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException ignored) {
		}
	}
}
