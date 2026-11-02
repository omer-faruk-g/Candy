package com.photonpulse.candy.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bloga sol tik — NeoForge uygulamasi.
 *
 * LeftClickBlock iki tarafli ateslenir (ServerPlayerGameMode:137 ve
 * MultiPlayerGameMode), tipki Fabric'in AttackBlockCallback'i gibi — yani
 * cagri yerindeki isClientSide() suzgeci ayni calisir.
 *
 * Iptal kurali Fabric'ten alindi: PASS disinda bir sonuc blok kirmayi iptal eder.
 */
@EventBusSubscriber
public final class BlokaSaldiri {

    public static final Kanal EVENT = new Kanal();

    private BlokaSaldiri() {}

    @FunctionalInterface
    public interface Dinleyici {
        InteractionResult interact(Player player, Level world, InteractionHand hand,
                                   BlockPos pos, Direction dir);
    }

    public static final class Kanal {
        private final List<Dinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private Kanal() {}

        public void register(Dinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        InteractionResult calistir(Player player, Level world, InteractionHand hand,
                                   BlockPos pos, Direction dir) {
            for (Dinleyici d : dinleyiciler) {
                InteractionResult sonuc = d.interact(player, world, hand, pos, dir);
                if (sonuc != InteractionResult.PASS) return sonuc;
            }
            return InteractionResult.PASS;
        }
    }

    @SubscribeEvent
    public static void onSolTik(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        InteractionResult sonuc = EVENT.calistir(
            player, player.level(), event.getHand(), event.getPos(), event.getFace());
        if (sonuc != InteractionResult.PASS) {
            event.setCanceled(true);
        }
    }
}
