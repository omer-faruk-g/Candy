package com.photonpulse.candy.sign;

import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.tree.TreeNode;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;

/**
 * SignTemplates — tabelalara yazılacak metin.
 *
 * v0.9+: Text.translatable ile lang dosyalarından okunur (en_us + tr_tr).
 * Sign metni translation key olarak saklandığı için her oyuncu kendi dilinde görür.
 */
public class SignTemplates {

    private static final Random RNG = new Random();

    // ── Kurulum soruları (translation keys) ──
    public static final List<String> KURULUM_SORULARI = List.of(
        "candy.setup.q1", "candy.setup.q2", "candy.setup.q3", "candy.setup.q4"
    );
    public static final String CEVAPLA_HINT = "candy.setup.answer_hint";

    // ── Gözlem havuzları (0-N indexleri lang'da var) ──
    private static final int GOZLEM_KAC_N = 3;
    private static final int GOZLEM_SAKLAN_N = 3;
    private static final int GOZLEM_KESFET_N = 3;
    private static final int GOZLEM_SESSIZ_N = 3;

    /**
     * Gözlem cümlesi — translatable Text döner (sign'a doğrudan konabilir).
     */
    public static Text gozlemUret(TruePersonality tp, TreeNode baskin) {
        String key;
        double en = Math.max(tp.kacOrani, Math.max(tp.donOrani, tp.kesfetOrani));
        if (en == tp.kacOrani && en > 0.3)
            key = "candy.observation.escape." + RNG.nextInt(GOZLEM_KAC_N);
        else if (en == tp.donOrani && en > 0.3)
            key = "candy.observation.hide." + RNG.nextInt(GOZLEM_SAKLAN_N);
        else if (en == tp.kesfetOrani && en > 0.3)
            key = "candy.observation.explore." + RNG.nextInt(GOZLEM_KESFET_N);
        else
            key = "candy.observation.silent." + RNG.nextInt(GOZLEM_SESSIZ_N);

        return traitTabanliStil(Text.translatable(key), baskin);
    }

    /**
     * Trait bazlı stil — text'in sonuna nokta/soru işareti ekler, küçük harf yapar vs.
     * Bu client-side'da lang string alınıp modifiye edilemez, o yüzden text'i "olduğu gibi"
     * kullanıp trait'e göre suffix Text ekleyip birleştiriyoruz.
     */
    private static Text traitTabanliStil(Text temel, TreeNode baskin) {
        if (baskin == null || baskin.trait == null) return Text.literal("").append(temel).append(".");

        double inkar = baskin.trait.getOrDefault("inkarEgilimi", 0.0);
        double aciklik = baskin.trait.getOrDefault("aciklik", 0.0);
        double merak = baskin.trait.getOrDefault("kesfetmeIstegi", 0.0);
        double panik = baskin.trait.getOrDefault("panikEsigi", 0.0);

        String suffix;
        if (merak > 0.5 && RNG.nextDouble() < merak) suffix = "?";
        else if (inkar > 0.4) suffix = ".";
        else if (panik > 0.5) suffix = "...";
        else if (aciklik > 0.5 && RNG.nextDouble() < 0.5) suffix = ".";
        else suffix = ".";

        return Text.literal("").append(temel).append(suffix);
    }

    /**
     * Kişisel atıf — oyuncunun geçmiş verisine dokunur.
     */
    public static Text kisiselAtif(TruePersonality tp, TreeNode baskin) {
        Text temel;
        double r = RNG.nextDouble();
        if (tp.geceKonumu != null && r < 0.35) {
            temel = Text.translatable("candy.attribution.night_location");
        } else if (tp.enSikEkipman() != null && r < 0.6) {
            String ekipman = tp.enSikEkipman();
            String kisa = ekipman.substring(ekipman.indexOf(':') + 1);
            temel = Text.translatable("candy.attribution.equipment", kisa);
        } else if (tp.tabelaCevaplari.size() > 3) {
            temel = Text.translatable("candy.attribution.lied");
        } else {
            temel = Text.translatable("candy.attribution.watching");
        }
        return traitTabanliStil(temel, baskin);
    }

    /**
     * Hangi kurulum sorusu translation key'i konulacak.
     */
    public static String siradakiKurulumSorusu(TruePersonality tp) {
        int index = tp.tabelaCevaplari.size();
        if (index >= KURULUM_SORULARI.size()) return null;
        return KURULUM_SORULARI.get(index);
    }

    /**
     * Oyuncunun tabelaya yazdığı cevabı analiz et — dile bakmadan çalışır.
     * Çünkü küçük harfe indirip Türkçe/İngilizce keyword'ler ARIYORUZ.
     */
    public static void cevapAnaliz(String cevap, TruePersonality tp) {
        String d = cevap.toLowerCase().trim();

        if (d.isEmpty()) {
            tp.aciklik = TruePersonality.kayma(tp.aciklik, 0.0, 0.3);
            tp.tabelaCevaplari.add("<sessiz>");
            hafizayiKirp(tp);
            return;
        }

        double uzunlukHedef = Math.min(1.0, d.length() / 40.0);
        tp.aciklik = TruePersonality.kayma(tp.aciklik, uzunlukHedef, 0.4);

        // Türkçe + İngilizce keyword'ler
        if (d.contains("hayır") || d.contains("hayir") || d.contains("yok")
            || d.contains("korkmuyorum") || d.contains("değilim")
            || d.contains("no") || d.contains("not afraid") || d.contains("nope")) {
            tp.inkarEgilimi = TruePersonality.kayma(tp.inkarEgilimi, 1.0, 0.3);
        }
        if (d.contains("evet") || d.contains("korkuyorum") || d.contains("belki")
            || d.contains("yes") || d.contains("scared") || d.contains("afraid")
            || d.contains("maybe")) {
            tp.inkarEgilimi = TruePersonality.kayma(tp.inkarEgilimi, 0.0, 0.2);
            tp.aciklik = TruePersonality.kayma(tp.aciklik, 1.0, 0.2);
        }
        if (d.contains("neden") || d.contains("kim") || d.contains("nasıl")
            || d.contains("kimsin") || d.contains("nesin")
            || d.contains("why") || d.contains("who") || d.contains("how")
            || d.contains("what are you")) {
            tp.kesfetmeIstegi = TruePersonality.kayma(tp.kesfetmeIstegi, 1.0, 0.2);
        }
        if (d.contains("siktir") || d.contains("piç") || d.contains("git")
            || d.contains("defol")
            || d.contains("fuck") || d.contains("shit") || d.contains("go away")
            || d.contains("leave")) {
            tp.saldirOrani = TruePersonality.kayma(tp.saldirOrani, 1.0, 0.15);
        }

        tp.tabelaCevaplari.add(cevap);
        hafizayiKirp(tp);
    }

    private static final int MAKS_CEVAP_HAFIZASI = 50;

    private static void hafizayiKirp(TruePersonality tp) {
        if (tp.tabelaCevaplari.size() <= MAKS_CEVAP_HAFIZASI) return;
        if (!tp.kurulumTamam) return;
        tp.tabelaCevaplari.remove(0);
    }
}
