package com.photonpulse.candy.beyin.vagus;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.photonpulse.candy.Candy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * VagusIstemci — Ollama {@code /api/chat} istemcisi. <b>Yalnızca Vagus thread'inden</b>
 * çağrılır; hiçbir metodu ana thread'den çağrılmamalıdır (ağ bekler).
 *
 * <h3>CLAMP ZORUNLU</h3>
 * Ollama yapılandırılmış çıktıda şemanın <b>tipini</b> uygular ama {@code minimum}/
 * {@code maximum}'u UYGULAMAZ: şema {@code 0..1} derken ölçümde {@code tahmin: 60}
 * döndü. Aralık {@link VagusSezgi#olustur} içinde kesilir. JSON hiç ayrıştırılamazsa
 * o çevrim <b>sessizce atlanır</b> — yarım veri beyinlere girdi olarak gitmez.
 *
 * <h3>YENİ BAĞIMLILIK YOK</h3>
 * {@code java.net.http.HttpClient} modda zaten kullanılıyor
 * ({@code yuz/YuzHaritasi}, {@code client/SehirGonder}). Zaman aşımı hem builder'da
 * hem istekte verilir — builder'daki yalnızca bağlantı kurulumunu kapsar.
 */
public final class VagusIstemci {

    private static final Duration BAGLANTI = Duration.ofSeconds(2);
    private static final Duration ISTEK = Duration.ofSeconds(20);
    private static final Duration YOKLAMA = Duration.ofSeconds(3);

    private final VagusAyar ayar;
    private final HttpClient http;
    private final JsonObject sema;

    public VagusIstemci(VagusAyar ayar) {
        this.ayar = ayar;
        this.http = HttpClient.newBuilder().connectTimeout(BAGLANTI).build();
        this.sema = semaKur();
    }

    /** Ollama ayakta mı — açılışta bir kez, Vagus thread'inde. */
    public boolean yokla() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(ayar.adres + "/api/tags"))
                .timeout(YOKLAMA).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return false;
            JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray modeller = o.getAsJsonArray("models");
            if (modeller == null) return false;
            for (JsonElement e : modeller) {
                JsonObject m = e.getAsJsonObject();
                if (m.has("name") && ayar.model.equals(m.get("name").getAsString())) return true;
            }
            Candy.LOG.warn("[Candy] Vagus modeli bulunamadı: {} — Vagus kapalı", ayar.model);
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Bir çevrim: özeti gönder, sezgiyi al. Başarısızlıkta {@code null} (çevrim atlanır).
     *
     * @param tick sezginin üretim tick'i — bayatlık bundan ölçülür
     */
    public VagusSezgi sor(String ozet, long tick) {
        try {
            JsonObject govde = new JsonObject();
            govde.addProperty("model", ayar.model);
            govde.addProperty("stream", false);
            govde.addProperty("keep_alive", ayar.keepAlive);
            govde.add("format", sema);

            JsonObject secenek = new JsonObject();
            secenek.addProperty("temperature", 0.4);
            secenek.addProperty("num_predict", 220);
            govde.add("options", secenek);

            JsonArray mesajlar = new JsonArray();
            mesajlar.add(mesaj("system", VagusOzet.SISTEM));
            mesajlar.add(mesaj("user", ozet));
            govde.add("messages", mesajlar);

            HttpRequest req = HttpRequest.newBuilder(URI.create(ayar.adres + "/api/chat"))
                .timeout(ISTEK)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(govde.toString(),
                    StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                Candy.LOG.debug("[Candy] Vagus HTTP {}", resp.statusCode());
                return null;
            }
            JsonObject kok = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonObject mesaj = kok.getAsJsonObject("message");
            if (mesaj == null || !mesaj.has("content")) return null;

            String icerik = mesaj.get("content").getAsString();
            if (icerik == null || icerik.isBlank()) return null;

            JsonElement ayrisan = JsonParser.parseString(icerik);
            if (!ayrisan.isJsonObject()) return null;
            JsonObject c = ayrisan.getAsJsonObject();

            return VagusSezgi.olustur(
                metin(c, "hipotez"),
                sayi(c, "tahmin", 0.25),
                sayi(c, "guven", 0.0),
                metin(c, "surukleyen"),
                tick);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Vagus çevrimi atlandı: {}", t.getMessage());
            return null;
        }
    }

    private static JsonObject mesaj(String rol, String icerik) {
        JsonObject m = new JsonObject();
        m.addProperty("role", rol);
        m.addProperty("content", icerik);
        return m;
    }

    private static String metin(JsonObject o, String ad) {
        try {
            return (o.has(ad) && o.get(ad).isJsonPrimitive()) ? o.get(ad).getAsString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static double sayi(JsonObject o, String ad, double varsayilan) {
        try {
            return (o.has(ad) && o.get(ad).isJsonPrimitive())
                ? o.get(ad).getAsDouble() : varsayilan;
        } catch (Throwable t) {
            return varsayilan;   // model sayı yerine metin döndürdü
        }
    }

    /** Yapılandırılmış çıktı şeması — tip garantisi verir, aralık garantisi VERMEZ. */
    private static JsonObject semaKur() {
        JsonObject hipotez = new JsonObject();
        hipotez.addProperty("type", "string");
        JsonObject tahmin = new JsonObject();
        tahmin.addProperty("type", "number");
        JsonObject guven = new JsonObject();
        guven.addProperty("type", "number");
        JsonObject surukleyen = new JsonObject();
        surukleyen.addProperty("type", "string");

        JsonObject alanlar = new JsonObject();
        alanlar.add("hipotez", hipotez);
        alanlar.add("tahmin", tahmin);
        alanlar.add("guven", guven);
        alanlar.add("surukleyen", surukleyen);

        JsonArray zorunlu = new JsonArray();
        zorunlu.add("hipotez");
        zorunlu.add("tahmin");
        zorunlu.add("guven");
        zorunlu.add("surukleyen");

        JsonObject sema = new JsonObject();
        sema.addProperty("type", "object");
        sema.add("properties", alanlar);
        sema.add("required", zorunlu);
        return sema;
    }
}
