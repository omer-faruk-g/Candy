package com.photonpulse.candy.entity;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * SpectateSwap — oyuncunun kamerasını KISA süreliğine bir CandyDoppelEntity'e
 * kilitler. Oyuncu kendisini 3. şahıstan izliyor gibi olur — Candy'nin
 * bakış açısından bir an. Korku vericidir çünkü kontrol kayboluyormuş hissi.
 *
 * Güvenlik: sadece yakında bir doppel varsa çalışır. 2 saniye sonra kamera
 * player'a geri döner. Korku düşükse tetiklenmez.
 */
public class SpectateSwap {

    private static final int TICK_ARALIGI = 100;             // 5 sn kontrol
    private static final int TETIKLEME_ARALIGI_TICK = 3600;  // 3 dakika minimum
    private static final int SPECTATE_SURE_TICK = 40;        // 2 sn
    private static final double DOPPEL_YARICAP = 24.0;
    private static final double MIN_KORKU = 50.0;
    private static final double TETIKLEME_IHTIMAL = 0.15;    // her kontrol'de %15

    private static int tickSayaci = 0;
    private static final Random RNG = new Random();

    // Her oyuncu için: son spectate ne zaman + aktif spectate sonu (0 = yok)
    private static final Map<UUID, Long> sonSpectate = new HashMap<>();
    private static final Map<UUID, Long> spectateBitis = new HashMap<>();
    // Aktif spectate sırasında orijinal kamera entity'sini geri koymak için
    private static final Map<UUID, Entity> orijinalKamera = new HashMap<>();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(SpectateSwap::onTick);
        Candy.LOG.info("[Candy] SpectateSwap aktif — kamera hile'si");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonSpectate.remove(uuid);
        spectateBitis.remove(uuid);
        orijinalKamera.remove(uuid);
    }

    public static void sunucuKapan() {
        sonSpectate.clear();
        spectateBitis.clear();
        orijinalKamera.clear();
        tickSayaci = 0;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        long simdi = server.overworld().getGameTime();

        // Aktif spectate'leri kontrol et — süresi doldu mu, veya camera hedefi öldü mü?
        spectateBitis.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) return true;   // player yok, cleanup

            boolean cameraOldu = false;
            Entity cam = p.getCamera();
            if (cam != null && cam != p && (!cam.isAlive() || cam.isRemoved())) {
                cameraOldu = true;   // doppel öldü/kayboldu
            }

            if (!cameraOldu && simdi < e.getValue()) return false;

            // Süresi doldu VEYA camera öldü — kamerayı geri koy
            Entity orig = orijinalKamera.remove(e.getKey());
            p.setCamera(orig != null && !orig.isRemoved() ? orig : p);
            return true;
        });

        if (tickSayaci % TICK_ARALIGI != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (tp.korkuSkoru < MIN_KORKU) continue;

            // Zaten spectate'te mi?
            if (spectateBitis.containsKey(player.getUUID())) continue;

            // Son spectate'ten yeterince geçti mi?
            Long son = sonSpectate.get(player.getUUID());
            if (son != null && simdi - son < TETIKLEME_ARALIGI_TICK) continue;

            // İhtimal
            if (RNG.nextDouble() > TETIKLEME_IHTIMAL) continue;

            // Yakında doppel var mı?
            ServerLevel world = (ServerLevel) player.level();
            AABB arama = new AABB(player.blockPosition()).inflate(DOPPEL_YARICAP);
            List<CandyDoppelEntity> doppeller = world.getEntitiesOfClass(
                CandyDoppelEntity.class, arama, d -> {
                    var owner = d.getOwnerUuid();
                    return owner.isPresent() && owner.get().equals(player.getUUID()) && d.isAlive();
                });
            if (doppeller.isEmpty()) continue;

            // İlk doppel'e spectate — kısa süreli
            CandyDoppelEntity hedef = doppeller.get(0);
            orijinalKamera.put(player.getUUID(), player.getCamera());
            player.setCamera(hedef);
            spectateBitis.put(player.getUUID(), simdi + SPECTATE_SURE_TICK);
            sonSpectate.put(player.getUUID(), simdi);
            Candy.LOG.info("[Candy] SpectateSwap: {} kamerası doppel'e kilitlendi (2 sn)",
                player.getName().getString());
        }
    }
}
