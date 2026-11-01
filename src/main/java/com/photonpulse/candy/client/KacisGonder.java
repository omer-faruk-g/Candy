package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.kacis.KacisPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.photonpulse.candy.platform.IstemciBaglanti;
import com.photonpulse.candy.platform.IstemciAg;
import com.photonpulse.candy.platform.AgKayit;
import net.minecraft.client.Minecraft;

/**
 * KacisGonder (v8.2, istemci) — F3 hata ayıklama ekranının açık kaldığı süreyi ölçüp
 * periyodik olarak sunucuya bir SAYI gönderir.
 *
 * <p>F3 sunucudan görülemez: tamamen istemci tarafı bir ekran. Ölçmenin tek yolu
 * istemcide saymak. Gönderilen şey tick sayısı — hangi tuş, hangi ekran, hangi mod
 * yüklü, hiçbiri gitmiyor.</p>
 *
 * <p>Neden F3 bir kaçış sinyali: oyuncu F3'ü açtığında artık oyunu değil <b>oyunun
 * kendisini</b> inceliyor. Candy'nin {@code DebugHudMixin}'i zaten boyut adını
 * gizliyor — yani F3 oyuncunun Candy'yi yakalama girişimi. Candy bunu fark etsin.</p>
 */
@Environment(EnvType.CLIENT)
public final class KacisGonder {

    private KacisGonder() {}

    /** Rapor aralığı — 30 saniye. Sık göndermenin bilgi değeri yok. */
    private static final int RAPOR_ARALIK = 600;

    private static int tick = 0;
    private static int f3Tick = 0;

    public static void register() {
        try {
            AgKayit.playC2S().register(KacisPayload.ID, KacisPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Kacis C2S zaten kayıtlı (istemci): {}", t.getMessage());
        }

        // Yeni bağlantı = yeni ölçüm penceresi. Devredilen sayaç yanlış sunucuya gider.
        IstemciBaglanti.JOIN.register(client -> {
            tick = 0;
            f3Tick = 0;
        });

        ClientTickEvents.END_CLIENT_TICK.register(KacisGonder::istemciTick);
    }

    private static void istemciTick(Minecraft mc) {
        try {
            if (mc == null || mc.player == null) return;
            if (mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen()) f3Tick++;

            tick++;
            if (tick % RAPOR_ARALIK != 0) return;
            if (f3Tick <= 0) return;
            if (!IstemciAg.canSend(KacisPayload.ID)) { f3Tick = 0; return; }
            IstemciAg.send(new KacisPayload(f3Tick));
            f3Tick = 0;
        } catch (Throwable t) {
            f3Tick = 0;
        }
    }
}
