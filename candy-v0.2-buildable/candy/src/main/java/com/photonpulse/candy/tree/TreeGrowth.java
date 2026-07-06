package com.photonpulse.candy.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
    private static final double CATAL_ESIGI_AKTIF = 0.85;
    private static final long HIBERNASYON_TICK = 2000;
    private static final double HIBERNASYON_ESIGI = 0.10;
    private static final int MAX_COCUK = 4;
    private static final int MAX_TOPLAM_NODE = 40;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RNG = new Random();

    private static final Map<UUID, PersonalityTree> cache = new HashMap<>();
    private static Path treeKlasoru = null;

    private static int tickSayaci = 0;

    // ─────────────────────────────────────────────────────────
    // KAYIT / YÜKLEME
    // ─────────────────────────────────────────────────────────

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TreeGrowth::onTick);
        Candy.LOG.info("[Candy] TreeGrowth aktif — kişilik ağacı büyüyor");
    }

    public static void sunucuBaslat(MinecraftServer server) {
        Path worldPath = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT);
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
        try {
            Files.writeString(dosya, GSON.toJson(t));
        } catch (IOException e) {
            Candy.LOG.error("[Candy] ağaç yazılamadı: {}", t.oyuncuId, e);
        }
    }

    public static void hepsiniKaydet() {
        for (PersonalityTree t : cache.values()) kaydet(t);
    }

    // ─────────────────────────────────────────────────────────
    // TICK LOOP
    // ─────────────────────────────────────────────────────────

    private static void onTick(MinecraftServer server) {
        tickSayaci++;

        if (tickSayaci % TICK_ARALIGI == 0) {
            long simdi = server.getOverworld().getTime();
            for (var e : PersonalityManager.tumOyuncular().entrySet()) {
                UUID uid = e.getKey();
                TruePersonality tp = e.getValue();
                PersonalityTree tree = get(uid);
                guncelle(tree, tp, simdi);
            }
        }

        if (tickSayaci % AUTOSAVE_ARALIGI == 0) {
            hepsiniKaydet();
        }
    }

    // ─────────────────────────────────────────────────────────
    // BÜYÜME / SOLMA
    // ─────────────────────────────────────────────────────────

    private static void guncelle(PersonalityTree tree, TruePersonality tp, long simdi) {
        // 1. Her node'un aktifliğini güncelle
        for (TreeNode n : tree.nodes.values()) {
            if (n.hibernation) {
                // Uyanabilir mi?
                double potensiyel = hesaplaHedefAktiflik(n, tp);
                if (potensiyel > 0.4) {
                    n.hibernation = false;
                    n.aktiflik = potensiyel * 0.5;   // yavaş uyanır
                    Candy.LOG.debug("[Candy] node uyandı: {}", n.id);
                }
                continue;
            }
            double hedef = hesaplaHedefAktiflik(n, tp);
            n.aktiflik = TruePersonality.kayma(n.aktiflik, hedef, 0.03);
            n.bilgi = TruePersonality.kayma(n.bilgi, Math.min(1, n.aktiflik * 1.2), 0.005);
            if (n.dogumTick == 0) n.dogumTick = simdi;
            n.yas = simdi - n.dogumTick;
            n.beslenmeToplam += n.aktiflik;
        }

        // 2. Çatallanma
        List<TreeNode> yeniDoganlar = new ArrayList<>();
        if (tree.nodes.size() < MAX_TOPLAM_NODE) {
            for (TreeNode n : tree.nodes.values()) {
                if (n.hibernation) continue;
                if (n.aktiflik > CATAL_ESIGI_AKTIF
                    && n.yas > CATAL_ESIGI_TICK
                    && n.childIds.size() < MAX_COCUK) {
                    TreeNode cocuk = catallan(n, tree, simdi);
                    if (cocuk != null) yeniDoganlar.add(cocuk);
                    if (tree.nodes.size() + yeniDoganlar.size() >= MAX_TOPLAM_NODE) break;
                }
            }
        }
        for (TreeNode y : yeniDoganlar) {
            tree.nodes.put(y.id, y);
            Candy.LOG.info("[Candy] yeni dal doğdu: {} (parent: {})", y.id, y.parentId);
        }

        // 3. Solma / hibernasyon (kökler dışında)
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

    /**
     * Bir node'un HEDEF aktifliğini hesaplar (aktiflik buna asymptotik yaklaşır).
     * Formül: trait vektöründeki her alanın ağırlıklı ortalaması.
     */
    private static double hesaplaHedefAktiflik(TreeNode n, TruePersonality tp) {
        double toplam = 0;
        double agirlikToplam = 0;
        for (var e : n.trait.entrySet()) {
            double alanDeger = alanOku(tp, e.getKey());
            double w = e.getValue();
            toplam += w * alanDeger;
            agirlikToplam += Math.abs(w);
        }
        if (agirlikToplam < 0.001) return 0;
        return Math.max(0, Math.min(1, toplam / agirlikToplam));
    }

    private static double alanOku(TruePersonality tp, String alan) {
        return switch (alan) {
            case "kacOrani" -> tp.kacOrani;
            case "saldirOrani" -> tp.saldirOrani;
            case "donOrani" -> tp.donOrani;
            case "kesfetOrani" -> tp.kesfetOrani;
            case "aciklik" -> tp.aciklik;
            case "inkarEgilimi" -> tp.inkarEgilimi;
            case "kesfetmeIstegi" -> tp.kesfetmeIstegi;
            case "panikEsigi" -> tp.panikEsigi;
            default -> 0;
        };
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
