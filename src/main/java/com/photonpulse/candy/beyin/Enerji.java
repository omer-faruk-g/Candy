package com.photonpulse.candy.beyin;

import com.photonpulse.candy.Candy;

/**
 * Enerji — beynin metabolizması: sınırlı bütçe, rekabet, atrofi ve körelme.
 *
 * Kullanıcının özel isteği: "tüm ağlara gidecek enerji, yaşamak için hangi ağ ile
 * iletişimin önemsiz olduğu, komple bu beynin içinde olacak."
 *
 * ZARAFET: oyun içi kıtlık = CPU kıtlığı. Enerjisi kesilen lob süpürme atlar, yani
 * gerçekten daha az işlemci yer. Kurgusal bir sayaç değil, fiziksel bir kısıt.
 *
 * Esikler ESIT PAYA (1/lobSayisi) GORELIDIR — softmax toplami sabit 1,0 oldugu icin
 * mutlak esik, lob sayisi degistiginde sessizce anlamsizlasir:
 * <pre>
 *   pay > 1,8x esit pay   → tam hız
 *   0,9x – 1,8x           → öğrenme yavaşlar
 *   pay < 0,9x esit pay   → süpürmelerin yarısı atlanır (aktivasyon bayatlar, CPU düşer)
 *   pay < 0,3x, 40+       → ATROFİ: ağırlıklar ×0.998 sönmeye başlar
 *   önem < 0,08, 150+     → KÖRELME: nöronlar donar, sinapslar nörogeneze iade edilir
 * </pre>
 *
 * ABLASYON: {@code hayatiOnem} TAHMİN EDİLMEZ, ÖLÇÜLÜR. Candy düzenli olarak kendi bir
 * lobunu yarı güce kısar ve öngörü hatasının ne kadar bozulduğuna bakar
 * (leave-one-out importance). Tematik olarak da tam yerinde: Candy periyodik olarak
 * kendi bir parçasını kısıp "buna gerçekten ihtiyacım var mı?" diye soruyor.
 */
public class Enerji {

    /**
     * Atrofi esigi — esit payin bu KESRI. Mutlak deger olamaz: softmax toplami sabit
     * 1,0 oldugu icin lob sayisi degistiginde mutlak esik anlamini kaybeder.
     */
    private static final double ATROFI_ORANI = 0.30;
    private static final int ATROFI_SUPURME = 40;
    private static final float ATROFI_SONUM = 0.998f;
    private static final double KORELME_ONEM = 0.08;
    private static final int KORELME_SUPURME = 150;

    /**
     * Ablasyon: olcum icin kac tahmin beklenecek.
     *
     * KALIBRASYON NOTU: bu deger ABLASYON_SUPURME'den (BeyinMotoru, 20) KUCUK olmali.
     * Aksi halde bir sonraki ablasyon baslama zamani geldiginde onceki hala bitmemis olur,
     * ablasyonBaslat "ablasyonLob >= 0" guard'inda sessizce doner ve ROUND-ROBIN DURUR —
     * tum loblarin hayatiOnem'i sonsuza dek 0,5'te kalir.
     */
    private static final int ABLASYON_PENCERE = 16;
    /** Ablasyon sırasında lob çıkışına uygulanan kazanç. */
    public static final float ABLASYON_KAZANCI = 0.5f;

    /**
     * TARTI'nin enerji skoruna katkı ağırlığı.
     *
     * KILIT 2 (ÖLÇÜM KANAATİ YENER): bu değer, ölçülen {@code hayatiOnem} katsayısından
     * (2,2) kasten çok küçüktür. TARTI dağılımı <i>bükebilir</i>, ama ablasyonla
     * ÖLÇÜLMÜŞ bir gerçeği ezemez. Aksi halde lob, kendi hakkındaki kanaatiyle kendi
     * ölçümünü geçersiz kılan kapalı bir döngü kurardı.
     */
    private static final double TARTI_AGIRLIGI = 0.8;

    /** Oy vektoru tamponu — supurme basina cop uretmesin. */
    private static final ThreadLocal<double[]> OY =
        ThreadLocal.withInitial(() -> new double[Lob.CIKIS_NORON]);

    /** Lob skor tamponu (v8) — ayni ThreadLocal kalibi; supurme basina uye basina tahsis gitti. */
    private static final ThreadLocal<double[]> SKOR =
        ThreadLocal.withInitial(() -> new double[Lob.LOBLAR.length]);

    /**
     * Demet skor tamponu (v8). Demet sayisi beyin basina sabittir (19), bu yuzden tek
     * uzunluk onbellege alinir; uzunluk degisirse yeniden tahsis edilir. Boyut TAM
     * olmalidir — {@code softmax} tum diziyi normalize eder, fazla slot sonucu bozardi.
     */
    private static final ThreadLocal<double[]> DEMET_SKOR =
        ThreadLocal.withInitial(() -> new double[0]);

    private static double[] demetTamponu(int n) {
        double[] t = DEMET_SKOR.get();
        if (t.length != n) { t = new double[n]; DEMET_SKOR.set(t); }
        return t;
    }

    private Enerji() {}

    // ─────────────────────────────────────────────────────────
    // BÜTÇE DAĞITIMI
    // ─────────────────────────────────────────────────────────

    /**
     * Toplam 1.0 birimlik enerjiyi 10 loba (ve 19 demete) softmax ile dağıtır.
     *
     * Skor = ÖLÇÜLEN hayati önem × 2.2 + anlık talep × 1.0 (körelmiş lob dışlanır).
     * Yani hem "ne kadar gerekli olduğu kanıtlandı" hem "şu an ne kadar çalışıyor".
     */
    public static void dagit(Beyin b) {
        if (b.loblar == null) return;
        final Lob[] loblar = Lob.LOBLAR;
        // Tampon yeniden kullaniliyor; SIFIRLAMA ZORUNLU — null lobun slotu asagidaki
        // donguye girmez ve onceki cagrinin degeri sizardi (taze dizi 0.0 veriyordu).
        final double[] skor = SKOR.get();
        java.util.Arrays.fill(skor, 0.0);

        // ── TARTI'NIN OYU (v5) ──
        // Lob tek bir skaler degil, cikis sutunundan okunan bir OY VEKTORU uretir:
        // kanal l -> "l. loba daha cok / daha az enerji". Uc guvenlik kilidi:
        //   KILIT 1: kendi enerjisine oy veremez (asagida atlanir)
        //   KILIT 2: agirligi olculen hayatiOnem'inkinden cok kucuk (TARTI_AGIRLIGI)
        //   KILIT 3: TARTI korelmisse hic konusmaz
        LobDurum tartiDurum = b.loblar[Lob.TARTI.ordinal()];
        final boolean tartiKonusur = tartiDurum != null && !tartiDurum.koreldi;
        final double[] oylar = OY.get();
        if (tartiKonusur) b.cikisOylari(Lob.TARTI, oylar);
        else java.util.Arrays.fill(oylar, 0.0);

        for (Lob l : loblar) {
            LobDurum d = b.loblar[l.ordinal()];
            if (d == null) continue;
            double talep = b.lobOrtalamasi(l);
            d.talep = talep;
            double oy = (l == Lob.TARTI) ? 0.0 : oylar[l.ordinal()] * TARTI_AGIRLIGI;
            d.tartiOyu = oy;
            skor[l.ordinal()] = d.koreldi ? -10.0 : (d.hayatiOnem * 2.2 + talep + oy);
        }
        softmax(skor);
        for (Lob l : loblar) {
            LobDurum d = b.loblar[l.ordinal()];
            if (d != null) d.enerjiPayi = skor[l.ordinal()];
        }

        // Demetler kendi aralarında yarışır (beyaz madde bütçesi)
        if (b.demetler != null && b.demetler.length > 0) {
            final double[] ds = demetTamponu(b.demetler.length);
            for (int i = 0; i < b.demetler.length; i++) {
                Demet dm = b.demetler[i];
                if (dm == null) { ds[i] = -10; continue; }
                double kaynakTalep = 0;
                try {
                    kaynakTalep = b.lobOrtalamasi(Lob.valueOf(dm.kaynak));
                } catch (Throwable ignored) {}
                // Demet oylari cikis sutununun 10. kanalindan sonrasini kullanir
                // (0-9 loblara ayrildi); sutun 32 kanalli, 19 demet icin yeterli.
                double dOy = (10 + i < Lob.CIKIS_NORON)
                    ? oylar[10 + i] * TARTI_AGIRLIGI : 0.0;
                ds[i] = dm.hayatiOnem * 2.2 + kaynakTalep + dOy;
            }
            softmax(ds);
            for (int i = 0; i < b.demetler.length; i++) {
                if (b.demetler[i] == null) continue;
                b.demetler[i].enerjiPayi = ds[i];
                // Güç, enerji payına yumuşak izler (aç demet zayıflar).
                // GORELI: esit paydaki demetin gucu her zaman 1,0 olmali. Sabit 6,0
                // carpaniyla demet sayisi 10'dan 19'a cikinca tum demetler sebepsizce
                // 0,72'ye zayiflardi — beyaz madde topluca korelirdi.
                double esitDemetPay = 1.0 / b.demetler.length;
                double hedef = 0.4 + (ds[i] / esitDemetPay) * 0.6;
                b.demetler[i].guc = Math.max(0.0, Math.min(2.0,
                    b.demetler[i].guc * 0.9 + hedef * 0.1));
            }
        }
    }

    private static void softmax(double[] x) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : x) if (v > max) max = v;
        double toplam = 0;
        for (int i = 0; i < x.length; i++) {
            x[i] = Math.exp(Math.max(-30, Math.min(30, x[i] - max)));
            toplam += x[i];
        }
        if (toplam <= 0 || !Double.isFinite(toplam)) {
            java.util.Arrays.fill(x, 1.0 / x.length);
            return;
        }
        for (int i = 0; i < x.length; i++) x[i] /= toplam;
    }

    // ─────────────────────────────────────────────────────────
    // ATROFİ / KÖRELME
    // ─────────────────────────────────────────────────────────

    /**
     * Aç kalan lobları söndürür, çok uzun aç kalanı köreltir.
     * Körelme geri döndürülebilir: lobun duyu girdisi yeniden canlanırsa uyanır.
     */
    public static void atrofiKontrol(Beyin b) {
        if (b.loblar == null) return;
        for (Lob l : Lob.LOBLAR) {
            LobDurum d = b.loblar[l.ordinal()];
            if (d == null) continue;

            if (d.koreldi) {
                // Uyanma şansı: bu lobun duyu özellikleri yeniden anlamlı sinyal veriyor mu?
                if (girdiCanlandiMi(b, l)) {
                    d.koreldi = false;
                    d.acKalanSupurme = 0;
                    d.enerjiPayi = Lob.ESIT_PAY;
                    d.hayatiOnem = Math.max(d.hayatiOnem, 0.2);
                    Candy.LOG.debug("[Candy] beyin: {} lobu yeniden uyandı", l);
                }
                continue;
            }

            if (d.enerjiPayi < Lob.ESIT_PAY * ATROFI_ORANI) {
                d.acKalanSupurme++;
                if (d.acKalanSupurme > ATROFI_SUPURME) {
                    sondur(b, l);
                }
                if (d.hayatiOnem < KORELME_ONEM && d.acKalanSupurme > KORELME_SUPURME) {
                    d.koreldi = true;
                    serbestBirak(b, l);
                    Candy.LOG.info("[Candy] beyin: {} lobu KÖRELDİ (önem {}, {} süpürme aç)",
                        l, String.format("%.2f", d.hayatiOnem), d.acKalanSupurme);
                }
            } else {
                d.acKalanSupurme = 0;
            }
        }
    }

    /** Aç lobun ağırlıkları yavaşça söner. */
    private static void sondur(Beyin b, Lob lob) {
        int bas = lob.baslangic(), son = Math.min(lob.bitis(), b.noronSayisi);
        for (int i = bas; i < son; i++) {
            int s = b.girisBasi[i], e = b.girisBasi[i + 1];
            for (int j = s; j < e; j++) b.agirlik[j] *= ATROFI_SONUM;
        }
    }

    /** Körelen lobun sinapsları ölür → slotlar nörogeneze havuz olur. */
    private static void serbestBirak(Beyin b, Lob lob) {
        int bas = lob.baslangic(), son = Math.min(lob.bitis(), b.noronSayisi);
        for (int i = bas; i < son; i++) {
            int s = b.girisBasi[i], e = b.girisBasi[i + 1];
            for (int j = s; j < e; j++) {
                b.agirlik[j] = 0f;
                if (b.sinapsIz != null) b.sinapsIz[j] = 0f;
            }
            b.aktiflik[i] = 0f;
        }
    }

    /** Lobun kendi duyu özellikleri anlamlı sinyal veriyor mu? */
    private static boolean girdiCanlandiMi(Beyin b, Lob lob) {
        if (b.girdi == null) return false;
        double t = 0;
        int n = 0;
        for (int idx : lob.girdiIndeksleri) {
            if (idx >= 0 && idx < b.girdi.length) { t += b.girdi[idx]; n++; }
        }
        return n > 0 && (t / n) > 0.35;
    }

    // ─────────────────────────────────────────────────────────
    // ABLASYON TESTİ — "hangi ağ yaşamak için önemsiz?"
    // ─────────────────────────────────────────────────────────

    /**
     * Ablasyon durum makinesi. Sırayla her lob için:
     * <pre>
     *   1) temel hatayı oku    2) lobu yarı güce kıs
     *   3) 40 tahmin bekle     4) hatadaki bozulmayı ölç
     *   5) hayatiOnem'i güncelle, kazancı geri ver
     * </pre>
     * Hesaplama maliyeti neredeyse sıfır: zaten üretilen tahminlerin hatasını okur.
     */
    public static void ablasyonTick(Beyin b, OyuncuHaritasi harita) {
        if (b.loblar == null || harita == null) return;

        if (b.ablasyonLob < 0) return;   // aktif test yok

        LobDurum d = b.loblar[b.ablasyonLob];
        if (d == null) { b.ablasyonLob = -1; return; }

        // Yeterli tahmin toplandı mı?
        if (harita.tahminSayisi - b.ablasyonBaslangicTahmin < ABLASYON_PENCERE) return;

        double ablasyonHata = harita.sonPencereHatasi();
        double bozulma = ablasyonHata - b.ablasyonTemelHata;
        // 0.25'lik bozulma = tam hayati (Brier'da rastgele tahmin farkı)
        double onem = Math.max(0.0, Math.min(1.0, bozulma / 0.25));
        d.hayatiOnem = kayma(d.hayatiOnem, onem, 0.20);
        d.cikisKazanci = 1.0f;   // kazancı geri ver

        Candy.LOG.debug("[Candy] ablasyon: {} → bozulma {} → hayatiOnem {}",
            Lob.LOBLAR[b.ablasyonLob], String.format("%.3f", bozulma),
            String.format("%.2f", d.hayatiOnem));
        b.ablasyonLob = -1;
    }

    /** Sıradaki lob için ablasyon başlatır (round-robin). */
    public static void ablasyonBaslat(Beyin b, OyuncuHaritasi harita) {
        if (b.loblar == null || harita == null || b.ablasyonLob >= 0) return;
        if (harita.tahminSayisi < ABLASYON_PENCERE) return;   // henüz ölçecek veri yok

        int lob = b.ablasyonSira % Lob.LOBLAR.length;
        b.ablasyonSira++;
        LobDurum d = b.loblar[lob];
        if (d == null || d.koreldi) return;

        b.ablasyonLob = lob;
        b.ablasyonTemelHata = harita.sonPencereHatasi();
        b.ablasyonBaslangicTahmin = harita.tahminSayisi;
        d.cikisKazanci = ABLASYON_KAZANCI;
    }

    private static double kayma(double mevcut, double hedef, double hiz) {
        double r = mevcut + (hedef - mevcut) * hiz;
        return Double.isFinite(r) ? r : mevcut;
    }
}
