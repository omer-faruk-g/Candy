package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * CandyClient — client-side entry.
 *
 * Şu an sadece bir görevi var:
 *   Oyuncu pause menüsünü açtığında (ESC) chat'e "Henüz gitme." yaz.
 *
 * Bu Candy'nin oyunda ilk kez chat üzerinden konuştuğu andır.
 * Tüm oyun boyunca sessiz — sadece tabelalar. Ama çıkmaya çalışınca...
 *
 * NOT: Mesaj tamamen client-side üretilir. Sunucu paketi gerekmez.
 *      Bu yüzden single-player'da da çalışır, çok oyunculuda da.
 */
@Environment(EnvType.CLIENT)
public class CandyClient implements ClientModInitializer {

    // Aynı oturumda tekrar tekrar göstermeyelim
    private static long sonGosterim = 0;
    private static final long COOLDOWN_MS = 60_000L;   // 1 dakika

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GameMenuScreen) {
                belkiGoster(client);
            }
        });
        Candy.LOG.info("[Candy] client aktif — menü yakalayıcı hazır");
    }

    private static void belkiGoster(MinecraftClient client) {
        long simdi = System.currentTimeMillis();
        if (simdi - sonGosterim < COOLDOWN_MS) return;
        sonGosterim = simdi;

        if (client.player == null) return;

        Text mesaj = Text.literal("Henüz gitme.")
            .formatted(Formatting.DARK_GRAY, Formatting.ITALIC);

        // Bir sonraki tick'te chat'e yaz — screen init sırasında yollamak sorun çıkarabilir
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(mesaj, false);
            }
        });
    }
}
