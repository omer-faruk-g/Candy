package com.photonpulse.candy.core;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.entity.CandyEntities;
import com.photonpulse.candy.sessizlik.SessizlikState;
import com.photonpulse.candy.simulation.SimulatedPersonality;
import com.photonpulse.candy.simulation.SimulationManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * ZihinselProva (v3.1) — Candy'nin zihinsel provası / karar motoru.
 *
 * NİCEL (TruePersonality) + NİTEL (SimulatedPersonality/gölge) + İÇ SES (IcDusunce niyet/kanaat)
 * üçünden çıkan sonucu ALIR, aday eylemleri HAYALİ TESTTEN geçirir (her birinin bu oyuncuda
 * ne kadar etki edeceğini profilinden TAHMİN eder), en yüksek tahminliyi UYGULAR.
 *
 * Sonra gerçek tepkiyi ölçer → tahminini gerçekle karşılaştırır → öğrenme (isabet) güncellenir.
 * Yani Candy denemeden önce kafasında dener; yanılırsa bir dahakine daha iyi tahmin eder.
 */
public class ZihinselProva {

    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");
    private static final int ARALIK = 100;                         // 5 sn kontrol
    private static final long COOLDOWN = DEBUG ? 200L : 900L;      // debug 10sn / normal 45 sn
    private static final double MIN_KOTULUK = 20.0;

    private static final Random RNG = new Random();

    // Aday eylemler
    private enum Eylem { KLON_ARKA, KARANLIK, SES, SESSIZLIK, SORU, YAKIN_KLON }

    // Feedback bookkeeping (persist değil — canlı akıl)
    private record Bekleyen(Eylem eylem, double tahmin, double korkuOnce, long uygulamaTick) {}
    private static final Map<UUID, Bekleyen> bekleyen = new HashMap<>();
    private static final Map<UUID, Long> sonProva = new HashMap<>();

    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ZihinselProva::onTick);
        Candy.LOG.info("[Candy] ZihinselProva aktif — hayali test + karar motoru (v3.1)");
    }

    private static void onTick(MinecraftServer server) {
        tick++;
        if (tick % ARALIK != 0) return;
        long simdi = server.getOverworld().getTime();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                if (tp.bossOldurulmeTick > 0) continue; // WIN sessizliği
                UUID id = player.getUuid();

                // 1) Önceki eylemin gerçek sonucunu ölç → öğren (+ Silahlanma Yarışı)
                geribildirim(player, tp, id, simdi);

                // Silahlanma: emeklilik kalıcı olmasın — Candy zamanla varyasyon icat eder,
                // rastgele bir emekli mekaniği geri devreye alır (~her 100 sn'de bir).
                if (!tp.emekliMekanikler.isEmpty() && RNG.nextDouble() < 0.05) {
                    var it = tp.emekliMekanikler.iterator();
                    String geri = it.next();
                    it.remove();
                    tp.mekanikAdaptasyon.put(geri, 0.0);
                }

                if (tp.psikolojiKotulugu < MIN_KOTULUK) continue;
                Long son = sonProva.get(id);
                if (son != null && simdi - son < COOLDOWN) continue;

                // 2) 3 kaynağı topla
                SimulatedPersonality sp = SimulationManager.get(id);

                // 3) DENEY modu mu, KORKU modu mu? (idea 3 — Aktif Deney / Denek 239)
                // Bayesçi belirsizlik yüksekse Candy hangi hipotezin doğru olduğunu bilmiyor →
                // bazen korkuyu değil, hipotezleri en iyi AYIRAN eylemi seçer (bilgi kazancı).
                double belirsizlik = tp.hipotezBelirsizligi();
                boolean deneyModu = belirsizlik > 0.5 && RNG.nextDouble() < 0.5;

                // Aday eylemleri HAYALİ TESTTEN geçir — emekli mekanikleri atla (idea 12)
                Eylem enIyi = null; double enYuksek = -1;
                for (Eylem e : Eylem.values()) {
                    if (tp.emekliMekanikler.contains(e.name())) continue;
                    double u = (deneyModu ? ayirtEdicilik(e, tp) : tahminEt(e, tp, sp))
                             + RNG.nextDouble() * 0.1;
                    if (u > enYuksek) { enYuksek = u; enIyi = e; }
                }
                if (enIyi == null) { tp.emekliMekanikler.clear(); continue; } // hepsi emekliyse reset
                double fearTahmin = tahminEt(enIyi, tp, sp); // geribildirim korku beklentisi

                // 4) Kararı UYGULA (overworld saati — SessizlikState tutarlılığı için)
                ServerWorld world = (ServerWorld) player.getWorld();
                uygula(enIyi, player, world, simdi);
                sonProva.put(id, simdi);
                bekleyen.put(id, new Bekleyen(enIyi, fearTahmin, tp.korkuSkoru, simdi));
                Candy.LOG.info("[Candy] ZihinselProva[{}] kanaat={} mod={} → eylem={} (tahmin={})",
                    player.getName().getString(), tp.candyKanaati,
                    deneyModu ? "DENEY" : "korku", enIyi, String.format("%.2f", fearTahmin));

            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] ZihinselProva hata: {}", t.getMessage());
            }
        }
    }

    /** Önceki eylemden ~10 sn sonra gerçek korku değişimini ölç → öğrenme + Silahlanma. */
    private static void geribildirim(ServerPlayerEntity player, TruePersonality tp, UUID id, long simdi) {
        Bekleyen b = bekleyen.get(id);
        if (b == null) return;
        if (simdi - b.uygulamaTick() < 200) return; // 10 sn bekle
        bekleyen.remove(id);

        double gercekDelta = tp.korkuSkoru - b.korkuOnce();
        // Tahmin (0-1.5 arası utility) → beklenen korku artışı (~0-15)
        double beklenen = b.tahmin() * 10.0;
        double hata = Math.abs(gercekDelta - beklenen);
        boolean isabetli = hata < 5.0;
        // Öğrenme: isabetliyse Candy'nin öngörüsü güçlenir
        tp.ogrenmeHizi = TruePersonality.kayma(tp.ogrenmeHizi, isabetli ? 100 : 0, 0.15);

        // idea 12: Silahlanma Yarışı — eylem etkisizse oyuncu adapte oldu
        String ea = b.eylem().name();
        if (gercekDelta < 1.5) {
            double a = tp.mekanikAdaptasyon.merge(ea, 1.0, Double::sum);
            if (a >= 3 && !tp.emekliMekanikler.contains(ea)) {
                tp.emekliMekanikler.add(ea);
                if (tp.bossOldurulmeTick == 0) {
                    player.sendMessage(Text.translatable("candy.silahlanma." + RNG.nextInt(2))
                        .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
                }
                Candy.LOG.info("[Candy] Silahlanma[{}] '{}' emekliye ayrıldı — oyuncu adapte oldu",
                    player.getName().getString(), ea);
            }
        } else if (gercekDelta > 4) {
            // Etkili → adaptasyon geriler, emeklilikten çıkabilir (yeniden devreye)
            double a = tp.mekanikAdaptasyon.merge(ea, -1.0, Double::sum);
            if (a < 1) tp.emekliMekanikler.remove(ea);
        }
        Candy.LOG.debug("[Candy] ZihinselProva geri: eylem={} beklenen={} gercek={} isabet={}",
            b.eylem(), String.format("%.1f", beklenen), String.format("%.1f", gercekDelta), isabetli);
    }

    /**
     * idea 3: Eylemin rakip hipotezleri ne kadar AYIRT ettiği (bilgi kazancı proxy).
     * Her eylemin hipotez-başına beklenen tepki vektörü var; bu vektörün, güncel hipotez
     * ağırlıklarına göre VARYANSI yüksekse eylem hipotezleri iyi ayırır → deney için ideal.
     */
    private static double ayirtEdicilik(Eylem e, TruePersonality tp) {
        double[] r = tepkiVektoru(e); // [korkak, cesur, merakli, kayitsiz]
        double wK = tp.hipotezAgirliklari.getOrDefault("korkak", 0.25);
        double wC = tp.hipotezAgirliklari.getOrDefault("cesur", 0.25);
        double wM = tp.hipotezAgirliklari.getOrDefault("merakli", 0.25);
        double wZ = tp.hipotezAgirliklari.getOrDefault("kayitsiz", 0.25);
        double ort = r[0]*wK + r[1]*wC + r[2]*wM + r[3]*wZ;
        return wK*sq(r[0]-ort) + wC*sq(r[1]-ort) + wM*sq(r[2]-ort) + wZ*sq(r[3]-ort);
    }

    private static double sq(double x) { return x * x; }

    /** Eylem → hipotez-başına beklenen korku tepkisi. */
    private static double[] tepkiVektoru(Eylem e) {
        return switch (e) {
            case KLON_ARKA  -> new double[]{1.4, 0.5, 0.8, 0.3};
            case YAKIN_KLON -> new double[]{1.5, 0.6, 0.7, 0.3};
            case KARANLIK   -> new double[]{1.3, 0.6, 1.0, 0.4};
            case SES        -> new double[]{1.0, 0.5, 0.7, 0.4};
            case SESSIZLIK  -> new double[]{0.9, 1.3, 0.6, 0.5};
            case SORU       -> new double[]{0.6, 0.5, 1.1, 0.4};
        };
    }

    /**
     * HAYALİ TEST — bu eylemin BU oyuncuda ne kadar etki edeceğini profilinden tahmin et.
     * Tetikleyici profili (neyden korkuyor) + tepki profili (nasıl karşılık veriyor) +
     * kanaat + öngörülebilirlik kullanılır.
     */
    private static double tahminEt(Eylem e, TruePersonality tp, SimulatedPersonality sp) {
        double klonT = tetikNorm(tp, "klon");
        double karanlikT = tetikNorm(tp, "karanlik");
        String tepki = tp.baskinTepki();      // saldiri / kacma / donma / gormezden
        String kanaat = tp.candyKanaati;
        double ongoruF = tp.ongorulebilirlik / 100.0;

        // Tepkiye göre klon eyleminin çarpanı
        double klonTepki = switch (tepki) {
            case "saldiri" -> 0.5;   // hep klonları kesiyor → az etkili
            case "kacma" -> 1.4;     // kaçıyor → klon çok etkili
            case "donma" -> 1.2;
            default -> 1.0;          // gormezden → orta
        };

        return switch (e) {
            case KLON_ARKA -> (0.4 + klonT) * klonTepki;
            case KARANLIK  -> (0.3 + karanlikT * 1.2);
            case SES       -> 0.5 + (kanaat.equals("saklaniyor") || kanaat.equals("korkuyor") ? 0.4 : 0);
            case SESSIZLIK -> (kanaat.equals("meydan_okuyor") ? 1.3 : 0.4) + ongoruF * 0.5;
            case SORU      -> 0.5 + (tp.aktifNiyet.equals("bekliyor")
                                     || tp.aktifNiyet.equals("donuyor") ? 0.5 : 0);
            case YAKIN_KLON-> (kanaat.equals("korkuyor") ? 1.4 : 0.7) * klonTepki;
        };
    }

    private static double tetikNorm(TruePersonality tp, String tur) {
        if (tp.korkuTetikleyicileri.isEmpty()) return 0.3;
        double val = tp.korkuTetikleyicileri.getOrDefault(tur, 0.0);
        double max = 1.0;
        for (double v : tp.korkuTetikleyicileri.values()) max = Math.max(max, v);
        return val / max; // 0-1
    }

    // ─── Eylem uygulama (hafif, kendine yeten) ───
    private static void uygula(Eylem e, ServerPlayerEntity player, ServerWorld world, long owSimdi) {
        switch (e) {
            case KLON_ARKA -> klonSpawn(player, world, false);
            case YAKIN_KLON -> klonSpawn(player, world, true);
            case KARANLIK -> {
                player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.DARKNESS, 80, 0, true, false, false));
                world.playSound(null, player.getBlockPos(),
                    SoundEvents.AMBIENT_CAVE.value(), SoundCategory.AMBIENT, 1.0f, 0.6f);
            }
            case SES -> world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 0.8f, 0.7f);
            case SESSIZLIK -> SessizlikState.baslat(owSimdi, 400); // 20 sn build-up (overworld saati)
            case SORU -> IcDusunce.soruSor(player);
        }
    }

    private static void klonSpawn(ServerPlayerEntity player, ServerWorld world, boolean yakin) {
        // Cap — 8+ klon yakında varken yenisini spawn etme (CloneManager + boss + bu = birikim)
        var kutu = new net.minecraft.util.math.Box(player.getBlockPos()).expand(24);
        int mevcut = world.getEntitiesByClass(CandyDoppelEntity.class, kutu,
            e -> e.isAlive() && e.getCommandTags().contains("candy_clone")).size();
        if (mevcut >= 8) return;

        double yaw = Math.toRadians(player.getYaw());
        double mesafe = yakin ? 3.0 : 6.0;
        // yakin = önünde, uzak = arkasında
        double yon = yakin ? 1.0 : -1.0;
        double x = player.getX() - Math.sin(yaw) * mesafe * yon;
        double z = player.getZ() + Math.cos(yaw) * mesafe * yon;
        BlockPos pos = BlockPos.ofFloored(x, player.getY(), z);
        var spawned = CandyEntities.DOPPEL.spawn(world, pos, SpawnReason.MOB_SUMMONED);
        if (spawned instanceof CandyDoppelEntity klon) {
            klon.setOwnerUuid(player.getUuid());
            klon.addCommandTag("candy_clone");
            klon.setPersistent();
        }
    }

    public static void oyuncuAyrildi(UUID uuid) {
        bekleyen.remove(uuid);
        sonProva.remove(uuid);
    }
}
