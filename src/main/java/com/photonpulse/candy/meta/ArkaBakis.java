package com.photonpulse.candy.meta;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ArkaBakis (v8.2) — "arkasına ne sıklıkta bakıyor" eksenini ölçer.
 *
 * <h3>NİYE EKSEN, NİYE OLAY DEĞİL</h3>
 * Mirror.txt'nin en güçlü mekaniği bakılmayan yere müdahaleydi ve Candy'de bunun ölçümü
 * bile yoktu. Tek seferlik bir "arkasına baktı" olayı yerine <b>oran</b> tutuluyor, çünkü
 * Kurul'un öğrenebileceği şey olay değil kişilik boyutu: sürekli arkasını kollayan
 * oyuncuyla hiç dönmeyen oyuncu farklı iki profil ve farklı iki zayıflık.
 *
 * <h3>ÖLÇÜM</h3>
 * Oyuncunun yaw'ı 4 tick'te bir örneklenir. İki örnek arası <b>açısal</b> fark 120°'yi
 * geçerse bu bir "omuz üstü bakış"tır. Fark hesabı 360° sarmalını düzeltir: 350° → 10°
 * geçişi 340° değil 20°'dir, düzeltmezsek yürürken dönen her oyuncu sahte pozitif verir.
 *
 * <p>Oran EMA ile yürür ve dakikalık bir hedefe normalize edilir; mutlak sayı değil
 * <i>alışkanlık</i> ölçülür. Hiç dönmeyen oyuncuda 0'a, paranoyakta 1'e gider.</p>
 */
public final class ArkaBakis {

    private ArkaBakis() {}

    /** Örnekleme aralığı — bir baş çevirmesi ~5-10 tick sürer, 4 yeterince ince. */
    private static final int ARALIK = 4;
    /** Bu açıdan büyük sapma "arkaya bakış" sayılır. */
    private static final double ESIK_DERECE = 120.0;
    /**
     * Doyum: dakikada bu kadar bakış = eksen 1,0.
     * 6/dk = 10 saniyede bir omuz üstü bakış; gözlemlenmiş "paranoyak" ritim.
     */
    private static final double DOYUM_DK = 6.0;
    /** EMA katsayısı — eksen oturaklı olsun, tek bir dönüşle sıçramasın. */
    private static final double KAYMA = 0.04;
    /** Oran penceresi: 30 saniye (600 tick). */
    private static final long PENCERE_TICK = 600L;

    private static final Map<UUID, Float> oncekiYaw = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> pencereBakis = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> pencereBas = new ConcurrentHashMap<>();

    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ArkaBakis::onTick);
        Candy.LOG.info("[Candy] ArkaBakis aktif — arkaya bakma ekseni ölçülüyor");
    }

    private static void onTick(MinecraftServer server) {
        tick++;
        if (tick % ARALIK != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                olcumAl(player);
            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] ArkaBakis hata: {}", t.getMessage());
            }
        }
    }

    private static void olcumAl(ServerPlayer player) {
        UUID id = player.getUUID();
        float yaw = player.getYRot();
        Float onceki = oncekiYaw.put(id, yaw);
        if (onceki == null) return;

        if (aciFark(onceki, yaw) >= ESIK_DERECE) {
            pencereBakis.merge(id, 1, Integer::sum);
        }

        long simdi = player.level().getGameTime();
        long bas = pencereBas.getOrDefault(id, 0L);
        if (bas == 0L) { pencereBas.put(id, simdi); return; }
        if (simdi - bas < PENCERE_TICK) return;

        // Pencere kapandı — oranı eksene işle.
        int bakis = pencereBakis.getOrDefault(id, 0);
        pencereBakis.put(id, 0);
        pencereBas.put(id, simdi);

        double dakika = (simdi - bas) / 1200.0;        // 1200 tick = 1 dk
        if (dakika <= 0.001) return;
        double hedef = Math.min(1.0, (bakis / dakika) / DOYUM_DK);

        TruePersonality tp = PersonalityManager.get(player);
        if (tp == null) return;
        tp.arkayaBakma = TruePersonality.kayma(tp.arkayaBakma, hedef, KAYMA);
    }

    /**
     * İki yaw arası en kısa açısal fark (0..180).
     *
     * <p>Düz çıkarma yanlış olurdu: 350° ile 10° arasındaki gerçek fark 20°, düz
     * çıkarmada 340°. Düzeltmezsek kuzeye bakan her oyuncu sahte "arkaya baktı"
     * üretir — eksen de sürekli 1,0'da takılır.</p>
     */
    static double aciFark(float a, float b) {
        double d = Math.abs(((double) b - a) % 360.0);
        if (d > 180.0) d = 360.0 - d;
        return d;
    }

    public static void oyuncuAyrildi(UUID uuid) {
        oncekiYaw.remove(uuid);
        pencereBakis.remove(uuid);
        pencereBas.remove(uuid);
    }

    public static void sunucuKapan() {
        oncekiYaw.clear();
        pencereBakis.clear();
        pencereBas.clear();
    }
}
