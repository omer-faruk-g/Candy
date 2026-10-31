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

        final String ham = msg.getSignedContent();
        String metin = ham.toLowerCase(Locale.ROOT);
        if (metin.isEmpty()) return;

        // v8.1: oyuncu ikilik şifreyi çözüp buraya yazdı mı? Jeton büyük/küçük harf
        // duyarsız eşleşiyor ama ham metin veriliyor — lowercase'lemek gerekmiyor.
        // v8.3: TUZAK kontrolü gerçek şifreden ÖNCE. Tuzak cevabı SifreIzleyici'ye
        // girerse "yanlış satır" sayılır ve Candy'nin özgüveni YÜKSELİR — olması
        // gerekenin tam tersi (tuzağı görülen Candy küçülür).
        if (com.photonpulse.candy.sifre.TuzakYayini.kontrol(sender, tp, ham)) return;
        com.photonpulse.candy.sifre.SifreIzleyici.kontrol(sender, tp, ham, "sohbet");

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

        // ── v3.2 (idea 9) Dil Modeli — oyuncunun kendi kelimelerini öğren ──
        dilOgren(tp, metin);
        // Nadiren, zengin kelime dağarcığı varsa Candy senin kelimeni geri söyler
        long simdi = sender.getWorld().getTime();
        if (tp.ogrenilenKelimeler.size() >= 6 && simdi - tp.sonDilKullanimi > 3600
                && tp.bossOldurulmeTick == 0 && Math.random() < 0.10) {
            String kelime = enSikKelime(tp);
            if (kelime != null) {
                tp.sonDilKullanimi = simdi;
                sender.sendMessage(net.minecraft.text.Text.translatable(
                        "candy.dil." + (int) (Math.random() * 2), kelime)
                    .formatted(net.minecraft.util.Formatting.DARK_GRAY,
                               net.minecraft.util.Formatting.ITALIC), false);
                Candy.LOG.info("[Candy] Dil modeli[{}] senin kelimen → '{}'",
                    sender.getName().getString(), kelime);
            }
        }
    }

    // Kısa/işlevsel kelimeleri ele — sadece anlamlı sözcükleri öğren
    private static final java.util.Set<String> DURAK = java.util.Set.of(
        "bir", "bu", "şu", "için", "ama", "gibi", "daha", "çok", "ben", "sen",
        "the", "and", "you", "for", "with", "that", "this", "have", "not", "are");

    /** Chat metninden anlamlı kelimeleri çıkar → ogrenilenKelimeler (kelime→sayı). */
    private static void dilOgren(TruePersonality tp, String metin) {
        for (String kelime : metin.split("[^\\p{L}]+")) {
            if (kelime.length() < 4 || DURAK.contains(kelime)) continue;
            tp.ogrenilenKelimeler.merge(kelime, 1, Integer::sum);
        }
        // Dağarcığı sınırla — en seyrek kullanılanları at (en fazla 50)
        if (tp.ogrenilenKelimeler.size() > 50) {
            tp.ogrenilenKelimeler.entrySet().stream()
                .min(java.util.Map.Entry.comparingByValue())
                .ifPresent(e -> tp.ogrenilenKelimeler.remove(e.getKey()));
        }
    }

    /** En sık kullanılan öğrenilmiş kelime (yoksa null). */
    private static String enSikKelime(TruePersonality tp) {
        return tp.ogrenilenKelimeler.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse(null);
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
