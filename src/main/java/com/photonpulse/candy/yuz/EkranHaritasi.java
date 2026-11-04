package com.photonpulse.candy.yuz;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.meta.SaatBilgisi;
import com.photonpulse.candy.net.EkranPayload;
import com.photonpulse.candy.platform.AgKayit;
import com.photonpulse.candy.platform.SunucuAg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EkranHaritasi (v9.1.0) — <b>sunucu tarafı</b>: istemciden gelen ekran karesini
 * kilitli bir haritaya basar ve oyuncunun envanterine koyar.
 *
 * <p>İstemci tarafı ve mekaniğin gerekçesi: {@code client/EkranGonder}. Burada yalnızca
 * 16384 bayt harita verisi alınıyor; sunucu kimsenin ekranını görmüyor, gördüğü şey
 * oyuncunun kendi istemcisinin hazırlayıp yolladığı harita baytları.</p>
 *
 * <h3>HARİTANIN ADI = GERÇEK SAAT</h3>
 * Harita, basıldığı andaki <b>duvar saatiyle</b> adlandırılıyor ({@link SaatBilgisi}).
 * Envanterinde "03:14" yazan bir harita duruyor ve üstünde o anda ekranında ne varsa o
 * var. Saat bilinmiyorsa (istemci paketi geçmediyse) ad "???" oluyor — mekanik yine
 * çalışıyor, yalnızca o satır susuyor.
 *
 * <h3>DİSK VE KÖTÜYE KULLANIM</h3>
 * Her harita dünya kaydında bir {@code map_N} girdisi açar. O yüzden sunucuda da
 * bekleme süresi var ({@link #BEKLEME_MS}): değiştirilmiş bir istemci tuşa basılı tutup
 * kaydı şişiremiyor. Paket boyu da sabit — {@link EkranPayload#BOYUT} dışındaki her şey
 * sessizce atılıyor.
 */
public class EkranHaritasi {

    /** Sunucu tarafı bekleme — istemcideki 3 sn'nin üstüne gerçek kapı. */
    private static final long BEKLEME_MS = 15_000L;

    private static final Map<UUID, Long> sonCekim = new ConcurrentHashMap<>();

    public static void register() {
        try {
            AgKayit.playC2S().register(EkranPayload.ID, EkranPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Ekran C2S zaten kayıtlı: {}", t.getMessage());
        }

        SunucuAg.registerGlobalReceiver(EkranPayload.ID, (payload, context) -> {
            try {
                ServerPlayer player = context.player();
                byte[] renkler = payload.renkler();
                if (player == null || renkler == null || renkler.length != EkranPayload.BOYUT) return;

                long simdi = System.currentTimeMillis();
                Long son = sonCekim.get(player.getUUID());
                if (son != null && simdi - son < BEKLEME_MS) {
                    player.displayClientMessage(
                        Component.translatable("candy.ekran.bekle")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
                    return;
                }
                sonCekim.put(player.getUUID(), simdi);

                // Harita verisi ve envanter yalnızca sunucu iş parçacığında.
                context.server().execute(() -> {
                    try {
                        haritaVer(player, renkler);
                    } catch (Throwable ex) {
                        Candy.LOG.warn("[Candy] EkranHaritasi harita ver hata: {}", ex.getMessage());
                    }
                });
            } catch (Throwable ignored) {}
        });

        Candy.LOG.info("[Candy] EkranHaritasi aktif (v9.1.0) — bekleme {} sn", BEKLEME_MS / 1000);
    }

    private static void haritaVer(ServerPlayer player, byte[] renkler) {
        if (player.isRemoved()) return;
        ServerLevel world = (ServerLevel) player.level();

        MapId id = world.getFreeMapId();
        MapItemSavedData veri = MapItemSavedData.createForClient((byte) 0, true, world.dimension());
        System.arraycopy(renkler, 0, veri.colors, 0, EkranPayload.BOYUT);
        world.setMapData(id, veri);
        veri.setDirty();

        String saat = SaatBilgisi.metin(player);

        ItemStack harita = new ItemStack(Items.FILLED_MAP);
        harita.set(DataComponents.MAP_ID, id);
        harita.set(DataComponents.CUSTOM_NAME,
            Component.literal(saat != null ? saat : "???")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));

        if (!player.addItem(harita)) {
            player.drop(harita, false);      // envanter dolu — yere bırak, kaybolmasın
        }
        Candy.LOG.info("[Candy] EkranHaritasi: {} icin kare haritaya basildi",
            player.getName().getString());
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonCekim.remove(uuid);
    }

    public static void sunucuKapan() {
        sonCekim.clear();
    }
}
