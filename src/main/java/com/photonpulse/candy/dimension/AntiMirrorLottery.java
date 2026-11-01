package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import java.util.Random;

/**
 * AntiMirrorLottery (v3.0 Phase 4) — Anti-Mirror'da lottery ölüm sistemi.
 *
 * Her 5 sn: 1-100 arası random sayı. 50 çıkarsa player ölür.
 * Kullanılan sayılar bir daha çıkmaz — havuz daralır (50 hariç 99 sayı biterse mercy reset).
 *
 * Death mesajı custom damage type ile "Candy'nin dünyasında kurallara uymadığı için
 * Candy tarafından öldürüldü."
 */
public class AntiMirrorLottery {

    private static final int TICK_ARALIGI = 100; // 5 sn
    private static final Random RNG = new Random();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(AntiMirrorLottery::onTick);
        // Not: Anti-Mirror ölüm sonrası gerçek dünyaya dönüş MirrorGirisi'nde (AFTER_RESPAWN).
        Candy.LOG.info("[Candy] AntiMirrorLottery aktif (v3.0)");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTickCount() % TICK_ARALIGI != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel world = (ServerLevel) player.level();
            if (!world.dimension().equals(CandyDimensions.ANTI_MIRROR)) continue;

            TruePersonality tp = PersonalityManager.get(player);

            // Havuzdan sayı çek. 50 HER ZAMAN çekilebilir (kullanılan set'e eklenmez) —
            // yani player her zaman ölüm riski taşır. Set sadece 50-dışı sayıları tutar.
            int lottery = 1 + RNG.nextInt(100);
            int deneme = 0;
            while (lottery != 50 && tp.kullanilanLotterySayilari.contains(lottery)
                   && deneme < 200) {
                lottery = 1 + RNG.nextInt(100);
                deneme++;
            }
            if (lottery != 50) {
                tp.kullanilanLotterySayilari.add(lottery);
            }

            // Mercy reset — 50 dışındaki 99 sayının hepsi tükendiyse belleği sıfırla
            if (tp.kullanilanLotterySayilari.size() >= 99) {
                tp.kullanilanLotterySayilari.clear();
                Candy.LOG.info("[Candy] Anti-Mirror mercy reset: {}",
                    player.getName().getString());
                CandyBasarimlar.tetikle(player, "sansli_49");
                continue;
            }

            if (lottery == 50) {
                // Ölüm — custom damage type
                try {
                    var registry = world.registryAccess()
                        .lookupOrThrow(Registries.DAMAGE_TYPE);
                    Holder<net.minecraft.world.damagesource.DamageType> entry;
                    // v3.0 anti_mirror_lottery damage type
                    var id = ResourceLocation.fromNamespaceAndPath("candy", "anti_mirror_lottery");
                    var maybe = registry.get(id);
                    entry = maybe.orElse(
                        registry.get(ResourceLocation.fromNamespaceAndPath("candy", "kalp_krizi")).orElseThrow());
                    DamageSource source = new DamageSource(entry);
                    player.hurtServer(world, source, 1000f);
                } catch (Throwable t) {
                    player.setHealth(0.0f);
                }
                Candy.LOG.info("[Candy] Anti-Mirror lottery = 50 → {} öldü",
                    player.getName().getString());
            }
        }
    }
}
