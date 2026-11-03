package com.photonpulse.candy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.platform.Yukleyici;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CandyMixinConfig — client-side mixin efektlerinin on/off toggle'ı.
 * Config dosyası: .minecraft/config/candy-mixin.json
 *
 * Her mixin başlangıçta ilgili flag'ini kontrol eder.
 *
 * <h3>v8: DOSYA ARTIK GERÇEKTEN ÇALIŞIYOR</h3>
 * Eski sürüm hem yazarken hem okurken sessizce hiçbir şey yapmıyordu:
 * <ul>
 *   <li>Bayraklar {@code static}'ti ve {@code snapshot.chatCorruption = chatCorruption}
 *       satırları aslında <b>statik alanın kendisine kendisini atıyordu</b> — no-op.</li>
 *   <li>Gson {@code static} alanları serileştirmez; dolayısıyla dosyaya {@code "{}"}
 *       yazılıyordu. Okuma tarafı da {@code okundu.chatCorruption} ile yine statik alanı
 *       okuyordu, yani dosyadaki değer hiçbir zaman uygulanmıyordu.</li>
 * </ul>
 * Sonuç: kullanıcının dosyada yaptığı değişiklik hiçbir etki etmiyor, varsayılanlar
 * daima kazanıyordu. Çözüm: disk biçimi <b>non-static alanlı ayrı bir nesne</b>
 * ({@link Veri}); statik bayraklar yalnızca çalışma zamanı API'si olarak kalıyor
 * (mixin'ler onları okuyor).
 *
 * <h3>v8.2: YABANCI ANAHTARLAR KORUNUYOR</h3>
 * Dosya artık {@link JsonObject} üzerinden okunup yazılıyor. Sebep {@link KonfigMudahale}:
 * Candy dosyaya kendi satırını ekleyebiliyor ve bu satır bir sonraki kaydetmede
 * <b>silinmemek</b> zorunda. Gson DTO'su bilmediği anahtarı sessizce düşürürdü — yani
 * oyuncunun gördüğü o tek tuhaf satır, oyun bir kez ayar yazdığında kaybolurdu.
 * {@link #yabanci} bilinmeyen her anahtarı tutar ve kaydetmede geri yazar.
 */
public class CandyMixinConfig {

    public static boolean chatCorruption = true;
    public static boolean hudFlash = true;
    public static boolean deathScreen = true;
    public static boolean cameraShake = true;
    public static boolean camscreen = true;
    /**
     * v8.1 — Candy açık uygulamalardan beyaz listede olanı tanısın mı
     * ({@link UygulamaGonder}). false ise süreç listesi HİÇ okunmaz.
     */
    public static boolean uygulamaSezgisi = true;

    /**
     * v8.4 — Yoğunluk tavanı (0.0–1.0). {@link IntensityState#get()} döndürdüğü değeri
     * bu tavanla kırpar, yani oyuncu "aynı mod, daha az kaos" diyebilir.
     *
     * <p>Pazar sebebi: bu kategorideki en yaygın şikâyet "çok sık / çok şiddetli" ve
     * en yaygın çözüm bir açma-kapama değil, bir <i>kısma</i> ayarıdır (Man From The Fog
     * {@code man_config}, The Root of Fear intensity, ooo.jar intensity). Efekti komple
     * kapatan bayrak oyuncuyu modun yarısından mahrum eder; tavan ise mekanikleri
     * yerinde bırakıp şiddeti düşürür. Yeni mekanik yok — yalnızca üst sınır.</p>
     *
     * <p>1.0 = dokunulmamış davranış (varsayılan). 0.5 = mixinler yarı şiddetle çalışır.
     * 0.0 = yoğunluğa bağlı efektler susar; olay akışı (spawn, diyalog, lore) sürer.</p>
     */
    public static double yogunlukTavani = 1.0;

    /** v8.2 — Candy'nin dosyaya kendi eklediği / tanımadığımız anahtarlar. */
    private static final Map<String, JsonElement> yabanci = new LinkedHashMap<>();

    /** Bilinen bayrak adları — yabancı anahtar ayıklaması bunun dışını alır. */
    private static final String[] BILINEN = {
        "chatCorruption", "hudFlash", "deathScreen", "cameraShake", "camscreen",
        "uygulamaSezgisi", "yogunlukTavani"
    };

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path yol() {
        return Yukleyici.ayarDizini().resolve("candy-mixin.json");
    }

    /** Dosya yolu — {@link KonfigMudahale} aynı dosyaya yazıyor. */
    public static Path dosyaYolu() {
        try {
            return yol();
        } catch (Throwable t) {
            return null;
        }
    }

    public static void load() {
        try {
            Path yol = yol();
            if (!Files.exists(yol)) {
                save();
                return;
            }
            JsonElement kok = JsonParser.parseString(
                Files.readString(yol, StandardCharsets.UTF_8));
            if (kok == null || !kok.isJsonObject()) return;
            JsonObject o = kok.getAsJsonObject();

            chatCorruption  = bool(o, "chatCorruption",  chatCorruption);
            hudFlash        = bool(o, "hudFlash",        hudFlash);
            deathScreen     = bool(o, "deathScreen",     deathScreen);
            cameraShake     = bool(o, "cameraShake",     cameraShake);
            camscreen       = bool(o, "camscreen",       camscreen);
            uygulamaSezgisi = bool(o, "uygulamaSezgisi", uygulamaSezgisi);
            yogunlukTavani  = kesir(o, "yogunlukTavani", yogunlukTavani);

            yabanci.clear();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (!bilinenMi(e.getKey())) yabanci.put(e.getKey(), e.getValue());
            }

            Candy.LOG.info("[Candy] Mixin config yüklendi: chat={} hud={} death={} camera={} camscreen={} uygulama={} tavan={} yabanci={}",
                chatCorruption, hudFlash, deathScreen, cameraShake, camscreen,
                uygulamaSezgisi, yogunlukTavani, yabanci.size());
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] Config yüklenemedi, defaults: {}", t.getMessage());
        }
    }

    private static boolean bool(JsonObject o, String ad, boolean varsayilan) {
        try {
            JsonElement e = o.get(ad);
            if (e == null || !e.isJsonPrimitive()) return varsayilan;
            return e.getAsBoolean();
        } catch (Throwable t) {
            return varsayilan;
        }
    }

    /** 0.0–1.0 arası kesir okur; bozuk/aralık dışı değer sessizce kırpılır. */
    private static double kesir(JsonObject o, String ad, double varsayilan) {
        try {
            JsonElement e = o.get(ad);
            if (e == null || !e.isJsonPrimitive()) return varsayilan;
            double d = e.getAsDouble();
            if (Double.isNaN(d)) return varsayilan;
            return Math.min(1.0, Math.max(0.0, d));
        } catch (Throwable t) {
            return varsayilan;
        }
    }

    private static boolean bilinenMi(String ad) {
        for (String b : BILINEN) if (b.equals(ad)) return true;
        return false;
    }

    public static void save() {
        try {
            Path yol = yol();
            Files.createDirectories(yol.getParent());
            Files.writeString(yol, GSON.toJson(nesne()), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] Config yazılamadı: {}", t.getMessage());
        }
    }

    /** Diskteki biçimin bellekteki hâli — yabancı anahtarlar dahil. */
    private static JsonObject nesne() {
        JsonObject o = new JsonObject();
        o.addProperty("chatCorruption", chatCorruption);
        o.addProperty("hudFlash", hudFlash);
        o.addProperty("deathScreen", deathScreen);
        o.addProperty("cameraShake", cameraShake);
        o.addProperty("camscreen", camscreen);
        o.addProperty("uygulamaSezgisi", uygulamaSezgisi);
        o.addProperty("yogunlukTavani", yogunlukTavani);
        for (Map.Entry<String, JsonElement> e : yabanci.entrySet()) {
            if (!bilinenMi(e.getKey())) o.add(e.getKey(), e.getValue());
        }
        return o;
    }

    /** v8.2 — Candy'nin eklediği satırı belleğe koy (kaydetmede dosyaya gider). */
    static void yabanciKoy(String ad, JsonElement deger) {
        if (ad == null || bilinenMi(ad)) return;
        yabanci.put(ad, deger);
    }

    static boolean yabanciVarMi(String ad) {
        return ad != null && yabanci.containsKey(ad);
    }
}
