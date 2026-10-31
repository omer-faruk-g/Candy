package com.photonpulse.candy.beyin;

import java.util.UUID;

/**
 * Beyin — saf veri kabı + ileri besleme. Karar mantığı YOK (o BeyinMotoru'nda).
 *
 * VERİ DÜZENİ: nesne değil, "struct of arrays" (SoA) primitif diziler. 102.400 nöronu
 * ayrı nesneler olarak tutmak ~5 MB nesne başlığı + rastgele bellek erişimi demekti;
 * diziler ise cache-dostu ve Gson/ikili serileştirmeye doğrudan uygun.
 *
 * SEYREK GRAF (CSR): nöron i'nin gelen sinapsları {@code [girisBasi[i], girisBasi[i+1])}
 * aralığındadır; {@code kaynak[j]} kaynağın indeksi, {@code agirlik[j]} ağırlığıdır.
 *
 * KAYNAK İNDEKSLEME (kritik):
 * <pre>
 *   0 .. GIRDI_SAYISI-1        → duyu özelliği (BeyinGirdi.topla çıktısı)
 *   GIRDI_SAYISI .. +noron-1   → başka bir nöronun aktifliği
 * </pre>
 * Toplam adres uzayı 48 + 102.400 = 102.448 < 131.072, yani 17 bit'e sığar — ikili
 * kalıcılıkta (v2 formatı) sinaps başına 4 byte'lık paket bu sayede mümkün:
 * 17 bit kaynak + 12 bit nicelenmiş ağırlık + 1 bit "nörogenezle doğdu".
 *
 * ÖLÇEK (v5): 102.400 nöron, ~1,55M sinaps, 10 lob, 19 demet.
 */
public class Beyin {

    /** Duyu özelliği sayısı — {@link BeyinGirdi#OZELLIK_ADLARI} ile aynı olmalı. */
    public static final int GIRDI_SAYISI = 56;
    /**
     * Noron adres tavani. v2 ikili formatinda kaynak indeksi 17 bit (0..131.071);
     * adres uzayi GIRDI_SAYISI + noron oldugu icin tavan 131.072 - GIRDI_SAYISI.
     */
    public static final int MAX_NORON = 131_072 - GIRDI_SAYISI;
    /** Sert sinaps tavani — 102.400 noronluk hedef ~1,58M sinaps; buyumeye pay. */
    public static final int MAX_SINAPS = 2_000_000;
    /**
     * Ileri beslemenin kac dilimde tamamlandigi.
     *
     * 8 -> 128: buyuk beyinde tam supurme tek seferde degil, cok daha ince parcalara
     * yayilir. Tick basina dusen is DILIM_BUTCESI ile ayrica sinirlanir (BeyinMotoru).
     */
    public static final int DILIM_SAYISI = 128;

    // ── Künye ──
    /**
     * Kalıcılık şeması sürümü.
     *
     * <p>1 → ilk sürüm. 2 → {@code toplamSupurme} yeniden ölçeklendi ({@link BeyinGocu}).
     * <b>3 (v8.0)</b> → duyu bloğu 48'den 52'ye büyüdü, yani adres uzayı kaydı; diskteki
     * sinapsların nöron adresleri {@link #onar()} içinde taşınır.
     * <b>4 (v8.2)</b> → duyu bloğu 52'den 54'e büyüdü ({@code arkayaBakma},
     * {@code kacisArayisi}); aynı kaydırma yeniden gerekti.</p>
     */
    public static final int SEMA_SURUMU = 5;

    /**
     * Şema sürümünün diskteki duyu genişliği — adres kaydırma göçünün tek bildiği
     * tarihsel tablo. Dizi uzunluğundan türetilemez (sinaps dizisi duyu sayısını
     * taşımaz), bu yüzden her duyu büyümesinde buraya bir satır eklenir.
     *
     * <p>v8.2 DERS: tek bir {@code ESKI_GIRDI} sabiti ikinci büyümede yetmiyor. 52
     * genişlikli bir beyni "48'den geliyor" sayıp 6 kaydırmak, aylarca öğrenilmiş
     * ~1,5M sinapsı sessizce yanlış nörona bağlardı — ne çökme ne exception.</p>
     */
    private static int duyuGenisligi(int sema) {
        if (sema <= 2) return 48;   // v4-v7
        if (sema == 3) return 52;   // v8.0 - v8.1
        if (sema == 4) return 54;   // v8.2
        return GIRDI_SAYISI;        // güncel
    }

    public int semaSurumu = SEMA_SURUMU;

    /** Göçte kaydırılan sinaps sayısı (yalnızca izleme/log için; kalıcı değil). */
    public transient int gocKaydirilanSinaps = 0;
    public UUID oyuncuId;

    /**
     * KURUL ÜYE NUMARASI (0..{@link Kurul#UYE_SAYISI}-1).
     *
     * <b>Üye 0 "gövde"dir</b>: kişilik ağacını süren, tüm mevcut tüketicilerin okuduğu ve
     * oyuncuya mesaj sızdıran tek beyin odur. Diğer üyeler yalnızca karara katılır.
     * Böylece komite eklenirken hiçbir eski davranış değişmez.
     */
    public int uyeNo = 0;

    /**
     * MİZAÇ — plastisite çarpanı (0,6..1,4).
     *
     * Aynı bilgiyi alan özdeş on bir beyin, on bir kez aynı cevabı verirdi; komite ancak
     * üyeler FARKLI hata yaparsa tek üyeden iyi olur. Kablolama farkı (tohum) bunun
     * yarısıdır, öğrenme hızı farkı diğer yarısı: kimi üye çabuk öğrenip çabuk unutur,
     * kimi ağır ve inatçıdır. İkisi aynı veriden farklı sonuç çıkarır.
     */
    public float mizacPlastisite = 1.0f;

    /** MİZAÇ — öğrenilene atılganlık (0,2..0,8): Q'ya mı güvenir, sezgisele mi yapışır. */
    public float mizacAtilganlik = 0.5f;

    /** Bu üyenin ÖLÇÜLEN karnesi — hakemin ağırlığı buradan gelir. */
    public UyeSicili sicil = new UyeSicili();

    /** Aort'un bu beyne teslim ettiği son nabız — bütünlük sınaması için. */
    public transient long nabiz = 0L;
    public long toplamSupurme = 0L;
    public int noronSayisi;
    public int sinapsSayisi;
    /** Topoloji olcegi (-Dcandy.beyin.olcek). Uyusmazsa topoloji yeniden insa edilir. */
    public float olcek = 1.0f;
    /** Deterministik topoloji tohumu (oyuncu UUID'sinden). */
    public long topolojiTohumu = 0L;
    /** Topoloji surumu — baglanti semasi degisirse artar, eski topoloji gecersizlesir. */
    public int topolojiSurumu = 1;

    // ── Nöron dizileri (uzunluk = noronSayisi) ──
    // transient: bu diziler KUNYE JSON'una degil, kompakt ikili dosyaya yazilir
    // (IkiliKodlayici). Gson ile yazilsalar ~4-10 MB ve ana thread'de 150-400 ms surerdi.
    public transient float[] aktiflik;   // 0..1 — nöron çıktısı
    public transient float[] esik;       // bias
    public transient byte[] lobId;       // hangi lob (Lob.ordinal)
    public transient byte[] katman;      // lob içi katman derinliği

    // ── Sinaps dizileri (CSR) — ikili dosyada ──
    public transient int[] girisBasi;    // uzunluk noronSayisi+1
    public transient int[] kaynak;       // uzunluk sinapsSayisi
    public transient float[] agirlik;    // uzunluk sinapsSayisi

    // ── Lob / demet durumları ──
    public LobDurum[] loblar;
    public Demet[] demetler;

    /** Candy'nin oyuncu hakkındaki öngörücü modeli ("beyin haritası"). */
    public OyuncuHaritasi oyuncuHaritasi = new OyuncuHaritasi();
    /** Candy'nin kendisi hakkındaki modeli (iddialar, tutarlılık, çelişki). */
    public OzModel ozModel = new OzModel();

    /**
     * Q BAŞLIĞI (Aşama 9) — ZihinselProva'nın 6 eylemi için ÖĞRENİLEN değer fonksiyonu.
     *
     * ZihinselProva'da zaten tam bir pekiştirmeli öğrenme döngüsü vardı: durum, politika,
     * eylem uzayı, ε-keşif, 200 tick gecikmeli ödül ve hata ölçümü. Eksik olan TEK ŞEY
     * hatanın ağırlıklara geri akmasıydı — sezgisel {@code tahminEt()} sabit kodluydu.
     * Bu başlık o döngüyü kapatır.
     */
    /**
     * Q BAŞLIĞININ GİRDİSİ: 48 duyu özelliği + 10 lob çıkışı.
     *
     * <h3>NEDEN LOB ÇIKIŞLARI DA</h3>
     * Q başlığı önce yalnızca ham duyuyu okuyordu — yani 35.584 nöronluk ağ, eylem
     * seçimine <b>hiç girmiyordu</b>. Ağ ağacı sürüyor, ağaç davranışı süslüyordu; ama
     * "hangi eylem" kararı beyni atlıyordu.
     *
     * Kurulda bu ayrıca ölümcül: on bir üye aynı aortdan aynı 48 sayıyı alır, aynı
     * gradyanı görür ve Q başlıkları neredeyse ÖZDEŞ öğrenir. Üyeler ayırt edilemez hale
     * gelir, hakem her nabızda %100 anlaşma raporlar ve komite pahalı bir kopyalayıcıya
     * dönüşür. Lob çıkışları her üyenin ÖZEL okumasıdır (farklı topoloji, farklı ağırlık,
     * farklı mizaç) — çeşitliliğin girdiye girdiği yer burasıdır.
     */
    public static final int Q_GIRDI = GIRDI_SAYISI + 10;

    public float[][] qBasligi = new float[6][Q_GIRDI];
    public float[] qBias = new float[6];
    /** Kaç kez güncellendi — sezgiselden öğrenilene geçiş hızını belirler. */
    public int qGuncelleme = 0;

    // ── Dünyalar arası doğrulama (Aşama 8) ──
    /** Son 8 dünyanın doğrulama kaydı. */
    public java.util.List<Dogrulama> dunyalar = new java.util.ArrayList<>();
    /** İçinde bulunulan dünyanın kaydı (transient — her girişte tazelenir). */
    public transient Dogrulama aktifDunya;
    /** Kör doğrulama modunun biteceği süpürme (0 = kör mod kapalı). */
    public transient long dogrulamaBitis = 0L;
    /** Plastisite çarpanı: kör modda 0.20 (önce ölç, sonra öğren), sonra 1.0. */
    public transient float plastisiteCarpani = 1.0f;

    // ── Ablasyon testi durumu (hayatiOnem ÖLÇÜMÜ) ──
    /** Şu an ablasyona uğrayan lob (-1 = test yok). */
    public transient int ablasyonLob = -1;
    /** Round-robin sırası. */
    public int ablasyonSira = 0;
    public transient double ablasyonTemelHata = 0.5;
    public transient long ablasyonBaslangicTahmin = 0L;

    /** Anlık duyu girdisi — diske YAZILMAZ (her tick yeniden üretilir). */
    public transient float[] girdi = new float[GIRDI_SAYISI];

    /**
     * IC DUYU (v5) — beynin KENDI ic durumunu duyulayan vektor.
     *
     * 48 duyu ozelligi oyuncuyu anlatir; ust duzey loblar (YANKI, TARTI, GOLGE, BEYINCIK)
     * ise beynin KENDISI hakkinda bilgi ister: gecmis kararlarin sonucu, lob onemleri,
     * budama arsivi, zaman serisi. Bunlari GIRDI_SAYISI'na eklemek
     * {@code PersonalityTree.ALANLAR} sozlesmesini ve tum lob indeks tablolarini kirardi.
     *
     * Bunun yerine her ust duzey lobun ilk {@link Lob#enjekte} noronu SINAPSTAN
     * HESAPLANMAZ; aktifligi buradan yazilir (talamik role). {@code katman[i] = -1} ile
     * isaretlenir; ileri besleme ve plastisite bu noronlari atlar, ama diger noronlar
     * onlari normal kaynak olarak gorur.
     *
     * Diske yazilmaz — her tick yeniden uretilir.
     */
    public transient float[] icDuyu = new float[Lob.TOPLAM_ENJEKTE];

    /**
     * Sinaptik iz (eligibility trace) — "bu sinaps son zamanlarda ne kadar iş gördü".
     * Budama kararı ve (Aşama 9) gecikmeli ödül kredisi bunu kullanır.
     * Diske YAZILMAZ: kısa ömürlüdür, birkaç süpürmede yeniden dolar (%25 dosya tasarrufu).
     */
    public transient float[] sinapsIz;

    /**
     * Norogenez tarama imleci (noron indeksi).
     *
     * Rastgele ornekleme 1,5M sinapsta ise yaramiyordu: olu slot orani %1 civarinda
     * oldugu icin 768 rastgele deneme yalnizca ~10 slot buluyordu. Donen imlec ile her
     * cagri arda arda bir pencere tarar; tam kapsama ~32 cagrida tamamlanir.
     */
    public transient int norogenezImleci = 0;

    /**
     * Bu beynin ileri besleme dilimi (0..DILIM_SAYISI-1).
     *
     * v5: eskiden BeyinMotoru'nda KURESEL bir sayacti. LRU tahliyesi ve yeniden katilim
     * oyunculari zaten desenkronize ediyordu; kuresel sayac, yarim supurmede tahliye edilen
     * bir beynin geri geldiginde yanlis dilimden devam etmesine yol aciyordu.
     */
    public transient int dilim = 0;

    // ── ÜST DÜZEY LOBLARIN BELLEKLERİ (v5) ──
    // Üçü de künye JSON'una yazılır: küçüktürler (~30 KB toplam) ve taşınmaları anlamlıdır.
    // Bunlar sinaps değil ANI'dır; topoloji değişse bile göçte korunurlar.

    /** YANKI: geçmiş kararların izi — "ne yaptım, ne oldu?" */
    public KararIzi kararIzi = new KararIzi();
    /** BEYİNCİK: korku/etkinlik zaman serisi — "ne zaman?" */
    public ZamanIzi zamanIzi = new ZamanIzi();
    /** GÖLGE: budanan bağlantıların mezarlığı — "neyi bastırdım?" */
    public GolgeArsivi golgeArsivi = new GolgeArsivi();

    /** Horror sızıntısı durumu (Aşama 10). */
    public long sonSizintiSupurme = 0L;
    public boolean hatirlamaSizdi = false;

    /** Plastisite sayaçları (izleme/test). */
    public long budananToplam = 0L;
    public long doganToplam = 0L;
    /** Son hesaplanan nöromodülatör değeri (KENAR lobundan). */
    public transient float modulator = 1.0f;

    public Beyin() {}

    // ─────────────────────────────────────────────────────────
    // İLERİ BESLEME
    // ─────────────────────────────────────────────────────────

    /**
     * Nöronların {@code dilim}. dilimini hesaplar (tam süpürme = DILIM_SAYISI çağrı).
     *
     * Dilimleme sayesinde tick başına yalnızca ~384 nöron × ~32 fan-in ≈ 12k çarpma yapılır;
     * bu, mevcut DerinAnaliz'in entity taramalarının onda biri kadar bir yüktür.
     *
     * @return bu dilimde işlenen nöron sayısı
     */
    public int ileriDilim(int dilim) {
        if (aktiflik == null || girisBasi == null) return 0;
        final int bas = dilimBas(dilim);
        final int son = dilimSon(dilim);

        for (int i = bas; i < son; i++) {
            // ENJEKTE NORON: aktifligi sinapstan gelmez, icDuyuYaz() yazar. Ne hesapla
            // ne de sifirla — koreme kapisi bile buna dokunmamali, yoksa lobun tek ic
            // duyu kanali sessizce kapanir.
            if (katman[i] < 0) continue;
            // ENERJİ KAPILAMASI: körelmiş lob donar; aç lob süpürmelerin yarısını atlar.
            // Bu kurgusal bir ceza değil — atlanan nöron gerçekten hesaplanmaz, yani
            // oyun içi kıtlık doğrudan CPU tasarrufuna dönüşür.
            if (loblar != null) {
                int lid = lobId[i];
                if (lid >= 0 && lid < loblar.length && loblar[lid] != null) {
                    LobDurum ld = loblar[lid];
                    if (ld.koreldi) { aktiflik[i] = 0f; continue; }
                    // Esik GORELI: esit payin %90'i. Mutlak 0,15 ile 10 lobta hepsi ac kalirdi.
                    if (ld.enerjiPayi < Lob.ESIT_PAY * 0.9 && (toplamSupurme & 1L) == 0L)
                        continue; // bayat kalsın
                }
            }
            float net = esik[i];
            final int s = girisBasi[i];
            final int e = girisBasi[i + 1];
            for (int j = s; j < e; j++) {
                net += agirlik[j] * kaynakDegeri(kaynak[j]);
            }
            float a = aktivasyon(net);
            // NaN/Inf tek bir nöronda bile oluşsa tüm beyni sessizce öldürürdü
            aktiflik[i] = Float.isFinite(a) ? a : 0f;
        }
        return son - bas;
    }

    /**
     * Ic duyu vektorunu enjekte noronlarin aktifligine kopyalar.
     *
     * Supurmeye degil TICK'e bagli cagrilir (duyu tazeleme ile birlikte): supurme 6-13 sn
     * surdugu icin ic duyu da o kadar bayatlardi.
     */
    public void icDuyuYaz() {
        if (aktiflik == null || icDuyu == null) return;
        for (Lob lob : Lob.LOBLAR) {
            final int e = lob.enjekte;
            if (e <= 0) continue;
            final int bas = lob.baslangic();
            final int blok = lob.icDuyuBlok();
            for (int i = 0; i < e; i++) {
                int gi = bas + i;
                if (gi >= noronSayisi || blok + i >= icDuyu.length) break;
                float x = icDuyu[blok + i];
                aktiflik[gi] = Float.isFinite(x) ? Math.max(0f, Math.min(1f, x)) : 0.5f;
            }
        }
    }

    /** Kaynak indeksini değere çevirir: duyu özelliği mi, başka bir nöron mu? */
    public float kaynakDegeri(int idx) {
        if (idx < GIRDI_SAYISI) {
            float g = (girdi == null) ? 0f : girdi[idx];
            return Float.isFinite(g) ? g : 0f;
        }
        int ni = idx - GIRDI_SAYISI;
        return (ni >= 0 && ni < noronSayisi) ? aktiflik[ni] : 0f;
    }

    /**
     * Aktivasyon: softsign tabanlı, 0..1 aralığına ölçeklenmiş.
     *
     * {@code exp()} yerine {@code x/(1+|x|)} kullanılır — doyum davranışı benzer ama
     * yaklaşık 10× ucuz; tick başına on binlerce çağrıda fark eder.
     */
    private static float aktivasyon(float x) {
        // KAZANC: kazancsiz (g=1) rastgele bir agin net girdisi ~0 olur, tum aktiviteler
        // 0.5'te sikisir ve merkezlenmis ogrenme sinyali de ~0 olur -> ag hic ogrenmez
        // (simetri kirilmaz). Kazanc, kucuk net farklarini belirgin aktivite farkina
        // cevirerek simetriyi kirar ve plastisiteye tutunacak sinyal verir.
        final float g = 2.5f;
        float s = (g * x) / (1f + Math.abs(g * x));   // -1..1
        return 0.5f + 0.5f * s;                        // 0..1
    }

    // ─────────────────────────────────────────────────────────
    // ÇIKIŞ
    // ─────────────────────────────────────────────────────────

    /**
     * Bir lobun çıkışı: son {@link Lob#CIKIS_NORON} nöronunun ortalaması (0..1).
     * Bu değer ağacın karşılık gelen kök node'unun aktifliğini sürer.
     */
    /** Lobun HAM cikisi: cikis sutunu noronlarinin ortalamasi. */
    public double hamLobCikisi(Lob lob) {
        if (aktiflik == null) return 0.5;
        int son = Math.min(lob.bitis(), noronSayisi);
        int bas = Math.max(lob.baslangic(), son - Lob.CIKIS_NORON);
        if (bas >= son) return 0.5;
        double t = 0;
        for (int i = bas; i < son; i++) t += aktiflik[i];
        double c = t / (son - bas);
        return Double.isFinite(c) ? c : 0.5;
    }

    /**
     * Lobun agaca yazilan cikisi — kendi gecmis dagilimina gore normalize edilmis.
     *
     * SAF OKUYUCUDUR: istatistik guncellemesi {@link #cikisIstatistikGuncelle} icinde,
     * supurme basina TAM BIR KEZ yapilir. (Bu metot supurme basina birden cok kez
     * cagriliyor: BeyinCikti + debug loglari. EMA'yi buraya koymak cift guncelleme olurdu.)
     */
    public double lobCikisi(Lob lob) {
        double ham = hamLobCikisi(lob);
        LobDurum d = (loblar != null) ? loblar[lob.ordinal()] : null;
        double c = ham;
        if (d != null) {
            double std = Math.max(0.02, Math.sqrt(Math.max(0.0, d.cikisVar)));
            double z = (ham - d.cikisOrt) / (3.0 * std);
            double norm = 0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, z));
            double guven = Math.min(1.0, d.cikisOrnek / 200.0);
            c = ham * (1.0 - guven) + norm * guven;
            // Ablasyon kazanci EN SONDA — normalizasyondan sonra
            c = 0.5 + (c - 0.5) * d.cikisKazanci;
        }
        return Double.isFinite(c) ? Math.max(0.0, Math.min(1.0, c)) : 0.5;
    }

    /**
     * Lobun cikis sutununu bir OY VEKTORU olarak okur (-1..1, kanal basina).
     *
     * TARTI'nin oyu buradan gelir: bir lob tek bir skaler degil, 32 kanalli bir tercih
     * dizisi de uretebilir. Ortalama alindiginda kaybolan ayrim burada korunur —
     * "ARDIL'a daha cok ver" ile "SAKAK'a daha az ver" ayri iki karardir.
     *
     * <h3>NEDEN SUTUN ICI Z — VE NEDEN {@code cikisOrt} DEGIL</h3>
     * Ilk uygulama kanali lobun {@code cikisOrt}/{@code cikisVar} EMA'sina gore
     * normalize ediyordu. Ama o istatistik <b>32 noronun ORTALAMASININ</b> dagilimidir;
     * tek bir noronun oynakligi yaklasik {@code sqrt(32)} kat daha buyuktur. Sonuc
     * olculdu: her kanal aninda +-1'e doyuyor, oy vektoru dereceli bir tercih yerine
     * ac/kapa bir anahtar haline geliyordu — "olcum kanaati yener" kilidi de fiilen
     * anlamsizlasiyordu, cunku kanaat her zaman azami guctey di.
     *
     * Dogrusu sutunun KENDI ANLIK dagilimidir: oy, kanalin sutun ortalamasindan sapmasi
     * kadardir. Boylece oylar dogal olarak ~0 toplamlidir (TARTI enerjiyi sisirmez,
     * YENIDEN DAGITIR) ve sutun tek sesle konusuyorsa hic oy cikmaz.
     *
     * @param cikti en az {@link Lob#CIKIS_NORON} uzunlugunda, yeniden kullanilan tampon
     */
    public void cikisOylari(Lob lob, double[] cikti) {
        if (cikti == null) return;
        java.util.Arrays.fill(cikti, 0.0);
        if (aktiflik == null) return;
        final int son = Math.min(lob.bitis(), noronSayisi);
        final int bas = Math.max(lob.baslangic(), son - Lob.CIKIS_NORON);
        final int n = son - bas;
        if (n <= 1) return;

        double t = 0;
        for (int i = bas; i < son; i++) t += aktiflik[i];
        final double ort = t / n;
        double v = 0;
        for (int i = bas; i < son; i++) { double d = aktiflik[i] - ort; v += d * d; }
        final double std = Math.sqrt(v / n);
        // Sutun tek sesle konusuyorsa oy yok — 0/0'dan NaN sizmasin diye de gerekli.
        if (!(std > 1e-4) || !Double.isFinite(std)) return;

        final int adet = Math.min(n, cikti.length);
        for (int k = 0; k < adet; k++) {
            double z = (aktiflik[bas + k] - ort) / (2.0 * std);
            cikti[k] = Double.isFinite(z) ? Math.max(-1.0, Math.min(1.0, z)) : 0.0;
        }
    }

    /**
     * Cikis dagilim istatistigini gunceller — supurme basina TAM BIR KEZ cagrilmali.
     *
     * ABLASYON SIRASINDA DONAR: yoksa EMA kisilmis cikisa uyum saglar, bozulma sifirlanir
     * ve hayatiOnem her lob icin 0 olculur — Enerji.dagit tamamen coker.
     */
    public void cikisIstatistikGuncelle() {
        if (loblar == null) return;
        for (Lob lob : Lob.LOBLAR) {
            LobDurum d = loblar[lob.ordinal()];
            if (d == null) continue;
            if (ablasyonLob == lob.ordinal()) continue;   // ablasyonda dondur
            double ham = hamLobCikisi(lob);
            double a = 0.02;                              // yavas EMA
            double fark = ham - d.cikisOrt;
            d.cikisOrt += a * fark;
            d.cikisVar = (1 - a) * (d.cikisVar + a * fark * fark);
            if (!Double.isFinite(d.cikisOrt)) d.cikisOrt = 0.5;
            if (!Double.isFinite(d.cikisVar) || d.cikisVar < 0) d.cikisVar = 0.0225;
            d.cikisOrnek++;
        }
    }

    /**
     * Dilim sinirlari — LOB-HIZALI.
     *
     * Duz oransal bolme lob sinirlarini keserdi; enerji kapisi noron basina karar vermek
     * zorunda kalirdi. Lob hizasinda kapi karari dilim basina bir kez verilir, cache
     * lokalitesi de artar. ileriDilim ve Plastisite.guncelleDilim AYNI yardimciyi kullanir
     * (formul once iki yerde kopyalanmisti — gizli bir kirilganlik).
     */
    public int dilimBas(int dilim) {
        return (int) ((long) noronSayisi * dilim / DILIM_SAYISI);
    }

    public int dilimSon(int dilim) {
        return (int) ((long) noronSayisi * (dilim + 1) / DILIM_SAYISI);
    }

    /** Lobun tüm nöronlarının ortalama aktifliği (enerji talebi + izleme için). */
    public double lobOrtalamasi(Lob lob) {
        if (aktiflik == null) return 0.0;
        int bas = lob.baslangic(), son = Math.min(lob.bitis(), noronSayisi);
        if (bas >= son) return 0.0;
        double t = 0;
        for (int i = bas; i < son; i++) t += aktiflik[i];
        double o = t / (son - bas);
        return Double.isFinite(o) ? o : 0.0;
    }

    // ─────────────────────────────────────────────────────────
    // Q BAŞLIĞI (öğrenilen eylem değeri)
    // ─────────────────────────────────────────────────────────

    /** Bir eylemin öğrenilmiş beklenen değeri. */
    public float q(int eylem) {
        float[] oz = qOzellik(null);
        return q(eylem, oz);
    }

    /** Verilen özellik vektörüyle Q değeri. */
    public float q(int eylem, float[] ozellik) {
        if (qBasligi == null || eylem < 0 || eylem >= qBasligi.length || ozellik == null) return 0f;
        float net = qBias[eylem];
        float[] w = qBasligi[eylem];
        int n = Math.min(Math.min(Q_GIRDI, w.length), ozellik.length);
        for (int i = 0; i < n; i++) net += w[i] * ozellik[i];
        return Float.isFinite(net) ? net : 0f;
    }

    /**
     * Q başlığının girdi vektörünü kurar: 48 duyu + 10 lob çıkışı.
     *
     * Lob çıkışları normalize edilmiş (0..1) okumalardır; 0,5 nötrdür, yani duyu
     * özellikleriyle aynı ölçekte ve aynı merkezdedirler — ayrı bir ölçekleme gerekmez.
     *
     * @param hedef yeniden kullanılan tampon (null ise yeni dizi)
     */
    public float[] qOzellik(float[] hedef) {
        float[] o = (hedef != null && hedef.length == Q_GIRDI) ? hedef : new float[Q_GIRDI];
        if (girdi != null) {
            int n = Math.min(GIRDI_SAYISI, girdi.length);
            for (int i = 0; i < n; i++) o[i] = Float.isFinite(girdi[i]) ? girdi[i] : 0f;
        }
        Lob[] loblar = Lob.LOBLAR;
        for (int i = 0; i < 10; i++) {
            double c = (i < loblar.length) ? lobCikisi(loblar[i]) : 0.5;
            o[GIRDI_SAYISI + i] = (float) (Double.isFinite(c) ? c : 0.5);
        }
        return o;
    }

    /**
     * Gecikmeli ödülle Q'yu günceller (TD hatası ile gradyan adımı).
     *
     * @param anlikGirdi eylem ANINDAKI duyu vektörü (kredi doğru duruma gitsin)
     * @param odul       gözlenen gerçek sonuç (ölçülen korku değişimi, 0..1'e ölçekli)
     */
    public void qGuncelle(int eylem, float[] anlikGirdi, float odul, float lr) {
        if (qBasligi == null || eylem < 0 || eylem >= qBasligi.length || anlikGirdi == null) return;
        // Tahmini o anki ozellik vektoruyle yeniden hesapla
        float tahmin = qBias[eylem];
        float[] w = qBasligi[eylem];
        final int n = Math.min(Math.min(Q_GIRDI, w.length), anlikGirdi.length);
        for (int i = 0; i < n; i++) tahmin += w[i] * anlikGirdi[i];
        float hata = odul - tahmin;
        if (!Float.isFinite(hata)) return;
        for (int i = 0; i < n; i++) {
            float yeni = w[i] + lr * hata * anlikGirdi[i];
            w[i] = Float.isFinite(yeni) ? Math.max(-8f, Math.min(8f, yeni)) : 0f;
        }
        float yb = qBias[eylem] + lr * hata;
        qBias[eylem] = Float.isFinite(yb) ? Math.max(-8f, Math.min(8f, yb)) : 0f;
        qGuncelleme++;
    }

    /**
     * Öğrenilen Q'ya ne kadar güvenilmeli (0..0.7).
     *
     * SICAK BAŞLANGIÇ SORUNUNUN ÇÖZÜMÜ: Q sıfırdan başladığı için ilk kararları
     * rastgeledir. Bu yüzden mevcut el yazımı sezgisel HER ZAMAN taban olarak kalır;
     * Q yalnızca öğrendikçe ağırlık kazanır. Regresyon riski yapısal olarak yok.
     */
    public float qGuveni() {
        return Math.min(0.7f, qGuncelleme / 200f);
    }

    // ─────────────────────────────────────────────────────────
    // ŞEMA GÖÇÜ / ONARIM
    // ─────────────────────────────────────────────────────────

    /**
     * Diskten yüklenen beyni güvenli hale getirir.
     *
     * {@link com.photonpulse.candy.core.TruePersonality#koleksiyonlariOnar()} deseninin
     * beyin karşılığı. KURAL: beyne yeni dizi/koleksiyon alanı eklendiğinde BURAYA da
     * eklenecek — Gson eksik alanları null bırakır.
     *
     * @return yapı tutarsızsa false (çağıran sıfırdan inşa etmeli)
     */
    /**
     * Q başlığını yeni genişliğe <b>kaydırarak</b> taşır.
     *
     * <p>Başlığın düzeni {@code [0..GIRDI_SAYISI-1] = duyu, [GIRDI_SAYISI..+9] = lob çıkışı}.
     * Duyu bloğu büyüyünce lob bloğu da kayar, yani düz bir {@code arraycopy} eski lob
     * ağırlıklarını yeni duyu kanallarının üstüne yazardı: öğrenilmiş değerler korunmuş
     * <i>görünür</i> ama yanlış girdiye bağlanmış olurdu. İki blok ayrı ayrı taşınır.</p>
     *
     * <p>Eski duyu genişliği, başlık uzunluğundan lob sayısı çıkarılarak türetilir
     * (48+10=58 → 48). Sayı makul değilse (ör. lob sayısının kendisi değişmişse) düz
     * kopyaya düşülür — yanlış kaydırmaktansa yalnızca duyu bloğunu doğru taşımak yeğdir.</p>
     */
    private static float[] qBasligiGoc(float[] eski) {
        float[] yeni = new float[Q_GIRDI];
        if (eski == null) return yeni;
        final int lobAdet = Lob.LOBLAR.length;
        final int eskiDuyu = eski.length - lobAdet;
        if (eskiDuyu <= 0 || eskiDuyu > GIRDI_SAYISI) {
            System.arraycopy(eski, 0, yeni, 0, Math.min(eski.length, Q_GIRDI));
            return yeni;
        }
        System.arraycopy(eski, 0, yeni, 0, Math.min(eskiDuyu, GIRDI_SAYISI));
        System.arraycopy(eski, eskiDuyu, yeni, GIRDI_SAYISI,
                         Math.min(lobAdet, Q_GIRDI - GIRDI_SAYISI));
        return yeni;
    }

    public boolean onar() {
        if (aktiflik == null || esik == null || lobId == null || katman == null
            || girisBasi == null || kaynak == null || agirlik == null) return false;

        noronSayisi = aktiflik.length;
        if (noronSayisi <= 0 || noronSayisi > MAX_NORON) return false;
        if (esik.length != noronSayisi || lobId.length != noronSayisi
            || katman.length != noronSayisi || girisBasi.length != noronSayisi + 1) return false;

        sinapsSayisi = Math.min(kaynak.length, agirlik.length);
        if (sinapsSayisi <= 0 || sinapsSayisi > MAX_SINAPS) return false;

        // CSR tutarlılığı — bozuk offset tüm ileri beslemeyi çökertir
        if (girisBasi[0] != 0) return false;
        for (int i = 0; i < noronSayisi; i++) {
            if (girisBasi[i] > girisBasi[i + 1] || girisBasi[i + 1] > sinapsSayisi) return false;
        }

        // ── v8 DUYU UZAYI GÖÇÜ (adres kaydırma) ──
        // Adres uzayı "0..GIRDI_SAYISI-1 = duyu, GIRDI_SAYISI+n = nöron n" biçiminde.
        // Duyu bloğunun BÜYÜMESİ (48 → 52) diskteki her nöron adresini 4 kaydırır:
        // eski 48 ("nöron 0") yeni şemada "duyu kanalı 48" olarak okunur. Kaydırmazsak
        // ~1,55M sinaps sessizce yanlış nöronu dinler — çökme yok, exception yok, sadece
        // aylarca öğrenilmiş her şeyin fark edilmeden bozulması. Şema sürümü bu yüzden 3.
        final boolean duyuGocu = semaSurumu < SEMA_SURUMU;
        gocKaydirilanSinaps = 0;
        if (duyuGocu) {
            final int eskiDuyu = duyuGenisligi(semaSurumu);
            final int fark = GIRDI_SAYISI - eskiDuyu;
            if (fark > 0) {
                for (int j = 0; j < sinapsSayisi; j++) {
                    if (kaynak[j] >= eskiDuyu) { kaynak[j] += fark; gocKaydirilanSinaps++; }
                }
                // Gölge arşivi de AYNI adres uzayını tutar (budanmış sinapsların izi).
                if (golgeArsivi != null && golgeArsivi.kaynak != null) {
                    for (int j = 0; j < golgeArsivi.kaynak.length; j++) {
                        if (golgeArsivi.kaynak[j] >= eskiDuyu) golgeArsivi.kaynak[j] += fark;
                    }
                }
            }
            semaSurumu = SEMA_SURUMU;
        }

        // NaN/aralık taraması
        for (int i = 0; i < noronSayisi; i++) {
            if (!Float.isFinite(aktiflik[i])) aktiflik[i] = 0f;
            else aktiflik[i] = Math.max(0f, Math.min(1f, aktiflik[i]));
            if (!Float.isFinite(esik[i])) esik[i] = 0f;
        }
        final int adresTavan = GIRDI_SAYISI + noronSayisi;
        for (int j = 0; j < sinapsSayisi; j++) {
            if (!Float.isFinite(agirlik[j])) agirlik[j] = 0f;
            else agirlik[j] = Math.max(-4f, Math.min(4f, agirlik[j]));
            if (kaynak[j] < 0 || kaynak[j] >= adresTavan) kaynak[j] = 0;
        }

        // ── LOB GOCU (BUG FIX) ──
        // Eskiden: uzunluk uyusmazsa dizi komple atiliyordu. Lob sayisi 6->10 olunca
        // ablasyonun aylardir olctugu TUM hayatiOnem degerleri sessizce silinirdi.
        // LobDurum.ad zaten enum adini tutuyor -> ADA GORE eslestirip tasi.
        LobDurum[] eskiLoblar = loblar;
        loblar = new LobDurum[Lob.LOBLAR.length];
        for (Lob l : Lob.LOBLAR) {
            LobDurum bulunan = null;
            if (eskiLoblar != null) {
                for (LobDurum d : eskiLoblar) {
                    if (d != null && l.name().equals(d.ad)) { bulunan = d; break; }
                }
            }
            if (bulunan == null) bulunan = new LobDurum(l);
            bulunan.onar(l);
            loblar[l.ordinal()] = bulunan;
        }

        // ── DEMET GOCU (BUG FIX) ──
        // Eskiden: "length == 0" kontrolu yuzunden mevcut 10 demet korunur, YENI demetler
        // hic eklenmezdi; Enerji.dagit onlari asla gormezdi. (kaynak,hedef) ciftine gore birlestir.
        Demet[] varsayilan = Demet.varsayilan();
        Demet[] eskiDemetler = demetler;
        demetler = new Demet[varsayilan.length];
        for (int i = 0; i < varsayilan.length; i++) {
            Demet v = varsayilan[i];
            Demet bulunan = null;
            if (eskiDemetler != null) {
                for (Demet d : eskiDemetler) {
                    if (d != null && v.kaynak.equals(d.kaynak) && v.hedef.equals(d.hedef)) {
                        bulunan = d; break;
                    }
                }
            }
            if (bulunan == null) bulunan = v;
            else { bulunan.kota = v.kota; bulunan.dalBasi = v.dalBasi; }  // topoloji varsayilandan
            bulunan.onar();
            demetler[i] = bulunan;
        }

        if (!Float.isFinite(olcek) || olcek <= 0f) olcek = 1.0f;
        if (topolojiSurumu < 1) topolojiSurumu = 1;
        dilim = 0;
        // NOT: asagidaki literal 6, ZihinselProva'nin 6 EYLEMIDIR — lob sayisi DEGIL.
        // Lob sayisi degisince buraya dokunulmamali.
        if (qBasligi == null || qBasligi.length != 6) qBasligi = new float[6][Q_GIRDI];
        for (int c = 0; c < 6; c++) {
            if (qBasligi[c] == null) {
                qBasligi[c] = new float[Q_GIRDI];
            } else if (qBasligi[c].length != Q_GIRDI) {
                // GENIŞLETME GOCU: eski basliklar KORUNUR, yeni agirliklar sifirdan baslar.
                // Yeni dizi acmak, oyuncunun aylardir ogrenilmis eylem degerlerini bir
                // guncellemede silerdi.
                qBasligi[c] = qBasligiGoc(qBasligi[c]);
            }
            for (int i = 0; i < Q_GIRDI; i++)
                if (!Float.isFinite(qBasligi[c][i])) qBasligi[c][i] = 0f;
        }
        if (qBias == null || qBias.length != 6) qBias = new float[6];
        if (dunyalar == null) dunyalar = new java.util.ArrayList<>();
        dunyalar.removeIf(d -> d == null);
        for (Dogrulama d : dunyalar) d.onar();
        while (dunyalar.size() > 8) dunyalar.remove(0);
        aktifDunya = null;
        dogrulamaBitis = 0L;
        plastisiteCarpani = 1.0f;
        if (oyuncuHaritasi == null) oyuncuHaritasi = new OyuncuHaritasi();
        oyuncuHaritasi.onar();
        if (ozModel == null) ozModel = new OzModel();
        ozModel.onar();
        ablasyonLob = -1;
        if (girdi == null || girdi.length != GIRDI_SAYISI) girdi = new float[GIRDI_SAYISI];
        if (icDuyu == null || icDuyu.length != Lob.TOPLAM_ENJEKTE)
            icDuyu = new float[Lob.TOPLAM_ENJEKTE];
        if (uyeNo < 0 || uyeNo >= Kurul.UYE_SAYISI) uyeNo = 0;
        if (!Float.isFinite(mizacPlastisite)) mizacPlastisite = 1.0f;
        mizacPlastisite = Math.max(0.4f, Math.min(2.0f, mizacPlastisite));
        if (!Float.isFinite(mizacAtilganlik)) mizacAtilganlik = 0.5f;
        mizacAtilganlik = Math.max(0.0f, Math.min(1.0f, mizacAtilganlik));
        if (sicil == null) sicil = new UyeSicili();
        sicil.onar();
        nabiz = 0L;
        if (kararIzi == null) kararIzi = new KararIzi();
        kararIzi.onar();
        if (zamanIzi == null) zamanIzi = new ZamanIzi();
        zamanIzi.onar();
        if (golgeArsivi == null) golgeArsivi = new GolgeArsivi();
        golgeArsivi.onar();
        norogenezImleci = 0;
        // sinapsIz diske yazılmaz; her yüklemede sıfırdan doğar
        if (sinapsIz == null || sinapsIz.length != sinapsSayisi) sinapsIz = new float[sinapsSayisi];
        if (semaSurumu < 1) semaSurumu = 1;
        return true;
    }

    /** Ortalama fan-in (izleme/hata ayıklama). */
    public double ortFanIn() {
        return noronSayisi == 0 ? 0 : (double) sinapsSayisi / noronSayisi;
    }
}
