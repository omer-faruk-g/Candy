package com.photonpulse.candy.sign;

import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.tree.TreeNode;

import java.util.List;
import java.util.Random;

/**
 * SignTemplates — tabelalara yazılacak metin.
 *
 * Değişim (v0.2): Sabit "Persona.yaziStili()" yok.
 * Artık baskın TreeNode'un trait vektörüne göre stil ÜRETİLİYOR.
 *
 * Örneğin:
 *  - inkarEgilimi yüksek trait → doğrudan, sert ("burada değilsin.")
 *  - aciklik yüksek trait → uzun, sorgulayıcı ("neden burada olduğunu bilmiyorum.")
 *  - kesfetmeIstegi yüksek trait → soru sorucu ("nereye bakıyorsun?")
 */
public class SignTemplates {

    private static final Random RNG = new Random();

    // ── Kurulum soruları ──
    public static final List<String> KURULUM_SORULARI = List.of(
        "Buraya neden geldin.",
        "Bir şeyden korkuyor musun.",
        "Yalnız mısın.",
        "Neden burada."
    );

    // ── Gözlem havuzları ──
    private static final List<String> GOZLEM_KAC = List.of(
        "Yine kaçıyorsun",
        "Neden koşuyorsun",
        "Sürekli kaçıyorsun"
    );

    private static final List<String> GOZLEM_SAKLAN = List.of(
        "Burada güvende hissediyorsun",
        "Yine buradasın",
        "Köşeler seni korumaz"
    );

    private static final List<String> GOZLEM_KESFET = List.of(
        "Meraklısın",
        "Nereye bakıyorsun",
        "Bunu daha önce görmemiştin"
    );

    private static final List<String> GOZLEM_SESSIZ = List.of(
        "Cevap vermedin",
        "Sessizsin",
        "Duyduğunu biliyorum"
    );

    /**
     * TruePersonality'ye göre gözlem cümlesi seç, baskın node'un stiline göre şekillendir.
     */
    public static String gozlemUret(TruePersonality tp, TreeNode baskin) {
        String temel;
        double en = Math.max(tp.kacOrani, Math.max(tp.donOrani, tp.kesfetOrani));
        if (en == tp.kacOrani && en > 0.3) temel = rastgele(GOZLEM_KAC);
        else if (en == tp.donOrani && en > 0.3) temel = rastgele(GOZLEM_SAKLAN);
        else if (en == tp.kesfetOrani && en > 0.3) temel = rastgele(GOZLEM_KESFET);
        else temel = rastgele(GOZLEM_SESSIZ);

        return traitTabanliStil(temel, baskin);
    }

    /**
     * Node'un trait'ine göre cümleyi şekillendir.
     * Sabit "AVCI → küçük harf" gibi kuralları yok.
     * Baskın trait ne diyor, ona göre eklenti/dönüşüm.
     */
    private static String traitTabanliStil(String temel, TreeNode baskin) {
        if (baskin == null || baskin.trait == null) return temel + ".";

        double inkar = baskin.trait.getOrDefault("inkarEgilimi", 0.0);
        double aciklik = baskin.trait.getOrDefault("aciklik", 0.0);
        double merak = baskin.trait.getOrDefault("kesfetmeIstegi", 0.0);
        double panik = baskin.trait.getOrDefault("panikEsigi", 0.0);
        double kac = baskin.trait.getOrDefault("kacOrani", 0.0);

        StringBuilder sb = new StringBuilder(temel);

        // Merak → soru işareti eklemeye eğilimli
        if (merak > 0.5 && RNG.nextDouble() < merak) {
            sb.append("?");
        }
        // İnkar → nokta, sert
        else if (inkar > 0.4) {
            sb.append(".");
        }
        // Açıklık → ek cümle
        else if (aciklik > 0.5 && RNG.nextDouble() < 0.5) {
            sb.append(".");
            sb.append(RNG.nextBoolean() ? " bilmiyorum." : " ama bilmek istiyorum.");
        }
        // Panik → üç nokta
        else if (panik > 0.5) {
            sb.append("...");
        }
        else {
            sb.append(".");
        }

        // Kaçma yüksekse → küçük harf (fısıltı gibi)
        if (kac > 0.5) return sb.toString().toLowerCase();

        return sb.toString();
    }

    /**
     * Kişisel atıf — oyuncunun geçmiş verisine dokunur.
     */
    public static String kisiselAtif(TruePersonality tp, TreeNode baskin) {
        String temel;
        double r = RNG.nextDouble();
        if (tp.geceKonumu != null && r < 0.35) {
            temel = "Dün de oraya gittin";
        } else if (tp.enSikEkipman() != null && r < 0.6) {
            String ekipman = tp.enSikEkipman();
            String kisa = ekipman.substring(ekipman.indexOf(':') + 1);
            temel = "Sürekli " + kisa + " taşıyorsun";
        } else if (tp.tabelaCevaplari.size() > 3) {
            temel = "Bana yalan söyledin";
        } else {
            temel = "Seni izliyorum";
        }
        return traitTabanliStil(temel, baskin);
    }

    /**
     * Hangi kurulum sorusunu koyacağını belirler.
     */
    public static String siradakiKurulumSorusu(TruePersonality tp) {
        int index = tp.tabelaCevaplari.size();
        if (index >= KURULUM_SORULARI.size()) return null;
        return KURULUM_SORULARI.get(index);
    }

    /**
     * Oyuncunun tabelaya yazdığı cevabı analiz et. Anlık TruePersonality günceller.
     */
    public static void cevapAnaliz(String cevap, TruePersonality tp) {
        String d = cevap.toLowerCase().trim();

        if (d.isEmpty()) {
            tp.aciklik = TruePersonality.kayma(tp.aciklik, 0.0, 0.3);
            tp.tabelaCevaplari.add("<sessiz>");
            return;
        }

        double uzunlukHedef = Math.min(1.0, d.length() / 40.0);
        tp.aciklik = TruePersonality.kayma(tp.aciklik, uzunlukHedef, 0.4);

        if (d.contains("hayır") || d.contains("yok") || d.contains("korkmuyorum")
            || d.contains("hayir")) {
            tp.inkarEgilimi = TruePersonality.kayma(tp.inkarEgilimi, 1.0, 0.3);
        }
        if (d.contains("evet") || d.contains("korkuyorum") || d.contains("belki")) {
            tp.inkarEgilimi = TruePersonality.kayma(tp.inkarEgilimi, 0.0, 0.2);
            tp.aciklik = TruePersonality.kayma(tp.aciklik, 1.0, 0.2);
        }
        if (d.contains("neden") || d.contains("kim") || d.contains("nasıl")
            || d.contains("kimsin")) {
            tp.kesfetmeIstegi = TruePersonality.kayma(tp.kesfetmeIstegi, 1.0, 0.2);
        }
        if (d.contains("siktir") || d.contains("piç") || d.contains("git")
            || d.contains("defol")) {
            tp.saldirOrani = TruePersonality.kayma(tp.saldirOrani, 1.0, 0.15);
        }

        tp.tabelaCevaplari.add(cevap);
    }

    private static String rastgele(List<String> liste) {
        return liste.get(RNG.nextInt(liste.size()));
    }
}
