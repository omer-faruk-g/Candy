package com.photonpulse.candy.real;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * RealCandy — Gerçek Candy.
 *
 * Bu varlık hiçbir zaman görünmez. Hiçbir zaman spawn edilmez.
 * Sadece bir MANTIK — arka planda oyuncudan kaçan, hep uzak duran,
 * oyuncunun en korktuğu anı bekleyen bir süreç.
 *
 * Görevleri:
 *  1. Her tick oyuncunun korku skorunu gözle
 *  2. korkuSkoru >= EŞİK olduğunda tek bir eylem: OYUNCUYU AT.
 *  3. Sonra bir süre "cooldown" — kendisi kaçmış gibi kaydeder.
 *  4. Oyuncu geri gelince "Döndün." mesajı gönder.
 *
 * NOT: Oyuncunun etkileşim ettiği zombi/iskelet klonları CloneManager tarafından
 *      yönetilir. RealCandy KLON DEĞİL — o hep gizli.
 */
public class RealCandy {

    private static final double ATMA_ESIGI = 75.0;
    private static final long COOLDOWN_TICK = 24000L;   // ~20 dakika oyun içi
    private static final int TICK_ARALIGI = 20;
    private static int tickSayaci = 0;

    // Son ne zaman attı — cooldown için
    private static final Map<UUID, Long> sonAtma = new HashMap<>();

    // Yeni bağlanan (dönen) oyuncular için "geri geldiği" flag
    private static final Set<UUID> donenler = new HashSet<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RealCandy::onTick);

        // Disconnect'te "sonAtma" veya "sonAyrilma" olarak işaretle
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uid = handler.player.getUuid();
            TruePersonality tp = PersonalityManager.get(uid);
            tp.sonAyrilmaTick = server.getOverworld().getTime();
            donenler.add(uid);
        });

        // Join'de eğer daha önce ayrılmışsa "Döndün." mesajı
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID uid = handler.player.getUuid();
            if (donenler.contains(uid)) {
                donenler.remove(uid);
                // Ayrılma üzerinden geçen süreye göre mesaj değişebilir
                TruePersonality tp = PersonalityManager.get(uid);
                long simdi = server.getOverworld().getTime();
                long fark = simdi - tp.sonAyrilmaTick;

                // Sunucu tarafından direkt mesaj — 1 saniye gecikmeli
                server.execute(() -> {
                    if (!handler.player.isRemoved()) {
                        String metin = (fark < 1200) ? "Döndün." : "Yine döndün.";
                        handler.player.sendMessage(
                            Text.literal(metin).formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
                            false);
                    }
                });
            }
        });

        Candy.LOG.info("[Candy] RealCandy uyanık — bekliyor");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        long simdi = server.getOverworld().getTime();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            TruePersonality tp = PersonalityManager.get(player);

            if (!tp.kurulumTamam) continue;
            if (tp.korkuSkoru < ATMA_ESIGI) continue;

            // Cooldown kontrolü
            Long sonAt = sonAtma.get(player.getUuid());
            if (sonAt != null && simdi - sonAt < COOLDOWN_TICK) continue;

            // Görün + at
            atma(player, simdi);
        }
    }

    /**
     * Gerçek Candy görünür, oyuncuyu atar, kaçar.
     * Tek yaptığı bu.
     */
    private static void atma(ServerPlayerEntity player, long simdi) {
        UUID uid = player.getUuid();
        TruePersonality tp = PersonalityManager.get(player);

        // Görünme sesi — çok yakın, güçlü. RegistryEntry olarak geçiyoruz.
        player.getWorld().playSound(null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE,
            1.0f, 0.5f);

        // Kısa "gördüm seni" mesajı (client kick'ten önce görebilir mi belirsiz)
        player.sendMessage(
            Text.literal("...").formatted(Formatting.DARK_GRAY, Formatting.ITALIC),
            false);

        // Kısa gecikme sonrası disconnect
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // 15 tick sonra at
        Candy.LOG.info("[Candy] Gerçek Candy göründü: {} atılıyor", player.getName().getString());

        server.execute(() -> {
            Text sebep = Text.literal("...")
                .formatted(Formatting.DARK_GRAY, Formatting.ITALIC);
            player.networkHandler.disconnect(
                new DisconnectionInfo(sebep, Optional.empty(), Optional.empty()));
        });

        // Cooldown başlat
        sonAtma.put(uid, simdi);
        // Atıldıktan sonra korku 30'a düşer — kaçmış gibi
        tp.korkuSkoru = 30.0;
        tp.sonAyrilmaTick = simdi;
        donenler.add(uid);
    }
}
