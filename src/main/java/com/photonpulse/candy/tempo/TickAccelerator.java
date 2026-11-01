package com.photonpulse.candy.tempo;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.tree.TreeBranch;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.tree.TreeNode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * TickAccelerator — Veri Açlığı Sistemi (Sistem 2).
 *
 * Global sunucu tick hızını (TPS) oyuncunun derinliği ve Candy'nin sabrına
 * göre yavaş yavaş yükseltir. Formül:
 *   hedefTPS = 20 + 40 * derinlikOrani * sabirSeviyesi   (clamp 20..60)
 * Mevcut TPS'e ÇOK küçük hızla asymptotik yaklaşır — "kaynayan kurbağa" etkisi.
 *
 * TPS ASLA 60'ı aşmaz. Yüzeye dönünce yavaşça 20'ye iner.
 *
 * DİKKAT — MP notu: TickManager global. Farklı derinliklerdeki birden fazla
 * gerçek oyuncu senaryosunda EN AGRESİF ihtiyacı olan hedefe göre karar
 * veriyoruz. Bu proje bağımsız (single-instance) kullanım için tasarlandı;
 * ileride MP'de kullanılırsa bu politika yeniden değerlendirilmeli.
 */
public class TickAccelerator {

    private static final int TICK_ARALIGI = 100;   // 5 saniye
    private static int tickSayaci = 0;

    // Vanilla TPS = 20, tavan 60 (spec)
    private static final float NORMAL_TPS = 20f;
    private static final float MAX_TPS = 60f;

    // Asymptotik kayma hızı — çok küçük, dakikalar boyunca fark edilmemeli
    private static final double KAYMA_HIZI = 0.015;

    // Log spam koruması — TPS bu kadar farklıysa logla
    private static final float LOG_ESIK = 2.0f;
    private static float sonLogladigimTPS = NORMAL_TPS;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TickAccelerator::onTick);
        Candy.LOG.info("[Candy] TickAccelerator aktif — kaynayan kurbağa");
    }

    /**
     * Sunucu kapanışı — TPS log offset'i sıfırlansın (single-player dünya değişimi).
     * Yeni dünya TPS=20'de başlar, static state 45'te kalırsa ilk tick spam logu atar.
     */
    public static void sunucuKapan() {
        tickSayaci = 0;
        sonLogladigimTPS = NORMAL_TPS;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        // En "istekli" oyuncuya göre hedef TPS
        double enYuksekIhtiyac = 0;
        ServerPlayer hedefOyuncu = null;
        double hedefDerinlik = 0, hedefSabir = 0;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            double derinlik = derinlikOrani(p);
            double sabir = sabirSeviyesi(p);
            double ihtiyac = derinlik * sabir;
            if (ihtiyac > enYuksekIhtiyac) {
                enYuksekIhtiyac = ihtiyac;
                hedefOyuncu = p;
                hedefDerinlik = derinlik;
                hedefSabir = sabir;
            }
        }

        // Hedef TPS hesapla — oyuncu yoksa NORMAL_TPS'e dön
        float hedefTPS = NORMAL_TPS;
        if (hedefOyuncu != null) {
            double raw = NORMAL_TPS + 40.0 * hedefDerinlik * hedefSabir;
            hedefTPS = (float) Math.max(NORMAL_TPS, Math.min(MAX_TPS, raw));
        }

        // Mevcut → hedef asymptotik kayma
        ServerTickRateManager tm = server.tickRateManager();
        float mevcutTPS = tm.tickrate();
        float yeniTPS = (float) TruePersonality.kayma(mevcutTPS, hedefTPS, KAYMA_HIZI);

        // Yuvarlama gürültüsünü engelle — ama çok küçük delta'lar için de uygula,
        // aksi halde düşük sabir*derinlik durumunda TPS hiç kıpırdamıyor.
        if (Math.abs(yeniTPS - mevcutTPS) > 0.0001f) {
            tm.setTickRate(yeniTPS);
        }

        // Log spam koruması — belirgin değişimde bir sefer logla
        if (Math.abs(yeniTPS - sonLogladigimTPS) >= LOG_ESIK) {
            Candy.LOG.info("[Candy] Tick hızı: {} (derinlik: {}, sabır: {})",
                String.format("%.1f", yeniTPS),
                String.format("%.2f", hedefDerinlik),
                String.format("%.2f", hedefSabir));
            sonLogladigimTPS = yeniTPS;
        }
    }

    /**
     * derinlikOrani = (denizSeviyesi - Y) / (denizSeviyesi - dunyaTabaniY), clamp 0..1
     * Yüzey/üstü = 0, bedrock = 1.
     */
    private static double derinlikOrani(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        int deniz = world.getSeaLevel();
        int taban = world.getMinY();
        double pay = deniz - player.getY();
        double payda = deniz - taban;
        if (payda <= 0) return 0;
        return Math.max(0, Math.min(1, pay / payda));
    }

    private static double sabirSeviyesi(ServerPlayer player) {
        TreeNode tepki = TreeGrowth.get(player.getUUID()).nodes.get(TreeBranch.TEPKI.id);
        if (tepki == null) return 0.5;
        return Math.max(0, Math.min(1, 1.0 - tepki.aktiflik));
    }
}
