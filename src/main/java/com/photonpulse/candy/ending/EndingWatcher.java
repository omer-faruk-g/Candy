package com.photonpulse.candy.ending;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.net.CamscreenPayload;
import com.photonpulse.candy.platform.SunucuTick;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

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
        SunucuTick.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % TICK_ARALIGI != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
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

    private static void tetikle(ServerPlayer player, TruePersonality tp) {
        tp.endingTetiklendi = true;
        ServerLevel world = (ServerLevel) player.level();

        // Warden death sesi — dramatik son
        world.playSound(null, player.blockPosition(),
            SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.5f, 0.6f);

        // 5 sn full-screen camscreen text index 6 (ending mesajı)
        try {
            ServerPlayNetworking.send(player, new CamscreenPayload(6, 5000));
        } catch (Throwable ignored) {}

        // Server-wide broadcast — herkes duyar
        String isim = player.getName().getString();
        Component msg = Component.literal(isim + " artık Candy.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        player.level().getServer().getPlayerList().broadcastSystemMessage(msg, false);

        Candy.LOG.info("[Candy] ENDING → {} artık Candy (ozguven=100)", isim);
    }
}
