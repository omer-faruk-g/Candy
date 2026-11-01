package com.photonpulse.candy.beyin;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.tree.PersonalityTree;
import com.photonpulse.candy.tree.TreeGrowth;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BeyinMotoru — beynin tek tick giriş noktası.
 *
 * <h3>TİCK BÜTÇESİ (v5'in en önemli mimari kararı)</h3>
 * Eskiden her işlem tick'inde TÜM oyuncular işleniyordu → maliyet oyuncu sayısıyla
 * doğrusal büyüyor ve 3 tick'te bir ani sıçrama olarak geliyordu. Artık dönen bir
 * imleçle tick başına EN FAZLA {@link #DILIM_BUTCESI} oyuncu-dilimi işlenir:
 * <pre>
 *   1 oyuncu  → tam süpürme 128 tick   (~6,4 s)
 *   6 oyuncu  → tam süpürme 256 tick   (~12,8 s)
 *   12 oyuncu → tam süpürme 512 tick   (~25,6 s)
 * </pre>
 * <b>Tick maliyeti oyuncu sayısından bağımsız hale gelir.</b> Ölçek arttıkça süpürme
 * yavaşlar, sunucu yavaşlamaz — doğru olan takas budur.
 *
 * <h3>ÖLÇÜM KATMANI SÜPÜRMEDEN AYRI</h3>
 * Süpürme 1,2 s'den 6-13 s'ye uzadığı için duyu tazeleme ve öğrenme ölçümü süpürmeye
 * bağlı kalsaydı 10× körelirdi (beyin bayat duyuyla hesaplar, niyet sınıflayıcısı
 * 13 saniyede bir örnek alır, ablasyon ve dünya doğrulaması onun üzerinden okuduğu için
 * tüm ölçüm makinesi yavaşlardı). Bu yüzden:
 * <ul>
 *   <li>duyu tazeleme → her {@link #GIRDI_TICK} tick (1 s)</li>
 *   <li>harita eğitimi + dünya doğrulama → her {@link #OLCUM_TICK} tick (1,2 s — v4 hızı)</li>
 * </ul>
 *
 * KARIŞIM: {@code -Dcandy.beyin.karisim} (0..1), varsayılan 0.25. Beyin ağacı ezmez.
 */
public class BeyinMotoru {

    /**
     * Tick başına işlenecek en fazla NÖRON — bütçe artık dilim değil, iş cinsinden.
     *
     * v6'da oyuncu başına {@link Kurul#UYE_SAYISI} beyin var; dilim saymak, üye sayısı
     * ya da lob ölçeği değiştiğinde tick maliyetini sessizce katlardı. Nöron cinsinden
     * bütçe hem oyuncu sayısından hem kurul boyutundan BAĞIMSIZ bir tavan verir: ölçek
     * büyüdükçe süpürme yavaşlar, sunucu yavaşlamaz.
     *
     * 3.200 nöron ≈ 0,6 ms/tick (ölçüldü) — 50 ms'lik tick'in %1,2'si.
     */
    private static final int NORON_BUTCESI = 3200;
    /** Duyu vektörü tazeleme periyodu (tick). */
    private static final int GIRDI_TICK = 20;
    /** Öğrenme ölçümü periyodu (tick) — v4'teki süpürme hızıyla aynı. */
    private static final int OLCUM_TICK = 24;
    /**
     * Kalıcılık periyodu (tick) — DUVAR SAATİ tabanlı, süpürmeye DEĞİL.
     *
     * SOAK DERSİ: kayıt eskiden gövdenin süpürmesine bağlıydı ({@code KAYIT_SUPURME}).
     * Tek beyinde bu iyiydi; kurulda gövde, nöron bütçesini 10 üyeyle paylaştığı için
     * ~11× daha yavaş süpürür. 4 oyunculu 30 dk testte gövde yalnızca ~75× süpürdü →
     * kayıt yalnızca ~6× tetiklendi → sıra imleci hiç 6'nın ötesine geçemedi ve
     * <b>her oyuncunun u6..u10 üyeleri HİÇ diske yazılmadı</b> (çökmede o 5 üyenin tüm
     * öğrenmesi kaybolurdu). Duvar saati tabanlı kayıt, oyuncu sayısından ve süpürme
     * hızından bağımsız olarak tüm üyeleri düzenli aralıkla döngüye sokar.
     *
     * 400 tick = 20 sn: 30 dk'da ~90 döngü → her üye ~8× yazılır.
     */
    private static final int KAYIT_TICK = 400;
    /**
     * Bellekte tutulacak en fazla KURUL — YALNIZCA ÇEVRİMDIŞI oyuncular için bir tavan.
     *
     * ÇÖKME DERSİ: bu değer 3 idi ve 4 oyunculu bir dünyada sunucuyu ANINDA çökertti.
     * Cache erişim-sıralı (access-order) LRU; kapasite oyuncu sayısından KÜÇÜKSE her
     * tick en eski kurulu tahliye eder, o kurul bir sonraki tick yine gerekir, yeniden
     * yüklenir — sonsuz çırpınma. Tahliye üstüne 11 beyni ANA THREAD'de senkron gzip'le
     * yazınca tek tick 90 saniye sürdü ve watchdog sunucuyu öldürdü.
     *
     * İki kalıcı düzeltme {@link #removeEldestEntry} içinde:
     *   1) ÇEVRİMİÇİ oyuncunun kurulu ASLA tahliye edilmez (her tick gerekir) — cache
     *      çevrimiçi oyuncu sayısına göre büyür, bu değer onları sınırlamaz.
     *   2) Tahliye kaydı ARKA PLANDA yapılır, ana thread bloklanmaz.
     * Yani bu tavan sadece AYRILMIŞ ama henüz temizlenmemiş kurulları sınırlar.
     */
    private static final int MAX_AKTIF_KURUL = 8;

    // ── Süpürme tabanlı bakım periyotları ──
    // Süpürme artık ~6-13 sn (v4'te 1,2 sn) → hepsi ~10× küçültüldü ki duvar saati korunsun.
    private static final int ENERJI_SUPURME = 1;
    private static final int BUDAMA_SUPURME = 3;
    private static final int NOROGENEZ_SUPURME = 4;
    private static final int OZ_DENETIM_SUPURME = 6;
    private static final int ABLASYON_SUPURME = 20;
    private static final int NORM_SUPURME = 30;

    private static final float HARITA_LR = 0.04f;
    private static final float PROTOTIP_LR = 0.06f;

    private static final Random RNG = new Random();
    private static final double KARISIM = karisimOku();

    /** LRU: çevrimdışı kurullar için sınırlı önbellek (11 beyinde bellek kritik). */
    private static final Map<UUID, Kurul> cache =
        new LinkedHashMap<>(8, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Kurul> eldest) {
                if (size() <= MAX_AKTIF_KURUL) return false;
                // ÇEVRİMİÇİ oyuncunun kurulu tahliye EDİLEMEZ — her tick yeniden gerekir;
                // tahliye + yeniden yükleme çırpınması + ana thread'de gzip = watchdog çökmesi.
                // Eldest çevrimiçiyse false döneriz: cache büyür, çevrimiçi kalır.
                if (PersonalityManager.tumOyuncular().containsKey(eldest.getKey())) return false;
                // Çevrimdışı straggler: ARKA PLANDA kaydet (ana thread bloklanmaz), tahliye et.
                eldest.getValue().hepsiniKaydetAsync();
                return true;
            }
        };

    /** Arka planda hazırlanan (oyuncu, üye) çiftleri — çift iş başlatmayı önler. */
    private static final Set<String> hazirlaniyor = ConcurrentHashMap.newKeySet();
    private static final ExecutorService INSACI = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "candy-beyin-insaci");
        t.setDaemon(true);
        return t;
    });

    private static int tickSayaci = 0;
    /** Dönen imleç — hangi oyuncudan devam edileceği. */
    private static int imlec = 0;
    /** Dilim işlemenin yeniden kullanılan oyuncu listesi — her tick tahsis etmemek için. */
    private static final List<UUID> dilimOyuncular = new ArrayList<>();
    private static String dunyaId = "?";

    public static void dunyaIdAyarla(String id) {
        dunyaId = (id == null || id.isEmpty()) ? "?" : id;
    }

    public static String dunyaId() {
        return dunyaId;
    }

    private static double karisimOku() {
        try {
            String s = System.getProperty("candy.beyin.karisim");
            if (s == null) return 0.25;
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(s)));
        } catch (Throwable t) {
            return 0.25;
        }
    }

    public static void register() {
        if (!BeyinGirdi.semaGecerli()) {
            Candy.LOG.error("[Candy] BEYİN ŞEMA HATASI: {} özellik bekleniyordu, {} bulundu — beyin devre dışı",
                Beyin.GIRDI_SAYISI, BeyinGirdi.OZELLIK_ADLARI.size());
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(BeyinMotoru::onTick);
        Candy.LOG.info("[Candy] BeyinMotoru aktif — KURUL: {} üye x {} nöron = {} nöron / {} lob, ölçek {}, karışım {}",
            Kurul.UYE_SAYISI, Lob.toplamNoron(),
            (long) Kurul.UYE_SAYISI * Lob.toplamNoron(), Lob.LOBLAR.length,
            String.format("%.2f", Lob.OLCEK), String.format("%.2f", KARISIM));
    }

    /**
     * Oyuncunun GÖVDE beynini getirir (üye 0).
     *
     * GERİYE UYUM SÖZLEŞMESİ: kurul eklenmeden önce bu metot ne döndürüyorsa aynısını
     * döndürmeye devam eder. ZihinselProva'nın GÖLGE/BEYİNCİK kapıları, TeshisRaporu ve
     * ağacı süren çıkış hep gövdeyi okur; komite yalnızca EYLEM SEÇİMİNE katılır.
     * Böylece "kurul eklendi" ile "Candy'nin davranışı değişti" birbirine karışmaz.
     *
     * Hazır değilse {@code null} — tüm çağıranlar null-güvenlidir.
     */
    public static Beyin get(UUID uuid) {
        Kurul k = kurul(uuid);
        return (k == null) ? null : k.govde();
    }

    /**
     * Oyuncunun kurulunu getirir; yoksa oluşturur ve 11 üyeyi ARKA PLANDA hazırlar.
     *
     * Üyeler tek tek gelir: 3. üye hazır olduğunda kurul 3 üyeyle karar vermeye başlar,
     * hepsini beklemez. Hazır olmayan üye {@link Karar#gecersiz} sayılır ve hakem onu
     * sıfır ağırlıkla geçer.
     */
    public static Kurul kurul(UUID uuid) {
        Kurul k = cache.get(uuid);
        if (k == null) {
            k = new Kurul(uuid);
            cache.put(uuid, k);
        }
        for (int u = 0; u < Kurul.UYE_SAYISI; u++) {
            if (k.uyeVar(u)) continue;
            final int uye = u;
            final Kurul hedef = k;
            String anahtar = uuid + "#" + uye;
            if (!hazirlaniyor.add(anahtar)) continue;
            INSACI.submit(() -> {
                try {
                    Beyin yeni = BeyinDeposu.yukle(uuid, uye);
                    if (yeni == null) yeni = BeyinInsaci.insaEt(uuid, uye);
                    DunyaDogrulayici.dunyayaGir(yeni, dunyaId);
                    hedef.uyeYerlestir(uye, yeni);
                    if (uye == Kurul.UYE_SAYISI - 1) {
                        Candy.LOG.info("[Candy] kurul hazır: {} üye, {} nöron, {} sinaps ({})",
                            hedef.hazirUye(), hedef.toplamNoron(), hedef.toplamSinaps(), uuid);
                    }
                } catch (Throwable t) {
                    Candy.LOG.error("[Candy] üye {} hazırlanamadı: {}", uye, uuid, t);
                } finally {
                    hazirlaniyor.remove(anahtar);
                }
            });
        }
        return k;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;

        // ── 1) NABIZ: aort duyu vektörünü bir kez üretir, 11 üyeye kopyalar ──
        if (tickSayaci % GIRDI_TICK == 0) {
            for (var e : PersonalityManager.tumOyuncular().entrySet()) {
                Kurul k = cache.get(e.getKey());
                TruePersonality tp = e.getValue();
                if (k != null && tp != null) {
                    try { k.nabiz(tp); } catch (Throwable ignored) {}
                }
            }
        }

        // ── 2) Öğrenme ölçümü (süpürmeden bağımsız — v4 hızını korur) ──
        if (tickSayaci % OLCUM_TICK == 0) {
            olcumAdimi(server);
        }

        // ── 3) Dilim bütçesi: tick başına en fazla DILIM_BUTCESI oyuncu-dilimi ──
        dilimAdimi(server);

        // ── 4) KALICILIK (duvar saati): her kurulun SIRADAKİ üyesini kaydet ──
        // Gövde süpürmesine bağlı değil; yoksa çok üyeli kurulda üyelerin çoğu hiç
        // yazılmaz. Kurul başına tek üye → I/O düzgün yayılır, havuz doymaz.
        if (tickSayaci % KAYIT_TICK == 0) {
            for (Kurul k : cache.values()) {
                try { k.sirayaGoreKaydet(); } catch (Throwable ignored) {}
            }
        }

        BeyinOlcer.raporla(cache.size());
        if (BeyinOlcer.aktif() && tickSayaci % 1201 == 0) durumRaporu();
    }

    /**
     * Harita eğitimi + dünya doğrulaması — ölçüm makinesinin kalbi.
     *
     * HER ÜYE İÇİN AYRI ÇALIŞIR ve bu zorunludur: ablasyon, {@code hayatiOnem} ve komite
     * ağırlıkları hep üyenin KENDİ haritasının hatasından okunur. Ortak bir harita
     * kullanılsaydı on bir üye tek bir ölçüme bağlanır, bağımsızlıkları biter ve
     * hakemin ağırlıkları anlamsızlaşırdı. Maliyeti küçüktür (harita 48→7 boyutlu bir
     * doğrusal sınıflayıcıdır, nöron ağı değil).
     */
    private static void olcumAdimi(MinecraftServer server) {
        for (var e : PersonalityManager.tumOyuncular().entrySet()) {
            UUID uid = e.getKey();
            TruePersonality tp = e.getValue();
            if (tp == null || !tp.kurulumTamam) continue;
            Kurul k = cache.get(uid);
            if (k == null) { kurul(uid); continue; }   // hazırlığı tetikle
            var player = server.getPlayerList().getPlayer(uid);
            for (Beyin b : k.uyeler()) {
                if (b == null) continue;
                try {
                    DunyaDogrulayici.supurme(b, player, tp);
                    if (b.oyuncuHaritasi != null) {
                        b.oyuncuHaritasi.egit(b.girdi, tp.aktifNiyet, HARITA_LR);
                        b.oyuncuHaritasi.haritayaIsle(b.girdi, PROTOTIP_LR);
                    }
                    Enerji.ablasyonTick(b, b.oyuncuHaritasi);
                } catch (Throwable t) {
                    Candy.LOG.debug("[Candy] ölçüm hatası ({} üye {}): {}", uid, b.uyeNo, t.getMessage());
                }
            }
        }
    }

    /** Dönen imleçle NÖRON bütçeli dilim işleme — oyuncu ve kurul boyutundan bağımsız. */
    private static void dilimAdimi(MinecraftServer server) {
        // v8: liste her tick yeniden tahsis ediliyordu (gate'in dışında kalmış). Aynı
        // tampon yeniden kullanılıyor — imleç zaten liste sırasına göre dönüyor.
        dilimOyuncular.clear();
        dilimOyuncular.addAll(PersonalityManager.tumOyuncular().keySet());
        final List<UUID> oyuncular = dilimOyuncular;
        if (oyuncular.isEmpty()) return;

        int islenen = 0;
        int denenen = 0;
        BeyinOlcer.basla();
        while (islenen < NORON_BUTCESI && denenen < oyuncular.size()) {
            if (imlec >= oyuncular.size()) imlec = 0;
            UUID uid = oyuncular.get(imlec++);
            denenen++;

            TruePersonality tp = PersonalityManager.tumOyuncular().get(uid);
            if (tp == null || !tp.kurulumTamam) continue;
            Kurul k = kurul(uid);
            if (k == null) continue;

            islenen += k.dilimAdimi(NORON_BUTCESI - islenen, RNG,
                uye -> supurmeTamam(server, k, uye, tp, uid));
        }
        BeyinOlcer.bitir(islenen);
    }

    /**
     * Bir üyenin tam düşünce döngüsü bitti.
     *
     * <h3>GÖVDE / ÜYE AYRIMI</h3>
     * METABOLİZMA (enerji, budama, nörogenez, ablasyon, norm, NaN, öz-model) HER üye için
     * çalışır — üyeler gerçekten yaşayan, kendi başına öğrenen beyinlerdir.
     *
     * DIŞA DÖNÜK olan her şey yalnızca gövdeye aittir: kişilik ağacına yazma, oyuncuya
     * sızıntı mesajı, {@code tp}'ye dokunma. Bunun sebebi estetik değil yapısal —
     * on bir üye ağaca yazsaydı çıkışları birbirine karışır, ve daha kötüsü {@code tp}
     * üzerinden bir sonraki nabızda hepsinin girdisine dönerdi. O an üyeler bağımsız
     * olmaktan çıkar, komitenin tüm istatistiksel dayanağı çöker.
     */
    private static void supurmeTamam(MinecraftServer server, Kurul kurul, Beyin b,
                                     TruePersonality tp, UUID uid) {
        final boolean govde = (b.uyeNo == 0);
        b.toplamSupurme++;
        b.modulator = Plastisite.modulator(b, tp.sasirmaSeviyesi);
        zamanIziKaydet(b);

        if (govde) {
            PersonalityTree tree = TreeGrowth.get(uid);
            BeyinCikti.uygula(b, tree, KARISIM);
            BeyinCikti.durumYansit(b, tp);
        } else {
            // Üye ağaca yazmaz ama kendi çıkış istatistiğini tutmalı: normalizasyon,
            // ablasyon ve TARTI'nın oyları hep o dağılımdan okunur.
            b.cikisIstatistikGuncelle();
        }

        long sup = b.toplamSupurme;
        if (sup % ENERJI_SUPURME == 0) {
            Enerji.dagit(b);
            Enerji.atrofiKontrol(b);
        }
        if (sup % ABLASYON_SUPURME == 0) {
            Enerji.ablasyonBaslat(b, b.oyuncuHaritasi);
        }
        if (sup % BUDAMA_SUPURME == 0) Plastisite.buda(b);
        if (sup % NOROGENEZ_SUPURME == 0) Plastisite.norogenez(b, RNG);
        if (sup % NORM_SUPURME == 0) {
            for (Lob lob : Lob.LOBLAR) Plastisite.normKontrol(b, lob);
        }
        if (Plastisite.nanTaramasi(b, RNG)) {
            Candy.LOG.warn("[Candy] üye {}: NaN sizintisi yakalandi ve temizlendi", b.uyeNo);
        }
        if (sup % OZ_DENETIM_SUPURME == 0 && b.ozModel != null) {
            b.ozModel.denetle(tp, dunyaId, sup);
            b.ozModel.ozSorgu = tp.candyOzFarkindalik;
        }
        // NOT: periyodik kayıt artık onTick'te DUVAR SAATİ tabanlı (KAYIT_TICK). Gövde
        // süpürmesine bağlamak, çok üyeli kurulda üyelerin çoğunu hiç yazmıyordu.

        if (govde) sizinti(b, tp, server.getPlayerList().getPlayer(uid), sup);
    }

    /**
     * BEYİNCİK'in zaman serisine süpürme başına bir örnek yazar.
     *
     * Etkinlik ölçüsü olarak "bekleme niyetinin tersi" kullanılır: oyuncu bekliyorsa
     * düşük, kazıyor/inşa ediyor/savaşıyorsa yüksek. Niyet kodu ise en güçlü niyetin
     * sırasıdır (0..6) — ritim analizi için ham sınıf yeter.
     */
    private static void zamanIziKaydet(Beyin b) {
        if (b.zamanIzi == null || b.girdi == null || b.girdi.length < 42) return;
        float korku = b.girdi[8];                       // korkuSkoru
        float etkinlik = 1f - b.girdi[38];              // 1 - niyet_bekle
        int enIyi = 0;
        float enYuksek = -1f;
        for (int i = 0; i < 7; i++) {                   // niyet one-hot: 35..41
            float g = b.girdi[35 + i];
            if (g > enYuksek) { enYuksek = g; enIyi = i; }
        }
        b.zamanIzi.kaydet(korku, etkinlik, enIyi);
    }

    /**
     * Beynin iç durumundan nadir, kısa sızıntılar. Candy asla "nöronum/lobum" demez;
     * yalnızca SONUÇ konuşur.
     */
    private static void sizinti(Beyin b, TruePersonality tp, ServerPlayer player, long sup) {
        if (player == null || tp.bossOldurulmeTick > 0) return;
        if (sup - b.sonSizintiSupurme < 40) return;
        if (RNG.nextDouble() > 0.35) return;

        String anahtar = null;
        Object arg = null;

        if (b.dunyalar != null && b.dunyalar.size() > 1 && !b.hatirlamaSizdi) {
            b.hatirlamaSizdi = true;
            anahtar = "candy.hatirlama." + RNG.nextInt(3);
        } else if (b.loblar != null && b.ablasyonLob < 0 && b.ablasyonSira > 0
                   && sup - b.sonSizintiSupurme > 90 && RNG.nextInt(3) == 0) {
            anahtar = "candy.kisma." + RNG.nextInt(2);
        } else if (b.loblar != null && korelmisVar(b) && RNG.nextInt(4) == 0) {
            anahtar = "candy.aclik.0";
        } else if (b.oyuncuHaritasi != null && b.oyuncuHaritasi.tahminSayisi > 150
                   && b.oyuncuHaritasi.isabetOrani() > 0.55 && RNG.nextInt(3) == 0) {
            anahtar = "candy.tanidim." + RNG.nextInt(2);
        } else if (b.ozModel != null) {
            Iddia supheli = b.ozModel.enSupheli();
            if (supheli != null && RNG.nextInt(3) == 0) {
                anahtar = "candy.yanilma.0";
                arg = supheli.anahtar;
            }
        }
        if (anahtar == null) return;
        b.sonSizintiSupurme = sup;
        Component mesaj = (arg == null) ? Component.translatable(anahtar) : Component.translatable(anahtar, arg);
        player.displayClientMessage(mesaj.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
    }

    private static boolean korelmisVar(Beyin b) {
        for (LobDurum d : b.loblar) if (d != null && d.koreldi) return true;
        return false;
    }

    // ─────────────────────────────────────────────────────────
    // İZLEME (DEBUG)
    // ─────────────────────────────────────────────────────────

    private static void durumRaporu() {
        for (Kurul kurul : cache.values()) {
            kurulRaporu(kurul);
            Beyin b = kurul.govde();
            if (b == null) continue;
            Candy.LOG.info("[Candy] beyin: sup={} dilim={} norm={} mod={} bud={} dog={} diri={} | {}",
                b.toplamSupurme, b.dilim, String.format("%.4f", ortAgirlikNormu(b)),
                String.format("%.2f", b.modulator), b.budananToplam, b.doganToplam,
                b.golgeArsivi == null ? 0 : b.golgeArsivi.dirilenToplam, lobOzeti(b));
            Candy.LOG.info("[Candy] ustduzey: karar={} tutarsiz={} pismanlik={} | ritim={} rehavet={} | arsiv={} | oy={}",
                b.kararIzi == null ? 0 : b.kararIzi.toplamKarar(),
                String.format("%.2f", b.kararIzi == null ? 0f : b.kararIzi.tutarsizlik()),
                String.format("%.2f", b.kararIzi == null ? 0f : b.kararIzi.pismanlik()),
                String.format("%.2f", b.zamanIzi == null ? 0f : b.zamanIzi.periyodiklik()),
                String.format("%.2f", b.zamanIzi == null ? 0f : b.zamanIzi.rehavet()),
                b.golgeArsivi == null ? 0 : b.golgeArsivi.dolu, oyOzeti(b));
            // SESSİZ ÇÖKÜŞ UYARISI: NaN'dan daha tehlikeli — her şey 0,5'e yapışırsa
            // beyin çalışır, CPU yanar ama ağaca hiçbir bilgi gitmez.
            double hamStd = hamLobStd(b);
            double zamanStd = zamanIciStd(b);
            // ÇÖKÜŞ UYARISI yalnizca beynin OLGUNLASTIGI durumda anlamli. Taze insa edilmis
            // bir uye henuz hic supurme yapmadigi icin tum aktiflikler 0,5'te durur ->
            // hamStd=0. Bunu cokme sanmak yanlis alarmdir (ilk kurul kurulusunda goruldu).
            // zamanStd<0 = "yeterli ornek yok" demek; o zaman da hukum verilemez.
            // ÇÖKÜŞ = uzamsal VE zamansal std'nin BİRLİKTE düşmesi.
            //   hamStd düşük  → tüm loblar AYNI değeri söylüyor (uzamsal çöküş)
            //   zamanStd düşük → çıkışlar zaman içinde DONMUŞ
            // Yalnız zamanStd düşükse beyin SAĞLIKLIDIR: oyuncunun durumu o an durgun
            // demektir, çökmüş değil. Soak'ta tek-oyunculu durgun durumlar hamStd 0,058
            // (sağlıklı) iken zamanStd'yi 0,009'a düşürdü ve DÜZİNELERCE yanlış alarm
            // üretti. Uzamsal çöküş birincil sinyaldir; zamansal yalnızca ONU teyit eder.
            boolean olgun = b.toplamSupurme > 60 && zamanStd >= 0.0;
            boolean uzamsalCokus = hamStd < 0.02;
            boolean donmus = hamStd < 0.035 && zamanStd < 0.008;
            if (olgun && (uzamsalCokus || donmus)) {
                Candy.LOG.warn("[Candy] UYARI: ham lob std {} / zaman içi std {} — beyin sessizce çökmüş olabilir",
                    String.format("%.4f", hamStd), String.format("%.4f", zamanStd));
            }
            if (b.oyuncuHaritasi != null) {
                Candy.LOG.info("[Candy] harita: tahmin={} isabet={}% hata={} hal={} hamStd={} zamanStd={} | onem={} | tutarlilik={}",
                    b.oyuncuHaritasi.tahminSayisi,
                    String.format("%.0f", b.oyuncuHaritasi.isabetOrani() * 100),
                    String.format("%.3f", b.oyuncuHaritasi.sonPencereHatasi()),
                    b.oyuncuHaritasi.kesfedilenHalSayisi(),
                    String.format("%.4f", hamStd),
                    zamanStd < 0.0 ? "?" : String.format("%.4f", zamanStd),
                    onemOzeti(b),
                    String.format("%.2f", b.ozModel == null ? 0 : b.ozModel.tutarlilik));
            }
            if (b.aktifDunya != null) {
                Candy.LOG.info("[Candy] dunya[{}]: tahmin={} brier={} ece={} genelleme={} plastisite={}",
                    b.aktifDunya.dunyaId, b.aktifDunya.tahmin,
                    String.format("%.3f", b.aktifDunya.brier()),
                    String.format("%.3f", b.aktifDunya.ece()),
                    String.format("%.2f", b.aktifDunya.genellemeSkoru),
                    String.format("%.2f", b.plastisiteCarpani));
            }
        }
    }

    /**
     * HAM lob çıkışlarının loblar arası standart sapması — "loblar birbirinden farklı
     * şeyler mi söylüyor?"
     *
     * ÖLÇÜM TUZAĞI (bu metot bir kez YANLIŞ yazıldı): eskiden aynı hesap
     * {@link Beyin#lobCikisi} üzerinden, yani UYARLANIR NORMALİZASYONDAN GEÇMİŞ değerlerle
     * yapılıyordu. O normalizasyon her lobu kendi geçmiş ortalamasına göre okur; EMA
     * oturduktan sonra tüm loblar tanım gereği 0,5'e yaklaşır ve loblar arası std
     * <b>matematiksel olarak</b> sıfıra gider. Sağlıklı bir beyinde bile alarm çalar,
     * gerçek bir çöküşte ise fark edilmez — yani gösterge tam ters yönde bozulur.
     * Çöküş HAM sinyalde aranmalı.
     */
    private static double hamLobStd(Beyin b) {
        Lob[] loblar = Lob.LOBLAR;
        double t = 0;
        for (Lob l : loblar) t += b.hamLobCikisi(l);
        double ort = t / loblar.length;
        double v = 0;
        for (Lob l : loblar) { double d = b.hamLobCikisi(l) - ort; v += d * d; }
        return Math.sqrt(v / loblar.length);
    }

    /**
     * Lobların ZAMAN İÇİNDEKİ ortalama oynaklığı — "lob çıkışı hareket ediyor mu?"
     *
     * Loblar arası fark, ölçümün yalnızca yarısıdır: on lob birbirinden farklı ama her
     * biri donmuş sabit değerler üretiyorsa beyin yine bilgi taşımıyordur. Bu sayı
     * uyarlanır normalizasyonun zaten tuttuğu EMA varyansından bedavaya gelir.
     */
    private static double zamanIciStd(Beyin b) {
        if (b.loblar == null) return 0.0;
        double t = 0;
        int n = 0;
        for (LobDurum d : b.loblar) {
            if (d == null || d.cikisOrnek < 20) continue;
            t += Math.sqrt(Math.max(0.0, d.cikisVar));
            n++;
        }
        // -1 = HENUZ BILINMIYOR (taze/goc etmis beyin). 1.0 dondurmek "her sey yolunda"
        // demek olurdu; ikisini ayirt etmemek uyariyi sessizce ise yaramaz hale getirir.
        return n == 0 ? -1.0 : t / n;
    }

    private static double ortAgirlikNormu(Beyin b) {
        if (b.agirlik == null || b.sinapsSayisi == 0) return 0;
        double t = 0;
        for (int j = 0; j < b.sinapsSayisi; j++) t += Math.abs(b.agirlik[j]);
        return t / b.sinapsSayisi;
    }

    private static String onemOzeti(Beyin b) {
        if (b.loblar == null) return "-";
        StringBuilder sb = new StringBuilder();
        for (Lob l : Lob.LOBLAR) {
            LobDurum d = b.loblar[l.ordinal()];
            if (d == null) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(kisaAd(l)).append('=').append(String.format("%.2f", d.hayatiOnem));
            if (d.koreldi) sb.append("(X)");
        }
        return sb.toString();
    }

    private static String lobOzeti(Beyin b) {
        StringBuilder sb = new StringBuilder();
        for (Lob l : Lob.LOBLAR) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(kisaAd(l)).append('=').append(String.format("%.3f", b.lobCikisi(l)));
        }
        return sb.toString();
    }

    /**
     * KURUL KARNESİ — bu mimarinin kabul sınavı.
     *
     * Komite pahalıdır: 11 beyin, 11 kat bellek, 11 kat disk. Bu masrafın tek meşru
     * gerekçesi, hakemin <b>en iyi tek üyeden daha isabetli</b> olmasıdır. Rapor bunu
     * doğrudan yan yana basar; hakem geride kalıyorsa mimari işe yaramıyor demektir ve
     * bunu gizlemek yerine loga yazmak doğrusudur.
     *
     * "Bağımsızlık" satırı da ölçülür: {@code ihlal} sıfırdan büyükse üyelerden biri
     * aort'un dağıttığından farklı bir vektörle karar vermiş demektir — komitenin
     * dayandığı "aynı bilgi" varsayımı kırılmıştır.
     */
    private static void kurulRaporu(Kurul k) {
        if (k == null) return;
        StringBuilder sb = new StringBuilder();
        double enIyiUye = 0, toplamUye = 0;
        int enIyiNo = -1, sayi = 0;
        for (Beyin b : k.uyeler()) {
            if (b == null) continue;
            double g = b.sicil.guvenilirlik();
            if (g > enIyiUye) { enIyiUye = g; enIyiNo = b.uyeNo; }
            toplamUye += g;
            sayi++;
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("u%d:%.0f%%", b.uyeNo, g * 100));
        }
        double ortUye = (sayi == 0) ? 0.5 : toplamUye / sayi;
        double hakemG = k.hakem.sicil.guvenilirlik();
        double ortKal = 0;
        double enIyiKal = 0;
        for (Beyin b : k.uyeler()) {
            if (b == null) continue;
            double kal = b.sicil.kalibrasyon();
            ortKal += kal;
            if (kal > enIyiKal) enIyiKal = kal;
        }
        ortKal = (sayi == 0) ? 0.5 : ortKal / sayi;
        double hakemKal = k.hakem.sicil.kalibrasyon();
        Candy.LOG.info("[Candy] kurul: {}/{} üye · {} nöron · nabız {} · ihlal {} (bekleyen {}) | {}",
            k.hazirUye(), Kurul.UYE_SAYISI, k.toplamNoron(), k.aort.nabiz(),
            k.aort.ihlal(), k.aort.beklemede(), sb);
        // ADİL TABAN = ORTALAMA ÜYE, en iyi üye DEĞİL.
        // "En iyi üye" on bir gürültülü tahminin maksimumudur; kazananın laneti yüzünden
        // gerçek yeteneğinin üstünde görünür. Hakemi ona karşı ölçmek, komiteyi
        // yapısal olarak kaybetmeye mahkûm eden hileli bir sınavdır. Doğru soru
        // "rastgele bir üyeyi dinlemek yerine hakemi dinlemek daha mı iyi?" — yani
        // ortalama üyeyle karşılaştırma. En iyi üye yine de basılır (üst sınır olarak).
        // BİRİNCİL ÖLÇÜT KALİBRASYON (Brier): uygun puanlama kuralı, saçılmayla
        // şişirilemez. İsabet oranı yine basılır ama ikincildir.
        Candy.LOG.info("[Candy] hakem KALİBRASYON: {}% · ort üye {}% · fark {} | en iyi üye {}%",
            String.format("%.0f", hakemKal * 100), String.format("%.0f", ortKal * 100),
            String.format("%+.1f", (hakemKal - ortKal) * 100),
            String.format("%.0f", enIyiKal * 100));
        Candy.LOG.info("[Candy] hakem: isabet {}% (n={}) · ort üye {}% · fark {} | tavan: u{} {}%{}",
            String.format("%.0f", hakemG * 100), k.hakem.sicil.toplamDegerlendirme,
            String.format("%.0f", ortUye * 100),
            String.format("%+.1f", (hakemG - ortUye) * 100),
            enIyiNo, String.format("%.0f", enIyiUye * 100),
            k.hakem.sicil.toplamDegerlendirme < 60 ? " (veri az)" : "");
    }

    /** TARTI'nin lob basina verdigi oyun ozeti — "hangi ag ise yariyor" kanaati. */
    private static String oyOzeti(Beyin b) {
        if (b.loblar == null) return "-";
        StringBuilder sb = new StringBuilder();
        for (Lob l : Lob.LOBLAR) {
            LobDurum d = b.loblar[l.ordinal()];
            if (d == null) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(kisaAd(l)).append(String.format("%+.2f", d.tartiOyu));
        }
        return sb.toString();
    }

    /**
     * Log kisaltmasi. 3 harf YETMEZ: YANLAMA ve YANKI ikisi de "YAN" olurdu ve
     * loglardaki lob ozetleri sessizce yaniltici hale gelirdi.
     */
    private static String kisaAd(Lob l) {
        String ad = l.name();
        return ad.substring(0, Math.min(4, ad.length()));
    }

    // ─────────────────────────────────────────────────────────
    // YAŞAM DÖNGÜSÜ
    // ─────────────────────────────────────────────────────────

    public static void oyuncuAyrildi(UUID uuid) {
        Kurul k = cache.remove(uuid);
        // ARKA PLANDA: 11 senkron gzip yazımı ana thread'de oyuncu çıkışında ~1 sn'lik
        // görünür bir donma yaratırdı. Havuz yazımları düşürmez.
        if (k != null) k.hepsiniKaydetAsync();
    }

    public static void sunucuKapan() {
        for (Kurul k : cache.values()) k.hepsiniKaydet();
        BeyinDeposu.kapanisBekle();
        cache.clear();
        hazirlaniyor.clear();
        tickSayaci = 0;
        imlec = 0;
        BeyinOlcer.sifirla();
    }

    public static int aktifBeyinSayisi() {
        int n = 0;
        for (Kurul k : cache.values()) n += k.hazirUye();
        return n;
    }
}
