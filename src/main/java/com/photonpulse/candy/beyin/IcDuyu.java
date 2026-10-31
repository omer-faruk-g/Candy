package com.photonpulse.candy.beyin;

/**
 * IcDuyu — beynin kendi ic durumunu duyulayan vektor (talamik role).
 *
 * <h3>NEDEN VAR</h3>
 * {@link BeyinGirdi} 48 ozellikle OYUNCUYU anlatir. Ust duzey loblar ise beynin
 * KENDISI hakkinda bilgi ister:
 * <ul>
 *   <li><b>YANKI</b> — "daha once verdigim kararlar tuttu mu?" ({@link KararIzi})</li>
 *   <li><b>TARTI</b> — "kurdugum aglar ise yariyor mu?" ({@link LobDurum}/{@link Demet})</li>
 *   <li><b>GOLGE</b> — "neyi bastirdim, geri donuyor mu?" ({@link GolgeArsivi})</li>
 *   <li><b>BEYINCIK</b> — "ne zaman?" ({@link ZamanIzi})</li>
 * </ul>
 * Bu bilgiyi {@code GIRDI_SAYISI}'na eklemek {@code PersonalityTree.ALANLAR}
 * sozlesmesini ve 10 lobun indeks tablolarinin tamamini kirardi. Bunun yerine her ust
 * duzey lobun ilk {@link #BLOK} noronu sinapstan hesaplanmaz; aktifligi buradan yazilir.
 *
 * <h3>SOZLESME</h3>
 * Tum degerler <b>0..1</b> araligindadir ve <b>notr deger 0,5</b>'tir. Bilgi yoksa 0,5
 * yazilir — 0 yazmak "sifir" degil "guclu olumsuz" anlamina gelirdi ve lobu yanlis
 * egitirdi (aktivasyon ve Oja merkezlemesi 0,5'e gore calisir).
 *
 * <h3>MALIYET</h3>
 * Tick basina degil, duyu tazeleme periyoduna (20 tick) bagli calisir. En pahali parca
 * otokorelasyon (8 gecikme x 96 ornek) ve golge lob paylari (tek gecis, 2048 kayit) —
 * saniyede ~3.000 islem, olculebilir bir yuk degil.
 */
public final class IcDuyu {

    /** Lob basina enjekte noron / ic duyu kanali sayisi. */
    public static final int BLOK = 32;

    /** Notr deger — "bu konuda bilgim yok". */
    public static final float NOTR = 0.5f;

    // Duyu vektorundeki indeksler (PersonalityTree.ALANLAR sirasi)
    private static final int G_INKAR = 5, G_KORKU = 8, G_MEMNUNIYETSIZ = 11,
        G_BASTIRILMIS = 12, G_ONGORULEBILIR = 14, G_COZULME = 16, G_TILT = 19,
        G_KONFOR = 20, G_SIKINTI = 30, G_GORMEZDEN = 34, G_NIYET_KAZI = 35,
        G_NIYET_BEKLE = 38;

    /** Golge lob paylari icin yeniden kullanilan tampon — supurme basina cop uretmesin. */
    private static final ThreadLocal<float[]> LOB_PAYI =
        ThreadLocal.withInitial(() -> new float[Lob.LOBLAR.length]);

    private IcDuyu() {}

    /**
     * Ic duyu vektorunu tazeler. {@link Beyin#icDuyuYaz()} bunu noron aktifligine kopyalar.
     */
    public static void doldur(Beyin b) {
        if (b == null) return;
        if (b.icDuyu == null || b.icDuyu.length != Lob.TOPLAM_ENJEKTE) {
            b.icDuyu = new float[Lob.TOPLAM_ENJEKTE];
        }
        final float[] v = b.icDuyu;

        yanki(b, v, Lob.YANKI.icDuyuBlok());
        tarti(b, v, Lob.TARTI.icDuyuBlok());
        golge(b, v, Lob.GOLGE.icDuyuBlok());
        beyincik(b, v, Lob.BEYINCIK.icDuyuBlok());

        // Son savunma: tek bir NaN, bagli lobun tum katmanlarini sessizce oldururdu.
        for (int i = 0; i < v.length; i++) {
            if (!Float.isFinite(v[i])) v[i] = NOTR;
            else if (v[i] < 0f) v[i] = 0f;
            else if (v[i] > 1f) v[i] = 1f;
        }
    }

    // ─────────────────────────────────────────────────────────
    // YANKI — "daha once verdigim kararlar tuttu mu?"
    // ─────────────────────────────────────────────────────────

    private static void yanki(Beyin b, float[] v, int o) {
        KararIzi iz = b.kararIzi;
        if (iz == null) { sabit(v, o, NOTR); return; }
        final int E = KararIzi.EYLEM_SAYISI;
        int toplam = Math.max(1, iz.toplamKarar());

        for (int e = 0; e < E; e++) {
            // Odul olcegi -0,5..1,5 -> 0..1
            yaz(v, o + e, (iz.sayi[e] == 0) ? NOTR : (iz.ortOdul[e] + 0.5f) / 2f);
            // Ongoru hatasi: 0..15 korku birimi -> 0..1
            yaz(v, o + 6 + e, (iz.sayi[e] == 0) ? NOTR : iz.ortHata[e] / 15f);
            // Bu eylemi ne siklikta sectim
            yaz(v, o + 12 + e, iz.sayi[e] / (float) toplam);
            // TUTARSIZLIK: ayni eylem farkli sonuclar mi veriyor?
            yaz(v, o + 24 + e, (iz.sayi[e] < 2) ? NOTR
                : (float) Math.sqrt(Math.max(0f, iz.varOdul[e])) / 0.6f);
        }
        // Son 6 kararin odulu — sirali, en yenisi once
        for (int i = 0; i < KararIzi.SON_KARAR; i++) {
            yaz(v, o + 18 + i, (iz.dolu <= i) ? NOTR : (iz.sonOdul(i) + 0.5f) / 2f);
        }
        yaz(v, o + 30, iz.pismanlik());
        yaz(v, o + 31, iz.tutarsizlik());
    }

    // ─────────────────────────────────────────────────────────
    // TARTI — "kurdugum aglar ise yariyor mu?"
    // ─────────────────────────────────────────────────────────

    private static void tarti(Beyin b, float[] v, int o) {
        final Lob[] loblar = Lob.LOBLAR;
        // 0-9: ablasyonla OLCULEN lob onemleri (kanaat degil, olcum)
        // 10-19: fiili enerji paylari, esit paya gore normalize
        for (int i = 0; i < loblar.length && i < 10; i++) {
            LobDurum d = (b.loblar != null) ? b.loblar[i] : null;
            yaz(v, o + i, (d == null) ? NOTR : (float) d.hayatiOnem);
            yaz(v, o + 10 + i, (d == null) ? NOTR
                : (float) (d.enerjiPayi / (2.0 * Lob.ESIT_PAY)));
        }
        // 20-27: EN KALIN 8 demetin "ne kadar isime yariyor" skoru (onem x guc)
        int yazilan = 0;
        if (b.demetler != null) {
            for (int i = 0; i < b.demetler.length && yazilan < 8; i++) {
                Demet dm = b.demetler[i];
                if (dm == null) continue;
                yaz(v, o + 20 + yazilan, (float) (dm.hayatiOnem * dm.guc / 2.0));
                yazilan++;
            }
        }
        while (yazilan < 8) { yaz(v, o + 20 + yazilan, NOTR); yazilan++; }

        // 28-31: agin genel sagligi
        yaz(v, o + 28, (float) (ortAgirlikNormu(b) / 0.5));
        long bud = Math.max(0L, b.budananToplam), dog = Math.max(0L, b.doganToplam);
        yaz(v, o + 29, (bud + dog == 0) ? NOTR : bud / (float) (bud + dog));
        OyuncuHaritasi h = b.oyuncuHaritasi;
        yaz(v, o + 30, (h == null) ? NOTR : 1f - (float) h.sonPencereHatasi());
        yaz(v, o + 31, (b.ozModel == null) ? NOTR : (float) b.ozModel.tutarlilik);
    }

    // ─────────────────────────────────────────────────────────
    // GOLGE — "neyi bastirdim, geri donuyor mu?"
    // ─────────────────────────────────────────────────────────

    private static void golge(Beyin b, float[] v, int o) {
        GolgeArsivi a = b.golgeArsivi;
        if (a == null) { sabit(v, o, NOTR); return; }
        yaz(v, o, a.doluluk());
        yaz(v, o + 1, a.ortBuyukluk());
        yaz(v, o + 2, (b.doganToplam <= 0) ? 0f
            : a.dirilenToplam / (float) Math.max(1L, b.doganToplam));
        yaz(v, o + 3, (b.sinapsSayisi <= 0) ? NOTR
            : Math.min(1f, b.budananToplam / (float) b.sinapsSayisi));

        // 4-13: hangi lobu ne kadar bastirdim (tek gecis)
        float[] pay = LOB_PAYI.get();
        a.lobPaylari(b, pay);
        for (int i = 0; i < 10; i++) {
            yaz(v, o + 4 + i, (i < pay.length) ? pay[i] : 0f);
        }
        // 14-19: bastirmanin DAVRANISSAL karsiligi (oyuncunun kendi bastirmasi)
        yaz(v, o + 14, girdi(b, G_BASTIRILMIS));
        yaz(v, o + 15, girdi(b, G_MEMNUNIYETSIZ));
        yaz(v, o + 16, girdi(b, G_INKAR));
        yaz(v, o + 17, girdi(b, G_SIKINTI));
        yaz(v, o + 18, girdi(b, G_COZULME));
        yaz(v, o + 19, girdi(b, G_GORMEZDEN));
        // 20-31: EN SON bastirilan 12 baglantinin gucu — "su an neyi bastiriyorum?"
        // Sabit 0,5 dolgu birakmak bedava degildi: Oja icin sabit girdi xm=0 demektir,
        // agirlik sifira soner ve 12 kanal fiilen olu sinaps olurdu. Gercek sinyal koy.
        for (int i = 0; i < 12; i++) yaz(v, o + 20 + i, a.sonBuyukluk(i));
    }

    // ─────────────────────────────────────────────────────────
    // BEYINCIK — "ne zaman?"
    // ─────────────────────────────────────────────────────────

    private static void beyincik(Beyin b, float[] v, int o) {
        ZamanIzi z = b.zamanIzi;
        if (z == null) { sabit(v, o, NOTR); return; }
        // 0-7: korku egrisinin gecikmeli otokorelasyonu -> RITIM
        float enGuclu = NOTR;
        for (int g = 1; g <= ZamanIzi.GECIKME_SAYISI; g++) {
            float r = z.otokorelasyon(g);
            yaz(v, o + g - 1, r);
            if (r > enGuclu) enGuclu = r;
        }
        // 8-13: son 6 korku ornegi (egrinin sekli)
        for (int i = 0; i < 6; i++) yaz(v, o + 8 + i, z.sonKorku(i));
        yaz(v, o + 14, z.egim());
        yaz(v, o + 15, z.ivme());
        yaz(v, o + 16, z.rehavet());
        yaz(v, o + 17, enGuclu);
        // 18-23: son 6 etkinlik ornegi
        for (int i = 0; i < 6; i++) yaz(v, o + 18 + i, z.sonEtkinlik(i));
        yaz(v, o + 24, z.dolu / (float) ZamanIzi.KAPASITE);
        yaz(v, o + 25, girdi(b, G_KONFOR));
        yaz(v, o + 26, girdi(b, G_TILT));
        yaz(v, o + 27, girdi(b, G_NIYET_BEKLE));
        yaz(v, o + 28, girdi(b, G_NIYET_KAZI));
        yaz(v, o + 29, girdi(b, G_ONGORULEBILIR));
        yaz(v, o + 30, girdi(b, G_KORKU));
        yaz(v, o + 31, girdi(b, G_COZULME));
    }

    // ─────────────────────────────────────────────────────────
    // Yardimcilar
    // ─────────────────────────────────────────────────────────

    private static double ortAgirlikNormu(Beyin b) {
        // Tam tarama pahali (1,5M) — 512 sinapslik duzgun ornekleme yeterince kararli.
        if (b.agirlik == null || b.sinapsSayisi <= 0) return 0.0;
        int adim = Math.max(1, b.sinapsSayisi / 512);
        double t = 0;
        int n = 0;
        for (int j = 0; j < b.sinapsSayisi; j += adim) { t += Math.abs(b.agirlik[j]); n++; }
        return n == 0 ? 0.0 : t / n;
    }

    private static float girdi(Beyin b, int idx) {
        if (b.girdi == null || idx < 0 || idx >= b.girdi.length) return NOTR;
        float g = b.girdi[idx];
        return Float.isFinite(g) ? g : NOTR;
    }

    private static void yaz(float[] v, int idx, float deger) {
        if (idx < 0 || idx >= v.length) return;
        if (!Float.isFinite(deger)) deger = NOTR;
        v[idx] = Math.max(0f, Math.min(1f, deger));
    }

    private static void sabit(float[] v, int o, float deger) {
        for (int i = 0; i < BLOK && o + i < v.length; i++) v[o + i] = deger;
    }
}
