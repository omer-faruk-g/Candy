package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.Random;

/**
 * MirrorAtmosphere (v3.0 Faz 1.1) — Mirror & Anti-Mirror ambient effektler.
 *
 * DARKNESS status effect sürekli yenilenir — Mirror'da vanilla warden darkness hissi.
 * Ambient particle spawn — ash + smoke, atmosferik.
 * Bu yaklaşım client mixin gerektirmez, vanilla efektlerle çalışır.
 */
public class MirrorAtmosphere {

    private static final Random RNG = new Random();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(server -> {
            // Her 20 tick (1 sn) — DARKNESS renew
            if (server.getTickCount() % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    var worldKey = player.level().dimension();
                    if (worldKey.equals(CandyDimensions.MIRROR)
                        || worldKey.equals(CandyDimensions.ANTI_MIRROR)) {
                        // DARKNESS 3 sn (60 tick) — 20 tick sonra tekrar yenilenecek
                        player.addEffect(new MobEffectInstance(
                            MobEffects.DARKNESS, 60, 0, true, false, false));
                    }
                }
            }

            // Her 10 tick (0.5 sn) — ash + smoke particle etrafında
            if (server.getTickCount() % 10 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    var worldKey = player.level().dimension();
                    if (!worldKey.equals(CandyDimensions.MIRROR)
                        && !worldKey.equals(CandyDimensions.ANTI_MIRROR)) continue;
                    ServerLevel world = (ServerLevel) player.level();

                    // 3-5 rastgele particle çevrede
                    for (int i = 0; i < 4; i++) {
                        double dx = (RNG.nextDouble() - 0.5) * 20;
                        double dy = (RNG.nextDouble() - 0.5) * 8;
                        double dz = (RNG.nextDouble() - 0.5) * 20;
                        world.sendParticles(ParticleTypes.WHITE_ASH,
                            player.getX() + dx, player.getY() + dy, player.getZ() + dz,
                            1, 0, 0, 0, 0);
                    }
                    // 1-2 smoke
                    if (RNG.nextBoolean()) {
                        double dx = (RNG.nextDouble() - 0.5) * 15;
                        double dy = (RNG.nextDouble() - 0.5) * 6;
                        double dz = (RNG.nextDouble() - 0.5) * 15;
                        world.sendParticles(ParticleTypes.LARGE_SMOKE,
                            player.getX() + dx, player.getY() + dy, player.getZ() + dz,
                            1, 0.1, 0.1, 0.1, 0.01);
                    }
                }
            }
        });

        Candy.LOG.info("[Candy] MirrorAtmosphere aktif — DARKNESS + ash + smoke (v3.0 Faz 1.1)");
    }
}
