package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Varliga saldiri kancasi — Fabric uygulamasi.
 *
 * Imza Fabric'in AttackEntityCallback'i ile birebir ayni tutuldu, boylece
 * cagri yerlerinde yalnizca import satiri degisiyor. Bes parametrenin
 * ikisinin Fabric'te SABIT oldugu dogrulandi (ServerPlayerMixin ve
 * MultiPlayerGameModeMixin): hand her zaman MAIN_HAND, hit her zaman null.
 * NeoForge tarafinin ayni iki degeri vermesi bu yuzden uydurma degil.
 */
public final class VarligaSaldiri {

    public static final Kanal EVENT = new Kanal();

    private VarligaSaldiri() {}

    @FunctionalInterface
    public interface Dinleyici {
        InteractionResult interact(Player player, Level world, InteractionHand hand,
                                   Entity target, EntityHitResult hit);
    }

    public static final class Kanal {
        private Kanal() {}

        public void register(Dinleyici dinleyici) {
            AttackEntityCallback.EVENT.register(dinleyici::interact);
        }
    }
}
