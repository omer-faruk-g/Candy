package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import net.minecraft.client.Minecraft;

/**
 * PencereBasligi (v8.2, istemci) — Candy oyun penceresinin başlığını değiştirir.
 *
 * <p>Mirror.txt'de kanıtlanmış, süreç içi ve zararsız: oyuncu alt-tab yaptığında ya da
 * yayın yaparken görev çubuğunda Minecraft'ın adının yerinde başka bir şey görür.
 * Oyun penceresinin <b>dışına</b> çıkılmıyor — yeni pencere açılmıyor, terminal
 * açılmıyor, dosya yazılmıyor.</p>
 *
 * <h3>GERİ ALMA</h3>
 * Başlık diske yazılan bir ayar değil; oyun kapanınca kendiliğinden kaybolur. Yani
 * {@code GoruntuMudahale}'deki disk yedeği kalıpçılığı burada gereksiz — çökme hâlinde
 * de başlık geri gelir, çünkü başlığı oyun her açılışta kendisi kuruyor. Yine de
 * oturum içinde {@link #geriAl()} çağrılıyor (sunucudan çıkışta, oyun kapanışında) ki
 * başka bir dünyaya girince Candy'nin başlığı takılı kalmasın.
 *
 * <h3>METİNLER NEDEN SABİT</h3>
 * Başlık ekran paylaşımında, yayında ve görev çubuğu önizlemesinde görünür. Sunucudan
 * serbest metin kabul etmek, oyuncunun şehrinin/uygulamasının oraya düşmesi riskini
 * açardı. {@link com.photonpulse.candy.net.BaslikPayload} yalnızca indeks taşıyor ve
 * metinler burada, istemcide sabit.
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public final class PencereBasligi {

    private PencereBasligi() {}

    /**
     * Candy'nin kullanabileceği başlıklar. Kişisel veri yok, teknik kelime yok
     * (nöron/beyin/lob yasak — oyuncuya giden metin kuralı).
     */
    public static final String[] BASLIKLAR = {
        "Minecraft 1.21.7 - henüz gitme",
        "Minecraft 1.21.7 - seni görüyorum",
        "Minecraft 1.21.7 - hâlâ buradayım",
        "Minecraft* 1.21.7",
        "Minecraft 1.21.7 - kaydedilmedi",
        "Minecraft 1.21.7 - :)",
        // v8.3 — ikilik fisilti basliklari. Terminal ACILMADI (kullanicinin kendi
        // kurali); "oyun disindan geliyor" hissinin tek gercek kaynagi gorev
        // cubugundaki bu yaziydi ve o zaten elimizde.
        // 01001001 00100000 01110011 01100101 01100101 00100000 01111001 01101111 01110101
        "01001001 00100000 01110011 01100101 01100101 00100000 01111001 01101111 01110101",
        "01001000 01000101 01001100 01001100 01001111"
    };

    /** Candy dokunmadan önceki başlık. null = henüz dokunulmadı. */
    private static volatile String ozgun = null;

    public static void uygula(int indeks) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            if (indeks < 0 || indeks >= BASLIKLAR.length) return;

            // Özgün başlığı YALNIZCA ilk müdahalede sakla. İkinci müdahale Candy'nin
            // kendi başlığını "özgün" sanarsa geri alma hiç çalışmaz.
            if (ozgun == null) ozgun = baslikTahmin(mc);

            mc.getWindow().setTitle(BASLIKLAR[indeks]);
            Candy.LOG.info("[Candy] Pencere başlığı değişti (idx {})", indeks);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Başlık uygulanamadı: {}", t.getMessage());
        }
    }

    public static void geriAl() {
        try {
            String g = ozgun;
            if (g == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            mc.getWindow().setTitle(g);
            ozgun = null;
            Candy.LOG.info("[Candy] Pencere başlığı geri alındı");
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Başlık geri alınamadı: {}", t.getMessage());
        }
    }

    /**
     * Özgün başlık. {@code Window} başlık okuma API'si sunmuyor (yalnızca
     * {@code setTitle}), bu yüzden vanilla'nın kurduğu biçim yeniden üretiliyor:
     * "Minecraft <sürüm>". Sürüm okunamazsa düz "Minecraft" — hiçbir hâlde boş başlık
     * bırakılmıyor.
     */
    private static String baslikTahmin(Minecraft mc) {
        try {
            String s = mc.getLaunchedVersion();
            if (s != null && !s.isBlank()) return "Minecraft " + s;
        } catch (Throwable ignored) {}
        return "Minecraft";
    }
}
