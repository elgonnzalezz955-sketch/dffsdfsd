package com.donutmarket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passively reads the chest-style GUI that /ah opens. Nothing is clicked or sent to the server:
 * we only look at the item stacks and tooltip lore the client already received.
 * Each visible item with a "Price" lore line is a listing. Listings are de-duplicated, and
 * for every item that has at least one NEW listing in a scan we record the cheapest unit price.
 */
public final class AhScanner {
    private static final AhScanner I = new AhScanner();
    public static AhScanner get() { return I; }

    private static final long SEEN_TTL = 72L * 3_600_000L;

    private final Gson gson = new Gson();
    private final Map<String, Long> seen = new HashMap<>();
    private volatile String status = "Open /ah on DonutSMP to start collecting prices";
    private long lastScan = 0, lastSave = System.currentTimeMillis();
    private boolean debugNext = false;
    private boolean dirty = false;
    private Pattern pricePat, sellerPat;
    private String patSource = "";
    private int totalPoints = 0;

    public String statusLine() { return status + "  |  " + totalPoints + " price points this session"; }
    public void requestDebug() { debugNext = true; }

    private Path seenPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutmarket").resolve("seen.json");
    }

    public void load() {
        try {
            Path p = seenPath();
            if (!Files.exists(p)) return;
            Map<String, Long> m = gson.fromJson(Files.readString(p), new TypeToken<Map<String, Long>>() {}.getType());
            if (m != null) seen.putAll(m);
        } catch (Exception e) { System.err.println("[DonutMarket] seen.json: " + e); }
    }

    public void save() {
        try {
            long now = System.currentTimeMillis();
            seen.values().removeIf(t -> now - t > SEEN_TTL);
            Path p = seenPath();
            Files.createDirectories(p.getParent());
            Files.writeString(p, gson.toJson(seen));
            dirty = false;
        } catch (Exception e) { System.err.println("[DonutMarket] seen.json: " + e); }
    }

    private static boolean onDonut(MinecraftClient mc) {
        ServerInfo si = mc.getCurrentServerEntry();
        return si != null && si.address != null && si.address.toLowerCase(Locale.ROOT).contains("donut");
    }

    private record Listing(String id, int count, double price, String seller) {}

    /** Called every client tick; does real work at most ~2x per second and only while a chest GUI is open. */
    public void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (dirty && now - lastSave > 120_000) { save(); PriceStore.get().save(); lastSave = now; }
        if (mc.player == null || !(mc.currentScreen instanceof GenericContainerScreen gs)) return;

        MarketConfig cfg = MarketConfig.get();
        if (cfg.onlyOnDonut() && !onDonut(mc)) { status = "Paused (not connected to DonutSMP)"; return; }
        if (now - lastScan < 500) return;
        lastScan = now;

        try {
            if (!cfg.priceRegex().equals(patSource)) {
                pricePat = Pattern.compile(cfg.priceRegex());
                sellerPat = Pattern.compile(cfg.sellerRegex());
                patSource = cfg.priceRegex();
            }
            GenericContainerScreenHandler h = gs.getScreenHandler();
            int slots = h.getRows() * 9;
            List<Listing> found = new ArrayList<>();
            int nonEmpty = 0, debugged = 0;

            for (int i = 0; i < slots && i < h.slots.size(); i++) {
                ItemStack st = h.slots.get(i).getStack();
                if (st.isEmpty()) continue;
                nonEmpty++;
                LoreComponent lore = st.get(DataComponentTypes.LORE);
                List<String> lines = new ArrayList<>();
                if (lore != null) for (Text t : lore.lines()) lines.add(t.getString());

                if (debugNext && debugged < 4 && !lines.isEmpty()) {
                    debugged++;
                    mc.player.sendMessage(Text.literal("[DonutMarket] slot " + i + ": " + st.getName().getString()
                            + " x" + st.getCount() + " | " + String.join(" / ", lines)), false);
                }

                double price = Double.NaN; String seller = "";
                for (String line : lines) {
                    Matcher m = pricePat.matcher(line);
                    if (Double.isNaN(price) && m.find()) price = parse(m.group(1), m.groupCount() >= 2 ? m.group(2) : null);
                    Matcher sm = sellerPat.matcher(line);
                    if (seller.isEmpty() && sm.find()) seller = sm.group(1);
                }
                if (Double.isNaN(price) || price <= 0) continue;
                String id = Registries.ITEM.getId(st.getItem()).toString();
                found.add(new Listing(id, Math.max(1, st.getCount()), price, seller));
            }
            if (debugNext) {
                debugNext = false;
                if (debugged == 0) mc.player.sendMessage(Text.literal("[DonutMarket] No items with lore in this GUI. Open /ah first."), false);
            }

            if (found.isEmpty()) {
                status = nonEmpty > 0 ? "GUI open, but no price lines found - run /dmarket debug, then open /ah"
                                      : "Waiting for listings...";
                return;
            }

            Map<String, Double> floor = new HashMap<>();
            Set<String> hasNew = new HashSet<>();
            for (Listing l : found) {
                floor.merge(l.id, l.price / l.count, Math::min);
                String key = l.id + "|" + l.count + "|" + (long) l.price + "|" + l.seller;
                Long first = seen.get(key);
                if (first == null || now - first > SEEN_TTL) { seen.put(key, now); hasNew.add(l.id); dirty = true; }
            }
            for (String id : hasNew) {
                PriceStore.get().add(id, floor.get(id), now);
                totalPoints++;
            }
            status = "Scanned " + found.size() + " listings, " + hasNew.size() + " items updated";
        } catch (Throwable t) {
            status = "Scan error: " + t.getClass().getSimpleName();
        }
    }

    private static double parse(String num, String suffix) {
        try {
            double v = Double.parseDouble(num.replace(",", ""));
            if (suffix != null) switch (suffix.toLowerCase(Locale.ROOT)) {
                case "k" -> v *= 1e3; case "m" -> v *= 1e6; case "b" -> v *= 1e9; case "t" -> v *= 1e12;
                default -> {}
            }
            return v;
        } catch (NumberFormatException e) { return Double.NaN; }
    }
}
