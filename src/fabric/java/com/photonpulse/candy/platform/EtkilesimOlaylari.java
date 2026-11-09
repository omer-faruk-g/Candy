package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Sag tik etkilesimleri — Fabric uygulamasi.
 *
 * Uc Fabric callback'i tek sinifta toplandi, cunku NeoForge tarafinda uclu de
 * PlayerInteractEvent'in ic siniflari; gruplama NeoForge'un kendi seklini
 * yansitiyor.
 *
 * VARLIK kanalinda Fabric'in besinci parametresi (EntityHitResult) dusuruldu:
 * NeoForge'un EntityInteract'inda karsiligi yok (yalnizca
 * EntityInteractSpecific tasiyor) ve tek cagri yeri kullanmiyor.
 * Gerekce: MULTILOADER.md.
 */
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
        private EsyaKanal() {}

        public void register(EsyaDinleyici dinleyici) {
            UseItemCallback.EVENT.register(dinleyici::interact);
        }
    }

    public static final class BlokKanal {
        private BlokKanal() {}

        public void register(BlokDinleyici dinleyici) {
            UseBlockCallback.EVENT.register(dinleyici::interact);
        }
    }

    public static final class VarlikKanal {
        private VarlikKanal() {}

        public void register(VarlikDinleyici dinleyici) {
            UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                dinleyici.interact(player, world, hand, entity));
        }
    }
}
