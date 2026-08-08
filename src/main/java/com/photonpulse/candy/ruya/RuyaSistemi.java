package com.photonpulse.candy.ruya;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Random;

/**
 * RuyaSistemi — bed'de uyurken %40 ihtimalle kabus (v1.4.3).
 *
 * Etki:
 *   - AMBIENT_CAVE sesi (yakın, düşük pitch)
 *   - DARKNESS + WEAKNESS (kısa süre "titrek uyanma")
 *   - REGENERATION 1 tick — hearts titretme efekti
 *   - Kotuluk +15, Ozguven +5 (Candy hoşlanır)
 *
 * Sadece kurulum tamamlanmış oyuncular için.
 */
public class RuyaSistemi {

    private static final double KABUS_IHTIMAL = 0.40;
    private static final Random RNG = new Random();

    public static void register() {
        EntitySleepEvents.STOP_SLEEPING.register((entity, sleepPos) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) return;
            if (RNG.nextDouble() > KABUS_IHTIMAL) return;

            ServerWorld dunya = (ServerWorld) player.getWorld();

            // Ses — sadece bu oyuncuya duyulsun, mağara sesi
            player.playSoundToPlayer(
                SoundEvents.AMBIENT_CAVE.value(),
                SoundCategory.AMBIENT,
                1.0f,
                0.5f + RNG.nextFloat() * 0.3f
            );

            // Ekstra korku sesi — kalp atışı
            dunya.playSound(
                null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                SoundCategory.AMBIENT,
                0.6f, 0.7f
            );

            // DARKNESS — flash hissi
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.DARKNESS, 80, 0, true, false, false));
            // WEAKNESS — "uyanmakta zorlanma"
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WEAKNESS, 100, 0, true, false, false));
            // REGENERATION 1 tick — hearts titretme (altın kalp flash)
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.REGENERATION, 20, 0, true, false, false));

            // Psikoloji: Candy hoşlanır
            tp.psikolojiKotulugu = Math.min(100, tp.psikolojiKotulugu + 15);
            tp.candyOzguven = Math.min(100, tp.candyOzguven + 5);

            Candy.LOG.info("[Candy] kabus[{}] — kotuluk +15, ozguven +5",
                player.getName().getString());
            com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "ruya_miydi");
        });
        Candy.LOG.info("[Candy] RuyaSistemi aktif — bed'de %40 kabus ihtimali");
    }
}
