package com.photonpulse.candy.beyin.vagus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.platform.Yukleyici;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * VagusAyar — {@code config/candy-vagus.json}. Vagus tamamen opsiyoneldir; bu dosya
 * yalnızca "nerede, hangi model, ne sıklıkla" sorularını yanıtlar.
 *
 * <h3>NEDEN {@code CandyMixinConfig} KALIBI KOPYALANMADI</h3>
 * O sınıfın {@code save()}'i bozuktur: Gson'a verilen nesnenin alanları {@code static}
 * olduğu için serileştirme onları atlar ve dosyaya {@code "{}"} yazılır; ayrıca okunan
 * nesne static alanlara geri atanmaz. Burada ayar <b>instance nesne + non-static alan</b>
 * olarak tutulur — Gson hem okur hem yazar, tek doğruluk kaynağı döndürülen nesnedir.
 *
 * <h3>ADRES YALNIZCA YEREL OLABİLİR</h3>
 * Prompt'a oyun telemetrisi giriyor. Telemetrinin makineden çıkmaması bir tasarım
 * kuralıdır, ayar dosyasıyla gevşetilemez: loopback olmayan bir adres yazılırsa uyarı
 * düşer ve varsayılana dönülür.
 */
public class VagusAyar {

    /** Vagus açık mı. Kapalıysa hiçbir thread kurulmaz, hiçbir istek atılmaz. */
    public boolean acik = true;
    /** Ollama adresi — yalnızca loopback kabul edilir. */
    public String adres = VARSAYILAN_ADRES;
    /** Model adı. Ölçülen seçim: llama3.2:3b (sıcak çağrı ~2,2 s). */
    public String model = "llama3.2:3b";
    /** İki istek arası en az bu kadar saniye geçer (oyuncu başına). */
    @SerializedName("aralik_sn")
    public int aralikSn = 45;
    /**
     * Ollama'nın modeli bellekte tutma süresi.
     *
     * Aralıktan BÜYÜK olmalı: süre dolunca model bellekten düşer ve sonraki çağrı
     * ~7,5 s model yükleme süresi yer (ölçüldü). 45 s aralık + 10 dk tutma → her zaman sıcak.
     */
    @SerializedName("keep_alive")
    public String keepAlive = "10m";

    static final String VARSAYILAN_ADRES = "http://127.0.0.1:11434";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Ayarı okur; dosya yoksa varsayılanı yazıp devam eder. Asla exception atmaz. */
    public static VagusAyar yukle() {
        VagusAyar ayar = new VagusAyar();
        Path yol;
        try {
            yol = Yukleyici.ayarDizini().resolve("candy-vagus.json");
        } catch (Throwable t) {
            ayar.dogrula();
            return ayar;      // ayar dizini yok (headless doğrulama) — varsayılanla devam
        }
        try {
            if (Files.exists(yol)) {
                VagusAyar okunan = GSON.fromJson(
                    Files.readString(yol, StandardCharsets.UTF_8), VagusAyar.class);
                if (okunan != null) ayar = okunan;
            } else {
                Files.createDirectories(yol.getParent());
                Files.writeString(yol, GSON.toJson(ayar), StandardCharsets.UTF_8);
                Candy.LOG.info("[Candy] Vagus ayarı oluşturuldu: {}", yol.getFileName());
            }
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] Vagus ayarı okunamadı ({}) — varsayılan kullanılıyor",
                t.getMessage());
            ayar = new VagusAyar();
        }
        ayar.dogrula();
        return ayar;
    }

    /** Bozuk/eksik alanları güvenli değerlere çeker. */
    private void dogrula() {
        if (model == null || model.isBlank()) model = "llama3.2:3b";
        if (keepAlive == null || keepAlive.isBlank()) keepAlive = "10m";
        aralikSn = Math.max(10, Math.min(600, aralikSn));
        if (adres == null || adres.isBlank()) adres = VARSAYILAN_ADRES;
        while (adres.endsWith("/")) adres = adres.substring(0, adres.length() - 1);
        if (!yerelMi(adres)) {
            Candy.LOG.warn("[Candy] Vagus adresi yerel değil ({}) — {} kullanılıyor",
                adres, VARSAYILAN_ADRES);
            adres = VARSAYILAN_ADRES;
        }
    }

    static boolean yerelMi(String adres) {
        try {
            String h = URI.create(adres).getHost();
            if (h == null) return false;
            h = h.toLowerCase(Locale.ROOT);
            return h.equals("127.0.0.1") || h.equals("localhost")
                || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1");
        } catch (Throwable t) {
            return false;
        }
    }

    public int aralikTick() {
        return aralikSn * 20;
    }
}
