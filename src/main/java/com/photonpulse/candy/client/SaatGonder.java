package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.SaatPayload;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import com.photonpulse.candy.platform.AgKayit;
import com.photonpulse.candy.platform.IstemciAg;
import com.photonpulse.candy.platform.IstemciBaglanti;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * SaatGonder (v9.1.0, istemci) — oyuncunun gerçek duvar saatini join'de sunucuya yollar.
 *
 * <p>Tek paket, tek sefer. Sunucu tarafı ({@link com.photonpulse.candy.meta.SaatBilgisi})
 * aradan geçen süreyi kendisi ekliyor, yani saat başı yenileme paketine gerek yok:
 * gece yarısını geçen bir oturumda değer kendiliğinden dönüyor.</p>
 *
 * <p>Giden veri: gün içi dakika + saat dilimi adı. Tarih yok, oturum süresi yok,
 * makineden okunan başka hiçbir şey yok. Ağ yoksa / paket geçmezse mekanik sessizce
 * atlanır — Steve ve Alex o oyuncuya saatten hiç söz etmez.</p>
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public final class SaatGonder {

    private SaatGonder() {}

    public static void register() {
        try {
            AgKayit.playC2S().register(SaatPayload.ID, SaatPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Saat C2S zaten kayıtlı (istemci): {}", t.getMessage());
        }

        IstemciBaglanti.JOIN.register(client -> client.execute(SaatGonder::gonder));
    }

    private static void gonder() {
        try {
            if (!IstemciAg.canSend(SaatPayload.ID)) return;
            LocalTime simdi = LocalTime.now();
            String dilim;
            try {
                dilim = ZoneId.systemDefault().getId();
            } catch (Throwable t) {
                dilim = "";
            }
            IstemciAg.send(new SaatPayload(simdi.getHour() * 60 + simdi.getMinute(), dilim));
            Candy.LOG.info("[Candy] Saat gonderildi");
        } catch (Throwable ignored) {}
    }
}
