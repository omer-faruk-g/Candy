package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blok olaylari — Fabric uygulamasi.
 *
 * Fabric'in besinci parametresi (BlockEntity) dusuruldu: NeoForge'un
 * BreakEvent'inde karsiligi yok ve iki cagri yerinin hicbiri kullanmiyor.
 * Gerekce: MULTILOADER.md.
 */
public final class BlokOlaylari {

    public static final Kanal AFTER = new Kanal();

    private BlokOlaylari() {}

    @FunctionalInterface
    public interface Dinleyici {
        void afterBlockBreak(Level world, Player player, BlockPos pos, BlockState state);
    }

    public static final class Kanal {
        private Kanal() {}

        public void register(Dinleyici dinleyici) {
            PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) ->
                dinleyici.afterBlockBreak(world, player, pos, state));
        }
    }
}
