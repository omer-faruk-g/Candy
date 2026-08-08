package com.photonpulse.candy.mixin.client;

import com.photonpulse.candy.client.CandyMixinConfig;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * DeathScreenMixin — ölüm ekranı başlığını Candy sözüyle değiştirir (v1.5.0-mixin).
 *
 * "You Died!" yerine %25 ihtimalle Candy'nin bir sözü gösterilir.
 * Ölüm ekranı sadece kısa süre açık olduğu için etki güçlü.
 */
@Mixin(DeathScreen.class)
public class DeathScreenMixin {

    private static final Random CANDY_RNG = new Random();

    private static final String[] CANDY_SOZLERI = {
        "Yine mi?",
        "Bu kaçıncı",
        "Sana söylemiştim",
        "Beni izliyor musun?",
        "Uyandığında konuşuruz",
        "Denek 239",
        "Ne kadar süredir buradasın",
        "Bir daha dene"
    };

    @Shadow @Final @Mutable
    private Text message;

    @Inject(method = "init", at = @At("HEAD"))
    private void candy$replaceTitle(CallbackInfo ci) {
        try {
            if (!CandyMixinConfig.deathScreen) return;
            if (CANDY_RNG.nextDouble() < 0.25) {
                this.message = Text.literal(
                    CANDY_SOZLERI[CANDY_RNG.nextInt(CANDY_SOZLERI.length)]);
            }
        } catch (Throwable ignored) {}
    }
}
