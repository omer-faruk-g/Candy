package com.photonpulse.candy.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sohbet olaylari — NeoForge uygulamasi.
 *
 * Fabric'teki ile AYNI sinif ve paket adi.
 *
 * ZAMANLAMA FARKI: Fabric'in CHAT_MESSAGE'i mesaj dagitildiktan SONRA bir
 * bildirim, NeoForge'un ServerChatEvent'i dagitimdan ONCE ve iptal edilebilir.
 * LOWEST oncelik + iptal kontrolu ile "dagitilacagi kesinlesmis mesaj"a en
 * yakin ana cekildi; bizden sonra takilan bir mod hala iptal edebilir.
 * Uc cagri yerinin hicbiri mesaji iptal etmiyor ve hicbiri dagitim sirasina
 * bagli degil (en hizlisi cevabi 5 sn sonraya kuyruga atiyor).
 *
 * getRawText() ~ signedContent(), getMessage() ~ decoratedContent(); ikisi de
 * ayni pakette ve ayni anlamda tasiniyor, ChatType.Bound karsiligi yok.
 */
@EventBusSubscriber
public final class SohbetOlaylari {

    public static final Kanal CHAT_MESSAGE = new Kanal();

    private SohbetOlaylari() {}

    @FunctionalInterface
    public interface Dinleyici {
        /**
         * @param gonderen     mesaji yazan oyuncu
         * @param hamMetin     oyuncunun yazdigi ham metin (getRawText)
         * @param sunulanMetin sohbete dusen suslenmis bilesen (getMessage)
         */
        void onChatMessage(ServerPlayer gonderen, String hamMetin, Component sunulanMetin);
    }

    public static final class Kanal {
        private final List<Dinleyici> dinleyiciler = new CopyOnWriteArrayList<>();

        private Kanal() {}

        public void register(Dinleyici dinleyici) {
            dinleyiciler.add(dinleyici);
        }

        void calistir(ServerPlayer gonderen, String hamMetin, Component sunulanMetin) {
            for (Dinleyici d : dinleyiciler) {
                d.onChatMessage(gonderen, hamMetin, sunulanMetin);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSohbet(ServerChatEvent event) {
        if (event.isCanceled()) return;
        CHAT_MESSAGE.calistir(event.getPlayer(), event.getRawText(), event.getMessage());
    }
}
