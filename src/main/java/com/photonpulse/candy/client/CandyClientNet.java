package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.BildirimPayload;
import com.photonpulse.candy.net.BossCountdownPayload;
import com.photonpulse.candy.net.CamscreenPayload;
import com.photonpulse.candy.net.CandyIntensityPayload;
import com.photonpulse.candy.net.HastalikPayload;
import com.photonpulse.candy.platform.IstemciAg;
import com.photonpulse.candy.platform.AgKayit;

/**
 * Client tarafı — CandyIntensityPayload dinleyicisi.
 * Gelen değerleri IntensityState'e yazar.
 */
public class CandyClientNet {

    public static void register() {
        // Single-player'da IntensityBroadcaster zaten kayıt etti — duplicate atarsa yut.
        try {
            AgKayit.playS2C().register(
                CandyIntensityPayload.ID, CandyIntensityPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Payload zaten kayıtlı (main tarafından): {}", t.getMessage());
        }

        IstemciAg.registerGlobalReceiver(
            CandyIntensityPayload.ID,
            (payload, context) -> {
                IntensityState.setFromServer(
                    payload.agresivlik(), payload.korku(), payload.ozguven());
            });

        // Camscreen payload
        try {
            AgKayit.playS2C().register(
                CamscreenPayload.ID, CamscreenPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Camscreen payload zaten kayıtlı: {}", t.getMessage());
        }
        IstemciAg.registerGlobalReceiver(
            CamscreenPayload.ID,
            (payload, context) -> {
                CamscreenState.tetikle(payload.textIndex(), payload.durationMs());
                // v9.0.0: ani korku anı — gerçek klavye ışığı da kısa seri yanıp söner
                KlavyeIsigi.darbe();
            });

        // Hastalık payload
        try {
            AgKayit.playS2C().register(HastalikPayload.ID, HastalikPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Hastalik payload zaten kayıtlı: {}", t.getMessage());
        }
        IstemciAg.registerGlobalReceiver(
            HastalikPayload.ID,
            (payload, context) -> {
                HastalikState.set(payload.hastalik());
            });

        // Boss countdown payload (Faz 8)
        try {
            AgKayit.playS2C().register(BossCountdownPayload.ID, BossCountdownPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] BossCountdown payload zaten kayıtlı: {}", t.getMessage());
        }
        IstemciAg.registerGlobalReceiver(
            BossCountdownPayload.ID,
            (payload, context) -> BossCountdownState.set(payload.saniye()));

        // v8.1: karanlık kademesi (çözülen şifre sayısı)
        try {
            AgKayit.playS2C().register(
                com.photonpulse.candy.net.KaranlikPayload.ID,
                com.photonpulse.candy.net.KaranlikPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Karanlik payload zaten kayıtlı: {}", t.getMessage());
        }
        IstemciAg.registerGlobalReceiver(
            com.photonpulse.candy.net.KaranlikPayload.ID,
            (payload, context) -> {
                KaranlikState.ayarla(payload.kademe());
                // v9.0.0: oyun içi ışık düştükçe gerçek klavye ışığının nabzı sıklaşır
                KlavyeIsigi.kademe(payload.kademe());
            });

        // v8.1: FOV / render mesafesi müdahalesi
        try {
            AgKayit.playS2C().register(
                com.photonpulse.candy.net.GoruntuPayload.ID,
                com.photonpulse.candy.net.GoruntuPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Goruntu payload zaten kayıtlı: {}", t.getMessage());
        }
        IstemciAg.registerGlobalReceiver(
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
            AgKayit.playS2C().register(
                com.photonpulse.candy.net.AltyaziPayload.ID,
                com.photonpulse.candy.net.AltyaziPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Altyazi payload zaten kayıtlı: {}", t.getMessage());
        }
        IstemciAg.registerGlobalReceiver(
            com.photonpulse.candy.net.AltyaziPayload.ID,
            (payload, context) -> AltyaziState.tetikle(payload.indeks(), payload.sureMs()));

        // v8.2: pencere başlığı
        try {
            AgKayit.playS2C().register(
                com.photonpulse.candy.net.BaslikPayload.ID,
                com.photonpulse.candy.net.BaslikPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Baslik payload zaten kayıtlı: {}", t.getMessage());
        }
        IstemciAg.registerGlobalReceiver(
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
            AgKayit.playS2C().register(
                com.photonpulse.candy.net.IkilikEkranPayload.ID,
                com.photonpulse.candy.net.IkilikEkranPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] IkilikEkran payload zaten kayıtlı: {}", t.getMessage());
        }
        IstemciAg.registerGlobalReceiver(
            com.photonpulse.candy.net.IkilikEkranPayload.ID,
            (payload, context) -> IkilikEkranState.tetikle(
                payload.indeks(), payload.sureMs(), payload.kanal()));

        // v9.2.0: dış kanal — Candy aynı oyun-içi fısıltıyı oyuncunun gerçek e-postasına
        // da düşürebilir. İçerik TanidikFisilti ile aynı: sunucu yalnız anahtar+arg
        // yolluyor, istemci kendi dilinde çözüyor. E-posta adresi bu pakette YOK; o
        // yalnızca istemci config'inde. MektupGonder config boşsa hiçbir şey yapmaz.
        try {
            AgKayit.playS2C().register(BildirimPayload.ID, BildirimPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Bildirim payload zaten kayıtlı: {}", t.getMessage());
        }
        IstemciAg.registerGlobalReceiver(
            BildirimPayload.ID,
            (payload, context) -> {
                // Paketi HERHANGİ bir sunucu yollayabilir: anahtar ve argüman güvenilmez.
                // Denetimsiz hâliyle bilinmeyen bir anahtar kendisi olarak çözülüyordu,
                // yani sunucu oyuncunun gerçek gelen kutusuna "Candy" adıyla istediği
                // metni (ör. oltalama linki) düşürebiliyordu.
                if (!MektupGonder.bildirimGuvenli(payload.anahtar(), payload.arg())) {
                    Candy.LOG.warn("[Candy] Dış kanal paketi reddedildi (anahtar: {})",
                        payload.anahtar());
                    return;
                }
                String satir = payload.arg() == null || payload.arg().isEmpty()
                    ? net.minecraft.network.chat.Component.translatable(payload.anahtar()).getString()
                    : net.minecraft.network.chat.Component.translatable(payload.anahtar(), payload.arg()).getString();
                MektupGonder.belki(satir);
            });

        // NOT: Arkadaş'ın fısıltıları için ayrı payload YOK. Sunucu
        // Text.translatable(anahtar, oyuncuAdı) yolluyor, istemci kendi lang
        // dosyasından çözüyor — dialog sisteminin zaten kullandığı yol. Oyuncu adı
        // çok oyunculuda herkese görünür bir bilgi, gizlilik kuralını kırmıyor.

        Candy.LOG.info("[Candy] CandyClientNet aktif — intensity + camscreen + hastalık + boss + karanlık + görüntü + altyazı + başlık + ikilik + klavye ışığı + mektup");
    }
}
