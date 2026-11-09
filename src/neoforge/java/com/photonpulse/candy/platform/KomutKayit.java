package com.photonpulse.candy.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Komut kaydi — NeoForge uygulamasi.
 *
 * RegisterCommandsEvent birebir karsilik: dispatcher, build context ve
 * CommandSelection ucu de olayin uzerinde.
 */
@EventBusSubscriber
public final class KomutKayit {

    public static final Kanal EVENT = new Kanal();

    private KomutKayit() {}

    @FunctionalInterface
    public interface Dinleyici {
        void komutlariKaydet(CommandDispatcher<CommandSourceStack> dispatcher,
                             CommandBuildContext access,
                             Commands.CommandSelection env);
    }

    public static final class Kanal {
        private final List<Dinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private Kanal() {}

        public void register(Dinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        void calistir(CommandDispatcher<CommandSourceStack> dispatcher,
                      CommandBuildContext access, Commands.CommandSelection env) {
            for (Dinleyici d : dinleyiciler) {
                d.komutlariKaydet(dispatcher, access, env);
            }
        }
    }

    @SubscribeEvent
    public static void onKomutKayit(RegisterCommandsEvent event) {
        EVENT.calistir(event.getDispatcher(), event.getBuildContext(),
                       event.getCommandSelection());
    }
}
