package com.photonpulse.candy.net;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * CamscreenTetikleyici (v1.6.0-mixin) — kotuluk-driven jump scare.
 *
 * Kotuluk 0-30 → hiç
 * Kotuluk 30-50 → nadir (10 dk cooldown, %40 ihtimal)
 * Kotuluk 50-80 → orta (5 dk cooldown, %50 ihtimal)
 * Kotuluk 80+ → sık (2 dk cooldown, %70 ihtimal)
 *
 * Player kotuluk'unu düşürürse (çiçek/elmas/gündüz) tekrar sakinleşir.
 */
public class CamscreenTetikleyici {

    // v1.6.1: 3 kategori — text (4 alt-varyant), smiley image, "candy is never die" image
    // Server önce kategori seçer (uniform 1/3), text seçilirse alt-varyant random
    private static final int TEXT_VARYANT_SAYI = 4;
    private static final int IDX_SMILEY = 4;
    private static final int IDX_NEVERDIE = 5;
    // v3.2: 2 yeni image varyantı (6=ending, 7=blackout rezerve — bu yüzden 8,9)
    private static final int IDX_MASK = 8;
    private static final int IDX_SSSSS = 9;
    private static final int[] IMAGE_IDX = {IDX_SMILEY, IDX_NEVERDIE, IDX_MASK, IDX_SSSSS};
    private static final int KONTROL_ARALIGI = 1200; // 1 dk
    private static final Random RNG = new Random();
    private static final Map<UUID, Long> sonTetik = new HashMap<>();

    public static void register() {
        try {
            PayloadTypeRegistry.playS2C().register(
                CamscreenPayload.ID, CamscreenPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Camscreen payload zaten kayıtlı: {}", t.getMessage());
        }

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % KONTROL_ARALIGI != 0) return;
            long simdi = server.getOverworld().getTime();
            // v1.9.0: sessizlik protokolü aktifse camscreen atma
            if (com.photonpulse.candy.sessizlik.SessizlikState.sessizlikte(simdi)) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                if (player.isCreative() || player.isSpectator()) continue;
                double kotuluk = tp.psikolojiKotulugu;
                if (kotuluk < 30) continue;

                long cooldownTick;
                double ihtimal;
                if (kotuluk < 50) {
                    cooldownTick = 12000L; // 10 dk
                    ihtimal = 0.4;
                } else if (kotuluk < 80) {
                    cooldownTick = 6000L; // 5 dk
                    ihtimal = 0.5;
                } else {
                    cooldownTick = 2400L; // 2 dk
                    ihtimal = 0.7;
                }

                // Ekstra çarpanlar
                if (player.getHealth() <= 4.0f) ihtimal *= 1.3;
                if (tp.korkuSkoru >= 70) ihtimal *= 1.2;
                ihtimal = Math.min(0.95, ihtimal);

                Long son = sonTetik.get(player.getUuid());
                if (son != null && simdi - son < cooldownTick) continue;
                if (RNG.nextDouble() > ihtimal) continue;

                sonTetik.put(player.getUuid(), simdi);

                // Text (1/3) vs image (2/3). Image ise 4 varyanttan uniform:
                // smiley / neverdie / mask / sssss (denge korunur, çeşit artar)
                int kategori = RNG.nextInt(3);   // 0 = text, 1-2 = image
                boolean image = kategori != 0;
                int idx = image
                    ? IMAGE_IDX[RNG.nextInt(IMAGE_IDX.length)]
                    : RNG.nextInt(TEXT_VARYANT_SAYI);

                // Image varyantları biraz daha uzun (400-700ms), text kısa (350-550ms)
                int sure = image ? (400 + RNG.nextInt(300)) : (350 + RNG.nextInt(200));

                try {
                    ServerPlayNetworking.send(player, new CamscreenPayload(idx, sure));
                } catch (Throwable t) {
                    // Vanilla client — payload bilinmiyor, sessizce geç
                    continue;
                }

                // Warden sonic boom sesi
                ((ServerWorld) player.getWorld()).playSound(
                    null, player.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                    SoundCategory.HOSTILE, 1.2f, 1.0f);

                Candy.LOG.info("[Candy] CAMSCREEN[{}] kotuluk={} idx={} sure={}ms",
                    player.getName().getString(),
                    String.format("%.1f", kotuluk), idx, sure);
                com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "kamera_acisi");
            }
        });

        Candy.LOG.info("[Candy] CamscreenTetikleyici aktif — kotuluk-driven jump scare");
    }

    /**
     * Son camscreen bu oyuncuya {@code pencere} tick içinde mi gönderildi?
     * BlackoutGlitch aynı client overlay'ini paylaştığı için, devam eden bir
     * jumpscare'i ortasından kesmesin diye kontrol eder.
     */
    public static boolean yakindaTetiklendi(UUID uuid, long simdi, long pencere) {
        Long son = sonTetik.get(uuid);
        return son != null && simdi - son < pencere;
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonTetik.remove(uuid);
    }

    public static void sunucuKapan() {
        sonTetik.clear();
    }
}
