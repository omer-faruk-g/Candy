package com.photonpulse.candy.platform;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sohbet olaylari — Fabric uygulamasi.
 *
 * Fabric PlayerChatMessage tasiyor, NeoForge tasimiyor; o yuzden sarmalayici
 * mesaj nesnesi yerine cagri yerlerinin gercekten okudugu iki icerigi
 * tasiyor: imzali ham metin ve suslenmis bilesen. Fabric'in ucuncu
 * parametresi (ChatType.Bound) dusuruldu — NeoForge'da karsiligi yok ve hicbir
 * cagri yeri kullanmiyor. Gerekce: MULTILOADER.md.
 */
public final class SohbetOlaylari {

    public static final Kanal CHAT_MESSAGE = new Kanal();

    private SohbetOlaylari() {}

    @FunctionalInterface
    public interface Dinleyici {
        /**
         * @param gonderen     mesaji yazan oyuncu
         * @param hamMetin     oyuncunun yazdigi imzali metin (signedContent)
         * @param sunulanMetin sohbete dusen suslenmis bilesen (decoratedContent)
         */
        void onChatMessage(ServerPlayer gonderen, String hamMetin, Component sunulanMetin);
    }

    public static final class Kanal {
        private Kanal() {}

        public void register(Dinleyici dinleyici) {
            ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                dinleyici.onChatMessage(sender, message.signedContent(), message.decoratedContent()));
        }
    }
}
