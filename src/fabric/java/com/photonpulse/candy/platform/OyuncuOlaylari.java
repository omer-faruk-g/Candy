package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Oyuncu olaylari — Fabric uygulamasi.
 *
 * Fabric'in ucuncu parametresi (oldPlayer) kasten dusuruldu: iki cagri yeri de
 * onu kullanmiyor ve NeoForge'un PlayerRespawnEvent'i tasimiyor.
 *
 * "canli" parametresi iki loader'da da ayni vanilla degeridir:
 * PlayerList.respawn'in ikinci argumani (End portalindan donus). Fabric mixin
 * onu oradan okuyor, NeoForge ayni argumani firePlayerRespawnEvent'e geciriyor.
 */
public final class OyuncuOlaylari {

    public static final YenidenDogus AFTER_RESPAWN = new YenidenDogus();

    private OyuncuOlaylari() {}

    @FunctionalInterface
    public interface Dinleyici {
        void yenidenDogdu(ServerPlayer yeni, boolean canli);
    }

    public static final class YenidenDogus {
        private YenidenDogus() {}

        public void register(Dinleyici dinleyici) {
            ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> dinleyici.yenidenDogdu(newPlayer, alive));
        }
    }
}
