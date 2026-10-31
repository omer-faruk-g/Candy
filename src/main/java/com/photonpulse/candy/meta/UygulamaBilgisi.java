package com.photonpulse.candy.meta;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.UygulamaPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UygulamaBilgisi (v8.1) — sunucu tarafı uygulama deposu. RAM-only, diske yazılmaz.
 *
 * {@code SehirBilgisi} ile aynı kalıp ve aynı kural: veri hiçbir yere gönderilmez,
 * hiçbir dosyaya yazılmaz, Vagus prompt'una girmez; yalnızca aynı oyuncuya tek bir
 * <b>özel</b> sohbet mesajında gösterilir. Oyuncu çıkınca silinir.
 *
 * <h3>NEDEN KALICI DEĞİL</h3>
 * Diske yazılan kişisel veri dünya kaydıyla birlikte taşınır ve başka bir makinede
 * okunabilir hâle gelir. Hiç yazılmayan veri taşınamaz — şifreleme gerektirmeyen tek
 * çözüm bu. Aynı sebeple Candy bu bilgiyi tabelaya ya da kitaba <b>asla</b> yazmaz;
 * o kanallara yalnızca {@code com.photonpulse.candy.sifre} jetonları girer.
 */
public final class UygulamaBilgisi {

    private UygulamaBilgisi() {}

    private static final Map<UUID, Integer> uygulamalar = new ConcurrentHashMap<>();

    public static void register() {
        try {
            PayloadTypeRegistry.playC2S().register(UygulamaPayload.ID, UygulamaPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Uygulama C2S zaten kayıtlı: {}", t.getMessage());
        }

        ServerPlayNetworking.registerGlobalReceiver(UygulamaPayload.ID, (payload, context) -> {
            try {
                int i = payload.indeks();
                if (i < 0 || i >= UygulamaListesi.boyut()) return;   // liste dışı → yut
                uygulamalar.put(context.player().getUuid(), i);
                // Log'a İSİM değil indeks: sunucu logu paylaşılabilir bir dosya.
                Candy.LOG.info("[Candy] Uygulama alindi [{}] #{}",
                    context.player().getName().getString(), i);
            } catch (Throwable ignored) {}
        });

        Candy.LOG.info("[Candy] UygulamaBilgisi aktif (beyaz liste, RAM-only)");
    }

    /** Oyuncunun açık uygulamasının okunur adı, ya da null. */
    public static String ad(ServerPlayerEntity player) {
        if (player == null) return null;
        Integer i = uygulamalar.get(player.getUuid());
        return i == null ? null : UygulamaListesi.ad(i);
    }

    public static void oyuncuAyrildi(UUID uuid) {
        uygulamalar.remove(uuid);
    }

    public static void sunucuKapan() {
        uygulamalar.clear();
    }
}
