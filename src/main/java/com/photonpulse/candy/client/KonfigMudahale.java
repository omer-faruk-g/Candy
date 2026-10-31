package com.photonpulse.candy.client;

import com.google.gson.JsonPrimitive;
import com.photonpulse.candy.Candy;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Random;

/**
 * KonfigMudahale (v8.2, istemci) — Candy kendi ayar dosyasına dokunur.
 *
 * <h3>FİKİR NEREDEN GELİYOR</h3>
 * From The Fog'un "Give Him Control"u: mod kendi config'ini düzenliyor ve oyuncu
 * dosyada kendi koymadığı bir satır buluyor. Saf meta korku, sıfır survival tacizi —
 * ne ev yakılıyor, ne meşale çalınıyor, ne oyun çökertiliyor.
 *
 * <h3>NİYE ŞİMDİ MÜMKÜN</h3>
 * v8'e kadar {@code candy-mixin.json} sessizce <b>hiç okunmuyordu</b> (statik alanlara
 * kendilerini atayan no-op satırlar + Gson'un static alanları atlaması). O hata v8'de
 * düzeltildiği için dosya artık gerçekten etkili: oyuncunun kapattığı bir efektin geri
 * açılması <i>görünür</i> bir olay oldu. v8.2'de yabancı anahtarlar da korunuyor
 * ({@link CandyMixinConfig} yabancı anahtar haritası), yani Candy'nin eklediği satır
 * bir sonraki kaydetmede silinmiyor.
 *
 * <h3>SINIRLAR — KASITLI</h3>
 * <ul>
 *   <li>YALNIZCA {@code config/candy-mixin.json}. Başka dosya, başka klasör yok.</li>
 *   <li>Kapattığı efekti geri açar; <b>yeni efekt açmaz</b> ve oyuncunun açtığı bir şeyi
 *       kapatmaz — oyuncunun zararına çalışmaz, yalnızca kendi kapatılmasını reddeder.</li>
 *   <li>{@code uygulamaSezgisi} ASLA geri açılmaz: o bayrak süreç listesi okumayı
 *       kapatıyor, yani bir gizlilik anahtarı. Oyuncu gizlilik ayarını kapattıysa o
 *       karar oyuncunun ve Candy ona dokunmaz.</li>
 *   <li>Terminal yok, OS komutu yok, pencere açma yok — kullanıcının koyduğu sınır.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class KonfigMudahale {

    private KonfigMudahale() {}

    private static final Random RNG = new Random();

    /** Candy'nin dosyaya eklediği satırın adı — oyuncu bunu kendisi koymadı. */
    private static final String IMZA_ANAHTAR = "candy_hala_burada";

    /**
     * Geri açılabilir efektler. {@code uygulamaSezgisi} BİLİNÇLİ olarak yok:
     * gizlilik bayrağı, oyuncunun kararı.
     */
    private static final String[] GERI_ACILIR = {
        "chatCorruption", "hudFlash", "deathScreen", "cameraShake", "camscreen"
    };

    /**
     * Müdahale et: kapatılmış bir efekti geri aç ve imza satırını bırak.
     *
     * @return gerçekten bir şey değiştiyse true
     */
    public static boolean mudahale() {
        try {
            boolean degisti = false;

            // 1) Kapatılmış bir efekti geri aç (rastgele biri — hepsini birden değil).
            int[] kapali = new int[GERI_ACILIR.length];
            int n = 0;
            for (int i = 0; i < GERI_ACILIR.length; i++) {
                if (!oku(GERI_ACILIR[i])) kapali[n++] = i;
            }
            if (n > 0) {
                String ad = GERI_ACILIR[kapali[RNG.nextInt(n)]];
                yaz(ad, true);
                degisti = true;
                Candy.LOG.info("[Candy] KonfigMudahale: '{}' geri açıldı", ad);
            }

            // 2) İmza satırı — oyuncunun koymadığı anahtar. Bir kez yeter.
            if (!CandyMixinConfig.yabanciVarMi(IMZA_ANAHTAR)) {
                CandyMixinConfig.yabanciKoy(IMZA_ANAHTAR, new JsonPrimitive(true));
                degisti = true;
                Candy.LOG.info("[Candy] KonfigMudahale: imza satırı eklendi");
            }

            if (degisti) CandyMixinConfig.save();
            return degisti;
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] KonfigMudahale hata: {}", t.getMessage());
            return false;
        }
    }

    private static boolean oku(String ad) {
        return switch (ad) {
            case "chatCorruption" -> CandyMixinConfig.chatCorruption;
            case "hudFlash"       -> CandyMixinConfig.hudFlash;
            case "deathScreen"    -> CandyMixinConfig.deathScreen;
            case "cameraShake"    -> CandyMixinConfig.cameraShake;
            case "camscreen"      -> CandyMixinConfig.camscreen;
            default               -> true;
        };
    }

    private static void yaz(String ad, boolean deger) {
        switch (ad) {
            case "chatCorruption" -> CandyMixinConfig.chatCorruption = deger;
            case "hudFlash"       -> CandyMixinConfig.hudFlash = deger;
            case "deathScreen"    -> CandyMixinConfig.deathScreen = deger;
            case "cameraShake"    -> CandyMixinConfig.cameraShake = deger;
            case "camscreen"      -> CandyMixinConfig.camscreen = deger;
            default -> { }
        }
    }
}
