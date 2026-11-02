package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.entity.CandyEntities;
import com.photonpulse.candy.platform.Yukleyici;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

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
        // v0.7: CandyDoppelEntity için renderer
        EntityRendererRegistry.register(CandyEntities.DOPPEL, CandyDoppelRenderer::new);
        // v3.1: boss renderer
        EntityRendererRegistry.register(CandyEntities.TRUE_REAL_CANDY, TrueRealCandyRenderer::new);
        // v8.3: Arkadaş — oyuncu modeli, tamamen siyah doku
        EntityRendererRegistry.register(CandyEntities.ARKADAS, ArkadasRenderer::new);

        // ME dünyası: her blok oyuncunun yüzü olur (skin okunamazsa gemideki yüz dokusu kalır)
        ClientTickEvents.END_CLIENT_TICK.register(MeYuz::istemciTick);

        // v1.5.0-mixin: config yükle + server intensity dinleyici
        CandyMixinConfig.load();
        CandyClientNet.register();
        // v3.0: donanım parçalarını (GPU/CPU/OS/RAM) join'de sunucuya yolla
        DonanimGonder.register();
        // v3.1: IP-tabanlı şehir tespiti, join'de sunucuya yolla
        SehirGonder.register();

        // v8.1: EN ÖNCE — geçen oturum çökmüşse FOV/render ayarını geri al.
        // Config yüklendikten hemen sonra, oyuncu hiçbir ekran görmeden.
        GoruntuMudahale.acilistaOnar();
        // v8.1: beyaz listedeki açık uygulamayı join'de sunucuya yolla (kapatılabilir)
        UygulamaGonder.register();
        // v8.1: temiz çıkışta da geri al — disk yedeği zaten güvence, bu hızlandırıyor.
        // v8.2: pencere başlığı ve altyazı da aynı anda temizlenir. Başlık diske
        // yazılmıyor (oyun her açılışta kendisi kuruyor), o yüzden disk yedeği gereksiz;
        // ama oturum içinde başka bir dünyaya girince takılı kalmasın.
        com.photonpulse.candy.platform.IstemciBaglanti.DISCONNECT
            .register(client -> {
                GoruntuMudahale.geriAl();
                PencereBasligi.geriAl();
                AltyaziState.sifirla();
                IkilikEkranState.sifirla();
                // v8.2: Candy ayar dosyasına OYUNCU ÇIKARKEN dokunur — From The Fog'un
                // "sen gittikten sonra" kalıbı. Dosya o anda kimse tarafından okunmuyor
                // ve oyuncu değişikliği bir sonraki bakışında bulur.
                //
                // Kapı: KaranlikState.kademe() > 0 ise Candy bu oyuncuda gerçek bir
                // kazanım elde etmiş (şifre çözüldü, ışık düştü) — hak kazanılmış.
                // Aksi hâlde %12 ihtimal, yani ilk oturumlarda neredeyse hiç olmuyor.
                if (KaranlikState.kademe() > 0 || Math.random() < 0.12) {
                    KonfigMudahale.mudahale();
                }
                KaranlikState.sifirla();
            });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
            .register(client -> {
                GoruntuMudahale.geriAl();
                PencereBasligi.geriAl();
            });
        // v8.2: F3 süresini ölç ve periyodik olarak sunucuya bir SAYI yolla.
        KacisGonder.register();

        // Sodium tespiti — log
        if (Yukleyici.modYuklu("sodium")) {
            Candy.LOG.info("[Candy] Sodium tespit edildi — mixin'ler Sodium ile uyumlu");
        }

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof PauseScreen) {
                belkiGoster(client);
            }
        });
        Candy.LOG.info("[Candy] client aktif — menü yakalayıcı + doppel renderer hazır");
    }

    private static void belkiGoster(Minecraft client) {
        long simdi = System.currentTimeMillis();
        if (simdi - sonGosterim < COOLDOWN_MS) return;
        sonGosterim = simdi;

        if (client.player == null) return;

        Component mesaj = Component.translatable("candy.chat.stay")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);

        // Bir sonraki tick'te chat'e yaz — screen init sırasında yollamak sorun çıkarabilir
        client.execute(() -> {
            if (client.player != null) {
                client.player.displayClientMessage(mesaj, false);
            }
        });
    }
}
