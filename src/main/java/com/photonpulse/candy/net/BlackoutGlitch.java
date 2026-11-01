package com.photonpulse.candy.net;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.sessizlik.SessizlikState;
import com.photonpulse.candy.platform.SunucuTick;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * BlackoutGlitch (v3.0) — 0.1-0.3 sn ekran karartma "glitch" efekti.
 *
 * Tetiklenme şartları (hepsi sağlanmalı):
 *   1. Kurulum tamam
 *   2. Kurulumdan sonra 5 dk oynanmış (tp.kurulumTamamTick baz alınır)
 *   3. Her kontrolde (10 sn) çekilen 1-100 arası random sayı, 5 veya 5'in kuvveti olmalı
 *      → 1-100 aralığında geçerli değerler: 5 (5¹), 25 (5²) — 125 (5³) aralık dışı
 *   4. Son tetiklemeden bu yana 2 dk (cooldown) geçmiş olmalı
 *
 * CamscreenPayload'ı yeniden kullanır (idx=7 = saf siyah, fade YOK — ani "glitch" hissi,
 * yumuşak camscreen fade'inden farklı). Client: InGameHudMixin idx==7'yi ayrı işler.
 */
public class BlackoutGlitch {

    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");
    private static final int KONTROL_ARALIGI = 200;                    // 10 sn — her zaman aynı roll sıklığı
    private static final long BEKLEME_TICK = DEBUG ? 200L : 6000L;     // debug 10sn / normal 5 dk
    private static final long COOLDOWN = DEBUG ? 400L : 2400L;         // debug 20sn / normal 2 dk
    private static final int IDX_BLACKOUT = 7;

    // 1-100 arasında 5'in kuvvetleri
    private static final Set<Integer> TETIKLEYICI_SAYILAR = Set.of(5, 25);

    private static final Random RNG = new Random();
    private static final Map<UUID, Long> sonTetik = new HashMap<>();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % KONTROL_ARALIGI != 0) return;
            long simdi = server.overworld().getGameTime();
            if (SessizlikState.sessizlikte(simdi)) return;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                if (player.isCreative() || player.isSpectator()) continue;
                if (tp.kurulumTamamTick == 0L) continue;
                if (simdi - tp.kurulumTamamTick < BEKLEME_TICK) continue;

                if (tp.bossOldurulmeTick > 0) continue; // WIN 30dk sessizlik
                Long son = sonTetik.get(player.getUUID());
                long cd = tp.badEndingAktif ? COOLDOWN / 2 : COOLDOWN; // BAD ENDING 2x
                if (son != null && simdi - son < cd) continue;

                // Aynı client overlay'ini paylaşıyoruz — devam eden camscreen'i kesme
                if (CamscreenTetikleyici.yakindaTetiklendi(player.getUUID(), simdi, 40L)) continue;

                // DEBUG'da random roll atlanır (test kolaylığı) — diğer şartlar aynen geçerli
                boolean tetiklenecek = DEBUG || TETIKLEYICI_SAYILAR.contains(1 + RNG.nextInt(100));
                if (!tetiklenecek) continue;

                sonTetik.put(player.getUUID(), simdi);
                int sureMs = 100 + RNG.nextInt(201); // 100-300ms

                try {
                    ServerPlayNetworking.send(player, new CamscreenPayload(IDX_BLACKOUT, sureMs));
                } catch (Throwable t) {
                    continue; // vanilla client — payload bilinmiyor
                }

                Candy.LOG.info("[Candy] BlackoutGlitch[{}] sure={}ms",
                    player.getName().getString(), sureMs);
            }
        });

        Candy.LOG.info("[Candy] BlackoutGlitch aktif — {} (v3.0)",
            DEBUG ? "DEBUG (10sn bekleme, roll atlanır, 20sn cooldown)"
                  : "kurulum+5dk sonrası, 1-100'den 5/25, 2dk cooldown");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonTetik.remove(uuid);
    }

    public static void sunucuKapan() {
        sonTetik.clear();
    }
}
