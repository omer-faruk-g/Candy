package com.photonpulse.candy.sifre;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.net.KaranlikPayload;
import com.photonpulse.candy.platform.AgKayit;
import com.photonpulse.candy.platform.SunucuBaglanti;
import com.photonpulse.candy.platform.SunucuAg;
import net.minecraft.server.level.ServerPlayer;

/**
 * Karanlik (v8.1) — ışık kademesini client'a yollayan sunucu tarafı.
 *
 * <h3>NEDEN OYUNCU BAŞINA</h3>
 * Değer {@code TruePersonality.isikDusumu}'nda duruyor, yani oyuncunun hafızasıyla
 * birlikte kaydediliyor ve yeniden başlatmada kayıp olmuyor. Candy'nin geri kalanı da
 * oyuncu başına çalışıyor; "dünyanın ışığı" tek oyunculu bir dünyada aynı şey demek,
 * çok oyunculuda ise her oyuncunun kendi kazandığı karanlığı taşıması doğru davranış.
 *
 * <h3>GİRİŞTE TEKRAR YOLLANIR</h3>
 * Client state RAM'de; oyuncu çıkıp girdiğinde sıfırlanır. {@code JOIN} olayında değer
 * yeniden yollanıyor, yoksa kazanılmış karanlık her girişte kaybolurdu.
 */
public final class Karanlik {

    private Karanlik() {}

    public static void register() {
        try {
            AgKayit.playS2C().register(KaranlikPayload.ID, KaranlikPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Karanlik payload zaten kayıtlı: {}", t.getMessage());
        }

        SunucuBaglanti.JOIN.register((handler, server) -> {
            try {
                ServerPlayer player = handler.getPlayer();
                TruePersonality tp = PersonalityManager.get(player);
                if (tp.isikDusumu > 0) gonder(player, tp.isikDusumu);
            } catch (Throwable ignored) {}
        });

        Candy.LOG.info("[Candy] Karanlık aktif — şifre çözümü ışığı düşürüyor");
    }

    /** Kademeyi oyuncunun client'ına yollar. Vanilla client'ta sessizce düşer. */
    public static void gonder(ServerPlayer player, int kademe) {
        try {
            SunucuAg.send(player, new KaranlikPayload(kademe));
        } catch (Throwable ignored) {
            // Client bu payload'ı bilmiyor (vanilla) — karanlık yalnızca modlu client'ta.
        }
    }
}
