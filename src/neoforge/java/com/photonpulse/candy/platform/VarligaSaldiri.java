package com.photonpulse.candy.platform;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Varliga saldiri kancasi — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi. Zamanlama birebir: Fabric'in
 * mixin'i ServerPlayer.attack HEAD'inde, AttackEntityEvent de saldiridan once
 * ve iptal edilebilir.
 *
 * AttackEntityEvent'te getHand() ve isabet sonucu YOK. Uydurulmadi: Fabric'in
 * kendi cagri yerlerinde bu iki deger SABIT oldugu dogrulandi — hand her zaman
 * MAIN_HAND, hit her zaman null — yani ayni degerleri vermek birebir karsilik.
 *
 * Iptal kurali da Fabric'ten alindi: Fabric'in mixin'i "result != PASS ise
 * iptal" diyor, burada da ayni.
 */
@EventBusSubscriber
public final class VarligaSaldiri {

    public static final Kanal EVENT = new Kanal();

    private VarligaSaldiri() {}

    @FunctionalInterface
    public interface Dinleyici {
        InteractionResult interact(Player player, Level world, InteractionHand hand,
                                   Entity target, EntityHitResult hit);
    }

    public static final class Kanal {
        private final List<Dinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private Kanal() {}

        public void register(Dinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        boolean calistir(Player player, Entity target) {
            for (Dinleyici d : dinleyiciler) {
                InteractionResult sonuc = d.interact(
                    player, player.level(), InteractionHand.MAIN_HAND, target, null);
                if (sonuc != InteractionResult.PASS) return true;
            }
            return false;
        }
    }

    @SubscribeEvent
    public static void onSaldiri(AttackEntityEvent event) {
        if (EVENT.calistir(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }
}
