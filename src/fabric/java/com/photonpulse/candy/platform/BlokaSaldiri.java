package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Bloga sol tik — Fabric uygulamasi.
 *
 * PASS disinda bir sonuc blok kirmayi iptal eder.
 */
public final class BlokaSaldiri {

    public static final Kanal EVENT = new Kanal();

    private BlokaSaldiri() {}

    @FunctionalInterface
    public interface Dinleyici {
        InteractionResult interact(Player player, Level world, InteractionHand hand,
                                   BlockPos pos, Direction dir);
    }

    public static final class Kanal {
        private Kanal() {}

        public void register(Dinleyici dinleyici) {
            AttackBlockCallback.EVENT.register(dinleyici::interact);
        }
    }
}
