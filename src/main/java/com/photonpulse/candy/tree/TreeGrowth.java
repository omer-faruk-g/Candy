package com.photonpulse.candy.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.JsonDepo;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * TreeGrowth — Candy kişilik ağacının organik büyüme motoru.
 *
 * Her N tick'te tüm oyuncuların ağaçlarını günceller:
 *  1. Node aktifliklerini TruePersonality'den okuyarak yeniden hesapla
 *  2. Aktifliği yüksek + yaşlı node'lar çatallanır (yeni child doğar)
 *  3. Aktifliği düşük + yaşlı node'lar hibernasyona girer
 *
 * Persistence: <world>/candy/tree_<uuid>.json — TruePersonality'den ayrı dosya.
 */
public class TreeGrowth {

    private static final int TICK_ARALIGI = 100;              // 5 saniye
    private static final int AUTOSAVE_ARALIGI = 6000;         // 5 dakika
    private static final long CATAL_ESIGI_TICK = 1000;        // node yaşı
    // Eski 0.85 → hedef matematiği yüzünden hiçbir kök bu eşiği geçemiyor
    // (hareketNormalize yüzünden IZLEME max~0.46, diğer kökler max~0.75).
    // 0.40'a çekildi ki tüm kökler yeterli beslenmede çatallanabilsin.
    private static final double CATAL_ESIGI_AKTIF = 0.40;
    private static final long HIBERNASYON_TICK = 2000;
    private static final double HIBERNASYON_ESIGI = 0.10;
    private static final int MAX_COCUK = 4;
    // v5.0: 10 lob. 10 kok + ~70 alt dal.
    private static final int MAX_TOPLAM_NODE = 80;
    /** Tek bir kokun alt agacinin yiyebilecegi en fazla dal — butce tekeli olmasin. */
    private static final int MAX_ALT_AGAC = 8;

    // v0.4: Yas — klon öldürüldükten sonra bu kadar tick "yasta" kalır (~5 dk)
    private static final long YAS_SURESI_TICK = 6000L;
    // Travma başına aktiflik bonus çarpanı (asymptotik değil — sabit birikim)
    private static final double TRAVMA_BONUS_PER = 0.05;
    // Bonusun tavanı — sonsuza büyümesin
    private static final double TRAVMA_BONUS_MAX = 0.30;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RNG = new Random();

    private static final Map<UUID, PersonalityTree> cache = new HashMap<>();
    private static Path treeKlasoru = null;

    private static int tickSayaci = 0;

    // ─────────────────────────────────────────────────────────
    // KAYIT / YÜKLEME
    // ─────────────────────────────────────────────────────────

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(TreeGrowth::onTick);
        Candy.LOG.info("[Candy] TreeGrowth aktif — kişilik ağacı büyüyor");
    }

    public static void sunucuBaslat(MinecraftServer server) {
        Path worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        treeKlasoru = worldPath.resolve("candy");
        try {
            Files.createDirectories(treeKlasoru);
        } catch (IOException e) {
            Candy.LOG.error("[Candy] tree klasörü oluşturulamadı", e);
        }
    }

    public static void sunucuKapan() {
        hepsiniKaydet();
        cache.clear();
    }

    // ─────────────────────────────────────────────────────────
    // CACHE
    // ─────────────────────────────────────────────────────────

    public static PersonalityTree get(UUID oyuncuId) {
        PersonalityTree t = cache.get(oyuncuId);
        if (t != null) return t;

        // Diskten yüklemeyi dene
        if (treeKlasoru != null) {
            Path dosya = treeKlasoru.resolve("tree_" + oyuncuId + ".json");
            if (Files.exists(dosya)) {
                try {
                    String json = Files.readString(dosya);
                    t = GSON.fromJson(json, PersonalityTree.class);
                    if (t.oyuncuId == null) t.oyuncuId = oyuncuId;
                    if (t.nodes == null) t.nodes = new HashMap<>();
                    // JSON elle düzenlenmiş olabilir — her node'un koleksiyon alanlarını null'dan koru
                    for (TreeNode n : t.nodes.values()) {
                        if (n.childIds == null) n.childIds = new ArrayList<>();
                        if (n.trait == null) n.trait = new HashMap<>();
                    }
                    t.initEt();   // eksik kök varsa tamamla
                    cache.put(oyuncuId, t);
                    Candy.LOG.info("[Candy] ağaç yüklendi ({} node): {}", t.nodes.size(), oyuncuId);
                    return t;
                } catch (Exception e) {
                    Candy.LOG.error("[Candy] ağaç okunamadı, sıfırdan başlıyor", e);
                }
            }
        }

        // Yeni ağaç
        t = new PersonalityTree();
        t.oyuncuId = oyuncuId;
        t.initEt();
        cache.put(oyuncuId, t);
        return t;
    }

    public static void kaydet(PersonalityTree t) {
        if (treeKlasoru == null || t == null || t.oyuncuId == null) return;
        Path dosya = treeKlasoru.resolve("tree_" + t.oyuncuId + ".json");
        JsonDepo.yaz(dosya, GSON.toJson(t));   // v8: atomik + arka plan
    }

    public static void hepsiniKaydet() {
        for (PersonalityTree t : cache.values()) kaydet(t);
    }

    /**
     * Oyuncu ayrıldığında ağacı diske yaz ve cache'ten düş.
     */
    public static void oyuncuAyrildi(UUID uuid) {
        PersonalityTree t = cache.remove(uuid);
        if (t != null) kaydet(t);
    }

    // ─────────────────────────────────────────────────────────
    // TICK LOOP
    // ─────────────────────────────────────────────────────────

    private static void onTick(MinecraftServer server) {
        tickSayaci++;

        if (tickSayaci % TICK_ARALIGI == 0) {
            long simdi = server.overworld().getGameTime();
            for (var e : PersonalityManager.tumOyuncular().entrySet()) {
                UUID uid = e.getKey();
                TruePersonality tp = e.getValue();
                PersonalityTree tree = get(uid);
                guncelle(tree, tp, simdi);
            }
        }

        // v4.0 FAZ KAYDIRMA: 4 autosave sistemi de %6000==0 idi -> ayni tick'te
        // 4 senkron disk yazimi (10 oyuncuda ~380KB tek tick = gorunur stutter).
        // Bu sistem tree_*.json icin 1500 fazina kaydirildi.
        if (tickSayaci % AUTOSAVE_ARALIGI == 1500) {
            hepsiniKaydet();
        }
    }

    // ─────────────────────────────────────────────────────────
    // BÜYÜME / SOLMA
    // ─────────────────────────────────────────────────────────

    private static void guncelle(PersonalityTree tree, TruePersonality tp, long simdi) {
        guncelleAgac(tree, alanMapi(tp), simdi);
    }

    /**
     * Ana ağaç büyüme mantığı — TruePersonality bağımsız hale getirildi.
     * ShadowTree de SimulatedPersonality'den türetilmiş bir Map ile bu metodu çağırır.
     */
    public static void guncelleAgac(PersonalityTree tree, Map<String, Double> alanlar, long simdi) {
        // 1. Her node'un aktifliğini güncelle
        for (TreeNode n : tree.nodes.values()) {
            // v0.4: Yas bitiş kontrolü — 6000 tick sonra travma sayacı artar
            if (n.yasSuresindeMi && simdi - n.yasBasladiTick > YAS_SURESI_TICK) {
                n.yasSuresindeMi = false;
                n.travmaSayisi++;
                Candy.LOG.info("[Candy] Travma sonrası büyüme: {} dalı güçlendi ({}. kez)",
                    n.id, n.travmaSayisi);
            }

            if (n.hibernation) {
                double potensiyel = hesaplaHedefAktiflik(n, alanlar, tree);
                if (potensiyel > 0.4) {
                    n.hibernation = false;
                    n.aktiflik = potensiyel * 0.5;
                    Candy.LOG.debug("[Candy] node uyandı: {}", n.id);
                }
                continue;
            }
            double hedef = hesaplaHedefAktiflik(n, alanlar, tree);
            n.aktiflik = TruePersonality.kayma(n.aktiflik, hedef, 0.03);
            n.bilgi = TruePersonality.kayma(n.bilgi, Math.min(1, n.aktiflik * 1.2), 0.005);
            if (n.dogumTick == 0) n.dogumTick = simdi;
            n.yas = simdi - n.dogumTick;
            n.beslenmeToplam += n.aktiflik;
        }

        // 2. Çatallanma
        List<TreeNode> yeniDoganlar = new ArrayList<>();
        if (tree.nodes.size() < MAX_TOPLAM_NODE) {
            // KOK BASINA KOTA: tek bir lob tum dal butcesini yiyebiliyordu. 6 kokle bu
            // nadirdi; 10 kokle, surekli aktif olan bir lob (ORN. ARDIL) digerlerinin
            // hic catallanamamasina yol acardi — agac tek tarafli buyurdu.
            Map<String, Integer> altAgacBoyu = new HashMap<>();
            for (TreeNode n : tree.nodes.values()) {
                if (n.kokMu()) continue;
                String kok = kokBul(n, tree);
                if (kok != null) altAgacBoyu.merge(kok, 1, Integer::sum);
            }
            for (TreeNode n : tree.nodes.values()) {
                if (n.hibernation) continue;
                if (n.aktiflik > CATAL_ESIGI_AKTIF
                    && n.yas > CATAL_ESIGI_TICK
                    && n.childIds.size() < MAX_COCUK) {
                    String kok = n.kokMu() ? n.id : kokBul(n, tree);
                    if (kok != null && altAgacBoyu.getOrDefault(kok, 0) >= MAX_ALT_AGAC) continue;
                    TreeNode cocuk = catallan(n, tree, simdi);
                    if (cocuk != null) {
                        yeniDoganlar.add(cocuk);
                        if (kok != null) altAgacBoyu.merge(kok, 1, Integer::sum);
                    }
                    if (tree.nodes.size() + yeniDoganlar.size() >= MAX_TOPLAM_NODE) break;
                }
            }
        }
        for (TreeNode y : yeniDoganlar) {
            tree.nodes.put(y.id, y);
            Candy.LOG.info("[Candy] yeni dal doğdu: {} (parent: {})", y.id, y.parentId);
        }

        // 3. Solma / hibernasyon
        for (TreeNode n : tree.nodes.values()) {
            if (n.hibernation) continue;
            if (n.kokMu()) continue;
            if (n.aktiflik < HIBERNASYON_ESIGI && n.yas > HIBERNASYON_TICK) {
                n.hibernation = true;
                n.hibernationTick = simdi;
                Candy.LOG.debug("[Candy] node hibernasyona girdi: {}", n.id);
            }
        }
    }

    /** Bir dalin bagli oldugu kokun id'si (dongude sonsuza gitmemesi icin derinlik sinirli). */
    private static String kokBul(TreeNode n, PersonalityTree tree) {
        TreeNode c = n;
        for (int d = 0; d < 32 && c != null; d++) {
            if (c.kokMu()) return c.id;
            c = tree.nodes.get(c.parentId);
        }
        return null;
    }

    /**
     * TruePersonality'den trait alan Map'i çıkar. ShadowTree için de benzer bir map
     * SimulatedPersonality'den üretilir.
     */
    public static Map<String, Double> alanMapi(TruePersonality tp) {
        double[] v = doldur(tp, new double[PersonalityTree.ALANLAR.size()]);
        java.util.List<String> adlar = PersonalityTree.ALANLAR;
        Map<String, Double> m = new HashMap<>(96);
        for (int i = 0; i < adlar.size(); i++) m.put(adlar.get(i), v[i]);
        // Hipotez agirliklari yoksa anahtarlar HIC OLMAMALI (v4 davranisi birebir korunuyor):
        // agac eksik anahtari "sensor yok" diye, 0.0'i ise "sensor sifir" diye okuyor.
        if (tp == null || tp.hipotezAgirliklari == null) {
            m.remove("hipotez_korkak");
            m.remove("hipotez_cesur");
            m.remove("hipotez_merakli");
            m.remove("hipotez_kayitsiz");
        }
        return m;
    }

    /** {@link #alanDizisi} icin is parcacigi basina tek tampon — tick basina sifir cop. */
    private static final ThreadLocal<double[]> TAMPON =
        ThreadLocal.withInitial(() -> new double[PersonalityTree.ALANLAR.size()]);

    /**
     * {@link #alanMapi} ile AYNI degerleri, {@link PersonalityTree#ALANLAR} <b>indeks
     * sirasinda</b> dogrudan bir {@code float[]} e yazar.
     *
     * <p>v8 OPTIMIZASYON: {@code BeyinGirdi.topla} her 20 tick'te, her oyuncu icin
     * {@code alanMapi}'yi cagirip 52 hash aramasi + 52 unboxing yapiyor ve map'i aninda
     * cope atiyordu — oyuncu basina saniyede 2 HashMap + ~104 boxing. Beyin ada ihtiyac
     * duymuyor, indekse ihtiyac duyuyor. {@code alanMapi} agac icin duruyor (agac gercekten
     * ad ile okuyor) ve artik ikisi de ayni {@link #doldur} kaynagindan besleniyor, yani
     * zamanla birbirinden ayrilamaz.</p>
     *
     * @param hedef en az ALANLAR.size() uzunlugunda tampon (kisa/null ise yeni dizi)
     */
    public static float[] alanDizisi(TruePersonality tp, float[] hedef) {
        final int n = PersonalityTree.ALANLAR.size();
        float[] g = (hedef != null && hedef.length >= n) ? hedef : new float[n];
        if (tp == null) return g;
        double[] v = doldur(tp, TAMPON.get());
        for (int i = 0; i < n; i++) g[i] = (float) v[i];
        return g;
    }

    /**
     * TEK DOGRULUK KAYNAGI — {@link PersonalityTree#ALANLAR} sirasinda 0..1 normalize
     * degerler. Yeni alan eklenirken SADECE burasi ve ALANLAR degisir.
     *
     * <p>Indeks 42-45 (hipotezler) yoklukta 0.0 yazar; {@code alanMapi} bu durumda
     * anahtarlari tamamen kaldirir, {@code BeyinGirdi} ise eskiden de
     * {@code getOrDefault(...,0.0)} ile 0.0 goruyordu — iki tarafta da davranis ayni.</p>
     */
    private static double[] doldur(TruePersonality tp, double[] o) {
        if (o == null || o.length < PersonalityTree.ALANLAR.size()) {
            o = new double[PersonalityTree.ALANLAR.size()];
        }
        if (tp == null) return o;

        // -- 0-7: Hareket simpleksi + kisilik cekirdegi --
        o[0]  = b01(tp.kacOrani);
        o[1]  = b01(tp.saldirOrani);
        o[2]  = b01(tp.donOrani);
        o[3]  = b01(tp.kesfetOrani);
        o[4]  = b01(tp.aciklik);
        o[5]  = b01(tp.inkarEgilimi);
        o[6]  = b01(tp.kesfetmeIstegi);
        o[7]  = b01(tp.panikEsigi);

        // -- 8-13: Psikoloji (0..100 -> 0..1) --
        o[8]  = y100(tp.korkuSkoru);
        o[9]  = y100(tp.candyOzguven);
        o[10] = y100(tp.psikolojiKotulugu);
        o[11] = y100(tp.memnuniyetsizlik);
        o[12] = y100(tp.bastirilmisBaski);
        o[13] = b01(tp.riskToleransi);

        // -- 14-22: Analiz 2.0 --
        o[14] = y100(tp.ongorulebilirlik);
        o[15] = y100(tp.yetenekSkoru);
        o[16] = y100(tp.cozulme);
        o[17] = y100(tp.riskIstahi);
        o[18] = y100(tp.ogrenmeHizi);
        o[19] = y100(tp.tiltSeviyesi);
        o[20] = y100(tp.konforSkoru);
        o[21] = y100(tp.paranoyaSkoru);
        o[22] = y100(tp.metaFarkindalik);

        // -- 23-30: Bilissel Motor v4 --
        o[23] = y100(tp.candyOzFarkindalik);
        o[24] = y100(tp.sasirmaSeviyesi);
        o[25] = y100(tp.kehanetGuveni);
        o[26] = b01(tp.kehanetOlasilik);
        o[27] = y100(tp.duyguKorku);
        o[28] = y100(tp.duyguHayalKirikligi);
        o[29] = y100(tp.duyguMerak);
        o[30] = y100(tp.duyguSikinti);

        // -- 31-34: Candy karsisindaki tepki profili --
        o[31] = b01(tp.candyyeSaldiri);
        o[32] = b01(tp.candydenKacma);
        o[33] = b01(tp.candydeDonma);
        o[34] = b01(tp.candyGormezden);

        // -- 35-41: Aktif niyet (one-hot) --
        String niyet = tp.aktifNiyet == null ? "" : tp.aktifNiyet;
        o[35] = niyet.equals("kaziyor")     ? 1.0 : 0.0;
        o[36] = niyet.equals("insa_ediyor") ? 1.0 : 0.0;
        o[37] = niyet.equals("kesfediyor")  ? 1.0 : 0.0;
        o[38] = niyet.equals("bekliyor")    ? 1.0 : 0.0;
        o[39] = niyet.equals("donuyor")     ? 1.0 : 0.0;
        o[40] = niyet.equals("savasiyor")   ? 1.0 : 0.0;
        o[41] = niyet.equals("kaciyor")     ? 1.0 : 0.0;

        // -- 42-45: Bayesci hipotezler (zaten normalize) --
        if (tp.hipotezAgirliklari != null) {
            o[42] = b01(tp.hipotezAgirliklari.getOrDefault("korkak", 0.25));
            o[43] = b01(tp.hipotezAgirliklari.getOrDefault("cesur", 0.25));
            o[44] = b01(tp.hipotezAgirliklari.getOrDefault("merakli", 0.25));
            o[45] = b01(tp.hipotezAgirliklari.getOrDefault("kayitsiz", 0.25));
        } else {
            o[42] = 0.0; o[43] = 0.0; o[44] = 0.0; o[45] = 0.0;
        }

        // -- 46-47: Korku tetikleyicileri: mutlak deger degil, ORAN onemli --
        o[46] = tetikNorm(tp, "klon");
        o[47] = tetikNorm(tp, "karanlik");

        // -- 48: v8 tabela ofkesi --
        o[48] = b01(tp.candyOfke);

        // -- 49-51: v8 Vagus iletisi (bilgi yoksa 0,5 notr) --
        o[49] = b01(tp.vagusTahmin);
        o[50] = b01(tp.vagusKalibrasyon);
        o[51] = b01(tp.vagusDarbe);

        // -- 52-53: v8.2 geri besleme eksenleri --
        o[52] = b01(tp.arkayaBakma);
        o[53] = b01(tp.kacisArayisi);

        // -- 54-55: v8.3 dunyaya uzanan elin eksenleri --
        o[54] = b01(tp.arkadasYasi);
        o[55] = b01(tp.tuzakIlgisi);

        return o;
    }

    /** 0..100 araligini 0..1 e olcekler, NaN korumali. */
    private static double y100(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v / 100.0));
    }

    /** 0..1 sinirlama + NaN korumasi. */
    private static double b01(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Tetikleyici agirligini oyuncunun en buyuk tetikleyicisine gore 0..1 e olcekler. */
    private static double tetikNorm(TruePersonality tp, String tur) {
        if (tp.korkuTetikleyicileri == null || tp.korkuTetikleyicileri.isEmpty()) return 0.0;
        double max = 0.0;
        for (double v : tp.korkuTetikleyicileri.values()) max = Math.max(max, v);
        if (max < 0.001) return 0.0;
        return b01(tp.korkuTetikleyicileri.getOrDefault(tur, 0.0) / max);
    }

    private static double hesaplaHedefAktiflik(TreeNode n, Map<String, Double> alanlar,
                                               PersonalityTree tree) {
        double toplam = 0;
        double agirlikToplam = 0;
        for (var e : n.trait.entrySet()) {
            double alanDeger;
            if (PersonalityTree.PARENT_ALANI.equals(e.getKey())) {
                // v4.0 DERINLIK: parent CIKTISI bu node icin girdi olur. Bilgi akisi boyle
                // dogar; agac artik paralel noron bankasi degil, katmanli bir ag.
                TreeNode p = (n.parentId == null || tree == null) ? null : tree.nodes.get(n.parentId);
                alanDeger = (p == null) ? 0.0 : p.aktiflik;
            } else {
                alanDeger = alanlar.getOrDefault(e.getKey(), 0.0);
            }
            double w = e.getValue();
            toplam += w * alanDeger;
            agirlikToplam += Math.abs(w);
        }
        double hedef = agirlikToplam < 0.001 ? 0 : Math.max(0, Math.min(1, toplam / agirlikToplam));

        // v0.4: Travma sonrası büyüme bonusu — oyuncu ne kadar klon öldürürse Candy o kadar güçlenir
        if (n.travmaSayisi > 0) {
            hedef += Math.min(TRAVMA_BONUS_MAX, n.travmaSayisi * TRAVMA_BONUS_PER);
            if (hedef > 1.0) hedef = 1.0;
        }
        return hedef;
    }

    /**
     * Parent'tan yeni child doğur. Trait mutasyona uğrar.
     */
    private static TreeNode catallan(TreeNode parent, PersonalityTree tree, long simdi) {
        parent.cocukSayisi++;
        String yeniId = parent.id + ".dal_" + parent.cocukSayisi;
        if (tree.nodes.containsKey(yeniId)) return null;

        TreeNode cocuk = new TreeNode();
        cocuk.id = yeniId;
        cocuk.isim = parent.isim + " → " + parent.cocukSayisi + ". dal";
        cocuk.parentId = parent.id;
        cocuk.dogumTick = simdi;
        cocuk.trait = mutasyon(parent.trait);
        // v4.0 DERINLIK: her yeni dal parent uzerine POZITIF baglanir -> katman katman
        // bilgi akan gercek bir ag olusur.
        cocuk.trait.put(PersonalityTree.PARENT_ALANI, 0.30 + RNG.nextDouble() * 0.40);
        cocuk.aktiflik = parent.aktiflik * 0.3;   // yavaş başlar
        parent.childIds.add(yeniId);
        return cocuk;
    }

    /**
     * Trait vektörünü ±0.25 aralığında oynatır, %30 ihtimalle yeni bir alan da ekler.
     */
    private static Map<String, Double> mutasyon(Map<String, Double> orjinal) {
        Map<String, Double> yeni = new HashMap<>();
        for (var e : orjinal.entrySet()) {
            double delta = (RNG.nextDouble() - 0.5) * 0.5;
            yeni.put(e.getKey(), Math.max(-1, Math.min(1, e.getValue() + delta)));
        }
        if (RNG.nextDouble() < 0.3) {
            String yeniAlan = PersonalityTree.ALANLAR.get(RNG.nextInt(PersonalityTree.ALANLAR.size()));
            if (!yeni.containsKey(yeniAlan)) {
                yeni.put(yeniAlan, (RNG.nextDouble() - 0.5) * 0.8);
            }
        }
        return yeni;
    }
}
