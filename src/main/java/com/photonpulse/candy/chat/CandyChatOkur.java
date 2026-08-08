package com.photonpulse.candy.chat;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;

/**
 * CandyChatOkur (v1.7.2) — Candy oyuncunun chat mesajlarını okur ve state günceller.
 *
 * ÖNEMLİ: Candy CHAT'E CEVAP YAZMAZ. Candy sadece tabelalardan konuşur.
 * Bu sınıf sadece player davranışını analiz eder ve sessizce state'i günceller:
 *
 *   - Candy'den bahis     → korku +5, özgüven +5 (bilinmek Candy'yi güçlendirir)
 *   - Küfür/öfke          → özgüven +10, kotuluk +3
 *   - Korku ifadesi       → korku +10, özgüven +3
 *   - İnkar               → memnuniyetsizlik +5 (ShadowTree'yi besler)
 *
 * Reaksiyon SignSystem üzerinden gelir — bir sonraki tabela cevabında Candy'nin
 * tonu değişmiş olur (korku yüksekse daha agresif, kotuluk yüksekse alaycı).
 */
public class CandyChatOkur {

    private static final String[] CANDY_ANAHTAR = {
        "candy", "onu gördüm", "arkamda", "birisi var", "izliyor", "biri takip"
    };
    private static final String[] KUFUR_ANAHTAR = {
        "amk", "aq", "sik", "orospu", "puşt", "yarrak", "sktir", "siktir",
        "mk", "salak", "gerizekali", "s.ktir"
    };
    private static final String[] KORKU_ANAHTAR = {
        "korkuyorum", "korktum", "yardım", "kaçıyorum", "of ya", "aman", "korkunç"
    };
    private static final String[] INKAR_ANAHTAR = {
        "yok bir şey", "hiç", "boşver", "hayır yok", "yalanmış"
    };

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((msg, sender, params) -> {
            try {
                analiz(msg, sender);
            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] chat analiz hata: {}", t.getMessage());
            }
        });
        Candy.LOG.info("[Candy] CandyChatOkur aktif — chat analiz, cevaplar tabelalardan");
    }

    private static void analiz(SignedMessage msg, ServerPlayerEntity sender) {
        TruePersonality tp = PersonalityManager.get(sender);
        if (!tp.kurulumTamam) return;

        String metin = msg.getSignedContent().toLowerCase(Locale.ROOT);
        if (metin.isEmpty()) return;

        if (icerir(metin, CANDY_ANAHTAR)) {
            tp.korkuSkoru = Math.min(100, tp.korkuSkoru + 5);
            tp.candyOzguven = Math.min(100, tp.candyOzguven + 5);
            Candy.LOG.debug("[Candy] chat: Candy bahsi → +korku +özgüven ({})",
                sender.getName().getString());
        }

        if (icerir(metin, KUFUR_ANAHTAR)) {
            tp.candyOzguven = Math.min(100, tp.candyOzguven + 10);
            tp.psikolojiKotulugu = Math.min(100, tp.psikolojiKotulugu + 3);
            Candy.LOG.debug("[Candy] chat: küfür → ++özgüven +kotuluk ({})",
                sender.getName().getString());
        }

        if (icerir(metin, KORKU_ANAHTAR)) {
            tp.korkuSkoru = Math.min(100, tp.korkuSkoru + 10);
            tp.candyOzguven = Math.min(100, tp.candyOzguven + 3);
            Candy.LOG.debug("[Candy] chat: korku → ++korku +özgüven ({})",
                sender.getName().getString());
        }

        if (icerir(metin, INKAR_ANAHTAR)) {
            tp.memnuniyetsizlik = Math.min(100, tp.memnuniyetsizlik + 5);
            Candy.LOG.debug("[Candy] chat: inkar → +memnuniyetsizlik ({})",
                sender.getName().getString());
        }
    }

    private static boolean icerir(String metin, String[] anahtarlar) {
        for (String a : anahtarlar) {
            if (metin.contains(a)) return true;
        }
        return false;
    }

    public static void sunucuKapan() {
        // Nothing to clean — no queue
    }
}
