package com.photonpulse.candy.audio;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.sessizlik.SessizlikState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Random;

/**
 * FisiltiSistemi (v1.9.1) — korku + hastalık'a göre ambient rahatsız edici sesler.
 *
 * Kaynaklar:
 *   - AMBIENT_CAVE low-pitch (fısıltı)
 *   - WARDEN_HEARTBEAT (kalp atışı)
 *   - ITEM_PICKUP burst (klavye yazma)
 *   - LEVER_CLICK (fare tıkı)
 *   - PLAYER_BREATH low-pitch (yakında nefes)
 *
 * Yoğunluk = korku/100 + (hastalık varsa +0.3). 0.3 altı hiç ses yok.
 * Sesler ambient kategoride, positional (3-10 blok mesafede).
 * Sessizlik protokolünde skip.
 */
public class FisiltiSistemi {

    private static final int TICK_ARALIGI = 100; // 5 sn kontrol
    private static final Random RNG = new Random();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FisiltiSistemi::onTick);
        Candy.LOG.info("[Candy] FisiltiSistemi aktif — ambient rahatsızlık");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTicks() % TICK_ARALIGI != 0) return;
        long simdi = server.getOverworld().getTime();
        if (SessizlikState.sessizlikte(simdi)) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isCreative() || player.isSpectator()) continue;
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;

            double yogunluk = hesaplaYogunluk(tp);
            if (yogunluk < 0.3) continue;

            // İhtimal ölçekle — yoğunluk 1.0'da her check'te tetikler
            double ihtimal = (yogunluk - 0.3) * 0.8; // 0.3 → 0, 1.0 → 0.56
            if (RNG.nextDouble() > ihtimal) continue;

            calFisilti(player, yogunluk);
            com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "fisilti");
            // v1.9.14: hos_geldin — ilk Candy varlığı hissetmesi
            com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "hos_geldin");
        }
    }

    private static double hesaplaYogunluk(TruePersonality tp) {
        double f = tp.korkuSkoru / 100.0;
        double bonus = (tp.aktifHastalik != null && !tp.aktifHastalik.isEmpty()) ? 0.3 : 0;
        return Math.min(1.0, f + bonus);
    }

    private static void calFisilti(ServerPlayerEntity player, double yogunluk) {
        ServerWorld world = (ServerWorld) player.getWorld();
        double aci = RNG.nextDouble() * Math.PI * 2;
        double mesafe = 3 + RNG.nextDouble() * 7;
        double x = player.getX() + Math.cos(aci) * mesafe;
        double y = player.getY() + (RNG.nextDouble() - 0.5) * 2;
        double z = player.getZ() + Math.sin(aci) * mesafe;

        float vol = (float) (0.3 + yogunluk * 0.5);
        int tur = RNG.nextInt(7);

        switch (tur) {
            case 0 -> {
                // v1.9.2: Custom murmur (gerçek fısıltı ses kaydı)
                world.playSound(null, x, y, z,
                    CandySounds.MURMUR, SoundCategory.AMBIENT,
                    vol, 0.9f + RNG.nextFloat() * 0.3f);
            }
            case 6 -> {
                // v1.9.3: Crowd — kalabalık uğultu (-35 dB attenuated OGG)
                world.playSound(null, x, y, z,
                    CandySounds.CROWD, SoundCategory.AMBIENT,
                    vol * 1.2f, 0.8f + RNG.nextFloat() * 0.3f);
            }
            case 5 -> {
                // Cave whisper — düşük pitch
                world.playSound(null, x, y, z,
                    SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT,
                    vol, 0.3f + RNG.nextFloat() * 0.4f);
            }
            case 1 -> {
                // Kalp atışı (warden heartbeat)
                world.playSound(null, x, y, z,
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT,
                    vol * 0.7f, 0.7f + RNG.nextFloat() * 0.3f);
            }
            case 2 -> {
                // Klavye yazma — 3-6 hızlı item pickup
                int adet = 3 + RNG.nextInt(4);
                for (int i = 0; i < adet; i++) {
                    world.playSound(null, x, y, z,
                        SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.AMBIENT,
                        0.2f, 1.8f + RNG.nextFloat() * 0.4f);
                }
            }
            case 3 -> {
                // Fare tıkı
                world.playSound(null, x, y, z,
                    SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.AMBIENT,
                    vol * 0.8f, 1.5f + RNG.nextFloat() * 0.5f);
            }
            case 4 -> {
                // Yakında nefes — düşük pitch
                world.playSound(null, x, y, z,
                    SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.AMBIENT,
                    vol * 0.5f, 0.4f);
            }
        }
    }
}
