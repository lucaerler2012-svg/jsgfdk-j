package com.example.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("crystal-auto.json");

    private static ModConfig instance;

    // General settings
    public boolean enabled = true;
    public int keybind = 0; // Key code, 0 = unset

    // Crystal Mode settings
    public boolean crystalModeEnabled = true;
    public double crystalReach = 4.5; // Block reach distance
    public int crystalDelayTicks = 2; // Delay between actions in ticks
    public boolean crystalAutoAttack = true; // Auto left-click to explode

    // Obsidian Mode settings
    public boolean obsidianModeEnabled = true;
    public double obsidianReach = 4.5;
    public int obsidianDelayTicks = 3;
    public boolean obsidianAutoPlaceCrystal = true;
    public boolean obsidianAutoAttack = true;

    // Advanced settings
    public double maxTurnSpeed = 30.0; // Max degrees per tick for smooth rotation
    public boolean requireLineOfSight = true;
    public boolean onlyInCreative = false;
    public boolean showHudStatus = true;

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ModConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                    ModConfig config = GSON.fromJson(reader, ModConfig.class);
                    if (config != null) {
                        return config;
                    }
                }
            }
        } catch (IOException e) {
            TemplateMod.LOGGER.error("Failed to load config", e);
        }
        return new ModConfig();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            TemplateMod.LOGGER.error("Failed to save config", e);
        }
    }

    public void resetToDefaults() {
        this.enabled = true;
        this.keybind = 0;
        this.crystalModeEnabled = true;
        this.crystalReach = 4.5;
        this.crystalDelayTicks = 2;
        this.crystalAutoAttack = true;
        this.obsidianModeEnabled = true;
        this.obsidianReach = 4.5;
        this.obsidianDelayTicks = 3;
        this.obsidianAutoPlaceCrystal = true;
        this.obsidianAutoAttack = true;
        this.maxTurnSpeed = 30.0;
        this.requireLineOfSight = true;
        this.onlyInCreative = false;
        this.showHudStatus = true;
        save();
    }
}