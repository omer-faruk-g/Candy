package com.photonpulse.candy.meta;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.net.GoruntuPayload;
import com.photonpulse.candy.platform.SunucuTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GoruntuTetik (v8.1) — sunucu tarafı: FOV / render mesafesi müdahalesini yollar.
 *
 * <h3>SUNUCU TARAFI DA GERİ ALIYOR</h3>
 * Client {@code DISCONNECT} ve {@code CLIENT_STOPPING}'de kendi kendine geri alıyor, ama
 * oyuncu sunucudan düştüğünde client o paketi almayabilir. O yüzden burada
 * {@code DISCONNECT} olayında ayrıca "geri al" yollanıyor — ikisi de idempotent, iki kez
 * geri almak zarar vermez. Client tarafı diskteki yedekten çalıştığı için çökme hâlinde
 * de düzeliyor; bu paket yalnızca temiz çıkışı hızlandırıyor.
 */
public final class GoruntuTetik {

    private GoruntuTetik() {}

    /** Müdahale penceresi — bu kadar tick sonra kendiliğinden geri alınır. */
    private static final int SURE_TICK = 20 * 12;          // 12 sn
    /** İki müdahale arası en az bekleme. */
    private static final int SOGUMA_TICK = 20 * 60 * 6;    // 6 dk
    private static final double ESIK = 0.40;
    private static final double HIZ = 0.004;               // eşik üstü, saniye başına

    private static final SplittableRandom RNG = new SplittableRandom();
    /** UUID → müdahalenin bitmesi gereken tick (0 = aktif müdahale yok). */
    private static final Map<UUID, Long> bitis = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> sonTick = new ConcurrentHashMap<>();

    public static void register() {
        try {
            PayloadTypeRegistry.playS2C().register(GoruntuPayload.ID, GoruntuPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Goruntu payload zaten kayıtlı: {}", t.getMessage());
        }

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            try {
                ServerPlayer p = handler.getPlayer();
                geriAl(p);
                oyuncuAyrildi(p.getUUID());
            } catch (Throwable ignored) {}
        });

        SunucuTick.END_SERVER_TICK.register(server -> {
            final long simdi = server.getTickCount();
            if (simdi % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    oyuncuAdimi(player, simdi);
                } catch (Throwable t) {
                    Candy.LOG.debug("[Candy] görüntü tetik hata: {}", t.getMessage());
                }
            }
        });

        Candy.LOG.info("[Candy] GoruntuTetik aktif — FOV/render geçici, çıkışta geri alınır");
    }

    /**
     * Aktif müdahalenin süresi dolduysa geri alır, dolmadıysa bekler; müdahale yoksa
     * yeni bir tane açıp açmayacağına bakar.
     */
    private static void oyuncuAdimi(ServerPlayer player, long simdi) {
        final UUID id = player.getUUID();

        Long bit = bitis.get(id);
        if (bit != null) {
            if (simdi >= bit) {
                bitis.remove(id);
                sonTick.put(id, simdi);
                geriAl(player);
            }
            return;   // müdahale sürüyor — üstüne yenisini açma
        }

        Long son = sonTick.get(id);
        if (son != null && simdi - son < SOGUMA_TICK) return;

        TruePersonality tp = PersonalityManager.get(player);
        if (!tp.kurulumTamam) return;

        double baski = Math.min(1.0, tp.candyOzguven / 100.0 * 0.6
            + Math.min(1.0, tp.candyOfke) * 0.4);
        if (baski < ESIK) return;
        if (RNG.nextDouble() >= baski * HIZ) return;

        // Daralan görüş + kısalan mesafe: dünya yaklaşıyormuş gibi dursun.
        int fov = 30 + RNG.nextInt(25);        // 30..54
        int render = 2 + RNG.nextInt(3);       // 2..4
        uygula(player, fov, render);
        bitis.put(id, simdi + SURE_TICK);
        Candy.LOG.info("[Candy] görüntü müdahalesi [{}] fov={} render={} ({} sn)",
            player.getName().getString(), fov, render, SURE_TICK / 20);
    }

    public static void oyuncuAyrildi(UUID uuid) {
        bitis.remove(uuid);
        sonTick.remove(uuid);
    }

    public static void sunucuKapan() {
        bitis.clear();
        sonTick.clear();
    }

    /** FOV ve/veya render mesafesini değiştirir. -1 → o ayara dokunulmaz. */
    public static void uygula(ServerPlayer player, int fov, int render) {
        if (player == null) return;
        try {
            ServerPlayNetworking.send(player, new GoruntuPayload(fov, render, false));
        } catch (Throwable ignored) {
            // Vanilla client — görüntü müdahalesi yalnızca modlu client'ta.
        }
    }

    /** Oyuncunun kendi ayarlarına döner. */
    public static void geriAl(ServerPlayer player) {
        if (player == null) return;
        try {
            ServerPlayNetworking.send(player, new GoruntuPayload(-1, -1, true));
        } catch (Throwable ignored) {}
    }
}
