package com.photonpulse.candy.beyin;

import com.photonpulse.candy.tree.TreeBranch;

/**
 * Lob — Candy beyninin 10 lobu (v5.0; v4'te 6, v3'te 3).
 *
 * Her lob {@link TreeBranch} ile 1:1 eşleşir: beyin hesabın kendisidir, ağaç ise okuma
 * yüzeyi (readout). Lobun çıkış nöronlarının ortalaması, karşılık gelen ağaç kökünün
 * {@code aktiflik} değerini sürer — böylece mevcut tüketiciler (CloneManager, SignSystem,
 * MobSpawnBooster, TickAccelerator) hiç değişmeden çalışmaya devam eder.
 *
 * Toplam (v5): 6 temel lob (67.584) + 4 ust duzey lob (34.816) = 102.400 nöron.
 * Lob boyutlari ESIT DEGIL: ONCUL (karar yakinsama noktasi) en buyuk ve en derin
 * lobdur; duyusal/degerlendirici loblar daha kucuk yeter.
 */
public enum Lob {

    /** Oksipital — duyusal ön-işleme, "retina". Ham sinyali özelliğe çevirir. */
    ARDIL(TreeBranch.ARDIL, 10240, 3, new int[]{
        0, 1, 2, 3,                        // hareket simpleksi
        35, 36, 37, 38, 39, 40, 41,        // niyet one-hot
        46, 47                             // tetikleyiciler
    }),

    /** Parietal — uzam/rota entegrasyonu: neresi, ne kadar uzak, nereye döner. */
    YANLAMA(TreeBranch.IZLEME, 10240, 4, new int[]{
        0, 2, 3,                           // kac / don / kesfet
        14, 17, 20,                        // ongorulebilirlik, riskIstahi, konforSkoru
        37, 38, 39,                        // niyet: kesfet / bekle / don
        52                                 // arkayaBakma (v8.2) — uzamsal dikkat
    }),

    /** Temporal — dil, hafıza, örüntü: kelimeler, davranış imzası, Markov niyet. */
    SAKAK(TreeBranch.ETKILESIM, 14336, 5, new int[]{
        4, 5, 6,                           // aciklik, inkarEgilimi, kesfetmeIstegi
        14, 25, 26,                        // ongorulebilirlik, kehanetGuveni, kehanetOlasilik
        44, 42, 43, 45,                    // hipotezler
        35, 36,                            // niyet: kaziyor / insa
        55                                 // tuzakIlgisi (v8.3) — bulmaca kovalama
    }),

    /** İnsula — interosepsiyon: oyuncunun İÇ durumu. */
    ADACIK(TreeBranch.ADACIK, 8192, 3, new int[]{
        7, 8, 10,                          // panikEsigi, korkuSkoru, psikolojiKotulugu
        16, 19, 21, 22,                    // cozulme, tilt, paranoya, metaFarkindalik
        15,                                // yetenekSkoru
        52,                                // arkayaBakma (v8.2) — paranoyanin govde isareti
        54                                 // arkadasYasi (v8.3) — yas bir ic durum
    }),

    /** Limbik — ödül/ceza/travma → nöromodülatör yayını (Aşama 4'te plastisiteyi sürer). */
    KENAR(TreeBranch.KENAR, 8192, 3, new int[]{
        27, 28, 29, 30,                    // duygu vektörü
        11, 12,                            // memnuniyetsizlik, bastirilmisBaski
        8, 9,                              // korkuSkoru, candyOzguven
        48,                                // candyOfke (v8)
        53,                                // kacisArayisi (v8.2) — ofkenin ikinci kaynagi
        54                                 // arkadasYasi (v8.3) — sonmeyen ucuncu kaynak
    }),

    /** Frontal/PFC — karar, planlama, inhibisyon. En derin lob. */
    ONCUL(TreeBranch.TEPKI, 16384, 6, new int[]{
        9, 23, 24, 18,                     // candyOzguven, ozFarkindalik, sasirma, ogrenmeHizi
        13, 17,                            // riskToleransi, riskIstahi
        31, 32, 33, 34,                    // Candy'ye tepki profili
        42, 43, 45,                        // hipotezler
        1,                                 // saldirOrani
        48, 49,                            // candyOfke, vagusTahmin (v8)
        52, 53,                            // arkayaBakma, kacisArayisi (v8.2)
        54, 55                             // arkadasYasi, tuzakIlgisi (v8.3)
    }),

    /**
     * YANKI — retrospeksiyon: "daha once verilmis kararlari sorgulayarak anlam cikarma".
     *
     * Gecmis kararlarin (eylem + baglam + olculen sonuc) izini isler; cikisi hem ONCUL'e
     * ders olarak gider hem de ogrenme hizini module eder: kararlarim tutmadiysa daha
     * hizli degis.
     */
    YANKI(TreeBranch.YANKI, 10240, 4, 32, new int[]{
        18, 24, 25, 26,                    // ogrenmeHizi, sasirma, kehanetGuveni/Olasilik
        23, 22,                            // ozFarkindalik, metaFarkindalik
        9,                                 // candyOzguven
        49, 50, 51                         // Vagus: tahmin / kalibrasyon / darbe (v8)
    }),

    /**
     * TARTI — meta-degerlendirme: "kurulan aglarin ise yararliligini sorgulama".
     *
     * Ablasyonla OLCULEN lob onemlerini ve demet gucleri okur; cikisi enerji dagitimini
     * etkiler. Kendi metabolizmasini yoneten lob — ama olcum her zaman kanaati yener.
     */
    TARTI(TreeBranch.TARTI, 6144, 3, 32, new int[]{
        22, 23, 14,                        // metaFarkindalik, ozFarkindalik, ongorulebilirlik
        15, 20,                            // yetenekSkoru, konforSkoru
        18,                                // ogrenmeHizi
        50, 51                             // Vagus: kalibrasyon / darbe (v8)
    }),

    /**
     * GOLGE — bastirilmis oruntuler (bilincdisi).
     *
     * Budanan sinapslarin izi burada tutulur; GOLGE aktiflestikce norogenez yeni baglanti
     * yerine ESKI, bastirilmis desenleri diriltir. "Bastirilan geri doner" — hem sinaps
     * hem davranis duzeyinde. Mevcut Bastirma/ShadowTree temasiyla ortusur.
     */
    GOLGE(TreeBranch.GOLGE, 8192, 3, 32, new int[]{
        12, 11, 5,                         // bastirilmisBaski, memnuniyetsizlik, inkarEgilimi
        30, 16,                            // duyguSikinti, cozulme
        34                                 // candyGormezden
    }),

    /**
     * BEYINCIK — zamanlama: "ne zaman?"
     *
     * Kehanet "ne yapacak" der; bu lob "ne zaman" der. Korku/aktivite zaman serisinden
     * oyuncunun ritmini ogrenir (ne zaman rehavete kapiliyor) ve ZihinselProva'nin
     * cooldown'unu ogrenilen bir kapiya cevirir.
     */
    BEYINCIK(TreeBranch.BEYINCIK, 10240, 4, 32, new int[]{
        20, 16, 19,                        // konforSkoru, cozulme, tiltSeviyesi
        8, 14,                             // korkuSkoru, ongorulebilirlik
        38, 35                             // niyet: bekle / kaziyor
    });

    /** Bu lobun sürdüğü ağaç kökü. */
    public final TreeBranch dal;
    /** Lobdaki nöron sayısı. */
    public final int noronSayisi;
    /** Lob içi katman sayısı (derinlik). */
    public final int katmanSayisi;
    /** Bu lobun doğrudan gördüğü duyu özelliklerinin indeksleri (BeyinGirdi sırasına göre). */
    public final int[] girdiIndeksleri;

    /**
     * ENJEKTE NORON sayisi — bu noronlarin aktifligi sinapstan HESAPLANMAZ, disaridan
     * yazilir (talamik role). {@link Beyin#icDuyu} icerigi buraya kopyalanir.
     *
     * Yeni loblar 48 duyu ozelliginde bulunmayan bilgiye (karar gecmisi, lob onemleri,
     * budama arsivi, zaman serisi) ihtiyac duyar. GIRDI_SAYISI'ni buyutmek
     * PersonalityTree.ALANLAR sozlesmesini ve tum lob indeks tablolarini kirardi;
     * enjekte noron bunu kirmadan cozer.
     */
    public final int enjekte;

    /**
     * CIKIS SUTUNU — her lobun son bu kadar noronu ayri bir OKUMA BASLIGI'dir.
     *
     * v5 KRITIK: bu noronlar lobun son genis katmanindan {@link #CIKIS_FANIN} genisliginde
     * DUZGUN orneklenir. Aksi halde buyuk lobda 24 neredeyse bagimsiz noronun ortalamasi
     * merkeze (0,5) yakinsar; beyin calisir, CPU yanar ama agaca hicbir bilgi gitmez —
     * olceklendikce KOTULESEN, sessiz bir arizadir.
     */
    public static final int CIKIS_NORON = 32;

    /** Cikis sutunu noronunun son genis katmandan aldigi fan-in. */
    public static final int CIKIS_FANIN = 192;

    /**
     * YEREL PENCERE yaricapi. Derin katman noronu onceki katmanin TAMAMINDAN degil,
     * kendi oransal konumu etrafindaki +-bu kadar noronluk banttan orneklenir.
     *
     * Iki kazanc: (1) komsu noronlarin girdi kumeleri buyuk olcude ortusur -> ilinti
     * korunur, cikis olcekten bagimsiz olarak canli kalir; (2) gather her zaman L1'de
     * kalir -> ileri besleme 2-2,5x hizlanir.
     */
    public static final int PENCERE_YARI = 96;

    /**
     * Topoloji olcegi — {@code -Dcandy.beyin.olcek} ile ayarlanir (varsayilan 1.0).
     * Buyuk olcekte test/geri cekilme sigortasi: 0.5 ile yarim beyin tek bayrakla denenir.
     */
    public static final float OLCEK = olcekOku();

    /** Baglanti semasi surumu — degisirse eski topoloji gecersizlesir, kunye goc eder. */
    public static final int TOPOLOJI_SURUMU = 2;

    /**
     * Kurul modunda ÜYE BAŞINA varsayilan olcek.
     *
     * v6'da her oyuncunun 11 beyni var. Uye basina 1,0 (102.400 noron) secilse kurul
     * 1.126.400 norona, oyuncu basina ~216 MB RAM ve ~57 MB diske cikardi — normal bir
     * sunucuyu dusururdu. 0,35 ile uye ~35.840 noron, kurul 394.240 noron: bugunku TEK
     * beynin 3,85 kati, ~75 MB. Istege bagli olarak {@code -Dcandy.beyin.olcek=1.0} ile
     * 11 tam beyne cikilir.
     *
     * Not: kucuk uyelerden olusan bir komite, ayni toplam buyuklukteki tek bir agdan
     * genellikle daha iyi karar verir — kazanc parametre sayisindan degil, HATALARIN
     * BAGIMSIZLIGINDAN gelir.
     */
    private static final float KURUL_VARSAYILAN_OLCEK = 0.35f;

    private static float olcekOku() {
        try {
            String v = System.getProperty("candy.beyin.olcek");
            if (v == null) return KURUL_VARSAYILAN_OLCEK;
            float f = Float.parseFloat(v);
            return (f > 0.05f && f <= 4.0f) ? f : KURUL_VARSAYILAN_OLCEK;
        } catch (Throwable t) {
            return KURUL_VARSAYILAN_OLCEK;
        }
    }

    /** Olcege gore gercek noron sayisi (256'nin katina yuvarlanir). */
    public int noronSayisi() {
        if (OLCEK == 1.0f) return noronSayisi;
        int n = Math.round(noronSayisi * OLCEK / 256f) * 256;
        return Math.max(256, n);
    }

    Lob(TreeBranch dal, int noronSayisi, int katmanSayisi, int[] girdiIndeksleri) {
        this(dal, noronSayisi, katmanSayisi, 0, girdiIndeksleri);
    }

    Lob(TreeBranch dal, int noronSayisi, int katmanSayisi, int enjekte, int[] girdiIndeksleri) {
        this.dal = dal;
        this.noronSayisi = noronSayisi;
        this.katmanSayisi = katmanSayisi;
        this.enjekte = enjekte;
        this.girdiIndeksleri = girdiIndeksleri;
    }

    /** Bu lobun enjekte noronlarinin Beyin.icDuyu icindeki blok baslangici. */
    public int icDuyuBlok() {
        int t = 0;
        for (Lob l : LOBLAR) {
            if (l == this) return t;
            t += l.enjekte;
        }
        return t;
    }

    /**
     * Lob sabitlerinin TEK kopyasi.
     *
     * {@code values()} her cagrida diziyi KLONLAR — dil bunu zorunlu kilar, cunku enum
     * dizisi disariya sizarsa cagiran onu degistirebilirdi. Sicak yollarda bu gercek bir
     * maliyettir: {@code Plastisite.norogenez} pencere basina iki kez cagiriyordu,
     * pencere {@code max(256, n/32)} ve bu 11 uye icin supurme basina ~512 adet
     * {@code Lob[10]} tahsisi demekti. Tek paylasilan dizi tahsisi tamamen kaldirir.
     *
     * <b>Donen diziyi DEGISTIRME</b> — paylasilan ortak durumdur.
     */
    public static final Lob[] LOBLAR = values();

    /**
     * Esit dagilimda bir lobun enerji payi (1/lobSayisi).
     *
     * ENERJI ESIKLERI MUTLAK OLAMAZ. Softmax toplami her zaman 1,0'dir; lob sayisi 6'dan
     * 10'a cikinca esit pay 0,167'den 0,100'e duser. Eski mutlak esik 0,15 ile HER LOB
     * kalici olarak "ac" sayilir, supurmelerin yarisini atlar ve beyin sebepsiz yere
     * yariya yavaslardi — olcekle birlikte gelen, tamamen sessiz bir arizadir.
     */
    public static final double ESIT_PAY = 1.0 / LOBLAR.length;

    /**
     * Enjekte noronlarin TOPLAMI — {@link Beyin#icDuyu} vektorunun uzunlugu.
     *
     * Enum sabitleri diger statik alanlardan ONCE kurulur, bu yuzden burada values()
     * cagirmak guvenlidir.
     */
    public static final int TOPLAM_ENJEKTE = toplamEnjekteHesapla();

    private static int toplamEnjekteHesapla() {
        int t = 0;
        for (Lob l : LOBLAR) t += l.enjekte;
        return t;
    }

    // ─────────────────────────────────────────────────────────
    // TOPOLOJI GEOMETRISI — tek dogruluk kaynagi
    //
    // Bu formuller once BeyinInsaci icinde UC AYRI YERDE kopyalanmisti (katman kurulumu,
    // cikis sutunu, demet kaynagi). Enjekte blogu eklenince ucunun de kaymasi gerekiyordu;
    // birinin unutulmasi sessiz bir topoloji bozulmasi olurdu. Artik lob kendini tarif eder.
    // ─────────────────────────────────────────────────────────

    /** Enjekte blogundan SONRAKI, gercekten hesaplanan noron sayisi. */
    public int govdeSayisi() {
        return Math.max(Math.max(1, katmanSayisi) + CIKIS_NORON, noronSayisi() - enjekte);
    }

    /** Hesaplanan govdenin global baslangic indeksi (enjekte blogu atlanmis). */
    public int govdeBaslangic() {
        return baslangic() + enjekte;
    }

    /** Cikis sutunu HARIC, katmanlara bolunen noron sayisi. */
    public int katmanliAdet() {
        int kat = Math.max(1, katmanSayisi);
        return Math.max(kat, govdeSayisi() - CIKIS_NORON);
    }

    /** Bir katmanin noron sayisi. */
    public int katmanBoyu() {
        return Math.max(1, katmanliAdet() / Math.max(1, katmanSayisi));
    }

    /** Son genis katmanin baslangici — cikis sutununun ve giden demetlerin kaynagi. */
    public int sonKatmanBaslangic() {
        return govdeBaslangic() + (Math.max(1, katmanSayisi) - 1) * katmanBoyu();
    }

    /** Son genis katmanin bitisi (haric). */
    public int sonKatmanBitis() {
        return govdeBaslangic() + katmanliAdet();
    }

    /** Tüm lobların toplam nöron sayısı (olcek uygulanmis). */
    public static int toplamNoron() {
        int t = 0;
        for (Lob l : LOBLAR) t += l.noronSayisi();
        return t;
    }

    /** Bu lobun global nöron dizisindeki başlangıç indeksi. */
    public int baslangic() {
        int t = 0;
        for (Lob l : LOBLAR) {
            if (l == this) return t;
            t += l.noronSayisi();
        }
        return t;
    }

    /** Bu lobun global nöron dizisindeki bitiş indeksi (hariç). */
    public int bitis() {
        return baslangic() + noronSayisi();
    }
}
