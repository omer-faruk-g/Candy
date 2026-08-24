package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.BossCountdownPayload;
import com.photonpulse.candy.net.CamscreenPayload;
import com.photonpulse.candy.net.CandyIntensityPayload;
import com.photonpulse.candy.net.HastalikPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Client tarafı — CandyIntensityPayload dinleyicisi.
 * Gelen değerleri IntensityState'e yazar.
 */
public class CandyClientNet {

    public static void register() {
        // Single-player'da IntensityBroadcaster zaten kayıt etti — duplicate atarsa yut.
        try {
            PayloadTypeRegistry.playS2C().register(
                CandyIntensityPayload.ID, CandyIntensityPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Payload zaten kayıtlı (main tarafından): {}", t.getMessage());
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CandyIntensityPayload.ID,
            (payload, context) -> {
                IntensityState.setFromServer(
                    payload.agresivlik(), payload.korku(), payload.ozguven());
            });

        // Camscreen payload
        try {
            PayloadTypeRegistry.playS2C().register(
                CamscreenPayload.ID, CamscreenPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Camscreen payload zaten kayıtlı: {}", t.getMessage());
        }
        ClientPlayNetworking.registerGlobalReceiver(
            CamscreenPayload.ID,
            (payload, context) -> {
                CamscreenState.tetikle(payload.textIndex(), payload.durationMs());
            });

        // Hastalık payload
        try {
            PayloadTypeRegistry.playS2C().register(HastalikPayload.ID, HastalikPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Hastalik payload zaten kayıtlı: {}", t.getMessage());
        }
        ClientPlayNetworking.registerGlobalReceiver(
            HastalikPayload.ID,
            (payload, context) -> {
                HastalikState.set(payload.hastalik());
            });

        // Boss countdown payload (Faz 8)
        try {
            PayloadTypeRegistry.playS2C().register(BossCountdownPayload.ID, BossCountdownPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] BossCountdown payload zaten kayıtlı: {}", t.getMessage());
        }
        ClientPlayNetworking.registerGlobalReceiver(
            BossCountdownPayload.ID,
            (payload, context) -> BossCountdownState.set(payload.saniye()));

        Candy.LOG.info("[Candy] CandyClientNet aktif — intensity + camscreen + hastalık + boss");
    }
}
