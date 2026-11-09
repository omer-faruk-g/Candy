package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Oyuncunun dunya (boyut) degistirmesi — Fabric uygulamasi.
 */
public final class DunyaDegisimi {

    public static final OyuncuGecisi AFTER_PLAYER_CHANGE_WORLD = new OyuncuGecisi();

    private DunyaDegisimi() {}

    @FunctionalInterface
    public interface Dinleyici {
        void dunyaDegisti(ServerPlayer oyuncu, ServerLevel kaynak, ServerLevel hedef);
    }

    public static final class OyuncuGecisi {
        private OyuncuGecisi() {}

        public void register(Dinleyici dinleyici) {
            ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                dinleyici::dunyaDegisti);
        }
    }
}
