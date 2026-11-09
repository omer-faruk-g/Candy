package com.photonpulse.candy.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Blok olaylari — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi.
 *
 * ZAMANLAMA FARKI: Fabric'in AFTER'i blok kirildiktan SONRA atesleniyor,
 * NeoForge'un BreakEvent'i kirilmadan ONCE ve iptal edilebilir. LOWEST
 * oncelik + iptal kontrolu ile "kirilacagi kesinlesmis blok"a en yakin ana
 * cekildi; bizden sonra takilan bir mod hala iptal edebilir, o zaman
 * kirilmayan bir blok sayilmis olur. Iki cagri yeri de yalnizca sinyal
 * sayiyor (madencilik sayaci, psikoloji), oyun akisini belirlemiyor.
 *
 * world olarak event.getLevel() DEGIL player.level() kullaniliyor: getLevel()
 * LevelAccessor donuyor, cagri yerleri Level bekliyor (getGameTime cagiriyor).
 */
@EventBusSubscriber
public final class BlokOlaylari {

    public static final Kanal AFTER = new Kanal();

    private BlokOlaylari() {}

    @FunctionalInterface
    public interface Dinleyici {
        void afterBlockBreak(Level world, Player player, BlockPos pos, BlockState state);
    }

    public static final class Kanal {
        private final List<Dinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private Kanal() {}

        public void register(Dinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        void calistir(Level world, Player player, BlockPos pos, BlockState state) {
            for (Dinleyici d : dinleyiciler) {
                d.afterBlockBreak(world, player, pos, state);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKirma(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        Player oyuncu = event.getPlayer();
        AFTER.calistir(oyuncu.level(), oyuncu, event.getPos(), event.getState());
    }
}
