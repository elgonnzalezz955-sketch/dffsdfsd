package com.donutmarket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Saved to .minecraft/config/donutmarket/config.json */
public class MarketConfig {
    private boolean onlyOnDonut = true;
    /** Group 1 = number, group 2 = optional K/M/B/T suffix. Edit if DonutSMP's lore format differs. */
    private String priceRegex = "(?i)price\\W*\\$?\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*([kmbt])?";
    private String sellerRegex = "(?i)seller\\W+(\\w+)";
    private List<String> watchlist = new ArrayList<>(List.of("minecraft:diamond_block"));
    private List<String> tracked = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MarketConfig instance;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutmarket").resolve("config.json");
    }

    public static synchronized MarketConfig get() {
        if (instance == null) {
            try {
                Path p = path();
                if (Files.exists(p)) instance = GSON.fromJson(Files.readString(p), MarketConfig.class);
            } catch (Exception e) {
                System.err.println("[DonutMarket] Could not read config: " + e);
            }
            if (instance == null) { instance = new MarketConfig(); instance.save(); }
        }
        return instance;
    }

    public synchronized void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(this));
        } catch (Exception e) {
            System.err.println("[DonutMarket] Could not save config: " + e);
        }
    }

    public synchronized boolean onlyOnDonut() { return onlyOnDonut; }
    public synchronized String priceRegex() { return priceRegex; }
    public synchronized String sellerRegex() { return sellerRegex; }
    public synchronized List<String> watchlist() { return new ArrayList<>(watchlist); }
    public synchronized List<String> tracked() { return new ArrayList<>(tracked); }
    public synchronized boolean isWatched(String id) { return watchlist.contains(id); }

    public synchronized void addTracked(String id) {
        if (!tracked.contains(id)) { tracked.add(id); save(); }
    }

    public synchronized boolean toggleWatch(String id) {
        boolean now;
        if (watchlist.contains(id)) { watchlist.remove(id); now = false; }
        else { watchlist.add(id); now = true; }
        save();
        return now;
    }
}
