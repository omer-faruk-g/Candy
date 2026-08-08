package com.photonpulse.candy.mixin.client;

import com.photonpulse.candy.client.CandyMixinConfig;
import com.photonpulse.candy.client.IntensityState;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Random;

/**
 * ChatHudMixin — gelen chat mesajlarını bozar (v1.5.0-mixin).
 *
 * Intensity yüksekken (>0.5) gelen mesajlarda %5 ihtimalle rastgele iki harf yer değişir.
 * "hello there" → "hlelo there"
 * Vanilla API ile yapılamaz — chat mesajı Text objesi olarak client-side'a geldiğinde
 * ChatHud.addMessage'a girer, bu hook o noktada araya girer.
 */
@Mixin(ChatHud.class)
public class ChatHudMixin {

    private static final Random CANDY_RNG = new Random();

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true)
    private Text candy$corruptMessage(Text original) {
        try {
            if (original == null) return original;
            if (!CandyMixinConfig.chatCorruption) return original;
            double intensity = IntensityState.get();
            if (intensity < 0.5) return original;
            if (CANDY_RNG.nextDouble() > 0.05 * intensity) return original;

            // Sibling'i olan (formatlı, çok parçalı) mesajlara dokunma — format kaybolur.
            // Genelde vanilla chat mesajları çok parçalıdır (<Steve> + mesaj), sistem
            // mesajları düz literal olur. Sadece düz olanları bozalım.
            if (!original.getSiblings().isEmpty()) return original;

            String metin = original.getString();
            if (metin.length() < 4) return original;

            char[] karakter = metin.toCharArray();
            int i1 = CANDY_RNG.nextInt(karakter.length);
            int i2 = CANDY_RNG.nextInt(karakter.length);
            char geciciKarakter = karakter[i1];
            karakter[i1] = karakter[i2];
            karakter[i2] = geciciKarakter;

            MutableText yeni = Text.literal(new String(karakter));
            yeni.setStyle(original.getStyle());
            return yeni;
        } catch (Throwable t) {
            return original;
        }
    }
}
