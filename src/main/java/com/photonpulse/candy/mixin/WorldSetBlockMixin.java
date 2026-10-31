package com.photonpulse.candy.mixin;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.dimension.DunyaKlon;
import com.photonpulse.candy.dimension.KurulDunya;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WorldSetBlockMixin — "Klon" dünyasının çekirdek mekaniği: <b>ne yaparsan her chunk'a yansır</b>.
 *
 * <p>Kullanıcı "blok koyma kırma ateş yakma fln HERŞEY DAHİL" dedi. Fabric'in olay API'sinde
 * yalnızca blok KIRMA için temiz bir kanca var (yerleştirme/ateş yok). Bunların hepsini
 * yakalamanın doğru yolu, tüm blok değişikliklerinin aktığı tek noktaya — {@code World
 * .setBlockState(BlockPos, BlockState, int, int)} — takılmaktır. Oyuncu, ateş, akışkan,
 * redstone: hepsi buradan geçer.</p>
 *
 * <p>YENİDEN GİRİŞ KİLİDİ: {@link DunyaKlon#guncelle} değişikliği bütün chunk'lara yayarken
 * yine {@code setBlockState} çağırır → bu mixin tekrar tetiklenir → sonsuz özyineleme. Yayma
 * sırasında {@link DunyaKlon} bir {@code ThreadLocal} bayrak set eder; bu mixin bayrağı
 * görürse sessizce döner.</p>
 */
@Mixin(World.class)
public class WorldSetBlockMixin {

    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("RETURN")
    )
    private void candy$klonYansit(BlockPos pos, BlockState state, int flags, int maxDepth,
                                  CallbackInfoReturnable<Boolean> cir) {
        try {
            Boolean ok = cir.getReturnValue();
            if (ok == null || !ok) return;                       // değişiklik olmadı
            if (DunyaKlon.yayiliyorMu()) return;                 // kendi yaydığımız değişiklik
            if (!((Object) this instanceof ServerWorld world)) return;
            if (!world.getRegistryKey().equals(KurulDunya.KLON.anahtar)) return;
            DunyaKlon.guncelle(world, pos.toImmutable(), state);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] KlonMixin hata: {}", t.getMessage());
        }
    }
}
