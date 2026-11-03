package com.photonpulse.candy.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Oyuncunun dunya (boyut) degistirmesi — NeoForge uygulamasi.
 *
 * Olay ServerLevel degil ResourceKey tasiyor; ikisi de sunucudan cozuluyor.
 * Hedef icin oyuncunun kendi seviyesi kullanilmiyor, getTo() cozuluyor —
 * ikisi de ayni olurdu, ama anahtar olayin kendi verisi.
 *
 * Yamali kaynak dogrulamasi (ServerPlayer:1084): olay gecis TAMAMLANDIKTAN
 * sonra atiliyor, yani her iki seviye de yuklu ve cozulebilir.
 */
@EventBusSubscriber
public final class DunyaDegisimi {

    public static final OyuncuGecisi AFTER_PLAYER_CHANGE_WORLD = new OyuncuGecisi();

    private DunyaDegisimi() {}

    @FunctionalInterface
    public interface Dinleyici {
        void dunyaDegisti(ServerPlayer oyuncu, ServerLevel kaynak, ServerLevel hedef);
    }

    public static final class OyuncuGecisi {
        private final List<Dinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private OyuncuGecisi() {}

        public void register(Dinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        boolean bos() {
            return dinleyiciler.isEmpty();
        }

        void calistir(ServerPlayer oyuncu, ServerLevel kaynak, ServerLevel hedef) {
            for (Dinleyici d : dinleyiciler) {
                d.dunyaDegisti(oyuncu, kaynak, hedef);
            }
        }
    }

    @SubscribeEvent
    public static void onDunyaDegisti(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (AFTER_PLAYER_CHANGE_WORLD.bos()) return;
        if (!(event.getEntity() instanceof ServerPlayer oyuncu)) return;
        MinecraftServer sunucu = oyuncu.level().getServer();
        if (sunucu == null) return;
        ServerLevel kaynak = sunucu.getLevel(event.getFrom());
        ServerLevel hedef = sunucu.getLevel(event.getTo());
        if (kaynak == null || hedef == null) return;
        AFTER_PLAYER_CHANGE_WORLD.calistir(oyuncu, kaynak, hedef);
    }
}
