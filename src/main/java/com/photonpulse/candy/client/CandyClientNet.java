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

        // v8.1: karanlık kademesi (çözülen şifre sayısı)
        try {
            PayloadTypeRegistry.playS2C().register(
                com.photonpulse.candy.net.KaranlikPayload.ID,
                com.photonpulse.candy.net.KaranlikPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Karanlik payload zaten kayıtlı: {}", t.getMessage());
        }
        ClientPlayNetworking.registerGlobalReceiver(
            com.photonpulse.candy.net.KaranlikPayload.ID,
            (payload, context) -> KaranlikState.ayarla(payload.kademe()));

        // v8.1: FOV / render mesafesi müdahalesi
        try {
            PayloadTypeRegistry.playS2C().register(
                com.photonpulse.candy.net.GoruntuPayload.ID,
                com.photonpulse.candy.net.GoruntuPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Goruntu payload zaten kayıtlı: {}", t.getMessage());
        }
        ClientPlayNetworking.registerGlobalReceiver(
            com.photonpulse.candy.net.GoruntuPayload.ID,
            (payload, context) -> {
                // Ayar değişikliği render thread'inde olmalı.
                context.client().execute(() -> {
                    if (payload.geriAl()) GoruntuMudahale.geriAl();
                    else GoruntuMudahale.uygula(payload.fov(), payload.render());
                });
            });

        // v8.2: altyazı kanalı
        try {
            PayloadTypeRegistry.playS2C().register(
                com.photonpulse.candy.net.AltyaziPayload.ID,
                com.photonpulse.candy.net.AltyaziPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Altyazi payload zaten kayıtlı: {}", t.getMessage());
        }
        ClientPlayNetworking.registerGlobalReceiver(
            com.photonpulse.candy.net.AltyaziPayload.ID,
            (payload, context) -> AltyaziState.tetikle(payload.indeks(), payload.sureMs()));

        // v8.2: pencere başlığı
        try {
            PayloadTypeRegistry.playS2C().register(
                com.photonpulse.candy.net.BaslikPayload.ID,
                com.photonpulse.candy.net.BaslikPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Baslik payload zaten kayıtlı: {}", t.getMessage());
        }
        ClientPlayNetworking.registerGlobalReceiver(
            com.photonpulse.candy.net.BaslikPayload.ID,
            (payload, context) -> {
                // GLFW pencere çağrısı render thread'inde olmalı.
                context.client().execute(() -> {
                    if (payload.geriAl()) PencereBasligi.geriAl();
                    else PencereBasligi.uygula(payload.indeks());
                });
            });

        // v8.3: ikilik fısıltı ekranı (indeks → istemci kendisi çeviriyor)
        try {
            PayloadTypeRegistry.playS2C().register(
                com.photonpulse.candy.net.IkilikEkranPayload.ID,
                com.photonpulse.candy.net.IkilikEkranPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] IkilikEkran payload zaten kayıtlı: {}", t.getMessage());
        }
        ClientPlayNetworking.registerGlobalReceiver(
            com.photonpulse.candy.net.IkilikEkranPayload.ID,
            (payload, context) -> IkilikEkranState.tetikle(
                payload.indeks(), payload.sureMs(), payload.kanal()));

        // NOT: Arkadaş'ın fısıltıları için ayrı payload YOK. Sunucu
        // Text.translatable(anahtar, oyuncuAdı) yolluyor, istemci kendi lang
        // dosyasından çözüyor — dialog sisteminin zaten kullandığı yol. Oyuncu adı
        // çok oyunculuda herkese görünür bir bilgi, gizlilik kuralını kırmıyor.

        Candy.LOG.info("[Candy] CandyClientNet aktif — intensity + camscreen + hastalık + boss + karanlık + görüntü + altyazı + başlık + ikilik");
    }
}
