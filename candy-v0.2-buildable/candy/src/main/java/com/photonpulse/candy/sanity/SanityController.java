package com.photonpulse.candy.sanity;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.List;
import java.util.Random;

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

    // 1.21.x SoundEvents sabitleri SoundEvent tipinde
    private static final List<SoundEvent> DUSUK_SESLER = List.of(
        SoundEvents.AMBIENT_CAVE,
        SoundEvents.ENTITY_ENDERMAN_AMBIENT
    );

    private static final List<SoundEvent> ORTA_SESLER = List.of(
        SoundEvents.ENTITY_ENDERMAN_STARE,
        SoundEvents.ENTITY_WARDEN_HEARTBEAT,
        SoundEvents.BLOCK_DEEPSLATE_BREAK
    );

    private static final List<SoundEvent> YUKSEK_SESLER = List.of(
        SoundEvents.ENTITY_WARDEN_ANGRY,
        SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
        SoundEvents.ENTITY_ENDER_DRAGON_GROWL
    );

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SanityController::onTick);
        Candy.LOG.info("[Candy] SanityController aktif");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;

        if (tickSayaci % TICK_ARALIGI == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                efektUygula(player);
            }
        }

        if (tickSayaci % AMBIENT_SES_ARALIGI == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ambientSes(player);
            }
        }
    }

    private static void efektUygula(ServerPlayerEntity player) {
        TruePersonality tp = PersonalityManager.get(player);
        if (!tp.kurulumTamam) return;

        int sure = TICK_ARALIGI * 3;
        double k = tp.korkuSkoru;

        if (k >= 25 && k < 50) {
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.DARKNESS, sure, 0, true, false, false));
        }
        else if (k >= 50 && k < 75) {
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.DARKNESS, sure, 0, true, false, false));
            if (RNG.nextDouble() < 0.3) {
                player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NAUSEA, 60, 0, true, false, false));
            }
        }
        else if (k >= 75) {
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.DARKNESS, sure, 0, true, false, false));
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NAUSEA, sure, 0, true, false, false));
            if (RNG.nextDouble() < 0.1) {
                player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 15, 0, true, false, false));
            }
        }
    }

    private static void ambientSes(ServerPlayerEntity player) {
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

        player.getWorld().playSound(null, x, y, z, seciSes,
            SoundCategory.AMBIENT, ses, pitch);
    }
}
