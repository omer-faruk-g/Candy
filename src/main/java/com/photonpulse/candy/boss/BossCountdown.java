package com.photonpulse.candy.boss;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.BossCountdownPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * BossCountdown — Faz 8 geri sayımını client'a S2C ile yollar.
 */
public class BossCountdown {

    public static void register() {
        try {
            PayloadTypeRegistry.playS2C().register(BossCountdownPayload.ID, BossCountdownPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] BossCountdown payload zaten kayıtlı: {}", t.getMessage());
        }
    }

    /** saniye >= 0 göster, -1 gizle. */
    public static void gonder(ServerPlayer player, int saniye) {
        try {
            ServerPlayNetworking.send(player, new BossCountdownPayload(saniye));
        } catch (Throwable ignored) {}
    }
}
