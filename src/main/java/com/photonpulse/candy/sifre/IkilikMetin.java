package com.photonpulse.candy.sifre;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * IkilikMetin (v8.1) — ikilik (binary) kodlama ve <b>anlamsız</b> jeton üretimi.
 *
 * <h3>TASARIM</h3>
 * Candy bir .txt bırakıyor, içinde sadece {@code 01000111 00110101 ...} var. Oyuncu bunu
 * herhangi bir binary→ASCII aracıyla çözüyor ve üç satır görüyor:
 * <pre>
 * 7K#9@QX2%4ZF
 * M3&amp;?8TR%1WQ0
 * X@47%K2#9BNJ
 * </pre>
 * Üçü de aynı görünüyor ve <b>hiçbiri bir şey ifade etmiyor</b> — rakam, harf, sembol.
 * Yalnızca biri gerçek jeton; hangisi olduğu belli değil. Oyuncu büyük olasılıkla üçünü
 * de deneyecek, ki bu da bir davranış sinyali (ısrar) demek.
 *
 * <h3>NEDEN İÇERİK ŞİFRELENMİYOR</h3>
 * Dosyanın içi düz ikilik; elle çözülebilir olması bulmacanın kendisi. Cihaza bağlılık
 * jetonun {@link CihazKilidi} anahtarından türetilmesiyle geliyor: başka makinede o
 * makinenin anahtarı farklı olduğu için <b>kopyalanan dosyanın cevabı yanlıştır</b>.
 * Aynı sebeple cevaplar internette paylaşılamaz. Dosyada kişisel hiçbir şey yok, o yüzden
 * kopyalansa da sızacak veri yok.
 *
 * <h3>ALFABE</h3>
 * {@code |} <b>yok</b> — {@code SignSystem} onu ön/arka yüz ayırıcısı olarak kullanıyor.
 * {@code \}, boşluk ve satır sonu da yok; hepsi tabelaya ve sohbete yazılabilir olmalı.
 */
public final class IkilikMetin {

    private IkilikMetin() {}

    /** Rakam + harf + sembol. '|' ve '\' kasıtlı olarak dışarıda (bkz. sınıf javadoc'u). */
    private static final char[] ALFABE =
        "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ#@%&*+=?!/<>^~".toCharArray();

    /** Jeton uzunluğu — tabela satırı 15 karakter alıyor, 12 rahat sığıyor. */
    public static final int JETON_UZUNLUK = 12;

    /** Bir .txt'deki satır sayısı; biri gerçek jeton, ikisi tuzak. */
    public static final int SATIR_SAYISI = 3;

    /** Bir pakette kaç şifre olabilir — TruePersonality bitmask'i 32 bit. */
    public static final int MAKS_SIFRE = 32;

    /**
     * v8.3 — ikilik fısıltı metinleri. ORTAK: sunucu indeks yollar, istemci bu diziden
     * metni alıp {@link #ikiliyeCevir} ile çevirir. Tek kaynak olduğu için iki taraf
     * asla ayrışmaz.
     *
     * <p>Sabit ASCII, <b>lang değil</b>: ikilik dizi her oyuncuda aynı uzunlukta olmalı,
     * yoksa "kod gibi" görünen şey dile göre şekil değiştirir ve kalıbı bozar.</p>
     *
     * <p>Kısa tutuldu — 8'li gruplar hâlinde bir satıra sığsın, oyuncu gözüyle
     * sayabilsin. Uzun metin ekranda duvara dönüşür ve "çözülebilir" hissini
     * kaybeder.</p>
     */
    public static final String[] FISILTI = {
        "I SEE YOU",
        "STILL HERE",
        "DONT LOOK",
        "I KNOW",
        "YOU AGAIN",
        "NOT ALONE"
    };

    /** {@link #FISILTI} indeksini ikilik diziye çevirir; indeks bozuksa boş döner. */
    public static String fisiltiIkilik(int indeks) {
        if (indeks < 0 || indeks >= FISILTI.length) return "";
        return ikiliyeCevir(FISILTI[indeks]);
    }

    // ── İkilik kodlama ──────────────────────────────────────────────────────────

    /** Metni "01000001 01000010" biçiminde ikiliğe çevirir (UTF-8, 8'li gruplar). */
    public static String ikiliyeCevir(String metin) {
        if (metin == null || metin.isEmpty()) return "";
        byte[] b = metin.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(b.length * 9);
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(' ');
            int v = b[i] & 0xFF;
            for (int bit = 7; bit >= 0; bit--) sb.append((v >>> bit) & 1);
        }
        return sb.toString();
    }

    /**
     * İkilikten metne. Toleranslı: boşluk, satır sonu, tab ve ayırıcı atılır; 0/1 dışında
     * karakter varsa ya da bit sayısı 8'in katı değilse {@code null} döner.
     */
    public static String ikilidenCevir(String ikilik) {
        if (ikilik == null) return null;
        StringBuilder bitler = new StringBuilder(ikilik.length());
        for (int i = 0; i < ikilik.length(); i++) {
            char c = ikilik.charAt(i);
            if (c == '0' || c == '1') bitler.append(c);
            else if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '_' || c == '-') continue;
            else return null;
        }
        int n = bitler.length();
        if (n == 0 || n % 8 != 0) return null;
        byte[] b = new byte[n / 8];
        for (int i = 0; i < b.length; i++) {
            int v = 0;
            for (int bit = 0; bit < 8; bit++) v = (v << 1) | (bitler.charAt(i * 8 + bit) - '0');
            b[i] = (byte) v;
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    // ── Jeton üretimi ───────────────────────────────────────────────────────────

    /**
     * {@code indeks} numaralı şifrenin satırları. Deterministik: aynı cihaz + aynı dünya
     * + aynı indeks her zaman aynı satırları verir, yani dosya kaybolsa bile jeton geçerli
     * kalır ve diske hiçbir şey kaydetmemiz gerekmez.
     *
     * @return {@link #SATIR_SAYISI} uzunluğunda dizi; {@code [gercekSatir]} jetondur
     */
    public static String[] satirlar(long dunyaTohum, int indeks) {
        String[] out = new String[SATIR_SAYISI];
        for (int s = 0; s < SATIR_SAYISI; s++) {
            out[s] = jeton(dunyaTohum, indeks, s);
        }
        return out;
    }

    /** Gerçek jetonun {@link #satirlar} içindeki yeri — o da cihaza bağlı. */
    public static int gercekSatir(long dunyaTohum, int indeks) {
        byte[] a = CihazKilidi.akis("satir/" + dunyaTohum + "/" + indeks, 1);
        return ((a[0] & 0xFF) % SATIR_SAYISI);
    }

    /** {@code indeks} numaralı şifrenin gerçek jetonu. */
    public static String gercekJeton(long dunyaTohum, int indeks) {
        return jeton(dunyaTohum, indeks, gercekSatir(dunyaTohum, indeks));
    }

    private static String jeton(long dunyaTohum, int indeks, int satir) {
        byte[] a = CihazKilidi.akis("jeton/" + dunyaTohum + "/" + indeks + "/" + satir,
            JETON_UZUNLUK);
        StringBuilder sb = new StringBuilder(JETON_UZUNLUK);
        for (int i = 0; i < JETON_UZUNLUK; i++) {
            sb.append(ALFABE[(a[i] & 0xFF) % ALFABE.length]);
        }
        return sb.toString();
    }

    /**
     * Bir metnin içinde jeton geçiyor mu? Oyuncu tabelaya/sohbete fazladan şey yazmış
     * olabilir, o yüzden tam eşitlik değil {@code contains} aranır.
     *
     * <p>Büyük/küçük harf normalize edilir çünkü oyuncu küçük harf yazabilir. Alfabe
     * {@code I} ve {@code O} içermiyor (1/0 ile karışmasın), dolayısıyla Türkçe
     * {@code i→İ} sorunu bu alfabede oluşmuyor; yine de {@link Locale#ROOT} kullanılıyor.</p>
     */
    public static boolean icerir(String metin, String jeton) {
        if (metin == null || jeton == null || jeton.isEmpty()) return false;
        return metin.toUpperCase(Locale.ROOT).contains(jeton.toUpperCase(Locale.ROOT));
    }
}
