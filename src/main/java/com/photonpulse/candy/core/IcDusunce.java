package com.photonpulse.candy.core;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.platform.SunucuTick;
import com.photonpulse.candy.platform.BlokOlaylari;
import com.photonpulse.candy.platform.EtkilesimOlaylari;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * IcDusunce (v3.1) — Candy'nin iç sesi. Oyuncunun ne yaptığını ANLIK yorumlar.
 *
 * DerinAnaliz nicel veri toplar; bu sınıf oyuncunun NİYETİNİ çıkarır ("kazıyor",
 * "saklanıyor", "kaçıyor"...), bunlardan bir KANAAT oluşturur ("korkuyor",
 * "meydan okuyor", "takıntılı") ve:
 *   1. Nadiren felsefi sorular fısıldar ("Neden hep buraya dönüyorsun?")
 *   2. Kanaate göre oyunu şekillendirir (özgüven/korku + niyet-özel tepkiler)
 *
 * Tamamen server-side, mixin yok.
 */
public class IcDusunce {

    private static final int ARALIK = 40;              // 2 sn — niyet güncelle
    private static final long ICSES_COOLDOWN = 1800L;  // min 90 sn iki fısıltı arası
    private static final double ICSES_IHTIMAL = 0.20;

    private static final Random RNG = new Random();

    // Per-player: son kırma tick'i + kırma sayacı (mining sinyali)
    private static final Map<UUID, Long> sonKirmaTick = new HashMap<>();
    private static final Map<UUID, Integer> kirmaSayac = new HashMap<>();
    private static final Map<UUID, Long> sonKoymaTick = new HashMap<>();
    private static final Map<UUID, Vec3> oncekiPoz = new HashMap<>();

    private static int tick = 0;

    public static void register() {
        // Blok kırma → mining sinyali
        BlokOlaylari.AFTER.register((world, player, pos, state) -> {
            if (player instanceof ServerPlayer) {
                UUID id = player.getUUID();
                sonKirmaTick.put(id, world.getGameTime());
                kirmaSayac.merge(id, 1, Integer::sum);
            }
        });

        // Blok koyma (BlockItem kullanımı) → building sinyali
        EtkilesimOlaylari.BLOK.register((player, world, hand, hit) -> {
            if (player instanceof ServerPlayer
                && player.getItemInHand(hand).getItem() instanceof BlockItem) {
                sonKoymaTick.put(player.getUUID(), world.getGameTime());
            }
            return InteractionResult.PASS;
        });

        SunucuTick.END_SERVER_TICK.register(IcDusunce::onTick);
        Candy.LOG.info("[Candy] IcDusunce aktif — niyet çıkarımı + felsefi sorgulama (v3.1)");
    }

    private static void onTick(MinecraftServer server) {
        tick++;
        if (tick % ARALIK != 0) return;
        long simdi = server.overworld().getGameTime();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                UUID id = player.getUUID();
                ServerLevel world = (ServerLevel) player.level();

                // Hareket
                Vec3 simdiPoz = player.position();
                Vec3 onceki = oncekiPoz.get(id);
                double hiz = onceki == null ? 0 : simdiPoz.distanceTo(onceki);
                oncekiPoz.put(id, simdiPoz);

                // Sinyaller
                boolean kaziyor = simdi - sonKirmaTick.getOrDefault(id, -9999L) < 40
                    && kirmaSayac.getOrDefault(id, 0) >= 3;
                boolean koyuyor = simdi - sonKoymaTick.getOrDefault(id, -9999L) < 40;
                boolean yeralti = player.getBlockY() < 50;
                boolean kazma = player.getMainHandItem().is(ItemTags.PICKAXES);
                boolean klonYakin = klonYakinMi(world, player);
                boolean sprint = player.isSprinting() && hiz > 0.15;
                boolean sicakNoktada = sicakNoktadaMi(tp, player);

                // Niyet çıkarımı
                String niyet;
                if (kaziyor || (kazma && yeralti && hiz < 0.05)) niyet = "kaziyor";
                else if (koyuyor) niyet = "insa_ediyor";
                else if (sprint && klonYakin) niyet = "kaciyor";
                else if (tp.saldirOrani > 0.4 && klonYakin) niyet = "savasiyor";
                else if (hiz < 0.02) niyet = "bekliyor";
                else if (sicakNoktada) niyet = "donuyor";
                else if (hiz > 0.1) niyet = "kesfediyor";
                else niyet = "bilinmiyor";

                tp.aktifNiyet = niyet;
                if (!"bilinmiyor".equals(niyet)) {
                    // Birikim + eski kanıtları hafif söndür
                    tp.niyetAgirliklari.replaceAll((k, v) -> v * 0.95);
                    tp.niyetAgirliklari.merge(niyet, 1.0, Double::sum);
                }
                kirmaSayac.put(id, 0); // sayacı sıfırla (pencere bazlı)

                // v3.2 (1)(4) Kehanet Defteri + Şaşırma — reaktiften öngörücüye
                Kehanet.gozlemle(player, tp, niyet, simdi);

                // Kanaat oluştur (baskın niyetten) — NİCEL (tp) + NİTEL (gölge/simülasyon)
                var sp = com.photonpulse.candy.simulation.SimulationManager.get(id);
                tp.candyKanaati = kanaatUret(tp, sp);

                // Oyunu şekillendir — kanaate göre özgüven/korku (+ gölge memnuniyetsizliği)
                kanaatiUygula(tp);

                // v3.2 (2) Bayesçi kanaat — tek sonuç yerine ağırlıklı rakip hipotezler
                hipotezGuncelle(tp);
                // v3.2 (8) Kendini modelleme + (10) Yalan söyleyen kanaat
                bilissel(player, tp, simdi);

                // Felsefi soru fısılt (nadir)
                if (simdi - tp.sonIcSesTick >= ICSES_COOLDOWN && RNG.nextDouble() < ICSES_IHTIMAL) {
                    tp.sonIcSesTick = simdi;
                    felsefiSoru(player, tp);
                }

            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] IcDusunce hata: {}", t.getMessage());
            }
        }
    }

    /**
     * Baskın niyet + psikoloji (NİCEL) + gölge ikizi (NİTEL) → Candy'nin kanaati.
     * Gölge divergence düşükse (oyuncu, hayali zıttına hiç benzemiyor = net kimlik)
     * Candy "seni anladım" der ve kanaati keskinleşir.
     */
    private static String kanaatUret(TruePersonality tp,
            com.photonpulse.candy.simulation.SimulatedPersonality sp) {
        String baskin = baskinNiyet(tp);
        // NİTEL sorgu: gerçek saldırganlık vs hayali ikizin saldırganlığı
        double divergence = (sp == null) ? 0.5
            : Math.abs(tp.saldirOrani - sp.saldirOrani) + Math.abs(tp.kacOrani - sp.kacOrani);
        boolean netKimlik = divergence > 0.6; // oyuncu tutarlı/tanınabilir

        return switch (baskin) {
            case "kaciyor", "bekliyor" -> tp.korkuSkoru > 60 ? "korkuyor" : "saklaniyor";
            case "insa_ediyor" -> "saklaniyor";
            case "savasiyor" -> "meydan_okuyor";
            case "donuyor" -> "takintili";
            case "kesfediyor" -> tp.korkuSkoru > 60 ? "kayip"
                : (netKimlik ? "takintili" : "meydan_okuyor");
            case "kaziyor" -> "takintili";
            default -> "gozluyor";
        };
    }

    /** Kanaate göre oyunu şekillendir + gölge memnuniyetsizliği (Candy sıkılması) hızlandırır. */
    private static void kanaatiUygula(TruePersonality tp) {
        // NİTEL: Candy oyuncudan sıkıldıkça (memnuniyetsizlik) tırmanış hızlanır
        double sikinti = 1.0 + Math.min(1.0, tp.memnuniyetsizlik / 100.0);
        switch (tp.candyKanaati) {
            case "korkuyor", "saklaniyor" ->
                tp.candyOzguven = Math.min(100, tp.candyOzguven + 0.08 * sikinti);
            case "meydan_okuyor" ->
                tp.korkuSkoru = Math.min(100, tp.korkuSkoru + 0.05 * sikinti);
            case "takintili" ->
                tp.candyOzguven = Math.min(100, tp.candyOzguven + 0.05 * sikinti);
            default -> { /* gozluyor/kayip — nötr */ }
        }
    }

    // ── v3.2 (2) Bayesçi Kanaat — ağırlıklı rakip hipotezler ──
    private static final long OZ_COOLDOWN = 3600L;    // kendini modelleme 3 dk
    private static final long YALAN_COOLDOWN = 4800L; // yalan 4 dk
    private static final int OZ_SORU_SAYI = 4;

    /** 4 rakip hipotezi kanıtla güncelle (korkak/cesur/merakli/kayitsiz) + normalize et. */
    private static void hipotezGuncelle(TruePersonality tp) {
        double korkak  = tp.korkuSkoru * 0.5 + tp.kacOrani * 40 + tp.paranoyaSkoru * 0.3;
        double cesur   = tp.saldirOrani * 40 + tp.riskIstahi * 0.4 + (100 - tp.korkuSkoru) * 0.2;
        double merakli = tp.kesfetOrani * 40 + (100 - tp.ongorulebilirlik) * 0.3;
        double kayitsiz= tp.candyGormezden * 40 + (100 - tp.korkuSkoru) * 0.2;
        hedefKay(tp, "korkak", korkak);
        hedefKay(tp, "cesur", cesur);
        hedefKay(tp, "merakli", merakli);
        hedefKay(tp, "kayitsiz", kayitsiz);
        double t = 0;
        for (double v : tp.hipotezAgirliklari.values()) t += v;
        final double toplam = t;
        if (toplam > 1e-6) tp.hipotezAgirliklari.replaceAll((k, v) -> v / toplam);
    }

    private static void hedefKay(TruePersonality tp, String k, double raw) {
        double hedef = Math.max(0, Math.min(1, raw / 100.0));
        double mevcut = tp.hipotezAgirliklari.getOrDefault(k, 0.25);
        tp.hipotezAgirliklari.put(k, TruePersonality.kayma(mevcut, hedef, 0.08));
    }

    private static String enDusukHipotez(TruePersonality tp) {
        String enAz = "cesur"; double min = 2;
        for (var e : tp.hipotezAgirliklari.entrySet()) {
            if (e.getValue() < min) { min = e.getValue(); enAz = e.getKey(); }
        }
        return enAz;
    }

    /** (8) Kendini modelleme + (10) yalan söyleyen kanaat. */
    private static void bilissel(ServerPlayer player, TruePersonality tp, long simdi) {
        if (tp.bossOldurulmeTick > 0) return; // WIN sessizliği

        // (8) Seni tanıdıkça (kehanetGuveni) Candy kendi doğasını sorgular
        tp.candyOzFarkindalik = Math.min(100,
            tp.candyOzFarkindalik + 0.02 + tp.kehanetGuveni * 0.0008);
        if (tp.candyOzFarkindalik > 40 && simdi - tp.sonOzDusunce > OZ_COOLDOWN
                && RNG.nextDouble() < 0.05) {
            tp.sonOzDusunce = simdi;
            player.displayClientMessage(Component.translatable("candy.ozdusunce." + RNG.nextInt(OZ_SORU_SAYI))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
            Candy.LOG.info("[Candy] Kendini modelleme[{}] özfarkındalık={}",
                player.getName().getString(), String.format("%.0f", tp.candyOzFarkindalik));
            return; // aynı cycle'da yalan söyleme
        }

        // (10) Yalan — en DÜŞÜK hipotezi doğruymuş gibi söyle (provokasyon/manipülasyon)
        if (simdi - tp.sonYalanTick > YALAN_COOLDOWN && RNG.nextDouble() < 0.04
                && !tp.hipotezAgirliklari.isEmpty()) {
            tp.sonYalanTick = simdi;
            String yalan = enDusukHipotez(tp);
            player.displayClientMessage(Component.translatable("candy.yalan." + yalan)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
            CandyBasarimlar.tetikle(player, "yalanci");
            Candy.LOG.info("[Candy] Yalan[{}] gerçek={} → yalan={}",
                player.getName().getString(), tp.baskinHipotez(), yalan);
        }
    }

    /** Dış sistemlerin (ZihinselProva) Candy'ye soru fısıldatması için public API. */
    public static void soruSor(ServerPlayer player) {
        felsefiSoru(player, PersonalityManager.get(player));
    }

    /** Niyete göre felsefi soru fısılt — Candy'nin iç sesi sızıyor. */
    private static void felsefiSoru(ServerPlayer player, TruePersonality tp) {
        String niyet = tp.aktifNiyet;
        String key;
        boolean koordinatli = false;
        switch (niyet) {
            case "kaziyor" -> key = "candy.icses.kaziyor." + RNG.nextInt(2);
            case "insa_ediyor" -> key = "candy.icses.insa." + RNG.nextInt(2);
            case "kesfediyor" -> key = "candy.icses.kesfet." + RNG.nextInt(2);
            case "bekliyor" -> key = "candy.icses.bekle." + RNG.nextInt(2);
            case "donuyor" -> key = "candy.icses.don." + RNG.nextInt(2);
            case "savasiyor" -> key = "candy.icses.savas." + RNG.nextInt(2);
            case "kaciyor" -> key = "candy.icses.kac." + RNG.nextInt(2);
            default -> { key = "candy.icses.koordinat"; koordinatli = true; }
        }

        Component mesaj;
        if (koordinatli) {
            String koord = player.getBlockX() + " " + player.getBlockZ();
            mesaj = Component.translatable(key, koord);
        } else {
            mesaj = Component.translatable(key);
        }
        // Candy'nin iç sesi — isimsiz, koyu gri italik (bir düşünce, konuşma değil)
        player.displayClientMessage(mesaj.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
        Candy.LOG.info("[Candy] IcSes[{}] niyet={} → {}",
            player.getName().getString(), niyet, key);
    }

    private static String baskinNiyet(TruePersonality tp) {
        String enSik = "bilinmiyor"; double max = 0;
        for (var e : tp.niyetAgirliklari.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); enSik = e.getKey(); }
        }
        return enSik;
    }

    private static boolean klonYakinMi(ServerLevel world, ServerPlayer player) {
        AABB box = new AABB(player.blockPosition()).inflate(20);
        return !world.getEntitiesOfClass(CandyDoppelEntity.class, box, e -> e.isAlive()).isEmpty();
    }

    private static boolean sicakNoktadaMi(TruePersonality tp, ServerPlayer player) {
        int px = player.getBlockX(), pz = player.getBlockZ();
        for (int[] n : tp.sicakNoktalar) {
            int dx = n[0] - px, dz = n[2] - pz;
            if (dx * dx + dz * dz < 36) return true; // 6 blok
        }
        return false;
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonKirmaTick.remove(uuid);
        kirmaSayac.remove(uuid);
        sonKoymaTick.remove(uuid);
        oncekiPoz.remove(uuid);
        Kehanet.oyuncuAyrildi(uuid);
    }
}
