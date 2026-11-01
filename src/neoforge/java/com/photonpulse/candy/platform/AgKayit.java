package com.photonpulse.candy.platform;

import com.photonpulse.candy.Candy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payload tipi kaydi — NeoForge uygulamasi: KUYRUK.
 *
 * Fabric'teki ile AYNI sinif ve paket adi; cagri yerleri iki tarafta da
 * {@code AgKayit.playS2C().register(X.ID, X.CODEC)} yaziyor.
 *
 * NEDEN KUYRUK — iki ayri fark, ayni mekanizmayla kapaniyor:
 *
 * 1. ZAMANLAMA. Fabric'te kayit statik registry uzerinden, init sirasinda,
 *    cagrildigi anda olur. NeoForge'da yalnizca {@code RegisterPayloadHandlersEvent}
 *    icinde olabilir; o olay mod kurucularindan SONRA bir kez atesleniyor.
 *    Candy'nin butun register() cagrilari ModInitializer/ClientModInitializer
 *    karsiligindan, yani kurucu asamasindan geliyor -> kuyruk olaydan once
 *    doluyor, olay icinde bosaltiliyor. Bu farki sarmalayici GERCEKTEN gizliyor.
 *
 * 2. C2S'TE TIP+ALICI FUZYONU. javap ile dogrulandi (neoforge-21.11.48):
 *      playToClient(Type, StreamCodec)                    <- alicisiz asiri yukleme VAR
 *      playToClient(Type, StreamCodec, IPayloadHandler)
 *      playToServer(Type, StreamCodec, IPayloadHandler)   <- alicisiz asiri yukleme YOK
 *    Yani S2C tipi tek basina kaydedilebilir ama C2S kaydedilemez: tip ve alici
 *    ayni cagriya girmek zorunda. Fabric'te bu ikisi ayri siniflarda
 *    (PayloadTypeRegistry ve ServerPlayNetworking). Bu farki sarmalayici
 *    GIZLEYEMIYOR; o yuzden SunucuAg/IstemciAg da alicilarini buraya kuyrukluyor
 *    ve ortak arayuz "alici kaydi yalnizca init zamaninda" diyor (bkz. SunucuAg).
 *
 * TEKILLESTIRME: Fabric ayni tipi ikinci kez kaydedince atiyor, Candy bunu
 * her cagri yerinde try/catch ile yutuyor (tek oyunculuda sunucu ve istemci ayni
 * JVM'de ayni tipi kaydediyor). NeoForge'da ayni atis olay ICINDE olurdu, yani
 * o try/catch'lerin disinda. Kuyruk ID'ye gore tekillestiriyor, ikinci kayit
 * sessizce dusuyor; Candy'deki catch bloklari NeoForge'da hic ateslenmiyor.
 */
@EventBusSubscriber
public final class AgKayit {

    /**
     * NeoForge'un ag surumu. Fabric'te karsiligi yok (Fabric payload tipleri icin
     * surum pazarligi yapmiyor). optional() ile birlikte, surumu farkli iki Candy
     * baglanti kurabiliyor; yalnizca eksik kanal kullanilmiyor.
     */
    private static final String AG_SURUMU = "1";

    private static final Map<CustomPacketPayload.Type<?>, Kayit<?>> KUYRUK = new LinkedHashMap<>();
    private static boolean bosaltildi = false;

    private AgKayit() {}

    public static Yon playS2C() { return Yon.S2C; }
    public static Yon playC2S() { return Yon.C2S; }

    public enum Yon {
        S2C, C2S;

        public <T extends CustomPacketPayload> void register(
                CustomPacketPayload.Type<T> tip,
                StreamCodec<? super RegistryFriendlyByteBuf, T> kodek) {
            kuyrukla(tip, kodek, this == S2C);
        }
    }

    private static final class Kayit<T extends CustomPacketPayload> {
        private final CustomPacketPayload.Type<T> tip;
        private final StreamCodec<? super RegistryFriendlyByteBuf, T> kodek;
        private final boolean s2c;
        private IPayloadHandler<T> alici;

        Kayit(CustomPacketPayload.Type<T> tip,
              StreamCodec<? super RegistryFriendlyByteBuf, T> kodek, boolean s2c) {
            this.tip = tip;
            this.kodek = kodek;
            this.s2c = s2c;
        }

        void uygula(PayloadRegistrar kayitci) {
            if (s2c) {
                if (alici == null) kayitci.playToClient(tip, kodek);
                else kayitci.playToClient(tip, kodek, alici);
            } else {
                // Alicisiz C2S kaydi mumkun degil. Alici yoksa bu JVM o payload'i
                // hic almiyor (ornek: cok oyunculu istemci kendi C2S tipini yalnizca
                // GONDEREBILMEK icin kaydeder) -> bos alici dogru davranis.
                kayitci.playToServer(tip, kodek, alici != null ? alici : (p, b) -> {});
            }
        }
    }

    private static synchronized <T extends CustomPacketPayload> void kuyrukla(
            CustomPacketPayload.Type<T> tip,
            StreamCodec<? super RegistryFriendlyByteBuf, T> kodek, boolean s2c) {
        if (bosaltildi) {
            Candy.LOG.error("[Candy] AgKayit: {} mod yuklendikten SONRA kaydedildi — "
                + "NeoForge'da bu kanal hic acilmayacak", tip.id());
            return;
        }
        if (KUYRUK.containsKey(tip)) return;   // tekillestirme, bkz. sinif yorumu
        KUYRUK.put(tip, new Kayit<>(tip, kodek, s2c));
    }

    /** SunucuAg/IstemciAg buradan alici bagliyor. Tip kaydindan once de cagrilabilir. */
    @SuppressWarnings("unchecked")
    static synchronized <T extends CustomPacketPayload> void alici(
            CustomPacketPayload.Type<T> tip, IPayloadHandler<T> alici) {
        if (bosaltildi) {
            Candy.LOG.error("[Candy] AgKayit: {} alicisi mod yuklendikten SONRA "
                + "kaydedildi — NeoForge'da bu alici hic cagrilmayacak", tip.id());
            return;
        }
        Kayit<T> kayit = (Kayit<T>) KUYRUK.get(tip);
        if (kayit == null) {
            Candy.LOG.error("[Candy] AgKayit: {} icin alici var ama tip kaydi yok", tip.id());
            return;
        }
        if (kayit.alici != null) {
            Candy.LOG.warn("[Candy] AgKayit: {} icin ikinci alici — ilki korunuyor", tip.id());
            return;
        }
        kayit.alici = alici;
    }

    @SubscribeEvent
    public static synchronized void kaydet(RegisterPayloadHandlersEvent olay) {
        // optional() ZORUNLU. NeoForge kayitlari varsayilan olarak ZORUNLU kanal:
        // kanali tasimayan karsi taraf baglantidan DUSURULUR. Fabric bunu hicbir
        // zaman yapmaz, ve Candy vanilla karsi tarafa tahammul edecek sekilde
        // yazilmis (her gonderimde try/catch, istemcide canSend kapisi). optional()
        // olmadan NeoForge jar'i vanilla istemciyi atardi, Fabric jar'i atmazken —
        // derleyicinin goremeyecegi bir davranis degisikligi.
        PayloadRegistrar kayitci = olay.registrar(AG_SURUMU).optional();
        for (Kayit<?> kayit : KUYRUK.values()) kayit.uygula(kayitci);
        bosaltildi = true;
        Candy.LOG.info("[Candy] AgKayit: {} payload tipi kaydedildi", KUYRUK.size());
    }
}
