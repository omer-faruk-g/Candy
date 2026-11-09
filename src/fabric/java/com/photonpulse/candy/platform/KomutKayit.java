package com.photonpulse.candy.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Komut kaydi — Fabric uygulamasi.
 */
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
        private Kanal() {}

        public void register(Dinleyici dinleyici) {
            CommandRegistrationCallback.EVENT.register(dinleyici::komutlariKaydet);
        }
    }
}
