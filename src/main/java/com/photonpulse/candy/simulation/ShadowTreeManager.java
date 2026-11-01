package com.photonpulse.candy.simulation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.JsonDepo;
import com.photonpulse.candy.tree.PersonalityTree;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.tree.TreeNode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ShadowTreeManager — ShadowTree yaşam döngüsü + büyüme.
 *
 * ShadowTree var olan oyuncular için tick'te SimulatedPersonality'yle büyür.
 * Var etmek için {@link #dogur(UUID, PersonalityTree, long)} çağrılır (bir defalık,
 * genellikle Discontent eşik aştığında).
 */
public class ShadowTreeManager {

    private static final int TICK_ARALIGI = 100;
    private static final int AUTOSAVE_ARALIGI = 6000;
    private static int tickSayaci = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, ShadowTree> cache = new HashMap<>();
    private static Path klasor = null;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ShadowTreeManager::onTick);
        Candy.LOG.info("[Candy] ShadowTreeManager aktif");
    }

    public static void sunucuBaslat(MinecraftServer server) {
        Path worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        klasor = worldPath.resolve("candy");
        try {
            Files.createDirectories(klasor);
        } catch (IOException e) {
            Candy.LOG.error("[Candy] shadowtree klasörü oluşturulamadı", e);
        }
    }

    public static void sunucuKapan() {
        hepsiniKaydet();
        cache.clear();
    }

    /**
     * Var olan ShadowTree'yi getir; yoksa null. Doğurma bu metodla yapılmaz —
     * {@link #dogur} kullan.
     */
    public static ShadowTree getIfExists(UUID uuid) {
        ShadowTree st = cache.get(uuid);
        if (st != null) return st;

        if (klasor == null) return null;
        Path dosya = klasor.resolve("shadowtree_" + uuid + ".json");
        if (!Files.exists(dosya)) return null;

        try {
            st = GSON.fromJson(Files.readString(dosya), ShadowTree.class);
            if (st.oyuncuId == null) st.oyuncuId = uuid;
            if (st.tree == null) st.tree = new PersonalityTree();
            if (st.tree.oyuncuId == null) st.tree.oyuncuId = uuid;
            if (st.tree.nodes == null) st.tree.nodes = new HashMap<>();
            // Elle düzenlenmiş JSON'a karşı savunma
            for (TreeNode n : st.tree.nodes.values()) {
                if (n.childIds == null) n.childIds = new ArrayList<>();
                if (n.trait == null) n.trait = new HashMap<>();
            }
            st.tree.initEt();
            cache.put(uuid, st);
            return st;
        } catch (Exception e) {
            Candy.LOG.error("[Candy] shadowtree okunamadı: {}", uuid, e);
            return null;
        }
    }

    /**
     * Yeni ShadowTree doğur — ana ağacın snapshot'ıyla başlar, sonra bağımsız büyür.
     * Zaten varsa mevcut olanı döner.
     */
    public static ShadowTree dogur(UUID uuid, PersonalityTree anaAgacSnapshot, long simdi) {
        ShadowTree mevcut = getIfExists(uuid);
        if (mevcut != null) return mevcut;

        ShadowTree st = new ShadowTree(uuid, simdi);
        // Snapshot: ana ağacın node'larını kopyala (id -> yeni TreeNode)
        if (anaAgacSnapshot != null && anaAgacSnapshot.nodes != null) {
            for (Map.Entry<String, TreeNode> e : anaAgacSnapshot.nodes.entrySet()) {
                TreeNode orj = e.getValue();
                TreeNode kopya = new TreeNode();
                kopya.id = orj.id;
                kopya.isim = orj.isim + " (gölge)";
                kopya.parentId = orj.parentId;
                kopya.childIds = new ArrayList<>(orj.childIds);
                kopya.cocukSayisi = orj.cocukSayisi;   // çatallanma sayacı — çakışma önlemek için
                kopya.dogumTick = simdi;
                kopya.aktiflik = orj.aktiflik * 0.5;   // yavaş başlar
                kopya.bilgi = 0;
                kopya.trait = new HashMap<>(orj.trait);
                st.tree.nodes.put(kopya.id, kopya);
            }
        }
        st.tree.initEt();   // eksik kök varsa tamamla
        cache.put(uuid, st);
        kaydet(st);
        Candy.LOG.info("[Candy] ShadowTree doğdu: {} için ikinci kişilik başladı", uuid);
        return st;
    }

    public static void kaydet(ShadowTree st) {
        if (klasor == null || st == null || st.oyuncuId == null) return;
        Path dosya = klasor.resolve("shadowtree_" + st.oyuncuId + ".json");
        JsonDepo.yaz(dosya, GSON.toJson(st));   // v8: atomik + arka plan
    }

    public static void hepsiniKaydet() {
        for (ShadowTree st : cache.values()) kaydet(st);
    }

    public static void oyuncuAyrildi(UUID uuid) {
        ShadowTree st = cache.remove(uuid);
        if (st != null) kaydet(st);
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI == 0) {
            long simdi = server.overworld().getGameTime();
            for (Map.Entry<UUID, ShadowTree> e : cache.entrySet()) {
                UUID uid = e.getKey();
                ShadowTree st = e.getValue();
                SimulatedPersonality sp = SimulationManager.get(uid);
                Map<String, Double> alanlar = alanMapi(sp);
                TreeGrowth.guncelleAgac(st.tree, alanlar, simdi);
            }
        }
        // v4.0 FAZ KAYDIRMA: 4 autosave sistemi de %6000==0 idi -> ayni tick'te
        // 4 senkron disk yazimi (10 oyuncuda ~380KB tek tick = gorunur stutter).
        // Bu sistem shadowtree_*.json icin 4500 fazina kaydirildi.
        if (tickSayaci % AUTOSAVE_ARALIGI == 4500) {
            hepsiniKaydet();
        }
    }

    /**
     * Golge agacin girdi haritasi.
     *
     * v4.0: ana agac artik 48 alan okuyor ({@link TreeGrowth#alanMapi}). SimulatedPersonality
     * ise yalnizca 8 davranis alani tasiyor. Sema SIMETRIK olmali; aksi halde golge agacin
     * node'lari bilmedikleri 40 alandan 0.0 okur ve aktiflikleri yapay olarak coker.
     * Cozum: golgenin gercekten bildigi 8 alan kendi degerini, kalan alanlar NOTR 0.5 alir
     * ("golge o boyutu bilmiyor" = ne lehte ne aleyhte kanit).
     */
    private static Map<String, Double> alanMapi(SimulatedPersonality sp) {
        Map<String, Double> m = new HashMap<>(64);
        // Golgenin gercekten bildigi 8 boyut (zit ikiz)
        m.put("kacOrani", sp.kacOrani);
        m.put("saldirOrani", sp.saldirOrani);
        m.put("donOrani", sp.donOrani);
        m.put("kesfetOrani", sp.kesfetOrani);
        m.put("aciklik", sp.aciklik);
        m.put("inkarEgilimi", sp.inkarEgilimi);
        m.put("kesfetmeIstegi", sp.kesfetmeIstegi);
        m.put("panikEsigi", sp.panikEsigi);
        // Kalan alanlar: notr (sema simetrisi + aktiflik cokusu korumasi)
        for (String alan : PersonalityTree.ALANLAR) {
            m.putIfAbsent(alan, 0.5);
        }
        return m;
    }
}
