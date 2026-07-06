package com.photonpulse.candy.core;

import com.photonpulse.candy.Candy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FearDetection — Candy'nin korku ölçer'i.
 *
 * korkuSkoru 0..100 arası, kurulum sonrası 10'dan başlar.
 * Bu skor sadece "Candy oyuncuyu ne kadar sıkıştırıyor" değil,
 * aynı zamanda "Gerçek Candy'nin devreye gireceği eşik" için de kullanılır.
 *
 * Sinyaller:
 *  - Ani yön değişimi         → panik hareketi
 *  - Sprint spike             → kaçma tepkisi
 *  - Uzun süre hareketsizlik  → donup kalma
 *  - Aynı noktada ileri geri  → köşeye sıkışma
 *  - Envanter aç/kapa spike   → sinirlilik (isSneaking + hız kombosu ile tahmin)
 *
 * Ayrıca gündüz + güneşli ortam korkuyu YAVAŞÇA düşürür.
 * Candy tamamen bırakmaz — sadece bekler.
 */
public class FearDetection {

    private static final int SAMPLE_ARALIGI = 4;    // her 0.2 sn
    private static final int LOG_ARALIGI = 400;     // her 20 sn
    private static int tickSayaci = 0;

    // Son bakış vektörü — yön değişimi hesabı için
    private static final Map<UUID, Vec3d> onceBakis = new HashMap<>();
    // Son pozisyon — hız ve "aynı yere gel-git" için
    private static final Map<UUID, Vec3d> oncePos = new HashMap<>();
    // Yön değişim sayacı — kısa süre içindeki hızlı yön değişikliklerini biriktirir
    private static final Map<UUID, Double> yonDegisimAkumulator = new HashMap<>();
    // Aynı yerde ileri geri sinyali — pozisyon geçmişi
    private static final Map<UUID, Double> geriGelisAkumulator = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FearDetection::onTick);
        Candy.LOG.info("[Candy] FearDetection aktif");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % SAMPLE_ARALIGI != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            olc(player);
        }

        if (tickSayaci % LOG_ARALIGI == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                TruePersonality tp = PersonalityManager.get(player);
                Candy.LOG.debug("[Candy] korku[{}] = {}",
                    player.getName().getString(), String.format("%.1f", tp.korkuSkoru));
            }
        }
    }

    private static void olc(ServerPlayerEntity player) {
        UUID uid = player.getUuid();
        TruePersonality tp = PersonalityManager.get(player);

        // Kurulum bitmediyse skor 10'da tut, birikmesin
        if (!tp.kurulumTamam) {
            tp.korkuSkoru = TruePersonality.kayma(tp.korkuSkoru, 10.0, 0.02);
            return;
        }

        double dSkor = 0;

        // ── 1. Ani bakış / yön değişimi ──
        Vec3d bakis = player.getRotationVector();
        Vec3d oBakis = onceBakis.get(uid);
        if (oBakis != null) {
            double dot = bakis.dotProduct(oBakis);
            // dot = 1 ise aynı yön, -1 ise ters. Küçük dot = büyük değişim.
            double degisim = Math.max(0, 1.0 - dot);
            double akumule = yonDegisimAkumulator.getOrDefault(uid, 0.0);
            akumule = akumule * 0.9 + degisim * 6.0;
            yonDegisimAkumulator.put(uid, akumule);
            // 3'ün üstüne çıkarsa "paniklemiş yönde geziniyor"
            if (akumule > 3.0) dSkor += 0.15;
        }
        onceBakis.put(uid, bakis);

        // ── 2. Sprint spike ──
        // isSprinting + koşma yönü kısa süre içinde ters çevrildi = paniğe girdi
        if (player.isSprinting() && oBakis != null) {
            double dot = bakis.dotProduct(oBakis);
            if (dot < 0.6) dSkor += 0.25;    // hızlı yön değişimi + sprint = panik
        }

        // ── 3. Aynı yerde ileri geri (köşe sıkışması) ──
        Vec3d simdi = player.getPos();
        Vec3d oPos = oncePos.get(uid);
        if (oPos != null) {
            double mesafe = simdi.distanceTo(oPos);
            double geri = geriGelisAkumulator.getOrDefault(uid, 0.0);
            // Kısa mesafede sürekli hareket = ileri geri
            if (mesafe > 0.3 && mesafe < 2.5) geri = geri * 0.9 + 0.3;
            else geri *= 0.85;
            geriGelisAkumulator.put(uid, geri);
            if (geri > 1.5) dSkor += 0.1;
        }
        oncePos.put(uid, simdi);

        // ── 4. Sneaking + hızlı hareket (sinirlilik tahmini) ──
        if (player.isSneaking() && oPos != null) {
            double mesafe = simdi.distanceTo(oPos);
            if (mesafe > 0.15) dSkor += 0.05;
        }

        // ── 5. Alçalma (soğuma) ──
        // Gündüz + yüzey ışığı yüksek + hareketsiz → korku çok yavaş azalır
        long timeOfDay = player.getWorld().getTimeOfDay() % 24000L;
        boolean gunduz = timeOfDay < 12000L;
        int isik = player.getWorld().getLightLevel(player.getBlockPos());
        if (gunduz && isik > 10 && dSkor < 0.05) {
            dSkor -= 0.03;
        }

        // ── Uygula ──
        // Asymptotik: korku 100'e yaklaştıkça artış yavaşlar
        double kalanKapasite = Math.max(0, 100.0 - tp.korkuSkoru) / 100.0;
        tp.korkuSkoru = Math.max(0, Math.min(100, tp.korkuSkoru + dSkor * kalanKapasite * 4));

        // Panik eşiğini de güncelle: sık panikliyorsa eşik düşer (kolayca panikler)
        if (dSkor > 0.2) {
            tp.panikEsigi = TruePersonality.kayma(tp.panikEsigi, 0.15, 0.005);
        }
    }
}
