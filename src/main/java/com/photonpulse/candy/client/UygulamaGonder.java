package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.meta.UygulamaListesi;
import com.photonpulse.candy.net.UygulamaPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UygulamaGonder (v8.1) — açık uygulamalardan beyaz listede olan <b>bir</b> tanesini
 * sunucuya bildirir. "Steve/Alex senin başka ekranda ne açık olduğunu biliyor" anı için.
 *
 * <h3>NE OKUNUR, NE OKUNMAZ</h3>
 * {@link ProcessHandle#allProcesses()} — saf JDK, hiçbir OS komutu çalıştırılmıyor,
 * terminal açılmıyor, native çağrı yok. Her süreçten yalnızca çalıştırılabilir dosyanın
 * <b>adı</b> alınıyor; komut satırı, argümanlar, açılan dosya yolları, kullanıcı adı,
 * PID — hiçbiri okunmuyor.
 *
 * <h3>FİLTRE ÖNCE, SONRA AĞ</h3>
 * Eşleşme {@link UygulamaListesi} beyaz listesine göre burada, client'ta yapılıyor.
 * Ağa çıkan tek şey eşleşen tek bir <b>indeks</b>. Birden fazla eşleşirse rastgele biri
 * seçilir — hangi uygulamaların açık olduğunun tam listesi hiçbir yere gitmiyor.
 *
 * <h3>KAPATILABİLİR</h3>
 * {@link CandyMixinConfig#uygulamaSezgisi} false ise süreç listesi <b>hiç okunmaz</b>.
 * Tarama oturum başına bir kez, sunucuya bağlanırken, ayrı bir daemon thread'de yapılır.
 */
@Environment(EnvType.CLIENT)
public final class UygulamaGonder {

    private UygulamaGonder() {}

    public static void register() {
        try {
            PayloadTypeRegistry.playC2S().register(UygulamaPayload.ID, UygulamaPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Uygulama C2S zaten kayıtlı: {}", t.getMessage());
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!CandyMixinConfig.uygulamaSezgisi) return;
            Thread t = new Thread(() -> {
                final int indeks = tara();
                if (indeks < 0) return;
                // Ağ gönderimi ana thread'de olmalı.
                client.execute(() -> {
                    try {
                        if (ClientPlayNetworking.canSend(UygulamaPayload.ID)) {
                            ClientPlayNetworking.send(new UygulamaPayload(indeks));
                        }
                    } catch (Throwable ignored) {}
                });
            }, "Candy-Uygulama");
            t.setDaemon(true);
            t.start();
        });
    }

    /** Beyaz listede eşleşen rastgele bir indeks, ya da -1. */
    private static int tara() {
        try {
            List<Integer> bulunan = new ArrayList<>(4);
            ProcessHandle.allProcesses().forEach(ph -> {
                if (bulunan.size() >= UygulamaListesi.boyut()) return;
                Optional<String> komut = ph.info().command();
                if (komut.isEmpty()) return;
                String ad;
                try {
                    Path p = Path.of(komut.get()).getFileName();
                    if (p == null) return;
                    ad = p.toString();
                } catch (Throwable t) {
                    return;   // okunamayan yol — atla, başka hiçbir alana bakma
                }
                int i = UygulamaListesi.indeksBul(ad);
                if (i >= 0 && !bulunan.contains(i)) bulunan.add(i);
            });
            if (bulunan.isEmpty()) return -1;
            return bulunan.get(ThreadLocalRandom.current().nextInt(bulunan.size()));
        } catch (Throwable t) {
            // Süreç listesi izni olmayabilir (bazı sandbox'lar) — sessizce vazgeç.
            Candy.LOG.debug("[Candy] uygulama taraması başarısız: {}", t.getMessage());
            return -1;
        }
    }
}
