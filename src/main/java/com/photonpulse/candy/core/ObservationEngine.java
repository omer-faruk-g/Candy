package com.photonpulse.candy.core;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.psikoloji.Ozguven;
import com.photonpulse.candy.shadow.ShadowState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ObservationEngine — Candy'nin gözü.
 *
 * Her N tick'te bir tüm oyuncuları gözlemler ve TruePersonality'yi anlık günceller.
 * Ayrıca event callback'lerine bağlanarak saldırı gibi olayları yakalar.
 *
 * NOT: Sadece Fabric API event'leri kullanılır. Mixin YOK.
 */
public class ObservationEngine {

    // Gözlem sıklığı — 20 tick = 1 saniye
    private static final int GOZLEM_ARALIGI = 20;

    // Autosave sıklığı — 6000 tick = 5 dakika
    private static final int AUTOSAVE_ARALIGI = 6000;

    private static int tickSayaci = 0;

    // Son pozisyonlar (hız/rota hesabı için)
    private static final Map<UUID, Vec3d> oncekiPozisyon = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ObservationEngine::onServerTick);
        AttackEntityCallback.EVENT.register(ObservationEngine::onSaldiri);
        Candy.LOG.info("[Candy] ObservationEngine aktif");
    }

    private static void onServerTick(MinecraftServer server) {
        tickSayaci++;

        if (tickSayaci % GOZLEM_ARALIGI == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                gozlemle(player);
            }
        }

        if (tickSayaci % AUTOSAVE_ARALIGI == 0) {
            PersonalityManager.hepsiniKaydet();
        }
    }

    /**
     * Tek bir oyuncuyu gözlemle. TruePersonality anlık güncellenir.
     */
    private static void gozlemle(ServerPlayerEntity player) {
        TruePersonality tp = PersonalityManager.get(player);
        if (tp.ilkKayitTick == 0) tp.ilkKayitTick = player.getWorld().getTime();

        // ── Hareket analizi ──
        Vec3d simdi = player.getPos();
        Vec3d onceki = oncekiPozisyon.get(player.getUuid());
        double hiz = (onceki == null) ? 0 : simdi.distanceTo(onceki);
        oncekiPozisyon.put(player.getUuid(), simdi);

        // Sprint / koşma = kaçma eğilimi + (kurulum sonrası) Candy özgüveni
        if (player.isSprinting() && hiz > 0.15) {
            tp.kacOrani = TruePersonality.kayma(tp.kacOrani, 1.0, 0.03);
            if (tp.kurulumTamam) Ozguven.artir(tp, 0.02);   // kurulum bitmeden özgüven birikmesin
        }
        // Neredeyse hareketsiz = donma / donup kalma
        else if (hiz < 0.02) {
            tp.donOrani = TruePersonality.kayma(tp.donOrani, 1.0, 0.02);
        }
        // Normal hareket = keşfetme
        else {
            tp.kesfetOrani = TruePersonality.kayma(tp.kesfetOrani, 1.0, 0.015);
        }
        tp.hareketNormalize();

        // ── Ekipman ──
        ItemStack eldeki = player.getMainHandStack();
        if (!eldeki.isEmpty()) {
            String itemId = Registries.ITEM.getId(eldeki.getItem()).toString();
            tp.ekipmanKullanim.merge(itemId, 1.0, Double::sum);
        }

        // ── Konum ──
        BlockPos pos = player.getBlockPos();
        tp.enSonKonum = new int[]{pos.getX(), pos.getY(), pos.getZ()};

        // Gece/gündüz konumu
        long timeOfDay = player.getWorld().getTimeOfDay() % 24000L;
        if (timeOfDay > 13000L && timeOfDay < 23000L) {
            tp.geceKonumu = tp.enSonKonum;
        } else {
            tp.gunduzKonumu = tp.enSonKonum;
        }

        // Bulunduğu yer sık ziyaret ediliyorsa sıcak nokta
        if (tp.toplamGozlemTick % 60 == 0) {  // her ~1 dakikada bir
            tp.sicakNoktaEkle(pos.getX(), pos.getY(), pos.getZ());
        }

        // ── Panik göstergesi ──
        // Ani hız değişimi + envanter aç/kapa = panik. (envanter açık mı? server-side'da direkt yok,
        // ama isSneaking + hızlı yön değişimi bize sinyal verir)
        if (player.isSneaking() && hiz > 0.1) {
            tp.panikEsigi = TruePersonality.kayma(tp.panikEsigi, 0.2, 0.01);
        }

        tp.toplamGozlemTick++;

        // ── v0.3: ShadowState — sadece izleniyorsa bastırılmış baskı birikir ──
        ShadowState.kontrolEt(player, tp, player.getWorld().getTime());
    }

    /**
     * Oyuncu bağlantıdan düştüğünde çağır — per-player pozisyon cache'ini temizle.
     */
    public static void oyuncuAyrildi(UUID uuid) {
        oncekiPozisyon.remove(uuid);
    }

    private static ActionResult onSaldiri(net.minecraft.entity.player.PlayerEntity player,
                                          net.minecraft.world.World world,
                                          net.minecraft.util.Hand hand,
                                          net.minecraft.entity.Entity target,
                                          net.minecraft.util.hit.EntityHitResult hitResult) {
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        TruePersonality tp = PersonalityManager.get(sp);
        tp.saldirOrani = TruePersonality.kayma(tp.saldirOrani, 1.0, 0.05);
        tp.hareketNormalize();
        return ActionResult.PASS;
    }
}
