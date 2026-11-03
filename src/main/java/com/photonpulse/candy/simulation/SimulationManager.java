package com.photonpulse.candy.simulation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.JsonDepo;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SimulationManager — SimulatedPersonality yaşam döngüsü.
 * PersonalityManager'ın birebir eşdeğeri ama ayrı dosyada:
 *   world/candy/simulation_<uuid>.json
 */
public class SimulationManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, SimulatedPersonality> cache = new HashMap<>();
    private static Path klasor = null;

    public static void sunucuBaslat(MinecraftServer server) {
        Path worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        klasor = worldPath.resolve("candy");
        try {
            Files.createDirectories(klasor);
        } catch (IOException e) {
            Candy.LOG.error("[Candy] simulation klasörü oluşturulamadı", e);
        }
    }

    public static void sunucuKapan() {
        for (SimulatedPersonality sp : cache.values()) kaydet(sp);
        cache.clear();
    }

    public static SimulatedPersonality get(UUID uuid) {
        SimulatedPersonality sp = cache.get(uuid);
        if (sp != null) return sp;

        if (klasor != null) {
            Path dosya = klasor.resolve("simulation_" + uuid + ".json");
            if (Files.exists(dosya)) {
                try {
                    sp = GSON.fromJson(Files.readString(dosya), SimulatedPersonality.class);
                    if (sp.oyuncuId == null) sp.oyuncuId = uuid;
                    cache.put(uuid, sp);
                    return sp;
                } catch (Exception e) {
                    Candy.LOG.error("[Candy] simulation okunamadı, sıfırlanıyor: {}", uuid, e);
                }
            }
        }
        sp = new SimulatedPersonality(uuid);
        cache.put(uuid, sp);
        return sp;
    }

    public static void kaydet(SimulatedPersonality sp) {
        if (klasor == null || sp == null || sp.oyuncuId == null) return;
        Path dosya = klasor.resolve("simulation_" + sp.oyuncuId + ".json");
        JsonDepo.yaz(dosya, GSON.toJson(sp));   // v8: atomik + arka plan
    }

    public static void hepsiniKaydet() {
        for (SimulatedPersonality sp : cache.values()) kaydet(sp);
    }

    public static void oyuncuAyrildi(UUID uuid) {
        SimulatedPersonality sp = cache.remove(uuid);
        if (sp != null) kaydet(sp);
    }
}
