package com.photonpulse.candy.platform;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Oyuncu olaylari — NeoForge uygulamasi.
 *
 * "canli" parametresi uydurulmadi: Fabric mixin'i PlayerList.respawn'in ikinci
 * argumanini okuyor, NeoForge ayni metodun ayni argumanini
 * firePlayerRespawnEvent'e geciriyor (yamali kaynak PlayerList:430 ve :479).
 * Yani isEndConquered() ile Fabric'in "alive"i ayni vanilla degeridir.
 *
 * Fabric'in oldPlayer parametresi dusuruldu: hicbir cagri yeri kullanmiyor ve
 * bu olay tasimiyor.
 */
@EventBusSubscriber
public final class OyuncuOlaylari {

    public static final YenidenDogus AFTER_RESPAWN = new YenidenDogus();

    private OyuncuOlaylari() {}

    @FunctionalInterface
    public interface Dinleyici {
        void yenidenDogdu(ServerPlayer yeni, boolean canli);
    }

    public static final class YenidenDogus {
        private final List<Dinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private YenidenDogus() {}

        public void register(Dinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        void calistir(ServerPlayer yeni, boolean canli) {
            for (Dinleyici d : dinleyiciler) {
                d.yenidenDogdu(yeni, canli);
            }
        }
    }

    @SubscribeEvent
    public static void onYenidenDogdu(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer oyuncu)) return;
        AFTER_RESPAWN.calistir(oyuncu, event.isEndConquered());
    }
}
