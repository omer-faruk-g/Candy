package com.photonpulse.candy.mixin;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.travma.TotemIptali;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LivingEntityMixin (v3.0 Phase 3.11) — Totem İptali.
 *
 * tryUseDeathProtector (a.k.a. tryUseTotem) return true → totem aktivasyon başladı.
 * Player + kurulum tamam ise TotemIptali.baslat() ile Candy devreye giriyor:
 *   - Freeze frame 3 sn (slowness + blindness)
 *   - CandyDoppel spawn (4 blok karşıda)
 *   - Envanterdeki tüm totemler silinir
 *   - 3 sn sonra kick
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(
        method = "tryUseDeathProtector",
        at = @At("RETURN")
    )
    private void candy$totemIntercept(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        try {
            Boolean returned = cir.getReturnValue();
            if (returned == null || !returned) return;
            if (!((Object) this instanceof ServerPlayerEntity player)) return;

            TotemIptali.baslat(player);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] LivingEntityMixin totem hata: {}", t.getMessage());
        }
    }
}
