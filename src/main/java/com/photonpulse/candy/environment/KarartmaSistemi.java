package com.photonpulse.candy.environment;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;

/**
 * KarartmaSistemi — basit ışık karartma formülü (v1.4.1).
 *
 * Formül:
 *   - "Işık gücü - 2" hissi: her yer 2 seviye daha karanlık görünür.
 *   - Gece: ekstra karartma.
 *
 * Uygulama: Server tarafında block light propagation'a dokunulamaz (dünya bozulur),
 * o yüzden vanilla DARKNESS status effect kullanılıyor — client rendering'e vignette
 * ve gamma çekmesi ekler. Aynı hissi verir.
 *
 * Gündüz açık alan (sky light 15) tam ışıklı kalır — sadece kapalı/karanlık yerlerde
 * ve gecede karartma devreye girer.
 */
public class KarartmaSistemi {

    private static final int TICK_ARALIGI = 40;      // 2 sn'de bir kontrol
    private static final int EFEKT_SURESI = 100;     // 5 sn (check'ten uzun = kesintisiz)

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % TICK_ARALIGI != 0) return;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                if (player.isCreative() || player.isSpectator()) continue;

                ServerLevel dunya = (ServerLevel) player.level();
                BlockPos pos = player.blockPosition();
                long gunSaati = dunya.getDayTime() % 24000L;
                boolean gece = gunSaati >= 13000L && gunSaati <= 23000L;

                int gokIsik = dunya.getBrightness(LightLayer.SKY, pos);
                int blokIsik = dunya.getBrightness(LightLayer.BLOCK, pos);

                // Formül: gerçek ışık - 2 (gece -6)
                int algilananIsik = Math.max(gokIsik, blokIsik) - (gece ? 6 : 2);

                // Yeterince aydınlıksa efekt yok (gündüz açık alan güvenli)
                if (algilananIsik >= 13) continue;

                player.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS,
                    EFEKT_SURESI,
                    0,
                    true,   // ambient
                    false,  // particle yok
                    false   // icon yok
                ));

                // v1.9.14: karanlikta başarımı — 3 dk kesintisiz karartma altında yaşa
                long simdi = dunya.getGameTime();
                if (tp.ilkKarartmaBaslangicTick == 0L) {
                    tp.ilkKarartmaBaslangicTick = simdi;
                }
                if (!tp.karanliktaBasarilar && simdi - tp.ilkKarartmaBaslangicTick >= 3600L) {
                    tp.karanliktaBasarilar = true;
                    com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "karanlikta");
                }
            }
        });
        Candy.LOG.info("[Candy] KarartmaSistemi aktif — ışık gücü -2 (gece -6)");
    }
}
