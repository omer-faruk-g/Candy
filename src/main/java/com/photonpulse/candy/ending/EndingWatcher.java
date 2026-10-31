package com.photonpulse.candy.ending;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.net.CamscreenPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * EndingWatcher (v1.9.2) — candyOzguven 100'e ulaşınca ending tetikler.
 *
 * "Candy tamamen sen olursa oyun biter" — ozguven cap'te → Candy senin yerine geçer.
 * Efekt: 5 sn full-screen "SEN ARTIK BENSİN" + warden death sound + broadcast mesaj.
 * Tek sefer tetiklenir (endingTetiklendi flag).
 */
public class EndingWatcher {

    private static final int TICK_ARALIGI = 40; // 2 sn

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % TICK_ARALIGI != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                try {
                    TruePersonality tp = PersonalityManager.get(player);
                    if (tp.endingTetiklendi) continue;
                    if (tp.candyOzguven < 100.0) continue;
                    tetikle(player, tp);
                } catch (Throwable t) {
                    Candy.LOG.debug("[Candy] ending kontrol hata: {}", t.getMessage());
                }
            }
        });
        Candy.LOG.info("[Candy] EndingWatcher aktif — candyOzguven=100 → sen artık Candy'sin");
    }

    private static void tetikle(ServerPlayerEntity player, TruePersonality tp) {
        tp.endingTetiklendi = true;
        ServerWorld world = (ServerWorld) player.getWorld();

        // Warden death sesi — dramatik son
        world.playSound(null, player.getBlockPos(),
            SoundEvents.ENTITY_WARDEN_DEATH, SoundCategory.HOSTILE, 1.5f, 0.6f);

        // 5 sn full-screen camscreen text index 6 (ending mesajı)
        try {
            ServerPlayNetworking.send(player, new CamscreenPayload(6, 5000));
        } catch (Throwable ignored) {}

        // Server-wide broadcast — herkes duyar
        String isim = player.getName().getString();
        Text msg = Text.literal(isim + " artık Candy.").formatted(Formatting.DARK_RED, Formatting.BOLD);
        player.getWorld().getServer().getPlayerManager().broadcast(msg, false);

        Candy.LOG.info("[Candy] ENDING → {} artık Candy (ozguven=100)", isim);
    }
}
