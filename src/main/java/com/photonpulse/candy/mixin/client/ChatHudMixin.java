package com.photonpulse.candy.mixin.client;

import com.photonpulse.candy.client.CandyMixinConfig;
import com.photonpulse.candy.client.IntensityState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Random;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * ChatHudMixin — gelen chat mesajlarını bozar (v1.5.0-mixin).
 *
 * Intensity yüksekken (>0.5) gelen mesajlarda %5 ihtimalle rastgele iki harf yer değişir.
 * "hello there" → "hlelo there"
 * Vanilla API ile yapılamaz — chat mesajı Text objesi olarak client-side'a geldiğinde
 * ChatComponent.addMessage'a girer, bu hook o noktada araya girer.
 *
 * Hedef tanımlayıcı sürüme bağlı (javap ile ölçüldü):
 *   1.21.x  addMessage(Component)
 *   26.x    addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag)
 * 26.x'te tek parametreli aşırı yükleme kalmadı (çağrı yolları addClientSystemMessage /
 * addServerSystemMessage / addPlayerMessage'a ayrıldı), ama hepsi hâlâ bu tek private
 * metottan geçiyor — yani süzgeç noktası aynı. @ModifyVariable(argsOnly = true) ilk
 * Component argümanını hedeflediği için gövde de imza da aynen kalıyor.
 */
@Mixin(ChatComponent.class)
public class ChatHudMixin {

    private static final Random CANDY_RNG = new Random();

    //? if >=26 {
    /*@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true)
    */
    //?}
    //? if <26 {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), argsOnly = true)
    //?}
    private Component candy$corruptMessage(Component original) {
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

            MutableComponent yeni = Component.literal(new String(karakter));
            yeni.setStyle(original.getStyle());
            return yeni;
        } catch (Throwable t) {
            return original;
        }
    }
}
