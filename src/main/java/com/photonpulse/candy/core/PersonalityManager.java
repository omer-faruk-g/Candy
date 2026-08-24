package com.photonpulse.candy.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.photonpulse.candy.Candy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PersonalityManager — TruePersonality nesnelerinin yaşam döngüsü.
 *
 * Kalıcılık: JSON dosyaları world/candy/personality_<uuid>.json
 * Bellekte cache: her aktif oyuncu için tek nesne.
 *
 * Hivemind kuralı: JSON tabanlı, NBT değil. İnsan okuyabilir.
 */
public class PersonalityManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, TruePersonality> cache = new HashMap<>();
    private static Path candyKlasoru = null;

    /**
     * Sunucu başladığında world klasörünü belirler.
     * Ör: <world>/candy/
     */
    public static void sunucuBaslat(MinecraftServer server) {
        Path worldPath = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT);
        candyKlasoru = worldPath.resolve("candy");
        try {
            Files.createDirectories(candyKlasoru);
            Candy.LOG.info("[Candy] hafıza klasörü: {}", candyKlasoru);
        } catch (IOException e) {
            Candy.LOG.error("[Candy] hafıza klasörü oluşturulamadı", e);
        }
    }

    public static void sunucuKapan() {
        // Tüm cache'i diske yaz
        for (TruePersonality tp : cache.values()) {
            kaydet(tp);
        }
        cache.clear();
        Candy.LOG.info("[Candy] tüm hafıza diske yazıldı, cache temizlendi");
    }

    /**
     * Oyuncu için TruePersonality al. Yoksa diskten yükle, o da yoksa yeni oluştur.
     */
    public static TruePersonality get(ServerPlayerEntity player) {
        return get(player.getUuid());
    }

    public static TruePersonality get(UUID uuid) {
        TruePersonality tp = cache.get(uuid);
        if (tp != null) return tp;

        // Diskten yükle
        Path dosya = dosyaYolu(uuid);
        if (Files.exists(dosya)) {
            try {
                String json = Files.readString(dosya);
                tp = GSON.fromJson(json, TruePersonality.class);
                if (tp == null) throw new IllegalStateException("JSON boş/geçersiz");
                if (tp.oyuncuId == null) tp.oyuncuId = uuid;
                // Eski dünyalarda yeni sürümün alanları JSON'da yok → Gson null bırakır.
                // Tek noktadan onar (yeni alan eklenince TruePersonality.koleksiyonlariOnar'a ekle).
                tp.koleksiyonlariOnar();
                // v1.5.4: kurulum tabelası gate'i geri geldi — force upgrade kaldırıldı
                cache.put(uuid, tp);
                Candy.LOG.info("[Candy] hafıza yüklendi: {}", uuid);
                return tp;
            } catch (Exception e) {
                Candy.LOG.error("[Candy] hafıza okunamadı, yeni oluşturuluyor: {}", uuid, e);
            }
        }

        // Yeni oyuncu
        tp = new TruePersonality(uuid);
        cache.put(uuid, tp);
        return tp;
    }

    /**
     * Diske kaydet. Genelde otomatik — her N tick'te bir çağrılır.
     */
    public static void kaydet(TruePersonality tp) {
        if (candyKlasoru == null || tp == null || tp.oyuncuId == null) return;
        Path dosya = dosyaYolu(tp.oyuncuId);
        try {
            String json = GSON.toJson(tp);
            Files.writeString(dosya, json);
        } catch (IOException e) {
            Candy.LOG.error("[Candy] hafıza yazılamadı: {}", tp.oyuncuId, e);
        }
    }

    /**
     * Cache'teki her şeyi kaydet (periyodik autosave).
     */
    public static void hepsiniKaydet() {
        for (TruePersonality tp : cache.values()) {
            kaydet(tp);
        }
    }

    private static Path dosyaYolu(UUID uuid) {
        return candyKlasoru.resolve("personality_" + uuid.toString() + ".json");
    }

    public static Map<UUID, TruePersonality> tumOyuncular() {
        return cache;
    }

    /**
     * Oyuncu bağlantıdan düştüğünde çağır — diske yaz ve cache'ten çıkar.
     * Uzun oturumlu MP sunucularında cache birikmesin.
     */
    public static void oyuncuAyrildi(UUID uuid) {
        TruePersonality tp = cache.remove(uuid);
        if (tp != null) kaydet(tp);
    }
}
