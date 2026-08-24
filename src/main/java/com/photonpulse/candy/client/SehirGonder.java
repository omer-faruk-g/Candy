package com.photonpulse.candy.client;

import com.google.gson.JsonParser;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.SehirPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * SehirGonder (v3.1, client) — IP-tabanlı geolocation ile şehri bulur, sunucuya yollar.
 *
 * ipapi.co/json (HTTPS, free tier ~1000 istek/gün/IP) sorgulanır, "city" alanı okunur.
 * Ağ hatası/limit aşımı → sessizce vazgeçilir (mekanik atlanır, oyun etkilenmez).
 * Join'de bir kez çalışır. Sonuç hiçbir yere kaydedilmez, sadece RAM'de tutulur.
 */
@Environment(EnvType.CLIENT)
public class SehirGonder {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4)).build();

    // Şehir bu OYUN OTURUMUNDA (client açık kaldığı sürece) bir kez çekilip cache'lenir
    // (API rate-limit'ini boşa harcamamak için). Ama HER join'de sunucuya GÖNDERİLİR —
    // aksi halde 2. sunucuya bağlanan oyuncuya mekanik hiç çalışmazdı.
    private static String sehirCache = null;
    private static boolean cekmeDenendi = false;

    public static void register() {
        try {
            PayloadTypeRegistry.playC2S().register(SehirPayload.ID, SehirPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Sehir C2S zaten kayıtlı (client): {}", t.getMessage());
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (sehirCache != null) {
                // Zaten biliniyor — direkt gönder, yeni ağ isteği atma
                client.execute(() -> gonder(sehirCache));
                return;
            }
            if (cekmeDenendi) return; // bir kez denendi, başarısız oldu — tekrar deneme

            Thread t = new Thread(() -> {
                try {
                    String sehir = sehirCek();
                    cekmeDenendi = true;
                    if (sehir == null || sehir.isBlank()) return;
                    sehirCache = sehir;
                    client.execute(() -> gonder(sehir));
                } catch (Throwable ignored) {
                    cekmeDenendi = true;
                    // Ağ yok/API limit — sessizce vazgeç, mekanik atlanır
                }
            }, "Candy-SehirGonder");
            t.setDaemon(true);
            t.start();
        });
    }

    private static void gonder(String sehir) {
        try {
            if (!ClientPlayNetworking.canSend(SehirPayload.ID)) return;
            ClientPlayNetworking.send(new SehirPayload(sehir));
            Candy.LOG.info("[Candy] Sehir gonderildi");
        } catch (Throwable ignored) {}
    }

    private static String sehirCek() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://ipapi.co/json/"))
            .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;
        var obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (obj.has("error") && obj.get("error").getAsBoolean()) return null; // rate limit vb.
        if (!obj.has("city")) return null;
        String city = obj.get("city").getAsString();
        return (city == null || city.isBlank()) ? null : city.trim();
    }
}
