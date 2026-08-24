package com.photonpulse.candy.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * DebugHudMixin (v3.0 Faz 1.1) — F3 spoof.
 *
 * getLeftText() → çıktıyı tara. "candy:mirror" veya "candy:anti_mirror" içeren satırlar
 * "minecraft:overworld" ile değiştirilir. Ayrıca Y koordinatı Mirror'da -250000 gösterilir.
 */
@Mixin(DebugHud.class)
public class DebugHudMixin {

    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
    private void candy$f3Spoof(CallbackInfoReturnable<List<String>> cir) {
        try {
            List<String> original = cir.getReturnValue();
            if (original == null || original.isEmpty()) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return;

            String dimId = mc.world.getRegistryKey().getValue().toString();
            boolean mirror = dimId.equals("candy:mirror");
            boolean antiMirror = dimId.equals("candy:anti_mirror");
            boolean ayna = dimId.equals("candy:ayna");
            // Ayna KUSURSUZ kılık: F3 overworld der, Y hilesi YOK (Mirror'dan farkı bu)
            if (!mirror && !antiMirror && !ayna) return;

            List<String> yeni = new ArrayList<>(original.size());
            for (String satir : original) {
                if (satir == null) { yeni.add(satir); continue; }

                String s = satir;
                s = s.replace("candy:mirror", "minecraft:overworld");
                s = s.replace("candy:anti_mirror", "minecraft:overworld");
                s = s.replace("candy:ayna", "minecraft:overworld");

                // Y koordinatını -250000 göster (XYZ: satırı)
                if (mirror && s.startsWith("XYZ:")) {
                    try {
                        // Format: "XYZ: %.3f / %.5f / %.3f" — Y'yi bul ve değiştir
                        String[] parts = s.split(" / ");
                        if (parts.length == 3) {
                            s = parts[0] + " / -250000.00000 / " + parts[2];
                        }
                    } catch (Throwable ignored) {}
                }

                yeni.add(s);
            }
            cir.setReturnValue(yeni);
        } catch (Throwable ignored) {}
    }
}
