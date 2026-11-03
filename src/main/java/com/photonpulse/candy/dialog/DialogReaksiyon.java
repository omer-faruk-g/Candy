package com.photonpulse.candy.dialog;

import com.photonpulse.candy.core.TruePersonality;
import java.util.Random;
import net.minecraft.network.chat.Component;

/**
 * DialogReaksiyon — oyuncunun cevabına Candy'nin verdiği tepki.
 *
 * v0.9+: Text.translatable ile lang dosyalarından. Her oyuncu kendi dilinde görür.
 * Cevap keyword/uzunluk analizine göre reaksiyon kategorisi seçilir.
 */
public class DialogReaksiyon {

    private static final Random RNG = new Random();

    private static final int NO_N = 5;
    private static final int YES_N = 5;
    private static final int LONG_N = 4;
    private static final int SHORT_N = 5;
    private static final int CURSE_N = 5;
    private static final int QUESTION_N = 4;
    private static final int FEAR_N = 4;
    private static final int GENERAL_N = 5;

    /**
     * Cevap + TruePersonality'ye göre reaksiyon üret.
     * null dönerse reaksiyon yerleştirme atlanır.
     */
    public static Component reaksiyonUret(String cevap, TruePersonality tp) {
        if (cevap == null) return null;
        String d = cevap.toLowerCase().trim();
        if (d.isEmpty() || d.equals("<sessiz>")) return Component.translatable("candy.reaction.silent");

        // Yüksek korku → korku-özel tepki
        if (tp.korkuSkoru > 60 && RNG.nextDouble() < 0.4)
            return Component.translatable("candy.reaction.fear." + RNG.nextInt(FEAR_N));

        // Küfür/tehdit (TR+EN)
        if (d.contains("siktir") || d.contains("piç") || d.contains("defol")
            || d.contains(" git") || d.startsWith("git")
            || d.contains("fuck") || d.contains("shit") || d.contains("go away")
            || d.contains("leave"))
            return Component.translatable("candy.reaction.curse." + RNG.nextInt(CURSE_N));

        // İnkar
        if (d.contains("hayır") || d.contains("hayir") || d.contains("yok")
            || d.contains("korkmuyorum") || d.contains("değilim")
            || d.contains("no") || d.contains("not afraid") || d.contains("nope"))
            return Component.translatable("candy.reaction.no." + RNG.nextInt(NO_N));

        // Onaylama
        if (d.contains("evet") || d.contains("korkuyorum") || d.contains("belki")
            || d.contains("doğru")
            || d.contains("yes") || d.contains("scared") || d.contains("afraid"))
            return Component.translatable("candy.reaction.yes." + RNG.nextInt(YES_N));

        // Soru
        if (d.contains("neden") || d.contains("nasıl") || d.contains("kim")
            || d.contains("nesin") || d.contains("kimsin")
            || d.contains("why") || d.contains("who") || d.contains("how")
            || d.contains("what are you")
            || d.endsWith("?"))
            return Component.translatable("candy.reaction.question." + RNG.nextInt(QUESTION_N));

        // Uzunluk
        if (d.length() > 30) return Component.translatable("candy.reaction.long." + RNG.nextInt(LONG_N));
        if (d.length() < 8) return Component.translatable("candy.reaction.short." + RNG.nextInt(SHORT_N));

        return Component.translatable("candy.reaction.general." + RNG.nextInt(GENERAL_N));
    }
}
