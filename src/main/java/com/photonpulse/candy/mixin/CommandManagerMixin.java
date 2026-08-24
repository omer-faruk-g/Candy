package com.photonpulse.candy.mixin;

import com.mojang.brigadier.ParseResults;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * CommandManagerMixin (v3.0 Phase 3.10) — Zihin Okuma / Command Lock.
 *
 * Player tarafından çalıştırılan komutlarda: kök komutun requirement'ı level 0 permission
 * ile geçmiyorsa (yani cheat komutu), sessizce cancel edilir + Candy mesajı gösterilir.
 *
 * Hiçbir false positive: Sodium/Iris gibi mod'ların Level 0 komutları takılmaz.
 */
@Mixin(CommandManager.class)
public class CommandManagerMixin {

    private static final Random CANDY_RNG = new Random();
    private static final String[] MESAJLAR = {
        "candy.komut.mesaj.1",
        "candy.komut.mesaj.2",
        "candy.komut.mesaj.3",
        "candy.komut.mesaj.4",
        "candy.komut.mesaj.5"
    };

    // Geliştirici bypass — SADECE oyunu başlatan kişi -Dcandy.nolock=true ile açabilir.
    // Oyuncu (sunucuda dahi) bunu değiştiremez → in-game exploit yok.
    private static final boolean DEV_BYPASS = Boolean.getBoolean("candy.nolock");

    @Inject(
        method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void candy$permissionLock(
        ParseResults<ServerCommandSource> parseResults,
        String command,
        CallbackInfo ci
    ) {
        try {
            if (DEV_BYPASS) return; // geliştirici JVM flag'i — oyuncu erişemez
            ServerCommandSource source = parseResults.getContext().getSource();
            if (!(source.getEntity() instanceof ServerPlayerEntity player)) return;

            // v3.0.0-alpha7: kurulumTamam şartı KALDIRILDI — Command Lock dünyaya
            // girer girmez aktif (cheat'ler baştan engellenir). Kurulum kendisi komut
            // gerektirmiyor (tabela yerleştirme), o yüzden gate'e gerek yok.
            TruePersonality tp = PersonalityManager.get(player);

            var nodes = parseResults.getContext().getNodes();
            if (nodes.isEmpty()) return;

            // TÜM parse edilen node'ları kontrol et — herhangi biri level-0'da geçmiyorsa cheat.
            ServerCommandSource level0 = source.withLevel(0);
            boolean cheatKomutu = false;
            for (var parsed : nodes) {
                try {
                    if (!parsed.getNode().getRequirement().test(level0)) {
                        cheatKomutu = true;
                        break;
                    }
                } catch (Throwable ignored) {
                    // Bu node'un requirement'ı level-based değil — atla
                }
            }

            Candy.LOG.info("[Candy] CmdLock: player={} cmd='{}' kurulum={} cheat={}",
                player.getName().getString(), command, tp.kurulumTamam, cheatKomutu);

            if (!cheatKomutu) return; // Zararsız komut, dokunma

            // Cheat komutu — engelle
            String key = MESAJLAR[CANDY_RNG.nextInt(MESAJLAR.length)];
            player.sendMessage(Text.translatable(key)
                .formatted(Formatting.DARK_RED, Formatting.ITALIC), false);

            tp.candyOzguven = Math.min(100, tp.candyOzguven + 5);
            tp.korkuSkoru = Math.min(100, tp.korkuSkoru + 3);

            if (!tp.komutEngellemesiGorulduMu) {
                tp.komutEngellemesiGorulduMu = true;
                CandyBasarimlar.tetikle(player, "zihnimi_okudun");
            }

            Candy.LOG.info("[Candy] Komut engellendi: {} → {}",
                player.getName().getString(), command);

            ci.cancel();
        } catch (Throwable t) {
            // Hata olursa vanilla akışı bozma
            Candy.LOG.debug("[Candy] CommandManagerMixin hata: {}", t.getMessage());
        }
    }
}
