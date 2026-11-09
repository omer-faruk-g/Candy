package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * Oyuncu baglanti olaylari (istemci) — Fabric uygulamasi.
 *
 * Her dinleyici dogrudan Fabric'in kendi olayina baglaniyor; Fabric'in cagirma
 * sirasi aynen korunuyor.
 *
 * HANDLER VE SENDER DUSURULDU, yalnizca {@link Minecraft} kaldi. Fabric'in
 * imzasi JOIN'de {@code (handler, sender, client)}, DISCONNECT'te
 * {@code (handler, client)}; Candy'nin 5 cagrisinin hicbiri ilk iki parametreyi
 * okumuyor. NeoForge'da {@code ClientPlayerNetworkEvent.LoggingOut} aninda
 * {@code Minecraft.getConnection()} zaten null olabiliyor, yani handler'i
 * tasimak "bazen null, bazen degil" demekti — tasimiyoruz.
 */
public final class IstemciBaglanti {

    public static final GirisKanali JOIN = new GirisKanali();
    public static final CikisKanali DISCONNECT = new CikisKanali();

    private IstemciBaglanti() {}

    public static final class GirisKanali {
        private GirisKanali() {}

        public void register(Consumer<Minecraft> dinleyici) {
            ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> dinleyici.accept(client));
        }
    }

    public static final class CikisKanali {
        private CikisKanali() {}

        public void register(Consumer<Minecraft> dinleyici) {
            ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> dinleyici.accept(client));
        }
    }
}
