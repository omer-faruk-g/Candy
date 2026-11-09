package com.photonpulse.candy.platform;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sag tik etkilesimleri — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi. Uc kanal NeoForge'un
 * PlayerInteractEvent ic siniflarina esleniyor: RightClickItem,
 * RightClickBlock, EntityInteract.
 *
 * Iptal kurali Fabric'in kendi mixin'inden alindi (ServerPlayerGameModeMixin:
 * "result != PASS ise donus degerini ayarla ve iptal et"), burada da ayni:
 * setCancellationResult(sonuc) + setCanceled(true).
 *
 * EntityInteract isabet sonucu tasimiyor (yalnizca EntityInteractSpecific
 * tasiyor); VARLIK kanali bu yuzden dort parametreli. Tek cagri yeri
 * (BossManager) isabet sonucunu kullanmiyor.
 */
@EventBusSubscriber
public final class EtkilesimOlaylari {

    public static final EsyaKanal ESYA = new EsyaKanal();
    public static final BlokKanal BLOK = new BlokKanal();
    public static final VarlikKanal VARLIK = new VarlikKanal();

    private EtkilesimOlaylari() {}

    @FunctionalInterface
    public interface EsyaDinleyici {
        InteractionResult interact(Player player, Level world, InteractionHand hand);
    }

    @FunctionalInterface
    public interface BlokDinleyici {
        InteractionResult interact(Player player, Level world, InteractionHand hand, BlockHitResult hit);
    }

    @FunctionalInterface
    public interface VarlikDinleyici {
        InteractionResult interact(Player player, Level world, InteractionHand hand, Entity entity);
    }

    public static final class EsyaKanal {
        private final List<EsyaDinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private EsyaKanal() {}

        public void register(EsyaDinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        InteractionResult calistir(Player player, InteractionHand hand) {
            for (EsyaDinleyici d : dinleyiciler) {
                InteractionResult sonuc = d.interact(player, player.level(), hand);
                if (sonuc != InteractionResult.PASS) return sonuc;
            }
            return InteractionResult.PASS;
        }
    }

    public static final class BlokKanal {
        private final List<BlokDinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private BlokKanal() {}

        public void register(BlokDinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        InteractionResult calistir(Player player, InteractionHand hand, BlockHitResult hit) {
            for (BlokDinleyici d : dinleyiciler) {
                InteractionResult sonuc = d.interact(player, player.level(), hand, hit);
                if (sonuc != InteractionResult.PASS) return sonuc;
            }
            return InteractionResult.PASS;
        }
    }

    public static final class VarlikKanal {
        private final List<VarlikDinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private VarlikKanal() {}

        public void register(VarlikDinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        InteractionResult calistir(Player player, InteractionHand hand, Entity entity) {
            for (VarlikDinleyici d : dinleyiciler) {
                InteractionResult sonuc = d.interact(player, player.level(), hand, entity);
                if (sonuc != InteractionResult.PASS) return sonuc;
            }
            return InteractionResult.PASS;
        }
    }

    @SubscribeEvent
    public static void onEsya(PlayerInteractEvent.RightClickItem event) {
        InteractionResult sonuc = ESYA.calistir(event.getEntity(), event.getHand());
        if (sonuc != InteractionResult.PASS) {
            event.setCancellationResult(sonuc);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlok(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult sonuc = BLOK.calistir(event.getEntity(), event.getHand(), event.getHitVec());
        if (sonuc != InteractionResult.PASS) {
            event.setCancellationResult(sonuc);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onVarlik(PlayerInteractEvent.EntityInteract event) {
        InteractionResult sonuc = VARLIK.calistir(event.getEntity(), event.getHand(), event.getTarget());
        if (sonuc != InteractionResult.PASS) {
            event.setCancellationResult(sonuc);
            event.setCanceled(true);
        }
    }
}
