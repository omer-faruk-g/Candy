package com.photonpulse.candy.real;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    private static final double ATMA_ESIGI = 92.0;   // Eski 75 çok düşüktü — normal oyunda tetikleniyordu
    private static final long COOLDOWN_TICK = 24000L;   // ~20 dakika oyun içi
    private static final int TICK_ARALIGI = 20;
    // Kurulum tamamlandıktan sonra bu kadar tick boyunca atma yapılmaz (grace period ~10 dk)
    private static final long JOIN_GRACE_TICK = 12000L;
    private static int tickSayaci = 0;

    // Her oyuncu için "sunucuya ilk katıldığı tick" — grace period için
    private static final Map<UUID, Long> katilmaTick = new HashMap<>();

    // Son ne zaman attı — cooldown için
    private static final Map<UUID, Long> sonAtma = new HashMap<>();

    // Yeni bağlanan (dönen) oyuncular için "geri geldiği" flag
    private static final Set<UUID> donenler = new HashSet<>();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(RealCandy::onTick);

        // Disconnect state'i Candy.java DISCONNECT handler'ında merkezi yönetiliyor
        // (tp.sonAyrilmaTick + donenler işaretlemesi orada). Cache temizliği
        // sırası kritik — bu handler ayrı kalsa dp yazımı kaybolabilirdi.

        // Join'de eğer daha önce ayrılmışsa "Döndün." mesajı
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID uid = handler.player.getUUID();
            // Grace period: her join'de yeni sayaç — kick'ten hemen sonra tekrar kick olmasın
            katilmaTick.put(uid, server.overworld().getGameTime());
            if (donenler.contains(uid)) {
                donenler.remove(uid);
                // Ayrılma üzerinden geçen süreye göre mesaj değişebilir
                TruePersonality tp = PersonalityManager.get(uid);
                long simdi = server.overworld().getGameTime();
                long fark = simdi - tp.sonAyrilmaTick;

                // Sunucu tarafından direkt mesaj — 1 saniye gecikmeli
                server.execute(() -> {
                    if (!handler.player.isRemoved()) {
                        String key = (fark < 1200) ? "candy.chat.return" : "candy.chat.return_again";
                        handler.player.displayClientMessage(
                            Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                            false);
                    }
                });
            }
        });

        Candy.LOG.info("[Candy] RealCandy uyanık — bekliyor");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        // sonAtma silinmez — atma() cooldown'ı korunsun (DISCONNECT atma sonrası tetiklenir)
        katilmaTick.remove(uuid);   // JOIN'de yeni sayaç konacak
        donenler.add(uuid);
    }

    /**
     * Sunucu kapanırken per-server state — SADECE donenler ve tickSayaci temizle.
     * NOT: sonAtma KORUNUR. Aksi halde oyuncu kicklendikten sonra dünyayı yeniden yükleyince
     * cooldown sıfırlanıyor ve tekrar hemen kick yiyordu (spam kick). Bu bilinçli tercih.
     */
    public static void sunucuKapan() {
        donenler.clear();
        katilmaTick.clear();
        tickSayaci = 0;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        long simdi = server.overworld().getGameTime();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);

            if (!tp.kurulumTamam) continue;
            if (tp.korkuSkoru < ATMA_ESIGI) continue;

            // Grace period — join sonrası JOIN_GRACE_TICK dolmadan atma yok
            Long katilma = katilmaTick.get(player.getUUID());
            if (katilma != null && simdi - katilma < JOIN_GRACE_TICK) continue;

            // Cooldown kontrolü
            Long sonAt = sonAtma.get(player.getUUID());
            if (sonAt != null && simdi - sonAt < COOLDOWN_TICK) continue;

            // Görün + at
            atma(player, simdi);
        }
    }

    /**
     * Gerçek Candy görünür, oyuncuyu atar, kaçar.
     * Tek yaptığı bu.
     */
    private static void atma(ServerPlayer player, long simdi) {
        UUID uid = player.getUUID();
        TruePersonality tp = PersonalityManager.get(player);

        // Görünme sesi — çok yakın, güçlü. RegistryEntry olarak geçiyoruz.
        player.level().playSound(null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE,
            1.0f, 0.5f);

        // v1.7.3: Chat mesajı yok — Candy sadece tabelalardan konuşur.
        // Ses + disconnect yeterli atmosfer.

        // Kısa gecikme sonrası disconnect
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        // 15 tick sonra at
        Candy.LOG.info("[Candy] Gerçek Candy göründü: {} atılıyor", player.getName().getString());

        server.execute(() -> {
            if (player.connection == null || player.isRemoved()) return;
            Component sebep = Component.translatable("candy.chat.ellipsis")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            player.connection.disconnect(
                new DisconnectionDetails(sebep, Optional.empty(), Optional.empty()));
        });

        // Cooldown başlat
        sonAtma.put(uid, simdi);
        // Atıldıktan sonra korku 30'a düşer — kaçmış gibi
        tp.korkuSkoru = 30.0;
        tp.sonAyrilmaTick = simdi;
        // Kick en büyük müdahale — ShadowState baskı sıfırlansın, dönüşte sakin başlasın
        tp.sonMudahaleTick = simdi;
        tp.bastirilmisBaski = 0.0;
        donenler.add(uid);
    }
}
