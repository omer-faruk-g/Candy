package com.photonpulse.candy.uyum;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.platform.Yukleyici;

/**
 * SurumKapisi (v8.4) — Candy'nin hangi Minecraft sürümünde ve hangi komşularla
 * çalıştığını bilmesi.
 *
 * <h3>NE YAPMAZ — ÖNEMLİ</h3>
 * Bu sınıf Candy'yi <b>başka bir sürüme uyarlamaz</b>. Derlenmiş bir mod jar'ı buna
 * yetenekli değil: çağırdığı Minecraft metotlarının adları ve imzaları derleme anında
 * sabitlenir (yarn mapping), sürüm değişince o metotlar yok olur ve JVM sınıf yüklerken
 * {@code NoSuchMethodError} atar. Mixinler daha kırılgan: enjeksiyon hedefi metot
 * imzasıyla eşleşir, imza değişince hedef bulunamaz. Çalışma anında kod indirip
 * yüklemek de seçenek değil — Candy hiçbir yerle konuşmuyor, bu kalıcı kural.
 *
 * <h3>O ZAMAN NE YAPAR</h3>
 * Beklenmeyen bir sürümde <b>çökmek yerine küçülür</b>. Sürümü okur, test edilmiş
 * listeyle karşılaştırır ve test edilmemişse {@link #guvenliMi()} false döner —
 * alt sistemler buna bakıp kendi invazif kısımlarını kapatır. Çökmeyen bir mod,
 * kaldırılan bir moddan iyidir.
 *
 * <p>Gerçek çok-sürüm desteği kaynak düzeyinde çözülür (sürüm başına ayrı jar:
 * Stonecutter / Preprocessor), çalışma anında değil.</p>
 */
public final class SurumKapisi {

    private SurumKapisi() {}

    /** Üzerinde gerçekten test edilmiş sürümler. Buraya eklemek bir karar, tahmin değil. */
    private static final String[] TEST_EDILDI = { "1.21.7" };

    /** Mixin hedeflerinin büyük olasılıkla tuttuğu, test edilmemiş yakın sürümler. */
    private static final String[] YAKIN = { "1.21.6", "1.21.8" };

    private static volatile String surum = "bilinmiyor";
    private static volatile Durum durum = Durum.BILINMEYEN;

    public enum Durum { TEST_EDILDI, YAKIN, BILINMEYEN }

    public static void register() {
        surum = oku();
        durum = sinifla(surum);

        switch (durum) {
            case TEST_EDILDI -> Candy.LOG.info("[Candy] SurumKapisi: {} — test edilmiş sürüm", surum);
            case YAKIN -> Candy.LOG.warn(
                "[Candy] SurumKapisi: {} test edilmedi ama {} ailesine yakın. Candy tam çalışıyor; "
                + "bir mixin tutmazsa oyun çökmez, o efekt sessizce devre dışı kalır.",
                surum, TEST_EDILDI[0]);
            case BILINMEYEN -> Candy.LOG.warn(
                "[Candy] SurumKapisi: {} DESTEKLENMİYOR (test edilen: {}). Candy kendini kısıtlı "
                + "kipte açıyor: invazif alt sistemler kapalı. Bu sürüm için ayrı bir jar gerekir.",
                surum, String.join(", ", TEST_EDILDI));
        }

        komsulariBildir();
    }

    /** Minecraft sürümü — loader'dan okunur, yani mapping'e ve tarafa bağımlı değil. */
    private static String oku() {
        try {
            String surum = Yukleyici.minecraftSurumu();
            return surum == null ? "bilinmiyor" : surum;
        } catch (Throwable t) {
            return "bilinmiyor";
        }
    }

    private static Durum sinifla(String s) {
        for (String t : TEST_EDILDI) if (t.equals(s)) return Durum.TEST_EDILDI;
        for (String y : YAKIN) if (y.equals(s)) return Durum.YAKIN;
        return Durum.BILINMEYEN;
    }

    /**
     * Invazif bir alt sistem çalışmaya başlamadan önce buna bakar.
     * Test edilmemiş bir sürümde oyuncunun dünyasına/dosyasına dokunmak kabul edilemez.
     */
    public static boolean guvenliMi() {
        return durum != Durum.BILINMEYEN;
    }

    public static String surum() { return surum; }

    public static Durum durum() { return durum; }

    /**
     * Candy ile aynı yüzeylere dokunan bilinen komşular. Candy bunların varlığında
     * kendini kapatmaz — ayna fog'u ve lightmap karartması kimliğin parçası — ama
     * tespiti log'a yazar: "Candy shader'ımı bozdu" raporunu teşhis edilebilir yapan şey bu.
     */
    private static final String[] KOMSU = { "sodium", "iris", "immediatelyfast", "distanthorizons" };

    private static void komsulariBildir() {
        StringBuilder sb = new StringBuilder();
        for (String k : KOMSU) if (modVar(k)) sb.append(sb.isEmpty() ? "" : ", ").append(k);
        if (!sb.isEmpty()) {
            Candy.LOG.info("[Candy] Render komşuları: {} — fog/lightmap mixin'leri yine de "
                + "çalışıyor; görsel bir tuhaflık bildirilirse ilk bakılacak yer burası.", sb);
        }
    }

    /** Komşu mod var mı — çakışabilecek alanlarda kendini geri çekmek için. */
    public static boolean modVar(String id) {
        try {
            return Yukleyici.modYuklu(id);
        } catch (Throwable t) {
            return false;
        }
    }
}
