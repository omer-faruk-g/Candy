package com.photonpulse.candy.beyin;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.TruePersonality;

import java.util.UUID;

/**
 * Kurul — bir oyuncu için {@value #UYE_SAYISI} <b>birbirinden yalıtılmış</b> beyin.
 *
 * <h3>MİMARİ</h3>
 * <pre>
 *              ┌──────── AORT ────────┐   tek nabız, aynı vektör, 11 kopya
 *              ▼      ▼      ▼   ...  ▼
 *           beyin0 beyin1 beyin2 ... beyin10      ← aralarında HİÇBİR bağ yok
 *              │      │      │        │
 *              └──────┴──►HAKEM◄──────┘           ← tek yönlü: üyeler okunur, yazılmaz
 *                          │
 *                          ▼  tek eylem + gerekçe
 * </pre>
 *
 * <h3>YALITIM — NEDEN ÖNEMLİ, NASIL SAĞLANIYOR</h3>
 * Bir komitenin tek üyeden iyi olmasının matematiksel şartı, hataların
 * <b>ilintisiz</b> olmasıdır. İki beyin birbirini dinlerse aynı yanlışa birlikte
 * kayarlar ve on bir beyin, gürültüsü daha az tek bir beyne dönüşür — pahalı ve
 * faydasız. Bu yüzden:
 * <ul>
 *   <li>Üyeler arasında demet (beyaz madde) YOK; hiçbir üyenin nöronu diğerinin adres
 *       uzayında değil.</li>
 *   <li>Aort her üyeye girdi vektörünün <b>kopyasını</b> verir — ortak dizi yok, dolayısıyla
 *       yazma yoluyla sızıntı yok.</li>
 *   <li>{@link IcDuyu} paylaşılmaz: her üye kendi karar geçmişini, kendi gölge arşivini,
 *       kendi zaman izini duyar.</li>
 *   <li>Hakem üyeleri yalnızca <b>okur</b>. Kararını hiçbir üyeye geri yazmaz.</li>
 * </ul>
 * Geriye tek bir ortak kanal kalır: <b>dünyanın kendisi</b>. On bir beyin de aynı oyuncuya
 * bakar ve kurulun kararı dünyayı değiştirir. Bu kaçınılmazdır ve zaten istenen şeydir —
 * önemli olan, bir üyenin ÖZEL durumunun başka bir üyeye ulaşamamasıdır.
 *
 * <h3>ÇEŞİTLİLİK</h3>
 * Aynı bilgiyi alan on bir <i>özdeş</i> beyin, on bir kez aynı cevabı verirdi. Üyeler bu
 * yüzden farklı doğar:
 * <ul>
 *   <li><b>Farklı topoloji tohumu</b> — aynı mimari, farklı kablolama.</li>
 *   <li><b>Farklı mizaç</b> — plastisite çarpanı 0,6..1,4 (kimi çabuk öğrenir ve çabuk
 *       unutur, kimi ağır ve inatçı); atılganlık 0,2..0,8 (kimi öğrendiğine hemen güvenir,
 *       kimi sezgisele yapışır).</li>
 * </ul>
 * <b>Üye 0 nötr mizaçlıdır ve ağacı süren beyindir</b> — mevcut tüm tüketiciler
 * (CloneManager, SignSystem, MobSpawnBooster, TickAccelerator, TeshisRaporu) hiç
 * değişmeden onu okumaya devam eder. Kurul kararı yalnızca eylem seçimine girer.
 */
public class Kurul {

    /** Kurul üye sayısı. */
    public static final int UYE_SAYISI = 11;

    public final UUID oyuncuId;
    public final Aort aort = new Aort();
    public final Hakem hakem = new Hakem();

    /**
     * Üyeler — <b>volatile anlık görüntü</b> olarak yayımlanır.
     *
     * Üyeler arka plan thread'inde inşa edilir, ana thread'de okunur. Düz bir diziye
     * eleman yazmak thread'ler arası bir görünürlük garantisi VERMEZ: ana thread yarı
     * kurulmuş bir nesneyi ya da hiç görmeyebilir. x86'da çoğu zaman "çalışıyor" görünür,
     * bu da hatayı daha tehlikeli yapar — testte çıkmaz, sahada çıkar.
     *
     * Kopyala-yaz + volatile referans bunu kesin olarak çözer: volatile yazımdan önceki
     * her şey (beynin tüm dizileri dahil) volatile okumayı yapan thread'de görünür olur.
     * 11 elemanlı bir dizinin 11 kez kopyalanması pratikte bedavadır.
     */
    private volatile Beyin[] goruntu = new Beyin[UYE_SAYISI];

    /** Üyelerin güvenli anlık görüntüsü. Dönen diziyi DEĞİŞTİRME. */
    public Beyin[] uyeler() {
        return goruntu;
    }

    /** Bir üyeyi kurula yerleştirir (arka plan thread'inden çağrılır). */
    public synchronized void uyeYerlestir(int uye, Beyin b) {
        if (uye < 0 || uye >= UYE_SAYISI || b == null) return;
        Beyin[] yeni = goruntu.clone();
        yeni[uye] = b;
        goruntu = yeni;          // volatile yazim: b'nin tamami burada yayimlanir
    }

    /** Bu slot hazır mı? */
    public boolean uyeVar(int uye) {
        Beyin[] u = goruntu;
        return uye >= 0 && uye < u.length && u[uye] != null;
    }

    /** Dilim işlemede sıradaki üye. */
    private int uyeImleci = 0;
    /** Kayıtta sıradaki üye — 11 dosya aynı anda yazılmasın. */
    private int kayitImleci = 0;

    /** Son nabızda üretilen görüşler — geri bildirim geldiğinde puanlanacak. */
    public Karar[] bekleyenKararlar;
    public int bekleyenEylem = -1;
    public double bekleyenBeklenti = 0.0;
    /**
     * Karar ANINDAKİ özellik vektörleri — üye başına ayrı.
     *
     * Kredi doğru duruma gitmeli: ödül 10 saniye sonra gelir, o ana kadar hem duyu hem
     * lob çıkışları değişmiştir. Ayrıca her üyenin lob çıkışı KENDİNE aittir, dolayısıyla
     * tek bir ortak anlık görüntü saklamak on bir üyeyi yeniden özdeşleştirirdi.
     */
    public float[][] bekleyenOzellik;

    public Kurul(UUID oyuncuId) {
        this.oyuncuId = oyuncuId;
    }

    /**
     * Üyenin mizacını belirler — komitenin çeşitlilik kaynağının yarısı.
     *
     * <b>Üye 0 nötrdür</b> (1,0 / 0,5): gövde, komite eklenmeden önceki davranışın
     * birebir aynısını sürdürmelidir, yoksa "kurul eklendi" ile "Candy değişti"
     * birbirine karışır ve hiçbir ölçüm yorumlanamaz.
     *
     * Diğer üyeler iki eksende yayılır. Plastisite düzgün dağıtılır (0,6..1,4). Atılganlık
     * ise 7 adımlı bir tur ile dağıtılır: düzgün dağıtılsaydı yavaş öğrenen üyeler aynı
     * zamanda çekingen olurdu, yani iki eksen ilintili hale gelir ve on bir üye aslında
     * tek bir eksende dizilmiş olurdu. Kaydırma ikisini birbirinden ayırır.
     */
    public static void mizacUygula(Beyin b, int uye) {
        if (b == null) return;
        b.uyeNo = Math.max(0, Math.min(UYE_SAYISI - 1, uye));
        if (b.uyeNo == 0) {
            b.mizacPlastisite = 1.0f;
            b.mizacAtilganlik = 0.5f;
            return;
        }
        float t = (b.uyeNo - 1) / (float) Math.max(1, UYE_SAYISI - 2);   // 0..1
        b.mizacPlastisite = 0.6f + 0.8f * t;
        b.mizacAtilganlik = 0.2f + 0.6f * (((b.uyeNo * 7) % (UYE_SAYISI - 1)) / (float) (UYE_SAYISI - 2));
    }

    /** Ağacı ve tüm mevcut tüketicileri süren beyin. */
    public Beyin govde() {
        return goruntu[0];
    }

    /** Kaç üye hazır (arka planda inşa bitmiş). */
    public int hazirUye() {
        int n = 0;
        for (Beyin b : goruntu) if (b != null) n++;
        return n;
    }

    // ─────────────────────────────────────────────────────────
    // NABIZ — aynı bilgiyi herkese
    // ─────────────────────────────────────────────────────────

    /**
     * Duyu vektörünü üretir ve tüm üyelere dağıtır. Her üye kendi iç duyusunu
     * <b>kendisi</b> üretir; aort onu taşımaz.
     */
    public void nabiz(TruePersonality tp) {
        aort.pompala(tp);
        for (Beyin b : goruntu) {
            if (b == null) continue;
            aort.dagit(b);
            try {
                IcDuyu.doldur(b);
                b.icDuyuYaz();
            } catch (Throwable ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────
    // GÖRÜŞ TOPLAMA
    // ─────────────────────────────────────────────────────────

    /**
     * Her üyeden bir {@link Karar} toplar.
     *
     * @param sezgisel el yazımı sezgiselin eylem değerleri — TÜM üyeler için aynıdır,
     *                 çünkü o dünyanın bir özelliğidir, beynin değil. Üyeleri ayıran şey
     *                 ÖĞRENDİKLERİ (Q başlığı) ve ona ne kadar güvendikleridir.
     */
    public Karar[] gorusler(double[] sezgisel, boolean[] yasakli) {
        return gorusler(sezgisel, yasakli, 0.0);
    }

    /**
     * Görüşleri toplar, <b>öfke</b> üye ayrışmasını büyüterek.
     *
     * @param ofke 0..1 — {@code TruePersonality.candyOfke}. Üye başına keşif sapmasının
     *             GENLİĞİNİ ölçekler ve mimarinin içinden kaskad yapar: sapma büyür →
     *             üyeler daha çok ayrışır → {@code Hakem.Sonuc.anlasma} düşer → hakem
     *             keşif moduna geçer → en YÜKSEK YAYILIMLI, yani en belirsiz eylem
     *             kazanır. Tek skaler, gerçek belirsizliğe dönüşüyor.
     *
     *             <p>Bilinçli olarak {@code Psikoloji.agresivlikCarpani} DEĞİL: o çarpan
     *             Candy'yi daha SERT yapar, daha TAHMİN EDİLEMEZ yapmaz. İstenen varyans,
     *             şiddet değil.</p>
     */
    public Karar[] gorusler(double[] sezgisel, boolean[] yasakli, double ofke) {
        final int E = sezgisel.length;
        final double ofkeCarpan = 1.0 + 1.5 * (Double.isFinite(ofke)
            ? Math.max(0.0, Math.min(1.0, ofke)) : 0.0);
        Karar[] cikti = new Karar[UYE_SAYISI];
        bekleyenOzellik = new float[UYE_SAYISI][];

        final Beyin[] uyeler = goruntu;
        for (int u = 0; u < UYE_SAYISI; u++) {
            Beyin b = (u < uyeler.length) ? uyeler[u] : null;
            Karar k = new Karar(u, aort.nabiz());
            cikti[u] = k;
            if (b == null) { k.gecersiz = true; continue; }
            // Aynı bilgiye bakmayan üyenin oyu sayılmaz — sessiz bozulmaya karşı kilit.
            if (!aort.butunlukTuttu(b)) { k.gecersiz = true; continue; }

            // ÖĞRENİLENE NE KADAR GÜVENİLİR: qGuveni tabandır, mizaç onu büker.
            //
            // TABAN 0,08 — komitenin çalışabilmesi için gerekli. qGuveni sıfırdan başlar
            // ve 200 güncellemede olgunlaşır; o ana kadar w=0 olsaydı on bir üye
            // sezgiselin BİREBİR aynı kopyasını üretir, hakem her nabızda "%100 anlaşma"
            // görür ve komite ilk saatlerde hiçbir şey katmazdı. %8 ağırlık, eğitilmemiş
            // bir Q'nun kötü karar dayatmasına yetmez (sezgisel hâlâ %92) ama üyelerin
            // ayrışmasını görünür kılmaya yeter.
            float w = b.qGuveni() * (0.5f + b.mizacAtilganlik);
            w = Math.max(0.08f, Math.min(0.85f, w));

            // Karar anındaki özellik vektörü: 48 duyu + BU ÜYENİN 10 lob çıkışı.
            float[] oz = b.qOzellik(null);
            bekleyenOzellik[u] = oz;

            // ── ÜYE BAŞINA KEŞİF SAPMASI ──
            // Keşif gürültüsü ortak diziye eklenirse on bir üye AYNI sapmayı alır ve
            // hemfikir kalmaya devam eder; komite soğuk başlangıçta hiçbir şey katmaz.
            // Üye başına, üyeye özgü ve nabız boyunca SABİT bir sapma (bootstrapped
            // ensemble yöntemi) bunu çözer: her üye tutarlı biçimde farklı bir eylem
            // hakkında iyimserdir, hakem gerçek bir ayrışma görür ve on saniye sonra
            // KİMİN haklı olduğu ölçülür. Sapmanın büyüklüğü üyenin atılganlığıdır.
            java.util.SplittableRandom kesifRng =
                new java.util.SplittableRandom(aort.nabiz() * 1000003L + u);
            final double kesifOlcek = 0.12 * (0.4 + b.mizacAtilganlik) * ofkeCarpan;

            k.deger = new float[E];
            int en = -1, ikinci = -1;
            for (int e = 0; e < E; e++) {
                double d = sezgisel[e] * (1.0 - w) + b.q(e, oz) * w
                    + (kesifRng.nextDouble() - 0.5) * kesifOlcek;
                k.deger[e] = (float) (Double.isFinite(d) ? d : sezgisel[e]);
                if (yasakli != null && e < yasakli.length && yasakli[e]) continue;
                if (en < 0 || k.deger[e] > k.deger[en]) { ikinci = en; en = e; }
                else if (ikinci < 0 || k.deger[e] > k.deger[ikinci]) ikinci = e;
            }
            k.eylem = en;
            if (en < 0) { k.gecersiz = true; continue; }

            // GÜVEN: birinci ile ikinci arasındaki ayrım + öğrenilene güven.
            // Tek başına qGuveni yetmez: "iki eylem başa baş" durumu emin olmamaktır.
            double ayrim = (ikinci < 0) ? 0.5 : Math.min(1.0, (k.deger[en] - k.deger[ikinci]) / 0.5);
            k.guven = (float) Math.max(0.0, Math.min(1.0, 0.35 + 0.45 * ayrim + 0.20 * w));

            // KANIT: beyan değil, ölçüm.
            k.sicil = (float) b.sicil.guvenilirlik();
            k.kalibrasyon = (float) b.sicil.kalibrasyon();
            gerekceCikar(b, k);
        }
        return cikti;
    }

    /** "Neden bu hareket": kararı en çok süren lob ve duyu özelliği. */
    private static void gerekceCikar(Beyin b, Karar k) {
        double enSapma = -1;
        Lob enLob = null;
        for (Lob l : Lob.LOBLAR) {
            double c = b.lobCikisi(l);
            double sapma = Math.abs(c - 0.5);
            if (sapma > enSapma) { enSapma = sapma; enLob = l; k.lobKatkisi = (float) ((c - 0.5) * 2.0); }
        }
        if (enLob == null) return;
        k.surukleyenLob = enLob.name();

        // O lobun gördüğü duyu özelliklerinden en yükseği
        if (b.girdi == null) return;
        int enIdx = -1;
        float enDeger = -1f;
        for (int idx : enLob.girdiIndeksleri) {
            if (idx < 0 || idx >= b.girdi.length) continue;
            if (b.girdi[idx] > enDeger) { enDeger = b.girdi[idx]; enIdx = idx; }
        }
        if (enIdx >= 0 && enIdx < BeyinGirdi.OZELLIK_ADLARI.size()) {
            k.surukleyenDuyu = BeyinGirdi.OZELLIK_ADLARI.get(enIdx);
        }
    }

    // ─────────────────────────────────────────────────────────
    // PUANLAMA — kanıtın üretildiği yer
    // ─────────────────────────────────────────────────────────

    /**
     * Bir eylemin gerçek sonucu ölçüldü: her üyenin ve hakemin karnesi güncellenir.
     *
     * <h3>PUANLAMA KURALI</h3>
     * Yalnızca YAPILAN eylemin sonucu gözlenebilir; diğerleri karşı-olgusaldır. Bu yüzden
     * üye, <i>tavsiyesi</i> üzerinden puanlanır: eylemi öneren üye iyi sonuç aldıysa
     * isabet, kötü sonuç aldıysa ıska; önermeyen üye kötü sonuçta HAKLI çıkmış sayılır.
     * Bu, ikili bir tavsiyeyi puanlamanın doğru yoludur — susan üye bedavaya kazanamaz,
     * ama şüpheciliği doğrulanınca da hakkı yenmez.
     *
     * @param odul gözlenen ödül (-0,5 .. 1,5 ölçeği)
     */
    public void sonucOlcum(int yapilanEylem, double odul) {
        if (bekleyenKararlar == null || yapilanEylem < 0) return;
        final double odulNorm = Math.max(0.0, Math.min(1.0, (odul + 0.5) / 2.0));

        final Beyin[] uyeler = goruntu;
        for (Karar k : bekleyenKararlar) {
            if (k == null || k.gecersiz || k.deger == null) continue;
            if (k.uye < 0 || k.uye >= uyeler.length) continue;
            Beyin b = uyeler[k.uye];
            if (b == null) continue;

            // ── HER ÜYE KENDİ Q BAŞLIĞINI EĞİTİR ──
            // Eskiden yalnızca gövdenin Q'su güncelleniyordu; diğer on üyenin başlığı
            // sonsuza dek boş kalır, qGuveni 0'da çakılır ve hepsi sezgiselin aynı
            // kopyasını üretirdi. Komite o hâlde bir kopyalayıcıdır.
            //
            // Öğrenme hızı ÜYENİN KENDİ YANKI lobundan gelir: "kararlarım tutmuyorsa
            // daha hızlı değiş". Aynı gözlemden farklı hızlarda ve farklı özellik
            // vektörleriyle öğrenen on bir başlık, gerçekten ayrışır.
            if (bekleyenOzellik != null && k.uye < bekleyenOzellik.length
                && bekleyenOzellik[k.uye] != null) {
                float hiz = (float) (0.01 + 0.03 * b.lobCikisi(Lob.YANKI)) * b.mizacPlastisite;
                b.qGuncelle(yapilanEylem, bekleyenOzellik[k.uye], (float) odul, hiz);
            }

            // ÖNGÖRÜ İSABETİ: "bu eylem şunu getirir" dedin, ne oldu?
            // Üyeler hemfikirken bile ayrıştıran tek ölçüt budur.
            double tahminNorm = (yapilanEylem < k.deger.length)
                ? Math.max(0.0, Math.min(1.0, k.deger[yapilanEylem] / 1.5)) : 0.5;
            b.sicil.ongoruPuanla(tahminNorm, odulNorm);
            if (k.eylem == yapilanEylem) b.sicil.dinlendiKaydet(odul);
        }

        // Hakem AYNI kuralla puanlanır — yoksa "kurul tek üyeden iyi mi" sorusu
        // karşılaştırılamaz iki sayıyı yan yana koymuş olurdu.
        hakem.sicil.ongoruPuanla(
            Math.max(0.0, Math.min(1.0, bekleyenBeklenti / 1.5)), odulNorm);
        hakem.sicil.dinlendiKaydet(odul);

        // ── v8: VAGUS — oy kullanmayan 12. üye ──
        // Vagus karara karışmaz; yalnızca üyelerle AYNI ölçüm kuralıyla puanlanır
        // (ölçüt birliği kasıtlı: "Vagus kurula yaklaştı mı" sorusu ancak aynı ölçekte
        // anlamlı). Vagus kapalıysa (Ollama yok) çağrı ilk satırda döner ve kurul v7 ile
        // birebir aynı davranır.
        try {
            com.photonpulse.candy.beyin.vagus.Vagus.ongoruPuanla(
                oyuncuId, yapilanEylem, odulNorm);
        } catch (Throwable ignored) {}
        bekleyenKararlar = null;
        bekleyenOzellik = null;
    }

    // ─────────────────────────────────────────────────────────
    // İŞLEME / KALICILIK
    // ─────────────────────────────────────────────────────────

    /**
     * Nöron bütçesi kadar iş yapar — üyeler arasında dönen imleçle.
     *
     * @return işlenen nöron sayısı
     */
    public int dilimAdimi(int noronButcesi, java.util.Random rng, SupurmeBitti geriCagri) {
        final Beyin[] uyeler = goruntu;
        int islenen = 0;
        int denenen = 0;
        while (islenen < noronButcesi && denenen < UYE_SAYISI * 2) {
            if (uyeImleci >= uyeler.length) uyeImleci = 0;
            Beyin b = uyeler[uyeImleci++];
            denenen++;
            if (b == null) continue;
            try {
                islenen += b.ileriDilim(b.dilim);
                Plastisite.guncelleDilim(b, b.dilim, b.modulator,
                    b.plastisiteCarpani * b.mizacPlastisite);
                b.dilim++;
                if (b.dilim >= Beyin.DILIM_SAYISI) {
                    b.dilim = 0;
                    geriCagri.bitti(b);
                }
            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] kurul üye {} hata: {}", b.uyeNo, t.getMessage());
            }
        }
        return islenen;
    }

    /** Süpürme tamamlandığında çağrılacak geri çağrı. */
    public interface SupurmeBitti {
        void bitti(Beyin uye);
    }

    /** Sırayla TEK bir üyeyi kaydeder — 11 dosyayı aynı anda yazmak I/O tepesi yaratırdı. */
    public void sirayaGoreKaydet() {
        Beyin[] uyeler = goruntu;
        if (kayitImleci >= uyeler.length) kayitImleci = 0;
        Beyin b = uyeler[kayitImleci++];
        if (b != null) BeyinDeposu.kaydet(b, false);
    }

    /**
     * Kapanışta hepsi — SENKRON, veri kaybı olmasın. Yalnızca sunucu kapanışında
     * çağrılmalı: 11 senkron gzip yazımı ana thread'i ~1 sn bloklar, oyun içinde
     * kabul edilemez. Oyun içi tahliye/ayrılma {@link #hepsiniKaydetAsync} kullanır.
     */
    public void hepsiniKaydet() {
        for (Beyin b : goruntu) if (b != null) BeyinDeposu.kaydet(b, true);
    }

    /**
     * Tüm üyeleri ARKA PLANDA kaydeder — ana thread bloklanmaz.
     *
     * Oyun içi tahliye ve oyuncu ayrılışında kullanılır. {@link BeyinDeposu}'nun tampon
     * havuzu 11 yazımın hiçbirini düşürmez; havuz anlık dolarsa geri kalanlar kısa süre
     * sonra periyodik kayıtla zaten yazılır (bu üyeler için veri kaybı, en fazla son
     * birkaç saniyelik öğrenme — kabul edilebilir, çökme değil).
     */
    public void hepsiniKaydetAsync() {
        for (Beyin b : goruntu) if (b != null) BeyinDeposu.kaydet(b, false);
    }

    /** Kurulun toplam nöron/sinaps sayısı (izleme). */
    public long toplamNoron() {
        long t = 0;
        for (Beyin b : goruntu) if (b != null) t += b.noronSayisi;
        return t;
    }

    public long toplamSinaps() {
        long t = 0;
        for (Beyin b : goruntu) if (b != null) t += b.sinapsSayisi;
        return t;
    }
}
