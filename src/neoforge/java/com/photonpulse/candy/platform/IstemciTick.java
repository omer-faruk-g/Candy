package com.photonpulse.candy.platform;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Istemci tick kancasi — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi. ClientTickEvent.Post istemciyi
 * tasimiyor; Fabric'in verdigi Minecraft ornegi yerine Minecraft.getInstance()
 * kullaniliyor — ayni tekil ornek, uydurma degil.
 *
 * Dist.CLIENT ile sinirli: sunucu tarafinda bu sinif hic yuklenmemeli.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class IstemciTick {

    public static final Kanal END_CLIENT_TICK = new Kanal();

    private IstemciTick() {}

    public static final class Kanal {
        private final List<Consumer<Minecraft>> dinleyiciler = new CopyOnWriteArrayList<>();

        private Kanal() {}

        public void register(Consumer<Minecraft> dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        void calistir(Minecraft istemci) {
            for (Consumer<Minecraft> d : dinleyiciler) {
                d.accept(istemci);
            }
        }
    }

    @SubscribeEvent
    public static void onIstemciTick(ClientTickEvent.Post event) {
        END_CLIENT_TICK.calistir(Minecraft.getInstance());
    }
}
