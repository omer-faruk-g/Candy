package com.photonpulse.candy.mixin;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.dimension.DunyaKlon;
import com.photonpulse.candy.dimension.KurulDunya;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WorldSetBlockMixin — "Klon" dünyasının çekirdek mekaniği: <b>ne yaparsan her chunk'a yansır</b>.
 *
 * <p>Kullanıcı "blok koyma kırma ateş yakma fln HERŞEY DAHİL" dedi. Fabric'in olay API'sinde
 * yalnızca blok KIRMA için temiz bir kanca var (yerleştirme/ateş yok). Bunların hepsini
 * yakalamanın doğru yolu, tüm blok değişikliklerinin aktığı tek noktaya — {@code Level
 * .setBlock(BlockPos, BlockState, int, int)} — takılmaktır. Oyuncu, ateş, akışkan,
 * redstone: hepsi buradan geçer.</p>
 *
 * <p>YENİDEN GİRİŞ KİLİDİ: {@link DunyaKlon#guncelle} değişikliği bütün chunk'lara yayarken
 * yine {@code setBlock} çağırır → bu mixin tekrar tetiklenir → sonsuz özyineleme. Yayma
 * sırasında {@link DunyaKlon} bir {@code ThreadLocal} bayrak set eder; bu mixin bayrağı
 * görürse sessizce döner.</p>
 */
@Mixin(Level.class)
public class WorldSetBlockMixin {

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("RETURN")
    )
    private void candy$klonYansit(BlockPos pos, BlockState state, int flags, int maxDepth,
                                  CallbackInfoReturnable<Boolean> cir) {
        try {
            Boolean ok = cir.getReturnValue();
            if (ok == null || !ok) return;                       // değişiklik olmadı
            if (DunyaKlon.yayiliyorMu()) return;                 // kendi yaydığımız değişiklik
            if (!((Object) this instanceof ServerLevel world)) return;
            if (!world.dimension().equals(KurulDunya.KLON.anahtar)) return;
            DunyaKlon.guncelle(world, pos.immutable(), state);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] KlonMixin hata: {}", t.getMessage());
        }
    }
}
