package com.photonpulse.candy.chat;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * FakeMesajlar — Candy her zaman "oyunda". Sahte join/leave mesajları.
 *
 * Player JOIN'de %70 ihtimalle 3 sn gecikmeli "Candy joined the game".
 * Oyun sırasında rastgele aralıklarla (özgüven yüksekse sıklaşır) fake mesajlar.
 */
public class FakeMesajlar {

    private static final int TICK_ARALIGI = 200;                // 10 sn kontrol
    private static final long OYUNCU_COOLDOWN_TICK = 6000L;     // min 5 dk arayla
    private static final double MIN_KORKU = 30.0;
    private static final double BASE_IHTIMAL = 0.05;            // her kontrolde %5
    private static final double JOIN_FAKE_IHTIMAL = 0.7;        // join anında %70
    private static final int JOIN_GECIKME_TICK = 60;            // join sonrası 3 sn bekle

    private static int tickSayaci = 0;
    private static final Random RNG = new Random();
    private static final Map<UUID, Long> sonMesajTick = new HashMap<>();
    // Bekleyen sahte-join'ler: uid → hedef tick
    private static final Map<UUID, Long> bekleyenJoin = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FakeMesajlar::onTick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (RNG.nextDouble() > JOIN_FAKE_IHTIMAL) return;
            long hedef = server.overworld().getGameTime() + JOIN_GECIKME_TICK;
            bekleyenJoin.put(handler.player.getUUID(), hedef);
        });

        Candy.LOG.info("[Candy] FakeMesajlar aktif — sahte join/leave");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonMesajTick.remove(uuid);
        bekleyenJoin.remove(uuid);
    }

    public static void sunucuKapan() {
        sonMesajTick.clear();
        bekleyenJoin.clear();
        tickSayaci = 0;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        long simdi = server.overworld().getGameTime();

        // Bekleyen sahte-join'leri işle
        bekleyenJoin.entrySet().removeIf(e -> {
            if (simdi < e.getValue()) return false;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && !p.isRemoved()) fakeJoinMesajGoster(p);
            return true;
        });

        if (tickSayaci % TICK_ARALIGI != 0) return;

        // Periyodik fake mesajlar
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (tp.korkuSkoru < MIN_KORKU) continue;

            Long son = sonMesajTick.get(player.getUUID());
            double agresivlik = Psikoloji.agresivlikCarpani(tp);
            long cooldown = (long) (OYUNCU_COOLDOWN_TICK / agresivlik);
            if (son != null && simdi - son < cooldown) continue;
            if (RNG.nextDouble() > BASE_IHTIMAL * agresivlik) continue;

            if (RNG.nextBoolean()) fakeLeaveMesajGoster(player);
            else fakeJoinMesajGoster(player);
            sonMesajTick.put(player.getUUID(), simdi);
        }
    }

    private static void fakeJoinMesajGoster(ServerPlayer player) {
        Component mesaj = Component.translatable("multiplayer.player.joined", "Candy")
            .withStyle(ChatFormatting.YELLOW);
        player.displayClientMessage(mesaj, false);
        Candy.LOG.info("[Candy] fake join: {}", player.getName().getString());
    }

    private static void fakeLeaveMesajGoster(ServerPlayer player) {
        Component mesaj = Component.translatable("multiplayer.player.left", "Candy")
            .withStyle(ChatFormatting.YELLOW);
        player.displayClientMessage(mesaj, false);
        Candy.LOG.info("[Candy] fake leave: {}", player.getName().getString());
    }
}
