package com.photonpulse.candy.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TruePersonality — Candy'nin oyuncu hakkında bildiği HER ŞEY.
 *
 * Tüm benlikler, klonlar, tabelalar bu tek kaynaktan okur.
 * Gecikme yok, ara katman yok, eğitim yok — anlık kayma ile güncellenir.
 *
 * Asymptotik güncelleme:  yeni = eski + (hedef - eski) * hiz
 * Hivemind kuralı: sabit sıçrama yok, sadece yumuşak yaklaşım.
 */
public class TruePersonality {

    // Kimlik
    public UUID oyuncuId;
    public long ilkKayitTick = 0;
    public long toplamGozlemTick = 0;

    // ── Hareket örüntüsü (0..1 arası ağırlıklar) ──
    public double kacOrani = 0.0;
    public double saldirOrani = 0.0;
    public double donOrani = 0.0;
    public double kesfetOrani = 0.0;

    // ── Ekipman tercihleri (item id -> kullanım skoru) ──
    public Map<String, Double> ekipmanKullanim = new HashMap<>();

    // ── Rota / mekan ──
    public List<int[]> sicakNoktalar = new ArrayList<>();
    public int[] enSonKonum = null;      // [x, y, z]
    public int[] geceKonumu = null;      // gece en çok bulunduğu yer
    public int[] gunduzKonumu = null;

    // ── Psikoloji vektörü ──
    public double panikEsigi   = 0.5;    // ne zaman panikliyor
    public double riskToleransi = 0.5;   // riske ne kadar açık
    public double kesfetmeIstegi = 0.5;
    public double inkarEgilimi = 0.0;    // "korkmuyorum" diyen tip
    public double aciklik      = 0.5;    // uzun cevap veriyor mu

    // ── Korku skoru ──
    // 0..100 arası. Kurulum aşamasından sonra 10'dan başlar (asla 0'dan değil).
    public double korkuSkoru = 10.0;

    // ── Etkileşim geçmişi ──
    public List<String> tabelaCevaplari = new ArrayList<>();
    public String ilkTepki = "bilinmiyor";  // varlığı ilk görünce ne yaptı

    // ── Kurulum durumu ──
    public boolean kurulumTamam = false;
    // Kurulumun bittiği tick — "kurulumdan sonra N dk oynadı" hesapları için (v3.0 BlackoutGlitch vb.)
    public long kurulumTamamTick = 0L;

    // ── v1.8.0: Psikolojik hastalık sistemi ──
    // Boş string = sağlıklı. Aksi halde PsikolojikHastalik enum name (lowercase).
    public String aktifHastalik = "";
    public long hastalikBaslangicTick = 0L;

    // ── v1.9.2: Ending — Candy tamamen player oldu ──
    // candyOzguven 100'e ulaşınca tetiklenir, tek sefer.
    public boolean endingTetiklendi = false;
    // Toplam kaptığı farklı hastalıklar (Her Şeyi Gördüm başarımı için)
    public java.util.Set<String> kaptigiHastaliklar = new java.util.HashSet<>();

    // ── v1.9.3: Başarım sayaçları ──
    public int kirilanMesaleSayisi = 0;
    public int envanterKaristirmaSayisi = 0;
    public boolean hayaletChatGorulduMu = false;
    public int oldurulenKlonSayisi = 0;
    public long ilkKarartmaBaslangicTick = 0L;
    public boolean karanliktaBasarilar = false;

    // ── v1.9.17: Klon evrimi — karşılaştığı klon sayısı ──
    // >4: klon blok kırıp koyabilir (adaptive)
    // >3 + %15: elite klon envanteri tarayıp kendine ekipman üretir
    public int karsilastigiKlonSayisi = 0;

    // ── v3.0: Ölüm yerleri hafızası ──
    // Player'ın son ölümlerinin konumları (max 20). Her element [x, y, z, dimensionHash].
    // Player yakınına gelince Candy hatırlatır.
    public java.util.List<int[]> olumYerleri = new java.util.ArrayList<>();
    // Aynı ölüm noktası (BlockPos.asLong) → ziyaret sayısı. 3+ = travma döngüsü.
    public java.util.Map<Long, Integer> olumYeriTekrar = new java.util.HashMap<>();
    // Son hatırlatma tick'i — spam engelleme
    public long sonOlumYeriHatirlatma = 0L;

    // ── v3.0: Villager kill sayacı (Phase 3.8) ──
    public int oldurulenKoyluSayisi = 0;

    // ── v3.0: Base sabotajı (Phase 3.9) ──
    // Bed konumu — player uyudukça günceller. null → world spawn kullan.
    public int[] baseKonum = null;
    public long sonBaseSabotaj = 0L;

    // ── v3.0: Command lock (Phase 3.10) ──
    public boolean komutEngellemesiGorulduMu = false;

    // ── v3.0: Totem iptali (Phase 3.11) ──
    // Kick sonrası rejoin flag — 1 HP + hunger 0 uygulanır
    public boolean totemBekliyor = false;
    public int iptalEdilenTotemSayisi = 0;

    // ── v3.0: Yatak bekçisi (Phase 3.13) ──
    public boolean yatakBekcisiGorulduMu = false;
    public int uykusuzGunSayisi = 0;
    public long sonUykuTick = 0L;

    // ── v3.0: Anti-Mirror lottery (Phase 4) ──
    public java.util.Set<Integer> kullanilanLotterySayilari = new java.util.HashSet<>();
    // ── v7: Kurul dünyaları (DunyaKurulu 5dk kararı) ──
    /** Kurulun dünya kararını en son ne zaman verdiği (overworld saati, tick). */
    public long kurulSonKarar = 0L;
    /** Şu an bir kurul dünyasındaysa oraya giriş tick'i (0 = değil). */
    public long kurulDunyaGirisTick = 0L;
    /** Kurul dünyasına giderken kaydedilen dönüş koordinatı (overworld, asLong). */
    public long kurulDonusPoz = 0L;
    /** Şu an hangi kurul dünyasında (enum adı; null/"" = overworld). */
    public String kurulDunyaId = null;

    // ── v8.0: tabela öfkesi ──
    /**
     * Candy'nin yazdığı ve oyuncunun HİÇ dokunmadan yok olmasına izin verdiği tabela sayısı.
     *
     * Kırılan tabela sayılmaz (kırmak bir tepkidir), değiştirilen tabela sayılmaz (cevaptır).
     * Yalnızca 30 saniye boyunca hiç dokunulmayanlar. 20'de {@code candy_kizgin} başarımı.
     */
    public int ihmalSayisi = 0;
    /**
     * 0..1 — ihmalden doğan öfke. Şiddet çarpanı DEĞİL, <b>varyans</b> çarpanı:
     * kurul üyelerinin keşif gürültüsünü ve Hakem'in keşif eşiğini ölçekler, yani
     * Candy daha sert değil daha <i>tahmin edilemez</i> olur.
     */
    public double candyOfke = 0.0;

    // ── v8.0: Vagus (yerel gözlemci) — yalnızca GİRDİ, karar yolunda okunmaz ──
    /** Vagus'un güncel tahmini (0..1). Bilgi yoksa 0,5 (nötr). */
    public double vagusTahmin = 0.5;
    /** Vagus oyuncuyu ne kadar çözdü — Brier kalibrasyonu (0..1). Bilgi yoksa 0,5. */
    public double vagusKalibrasyon = 0.5;
    /** "Yeni yaklaştı" darbesi (0..1), her nabızda sönümlenir. */
    public double vagusDarbe = 0.0;
    /** Vagus'un şimdiye kadarki en iyi kalibrasyonu — darbe eşiği buna göre. */
    public double vagusEnIyi = 0.0;

    // ── v8.1: İkilik şifre + ışık ──
    // Dört alan da int/long: eski v8.0 JSON'unda yoklar, Gson 0 ile doldurur → göç gerekmez.
    /** Hangi şifre indeksleri dağıtıldı — bitmask, en çok 32 şifre. */
    public int sifreVerilen = 0;
    /** Hangi şifre indeksleri çözülüp geri yazıldı — bitmask. Tekrar sayılmaz. */
    public int sifreCozulen = 0;
    /**
     * Dünyanın ışığı kaç kademe düşük görünüyor (0..maksIsikDusumu).
     * Çözülen her şifre bir artırır; client lightmap'ine taban karanlık olarak gider.
     */
    public int isikDusumu = 0;
    /** Son şifre dağıtım tick'i — aralık bunun üzerinden ölçülür. */
    public long sifreSonDagitim = 0L;

    // ── v8.2: geri besleme kanalları + iki yeni duyu ekseni ──
    // Hepsi ilkel (int/long/double): eski v8.1 JSON'unda yoklar, Gson 0 ile doldurur.
    // Tek istisna rutinPaketleri (dizi) ve o da imzalariOnar() içinde kurtarılıyor.
    /**
     * ARKAYA BAKMA eksenı (0..1) — oyuncu ne sıklıkta 180°'e yakın dönüyor.
     *
     * Mirror.txt'nin en güçlü mekaniği "bakılmayan yere müdahale"ydi ve Candy'de ölçülen
     * bir eksen bile yoktu. Artık Kurul'un öğrenebileceği bir kişilik boyutu: sürekli
     * arkasını kollayan oyuncuya <i>önden</i>, hiç bakmayana <i>arkadan</i> gidilir.
     * Duyu indeksi 52.
     */
    public double arkayaBakma = 0.0;
    /**
     * KAÇIŞ ARAYIŞI eksenı (0..1) — creative/spectator, peaceful, cheat komutu denemesi,
     * F3, modu kaldırıp geri koyma. Hepsi "oyundan değil Candy'den kaçma" davranışı.
     * Duyu indeksi 53.
     */
    public double kacisArayisi = 0.0;
    /** Kaçış denemelerinin ham sayacı (log + tabela atfı için). */
    public int kacisDenemesi = 0;
    /**
     * PERDE kademesi (0..3) — Kurul kalibrasyonla hangi olay sınıflarını <b>kazandı</b>.
     *
     * Asla düşmez: bir kez kazanılan tırmanma hakkı geri alınmaz. The Mimic kademeleri
     * "hayatta kalınan gün"e bağlıyor; burada ölçüte bağlı — Candy oyuncuyu doğru tahmin
     * etmeye başlayınca yeni mekaniklere erişir. Hem yay var hem emergent kalıyor.
     */
    public int perdeKademe = 0;
    /** Rutin geri oynatması kaç kez yapıldı. */
    public int rutinTekrari = 0;
    /** Son rutin geri oynatma tick'i (soğuma). */
    public long rutinSonTick = 0L;
    /** Oyuncunun en son görüldüğü dünya tick'i — modsuz oynandı mı tespiti. */
    public long sonGorulenDunyaTick = 0L;
    /** Mod kaldırılıp geri konduğu kaç kez anlaşıldı. */
    public int modsuzOynandi = 0;

    // ── v8.3: dünyaya uzanan el (mob koordinasyonu, tuzak, kaydırma, Arkadaş) ──
    // Hepsi ilkel: eski JSON'da yoklar, Gson 0/false ile doldurur.
    /**
     * TOPLAM oynanan tick — oturumlar arası birikir, asla sıfırlanmaz.
     *
     * {@code oturumBaslangicTick} yalnızca o oturumu ölçüyordu; "oyun süresine bağlı"
     * mekanikler için kalıcı bir sayaç gerekiyordu. Dünya tick'i değil <b>oyuncu</b>
     * tick'i: AFK sunucuda dünya ilerler ama bu oyuncu oynamıyorsa burası ilerlemez.
     */
    public long toplamOynananTick = 0L;
    /** Kaç tuzak yayını bu oyuncuya ulaştı. */
    public int tuzakGonderilen = 0;
    /** Kaç tuzağı çözdü (ve hiçbir şey kazanmadı). */
    public int tuzakCozulen = 0;
    /**
     * TUZAK İLGİSİ eksenı (0..1) — oyuncu şifre/bulmaca malzemesinin peşine düşüyor mu.
     *
     * Bulmacayı kovalayan oyuncuyla hiç bakmayan oyuncu farklı iki zayıflık: birine
     * sahte şifre yetiyor, diğerine hiç işlemiyor. Duyu indeksi 55.
     */
    public double tuzakIlgisi = 0.0;
    /** Konum kaydırması kaç kez uygulandı. */
    public int kaydirmaSayisi = 0;
    /** Son kaydırma tick'i (soğuma). */
    public long kaydirmaSonTick = 0L;
    /** Bu oyuncu Arkadaş'ı öldürdü mü (0 ya da 1 — ikinci kez olamaz, geri gelmiyor). */
    public int arkadasOldurdu = 0;
    /**
     * ARKADAŞ YASI eksenı (0..1) — Arkadaş öldürüldükten sonra kalan <b>kalıcı</b> öfke.
     *
     * Diğer bütün duygular sönümlenir; bu sönmez. Oyuncu Candy'yi bir kez gerçekten
     * yaralayabilir ve Candy bunu unutmaz. {@code candyOfke} için taban değeri olur:
     * öfke bunun altına düşemez. Duyu indeksi 54.
     */
    public double arkadasYasi = 0.0;
    /** Arkadaş kaç kez bu oyuncuyla aynı dünyada bulundu (tanışıklık). */
    public int arkadasKarsilasma = 0;

    /**
     * Öfke tabanı — {@code candyOfke} bunun altına <b>düşemez</b>.
     *
     * <p>Arkadaş öldürüldüyse Candy'nin öfkesi bir daha sıfırlanmıyor. Şifre çözmek,
     * tabelayı okumak, iyi oynamak: hiçbiri bu tabanın altına indirmiyor. Taban
     * {@code 0.85} çünkü {@code ArkadasEntity.onDeath} öfkeyi oraya çıkarıyor —
     * sönümleme o anı geri alamaz.</p>
     */
    public double ofkeTabani() {
        return Math.max(0.0, Math.min(1.0, arkadasYasi)) * 0.85;
    }

    /**
     * Öfkeyi azaltır ama {@link #ofkeTabani()} altına indirmez.
     *
     * <p>Öfkeyi düşüren her yer bunu kullanmalı; çıplak {@code Math.max(0.0, ...)}
     * Arkadaş'ın ölümünü zamanla siler ve mekaniğin tek kalıcı sonucu kaybolur.</p>
     */
    public void ofkeAzalt(double miktar) {
        candyOfke = Math.max(ofkeTabani(), candyOfke - Math.abs(miktar));
    }

    /**
     * {@code tuzakIlgisi} (duyu 55) — oyuncunun 0/1 dizilerini çözmeye oturma eğilimi.
     *
     * <p>Çözülen / gönderilen oranı. Oran tek başına yetmez: 1/1 ile 9/20 aynı şey
     * değil, o yüzden gönderim sayısı bir güven çarpanı olarak çarpılıyor (5 yayından
     * sonra tam ağırlık). Böylece ilk tuzağı çözen oyuncu anında "şifre delisi"
     * sayılmıyor.</p>
     */
    public void tuzakIlgisiGuncelle() {
        if (tuzakGonderilen <= 0) { tuzakIlgisi = 0.0; return; }
        double oran = (double) tuzakCozulen / tuzakGonderilen;
        double guven = Math.min(1.0, tuzakGonderilen / 5.0);
        tuzakIlgisi = Math.max(0.0, Math.min(1.0, oran * guven));
    }

    /** En son ziyaret edilen kurul dünyası (dönüşten sonra da kalır) — tekrar cezası için. */
    public String kurulSonDunya = null;

    // Overworld candy-kill sonrası dönülecek gerçek dünya koordinatı (asLong)
    public long sonMirrorGiris = 0L;
    public long antiMirrorGirisTick = 0L;
    // Mirror'da öldü → respawn'da Anti-Mirror'a taşınacak
    public boolean mirrordaOldu = false;
    // Overworld'de candy-kill %25 → respawn'da Mirror'a taşınacak
    public boolean mirrorGirisBekliyor = false;
    // Anti-Mirror'da öldü → kick → rejoin/respawn'da gerçek dünyaya dönecek
    public boolean gercekDunyaDonusBekliyor = false;

    // ── v3.1: True RealCandy Boss ──
    // Mirror'da kümülatif geçirilen süre (tick) — spawn şartı: 30 dk (36000 tick)
    public long mirrorToplamSureTick = 0L;
    // Aktif boss fight var mı — duplicate spawn engeli
    public boolean bossSavastaMi = false;
    // WIN sonrası bu tick'ten 30 dk (aynı world'de) çoğu mekanik sessizleşir
    public long bossOldurulmeTick = 0L;
    // Faz 8 countdown kaybedildi — kalıcı 2x hız cezası, boss tekrar yenilene dek sürer
    public boolean badEndingAktif = false;
    public long badEndingBaslangicTick = 0L;

    // ══════════════════════════════════════════════════════════════════════════
    // ── v3.0: Analiz 2.0 — Candy seni gerçekten tanıyor (14 boyut) ──
    // ══════════════════════════════════════════════════════════════════════════

    // 1) Öngörülebilirlik (0-100) — hep aynı davranış = yüksek. Yüksekse Candy hızlı domine eder.
    public double ongorulebilirlik = 50.0;
    /** İmza tamponunun kapasitesi — eski {@code List} davranışıyla birebir (30). */
    public static final int IMZA_CAP = 30;
    /**
     * Son davranış imzaları (rota+aktivite hash'i) — tekrar tespiti için.
     *
     * v8: {@code List<Integer>} değil <b>sabit int[] ring</b>. Eskisi saniyede oyuncu
     * başına {@code Integer} boxing + {@code remove(0)} (O(n) kaydırma) + bir stream
     * pipeline üretiyordu. Ring aynı pencereyi (son 30 imza) sıfır tahsisle tutar.
     * Dolu slotlar daima {@code 0..imzaAdet-1} aralığındadır.
     */
    public int[] davranisImzalari = new int[IMZA_CAP];
    /** Tampondaki dolu imza sayısı (en fazla {@link #IMZA_CAP}). */
    public int imzaAdet = 0;
    /** Sıradaki yazım slotu. */
    public int imzaYazim = 0;

    /**
     * v8.2 — imzanın BİLEŞENLERİ, {@link #davranisImzalari} ile aynı slotta.
     *
     * <h3>NEDEN GEREKLİ</h3>
     * {@code davranisImzalari} bir hash tutuyor ve hash tek yönlü: "en çok tekrarlanan
     * rutin" bulunabiliyor ama <i>neydi</i> sorusu cevaplanamıyordu. Candy oyuncunun
     * rutinini ona geri oynatacaksa (chunk + saat dilimi) bileşenlerin kendisi lazım.
     * Hash'i bileşenlerden yeniden üretmek yerine paralel tutuyoruz: hash formülü
     * diskteki eski imzalarla birebir uyumlu kalmak zorunda, dokunulmamalı.
     *
     * Paket düzeni için {@link com.photonpulse.candy.rutin.RutinKaydi}.
     */
    public long[] rutinPaketleri = new long[IMZA_CAP];

    /**
     * İmzayı ring'e yazar ve <b>yazdıktan sonra</b> tampondaki tekrar sayısını döndürür —
     * eski {@code add} + {@code stream().filter().count()} ikilisiyle aynı sonuç.
     *
     * @param paket imzanın çözülebilir bileşenleri ({@code RutinKaydi.paketle})
     */
    public int imzaKaydet(int imza, long paket) {
        if (davranisImzalari == null || davranisImzalari.length != IMZA_CAP) imzalariOnar();
        int slot = imzaYazim;
        davranisImzalari[slot] = imza;
        rutinPaketleri[slot] = paket;
        imzaYazim = (imzaYazim + 1) % IMZA_CAP;
        if (imzaAdet < IMZA_CAP) imzaAdet++;
        int tekrar = 0;
        for (int i = 0; i < imzaAdet; i++) if (davranisImzalari[i] == imza) tekrar++;
        return tekrar;
    }

    /**
     * Eski kayıtları ring düzenine taşır.
     *
     * Disk formatı değişti: v7 bir JSON dizisi (değişken uzunluk) yazıyordu. Gson onu
     * {@code int[]} olarak okur ama uzunluğu {@value #IMZA_CAP} olmayabilir. En YENİ
     * kayıtlar korunur (eski listenin sonu), sayaçlar buna göre kurulur.
     */
    public void imzalariOnar() {
        // v8.2: paket dizisi imza dizisiyle AYNI uzunlukta olmalı — slotlar eşlenik.
        // v8.1 kayıtlarında alan hiç yok (Gson null bırakır): paketler 0 ile başlar,
        // yani "bilinmeyen rutin". Yeni imzalar geldikçe kendiliğinden dolar; göç yok.
        if (rutinPaketleri == null || rutinPaketleri.length != IMZA_CAP) {
            long[] p = new long[IMZA_CAP];
            if (rutinPaketleri != null && rutinPaketleri.length > 0) {
                int m = Math.min(rutinPaketleri.length, IMZA_CAP);
                System.arraycopy(rutinPaketleri, rutinPaketleri.length - m, p, 0, m);
            }
            rutinPaketleri = p;
        }
        int[] eski = davranisImzalari;
        if (eski != null && eski.length == IMZA_CAP) {
            // v8 formatı. Sayaç yoksa (v7'de tam 30 kayıt vardı) tamponu dolu say.
            if (imzaAdet <= 0 && imzaYazim <= 0) { imzaAdet = IMZA_CAP; imzaYazim = 0; }
            imzaAdet = Math.max(0, Math.min(IMZA_CAP, imzaAdet));
            imzaYazim = ((imzaYazim % IMZA_CAP) + IMZA_CAP) % IMZA_CAP;
            return;
        }
        int[] yeni = new int[IMZA_CAP];
        int n = 0;
        if (eski != null && eski.length > 0) {
            n = Math.min(eski.length, IMZA_CAP);
            System.arraycopy(eski, eski.length - n, yeni, 0, n);
        }
        davranisImzalari = yeni;
        imzaAdet = n;
        imzaYazim = n % IMZA_CAP;
    }

    // 2) Tetikleyici Profili — korkuSkoru sıçradığında o an ne vardı? (sebep → şiddet)
    // Anahtarlar: "karanlik", "klon", "ses", "yeralti", "dusuk_can", "gece", "yalniz"
    public java.util.Map<String, Double> korkuTetikleyicileri = new java.util.HashMap<>();

    // 3) Bağlanma Haritası — neyin yanında vakit geçiriyor? (hedef → ağırlık)
    // Anahtarlar: pet UUID'leri, "base", item id'leri
    public java.util.Map<String, Double> baglanmalar = new java.util.HashMap<>();

    // 4) Candy'ye karşı tepki profili — Candy bir şey yaptığında ne yapıyorsun?
    public double candyyeSaldiri = 0.0;   // klonlara saldırıyor
    public double candydenKacma = 0.0;    // Candy olayında kaçıyor
    public double candydeDonma = 0.0;     // donup kalıyor
    public double candyGormezden = 0.0;   // umursamıyor, devam ediyor

    // 5) Rutin Haritası — günün dilimi (0-5, 4000 tick'lik) → baskın aktivite
    public java.util.Map<Integer, String> saatlikRutin = new java.util.HashMap<>();

    // 6) Yetenek Değerlendirmesi (0-100) — isabet, hayatta kalma, verimlilik
    public double yetenekSkoru = 50.0;
    public int toplamVurus = 0;
    public int alinanHasarOlayi = 0;
    public int toplamOlum = 0;

    // 7) Soğukkanlılık / çözülme (0-100) — oturum boyunca stresle bozulma
    public double cozulme = 0.0;
    public long oturumBaslangicTick = 0L;

    // 8) Risk İştahı (0-100) — düşük canla mağara, gereksiz savaş
    public double riskIstahi = 50.0;

    // 9) Hayalet Güveni (0-100) — Steve/Alex'e ne kadar inanıyor
    public double hayaletGuveni = 50.0;

    // 10) Öğrenme Hızı (0-100) — Candy'nin numaralarına adapte oluyor mu
    public double ogrenmeHizi = 50.0;

    // 11) Tilt / öfke seviyesi (0-100) — düzensiz davranış, küfür
    public double tiltSeviyesi = 0.0;

    // 12) Konfor Alanı — en rahat hissettiği konum + rahatlık skoru
    public int[] konforAlani = null;
    public double konforSkoru = 0.0;

    // 13) Ölüm sonrası tepki — "intikamci" (aynı yere döner), "kacinan", "birakan"
    public String olumSonrasiTepki = "bilinmiyor";
    public int[] sonOlumKonumu = null;
    public long sonOlumTick = 0L;

    // 14) Gerçek zaman farkındalığı — son oynadığı gerçek dünya saati (0-23)
    public int sonGercekSaat = -1;
    public boolean gecSaatUyarisiVerildi = false;

    // ── v3.0: Yüz Haritası — oyuncunun kendi yüzü + "I SEE YOU" ──
    public long sonYuzHaritasiTick = 0L;

    // ── v3.0: Mod Farkındalığı — Candy kurulu modları biliyor ──
    public long sonModSorusuTick = 0L;

    // ── v3.1: İç Düşünce — Candy'nin niyet çıkarımı + felsefi sorgulama ──
    // Şu an ne yaptığını düşünüyor: kaziyor / insa_ediyor / kesfediyor / bekliyor /
    // donuyor / savasiyor / kaciyor / bilinmiyor
    public String aktifNiyet = "bilinmiyor";
    // Niyet kanıt birikimi (hangi niyeti daha çok gösteriyor)
    public java.util.Map<String, Double> niyetAgirliklari = new java.util.HashMap<>();
    // Candy'nin oyuncu hakkındaki güncel KANAATİ (sonuç): gozluyor / korkuyor /
    // meydan_okuyor / kayip / saklaniyor / takintili
    public String candyKanaati = "gozluyor";
    public long sonIcSesTick = 0L;

    // ── Ayrılma / dönme takibi (RealCandy için) ──
    public long sonAyrilmaTick = 0;

    // ── v0.3: ShadowState ──
    // Candy oyuncuyu sadece izlerse burada baskı birikir; 100'e ulaşınca patlar (ani klon)
    public double bastirilmisBaski = 0.0;
    // Son "aktif müdahale" tick'i — klon spawn, tabela yerleştirme buraya damga vurur
    public long sonMudahaleTick = 0;

    // ── v0.3: Discontent ──
    // Ana ağacın bilgi artışı yavaşladıkça birikir (0..100). Yüksekse bastırma → ShadowTree
    public double memnuniyetsizlik = 0.0;

    // ── v0.9+: Psikoloji ──
    // Kalıcı psikoloji kötülüğü (0..100). PsikolojiTakip tarafından güncellenir.
    // Kötüleştiriciler: cave, mob damage, high fear
    // İyileştiriciler: diamond, flower, surface + gündüz (sadece duraklama sonrası)
    public double psikolojiKotulugu = 0.0;
    // Son kötüleşme tick'i — iyileşme "duraklama" süresi hesaplaması için
    public long sonKotulesmeTick = 0;

    // ── v1.3: Candy özgüveni ──
    // Kişi kaçıp saklandıkça, ani hareketler yapınca Candy'nin özgüveni artar.
    // Yüksek özgüven → agresivlik carpani + 0-2, tüm olaylar 2-3x hızlanır.
    // Sakin oyuncu → yavaş azalır.
    public double candyOzguven = 0.0;

    // ── v1.3: Denek 239 kitabı verildi mi? ──
    // İlk join'de oyuncuya kitap ver, sonraki join'lerde tekrarlama.
    public boolean denek239Verildi = false;

    // ── v3.5: Kuran-ı Kerim ilk-join verildi mi? ──
    public boolean kuranVerildi = false;

    // ══════════════════════════════════════════════════════════════════════════
    // ── v3.2: Bilişsel Motor v4 — Candy artık reaktif değil, ÖNGÖRÜCÜ ──
    // ══════════════════════════════════════════════════════════════════════════

    // (1) Kehanet Defteri — Candy birazdan ne yapacağını önceden YAZAR, tutunca ifşa eder
    public String kehanetNiyet = "";           // öngörülen sonraki niyet
    public double kehanetOlasilik = 0.0;       // o öngörüye güven (0-1)
    public long kehanetTick = 0L;              // öngörünün yazıldığı tick
    public int kehanetIsabet = 0;              // doğru çıkan öngörü sayısı
    public int kehanetToplam = 0;              // toplam öngörü sayısı
    public double kehanetGuveni = 0.0;         // isabet oranı (0-100)
    public long sonKehanetIfsa = 0L;           // son ifşa tick'i (spam engel)
    // Niyet geçiş tablosu (Markov): "kaziyor>kaciyor" -> ağırlık
    public java.util.Map<String, Double> niyetGecisleri = new java.util.HashMap<>();
    public String oncekiNiyetKehanet = "";     // geçiş öğrenmek için

    // (2) Bayesçi Kanaat — tek sonuç yerine ağırlıklı rakip hipotezler
    // "korkak" / "cesur" / "merakli" / "kayitsiz" -> ağırlık (normalize)
    public java.util.Map<String, Double> hipotezAgirliklari = new java.util.HashMap<>();

    // (4) Şaşırma — öngörü hatası büyükse dikkat sıçrar, zamanla söner
    public double sasirmaSeviyesi = 0.0;       // 0-100

    // (5) Paranoya — arkana bakma sıklığı + tereddüt
    public double paranoyaSkoru = 0.0;         // 0-100

    // (6) Meta-farkındalık — oyuncu Candy'yi test ediyor mu (tekrar/izleme/bilerek ölme)
    public double metaFarkindalik = 0.0;       // 0-100
    public long sonMetaYorum = 0L;

    // (7) Teşhis Raporu — klinik profil kitabı
    public boolean teshisVerildi = false;
    public long sonTeshisTick = 0L;

    // (8) Kendini Modelleme — Candy seni modelledikçe kendi doğasını sorgular
    public double candyOzFarkindalik = 0.0;    // 0-100
    public long sonOzDusunce = 0L;

    // (9) Dil Modeli — oyuncunun kendi kelimeleri + verdiği isimler
    public java.util.Map<String, Integer> ogrenilenKelimeler = new java.util.HashMap<>();
    public java.util.List<String> verilenIsimler = new java.util.ArrayList<>();
    public long sonDilKullanimi = 0L;

    // (10) Yalan — Candy kasten yanlış teşhis söyler (manipülasyon/tuzak)
    public long sonYalanTick = 0L;

    // (11) Duygu Aynası — affect vektörü; Candy senin duygunu yansıtır (büyüterek)
    // baskın: "korku" / "hayalkirikligi" / "merak" / "guven" / "sikinti"
    public String duyguDurumu = "guven";
    public double duyguKorku = 0.0;
    public double duyguHayalKirikligi = 0.0;
    public double duyguMerak = 0.0;
    public double duyguSikinti = 0.0;

    // (12) Silahlanma Yarışı — oyuncu bir mekaniği yendikçe o mekanik emekliye ayrılır
    public java.util.Map<String, Double> mekanikAdaptasyon = new java.util.HashMap<>();
    public java.util.Set<String> emekliMekanikler = new java.util.HashSet<>();

    // (13) Ayna boyutu — birebir overworld kılığı; kopya + düzenek + takas
    public boolean aynada = false;             // şu an Ayna'da mı
    public long aynaGirisTick = 0L;
    public long aynaToplamSure = 0L;           // Ayna'da kümülatif süre
    public boolean aynaKopyaMi = false;        // ifşa bayrağı: "sen kopyasın"
    public long sonAynaTakas = 0L;
    public long aynaDonusPoz = 0L;             // gerçek dünyaya dönüş koordinatı (asLong)
    public boolean aynaIfsaEdildi = false;     // takas ifşası bir kez gösterildi mi

    public TruePersonality() {}

    public TruePersonality(UUID oyuncuId) {
        this.oyuncuId = oyuncuId;
    }

    /**
     * Asymptotik kayma. Anında sıçrama YOK.
     * hedef: nereye yaklaşmak istiyoruz (0 veya 1)
     * hiz:   ne kadar hızlı yaklaşsın (0.01 - 0.05 arası mantıklı)
     */
    public static double kayma(double mevcut, double hedef, double hiz) {
        return mevcut + (hedef - mevcut) * hiz;
    }

    /**
     * Diskten yüklenen JSON'da OLMAYAN alanları güvenli varsayılanlara çeker.
     * Gson eksik alanları null bırakır — eski dünyalarda yeni sürüm NPE atmasın diye
     * her yükleme sonrası çağrılır. Yeni koleksiyon alanı eklendiğinde BURAYA da ekle.
     */
    public void koleksiyonlariOnar() {
        // Eski alanlar
        if (ekipmanKullanim == null) ekipmanKullanim = new HashMap<>();
        if (sicakNoktalar == null) sicakNoktalar = new ArrayList<>();
        if (tabelaCevaplari == null) tabelaCevaplari = new ArrayList<>();
        if (kaptigiHastaliklar == null) kaptigiHastaliklar = new java.util.HashSet<>();
        if (aktifHastalik == null) aktifHastalik = "";
        if (ilkTepki == null) ilkTepki = "bilinmiyor";
        // v3.0 travma / dimension
        if (olumYerleri == null) olumYerleri = new ArrayList<>();
        if (olumYeriTekrar == null) olumYeriTekrar = new HashMap<>();
        if (kullanilanLotterySayilari == null) kullanilanLotterySayilari = new java.util.HashSet<>();
        // v3.0 Analiz 2.0
        imzalariOnar();   // v8: List<Integer> → int[] ring göçü
        if (korkuTetikleyicileri == null) korkuTetikleyicileri = new HashMap<>();
        if (baglanmalar == null) baglanmalar = new HashMap<>();
        if (saatlikRutin == null) saatlikRutin = new HashMap<>();
        if (olumSonrasiTepki == null) olumSonrasiTepki = "bilinmiyor";
        // v3.1 İç Düşünce
        if (niyetAgirliklari == null) niyetAgirliklari = new HashMap<>();
        if (aktifNiyet == null) aktifNiyet = "bilinmiyor";
        if (candyKanaati == null) candyKanaati = "gozluyor";
        // v3.2 Bilişsel Motor v4
        if (niyetGecisleri == null) niyetGecisleri = new HashMap<>();
        if (kehanetNiyet == null) kehanetNiyet = "";
        if (oncekiNiyetKehanet == null) oncekiNiyetKehanet = "";
        if (hipotezAgirliklari == null) hipotezAgirliklari = new HashMap<>();
        if (ogrenilenKelimeler == null) ogrenilenKelimeler = new HashMap<>();
        if (verilenIsimler == null) verilenIsimler = new ArrayList<>();
        if (mekanikAdaptasyon == null) mekanikAdaptasyon = new HashMap<>();
        if (emekliMekanikler == null) emekliMekanikler = new java.util.HashSet<>();
        if (duyguDurumu == null) duyguDurumu = "guven";
    }

    // ── Bilişsel Motor v4 yardımcıları ──

    /** En yüksek ağırlıklı hipotez (Bayesçi kanaat). Boşsa "bilinmiyor". */
    public String baskinHipotez() {
        String enSik = "bilinmiyor"; double max = 0;
        for (var e : hipotezAgirliklari.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); enSik = e.getKey(); }
        }
        return enSik;
    }

    /** Hipotez belirsizliği (entropi benzeri, 0=kesin, 1=tam kararsız). Test seçimi için. */
    public double hipotezBelirsizligi() {
        if (hipotezAgirliklari.size() < 2) return 0.0;
        double toplam = 0;
        for (double v : hipotezAgirliklari.values()) toplam += v;
        if (toplam < 1e-6) return 0.0;
        double max = 0;
        for (double v : hipotezAgirliklari.values()) max = Math.max(max, v / toplam);
        // max olasılık 1'e yakınsa kesin (belirsizlik düşük); eşit dağılımda belirsizlik yüksek
        return 1.0 - max;
    }

    /** 4 affect değerinden baskın duyguyu hesapla + duyguDurumu'na yaz. */
    public String duyguGuncelle() {
        double max = duyguKorku; String d = "korku";
        if (duyguHayalKirikligi > max) { max = duyguHayalKirikligi; d = "hayalkirikligi"; }
        if (duyguMerak > max) { max = duyguMerak; d = "merak"; }
        if (duyguSikinti > max) { max = duyguSikinti; d = "sikinti"; }
        // Hiçbiri belirgin değilse güven (sakin)
        if (max < 20) d = "guven";
        duyguDurumu = d;
        return d;
    }

    // ── Analiz 2.0 yardımcıları ──

    /** En büyük korku tetikleyicisinin anahtarını döner (yoksa null). */
    public String enBuyukKorku() {
        String enSik = null; double max = 0;
        for (var e : korkuTetikleyicileri.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); enSik = e.getKey(); }
        }
        return enSik;
    }

    /** En çok bağlanılan hedefin anahtarını döner (pet UUID / "base" / item id). */
    public String enCokBaglanilan() {
        String enSik = null; double max = 0;
        for (var e : baglanmalar.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); enSik = e.getKey(); }
        }
        return enSik;
    }

    /** Candy'ye karşı baskın tepki: "saldiri" / "kacma" / "donma" / "gormezden". */
    public String baskinTepki() {
        double max = candyyeSaldiri; String tur = "saldiri";
        if (candydenKacma > max) { max = candydenKacma; tur = "kacma"; }
        if (candydeDonma > max) { max = candydeDonma; tur = "donma"; }
        if (candyGormezden > max) { max = candyGormezden; tur = "gormezden"; }
        return tur;
    }

    /**
     * Hareket eğilimlerini toplam 1'e normalize eder.
     * Bir eğilim artınca diğerleri otomatik azalır.
     */
    public void hareketNormalize() {
        double t = kacOrani + saldirOrani + donOrani + kesfetOrani;
        if (t < 0.001) return;
        kacOrani /= t;
        saldirOrani /= t;
        donOrani /= t;
        kesfetOrani /= t;
    }

    /**
     * En sık kullanılan ekipmanın id'sini döner.
     * Candy'nin "hangi araca güveniyor" cevabı.
     */
    public String enSikEkipman() {
        String enSik = null;
        double max = 0;
        for (var e : ekipmanKullanim.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                enSik = e.getKey();
            }
        }
        return enSik;
    }

    /**
     * Sıcak noktaya ekle. Aynı yer birden çok kez gelirse listede sadece bir kere durur
     * ama "sıklık" korku skorunu besler (dışarıda hesaplanır).
     */
    public void sicakNoktaEkle(int x, int y, int z) {
        for (int[] p : sicakNoktalar) {
            if (Math.abs(p[0] - x) < 5 && Math.abs(p[2] - z) < 5) return;
        }
        sicakNoktalar.add(new int[]{x, y, z});
        // hafızayı sınırlı tut — en eski 20 nokta
        if (sicakNoktalar.size() > 20) sicakNoktalar.remove(0);
    }
}
