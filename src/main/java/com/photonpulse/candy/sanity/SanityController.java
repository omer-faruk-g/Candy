package com.photonpulse.candy.sanity;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * SanityController — korkuSkoru → görsel/işitsel efektlere çevirici.
 *
 * Client'a özel packet YOK — hepsi vanilla mekaniği:
 *  - StatusEffect.DARKNESS   → ekran kenarları kararır (vignette benzeri)
 *  - StatusEffect.NAUSEA     → distortion
 *  - StatusEffect.BLINDNESS  → kısa flash
 *  - world.playSound(...)    → ambient korku sesleri
 */
public class SanityController {

    private static final int TICK_ARALIGI = 40;
    private static final int AMBIENT_SES_ARALIGI = 200;
    private static int tickSayaci = 0;
    private static final Random RNG = new Random();

    // ── DARKNESS burst-cooldown ──
    // Bir burst 10 saniye sürer (200 tick). Ardından bekleme periyodu — korku
    // yükseldikçe kısalır. Böylece SÜREKLİ değil, ATMOSFERİK anlar oluşur.
    private static final int BURST_SURE_TICK = 200;              // 10 saniye burst
    private static final long BEKLEME_MIN_TICK = 300L;           // 15 sn (korku 90+)
    private static final long BEKLEME_MAX_TICK = 2400L;          // 2 dk (korku 45)
    // Oyuncu başına son burst başlangıç tick'i
    private static final Map<UUID, Long> sonBurstBaslangic = new HashMap<>();

    // 1.21.7 SoundEvents sabitlerinin bir kısmı SoundEvent, bir kısmı RegistryEntry<SoundEvent>. Tek tipe indir.
    private static SoundEvent se(Object o) {
        if (o instanceof Holder<?> re) return (SoundEvent) re.value();
        return (SoundEvent) o;
    }

    private static final List<SoundEvent> DUSUK_SESLER = List.of(
        se(SoundEvents.AMBIENT_CAVE),
        se(SoundEvents.ENDERMAN_AMBIENT)
    );

    private static final List<SoundEvent> ORTA_SESLER = List.of(
        se(SoundEvents.ENDERMAN_STARE),
        se(SoundEvents.WARDEN_HEARTBEAT),
        se(SoundEvents.DEEPSLATE_BREAK)
    );

    private static final List<SoundEvent> YUKSEK_SESLER = List.of(
        se(SoundEvents.WARDEN_ANGRY),
        se(SoundEvents.ELDER_GUARDIAN_CURSE),
        se(SoundEvents.ENDER_DRAGON_GROWL)
    );

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SanityController::onTick);
        Candy.LOG.info("[Candy] SanityController aktif");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;

        if (tickSayaci % TICK_ARALIGI == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                efektUygula(player);
            }
        }

        if (tickSayaci % AMBIENT_SES_ARALIGI == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ambientSes(player);
            }
        }
    }

    private static void efektUygula(ServerPlayer player) {
        TruePersonality tp = PersonalityManager.get(player);
        if (!tp.kurulumTamam) return;

        double k = tp.korkuSkoru;
        if (k < 45) return;

        // Sadece BLOCK light (meşale/glowstone/vs.) — gökyüzü ışığını YOK say.
        // Böylece gece yüzeyde de tetiklenmez, sadece meşale/yapay ışık YOKSA tetiklenir.
        int isik = player.level().getBrightness(LightLayer.BLOCK, player.blockPosition());
        if (isik >= 6) return;   // Meşale var → efekt yok

        // Burst-cooldown mantığı:
        //  - Aktif burst → DARKNESS uygulanır (10 sn boyunca sürekli refresh)
        //  - Bekleme periyodu → hiçbir şey
        //  - Bekleme bittiğinde → yeni burst başlar
        // Korku yükseldikçe bekleme kısalır — atmosferik anlar sıklaşır.
        UUID uid = player.getUUID();
        long simdi = player.level().getGameTime();
        Long baslangic = sonBurstBaslangic.get(uid);

        if (baslangic != null && simdi - baslangic < BURST_SURE_TICK) {
            // AKTİF BURST — DARKNESS uygula. 60 tick = 3 sn > TICK_ARALIGI (40 tick)
            // olduğundan burst boyunca sürekli refresh olur.
            player.addEffect(new MobEffectInstance(
                MobEffects.DARKNESS, 60, 0, true, false, false));
            return;
        }

        // Burst bitti — yeni burst için bekleme geçti mi?
        // Psikoloji kötüyse bekleme kısalır (agresivlik çarpanı).
        double agresivlik = Psikoloji.agresivlikCarpani(tp);
        long bekleme = (long) (beklemeSuresiTick(k) / agresivlik);
        if (baslangic == null || simdi - baslangic >= BURST_SURE_TICK + bekleme) {
            sonBurstBaslangic.put(uid, simdi);
            player.addEffect(new MobEffectInstance(
                MobEffects.DARKNESS, 60, 0, true, false, false));
        }
    }

    /**
     * Bekleme süresini korku ile lineer hesapla: k=45 → 2 dk, k=90+ → 15 sn.
     * Böylece korku arttıkça atmosferik anlar sıklaşır.
     */
    private static long beklemeSuresiTick(double k) {
        double norm = (k - 45.0) / 45.0;   // k=45 → 0.0, k=90 → 1.0
        norm = Math.max(0, Math.min(1, norm));
        return (long) (BEKLEME_MAX_TICK - norm * (BEKLEME_MAX_TICK - BEKLEME_MIN_TICK));
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonBurstBaslangic.remove(uuid);
    }

    public static void sunucuKapan() {
        sonBurstBaslangic.clear();
        tickSayaci = 0;
    }

    private static void ambientSes(ServerPlayer player) {
        TruePersonality tp = PersonalityManager.get(player);
        if (!tp.kurulumTamam) return;

        double k = tp.korkuSkoru;

        List<SoundEvent> havuz;
        double ihtimal;
        float ses = 0.4f;
        float pitch = 0.6f + RNG.nextFloat() * 0.6f;

        if (k < 25) {
            havuz = DUSUK_SESLER;
            ihtimal = 0.15;
        } else if (k < 50) {
            havuz = DUSUK_SESLER;
            ihtimal = 0.35;
            ses = 0.5f;
        } else if (k < 75) {
            havuz = ORTA_SESLER;
            ihtimal = 0.5;
            ses = 0.7f;
        } else {
            havuz = YUKSEK_SESLER;
            ihtimal = 0.7;
            ses = 1.0f;
        }

        if (RNG.nextDouble() > ihtimal) return;

        SoundEvent seciSes = havuz.get(RNG.nextInt(havuz.size()));

        // Belirsiz konum — oyuncunun 8-16 blok ötesinde
        double angle = RNG.nextDouble() * Math.PI * 2;
        double mesafe = 8 + RNG.nextDouble() * 8;
        double x = player.getX() + Math.cos(angle) * mesafe;
        double z = player.getZ() + Math.sin(angle) * mesafe;
        double y = player.getY();

        player.level().playSound(null, x, y, z, seciSes,
            SoundSource.AMBIENT, ses, pitch);
    }
}
