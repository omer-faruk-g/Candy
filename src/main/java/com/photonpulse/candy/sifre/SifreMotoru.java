package com.photonpulse.candy.sifre;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import java.util.SplittableRandom;

/**
 * SifreMotoru (v8.1) — şifre dağıtımının zamanlamasını yürüten sunucu döngüsü.
 *
 * <h3>AI DOLAYLI ETKİ EDİYOR — MEKANİZMA</h3>
 * Vagus (yerel AI) bu dosyada <b>çağrılmıyor</b>. Vagus yalnızca
 * {@code vagusTahmin / vagusKalibrasyon / vagusDarbe} alanlarını yazıyor ve o alanlar bir
 * sonraki nabızda kurula sıradan duyu girdisi olarak giriyor. Aşağıdaki baskı hesabı
 * {@code vagusDarbe}'yi <b>üç terimden biri</b> olarak okuyor; tek başına hiçbir eşiği
 * geçiremez (katkısı en çok 0,20, eşik 0,35). Yani:
 * <ul>
 *   <li>AI dosya bırakmıyor, ışığı düşürmüyor, hiçbir şeyi tetiklemiyor;</li>
 *   <li>yalnızca oyuncuyu daha iyi çözdüğünde bir girdiyi kaydırıyor;</li>
 *   <li>kararı Candy'nin kendi durumu veriyor.</li>
 * </ul>
 * Dış güç sisteme dokunabiliyor ama yalnızca dolaylı — doğrudan müdahale yolu <b>yok</b>,
 * çünkü böyle bir çağrı hiçbir yerde tanımlı değil.
 *
 * <h3>SAATLİ BOMBA DEĞİL</h3>
 * {@code aralikDk} yalnızca <i>en az</i> bekleme süresi. Süre dolduğunda dağıtım otomatik
 * olmuyor; baskı eşiği geçmezse hiç olmuyor. Candy ilgisiz bir oyuncuya şifre bırakmaz.
 */
public final class SifreMotoru {

    private SifreMotoru() {}

    /** Kontrol sıklığı — saniyede bir yeter, dağıtım dakikalar ölçeğinde. */
    private static final int ARALIK_TICK = 20;
    /** Bu baskının altında hiç dağıtım olmaz. */
    private static final double ESIK = 0.35;
    /** Eşik üstünde saniye başına dağıtım olasılığı çarpanı. */
    private static final double HIZ = 0.02;

    private static final SplittableRandom RNG = new SplittableRandom();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % ARALIK_TICK != 0) return;
            if (!SifreAyar.al().acik) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    oyuncuAdimi(player);
                } catch (Throwable t) {
                    Candy.LOG.debug("[Candy] şifre motoru hata: {}", t.getMessage());
                }
            }
        });
        Candy.LOG.info("[Candy] SifreMotoru aktif — ikilik şifre dağıtımı");
    }

    private static void oyuncuAdimi(ServerPlayer player) {
        TruePersonality tp = PersonalityManager.get(player);
        if (!tp.kurulumTamam) return;
        if (baski(tp) < ESIK) return;
        if (RNG.nextDouble() >= baski(tp) * HIZ) return;
        SifreDagitici.dagit(player, tp);
    }

    /**
     * 0..1 — Candy'nin "bu oyuncuyla uğraşmaya değer" ölçüsü.
     *
     * Özgüven ana terim (Candy kendini göstermeye hazır), öfke ikinci (ihmal edilmek
     * onu tahmin edilemez yapıyor), {@code vagusDarbe} en küçük terim.
     */
    private static double baski(TruePersonality tp) {
        double ozguven = Math.max(0.0, Math.min(1.0, tp.candyOzguven / 100.0));
        double ofke = Math.max(0.0, Math.min(1.0, tp.candyOfke));
        double darbe = Math.max(0.0, Math.min(1.0, tp.vagusDarbe));
        return ozguven * 0.5 + ofke * 0.3 + darbe * 0.2;
    }
}
