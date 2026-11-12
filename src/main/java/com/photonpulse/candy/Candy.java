package com.photonpulse.candy;

import com.photonpulse.candy.clone.CloneManager;
import com.photonpulse.candy.core.DerinAnaliz;
import com.photonpulse.candy.core.FearDetection;
import com.photonpulse.candy.core.ObservationEngine;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.disguise.DisguiseSystem;
import com.photonpulse.candy.entity.CandyEntities;
import com.photonpulse.candy.entity.SpectateSwap;
import com.photonpulse.candy.audio.BhootAmbient;
import com.photonpulse.candy.audio.CandyKahkaha;
import com.photonpulse.candy.audio.CandySounds;
import com.photonpulse.candy.audio.FisiltiSistemi;
import com.photonpulse.candy.audio.SesTaklit;
import com.photonpulse.candy.ending.EndingWatcher;
import com.photonpulse.candy.boss.BossEventler;
import com.photonpulse.candy.chat.CandyChatOkur;
import com.photonpulse.candy.chat.FakeMesajlar;
import com.photonpulse.candy.envanter.EnvanterKaristirici;
import com.photonpulse.candy.evler.Dikit;
import com.photonpulse.candy.evler.Kan;
import com.photonpulse.candy.evler.KanEvi;
import com.photonpulse.candy.environment.KarartmaSistemi;
import com.photonpulse.candy.environment.MesaleKirici;
import com.photonpulse.candy.grief.GriefSystem;
import com.photonpulse.candy.hastalik.HastalikYoneticisi;
import com.photonpulse.candy.hayalet.HayaletOyuncular;
import com.photonpulse.candy.kalp.KalpHizi;
import com.photonpulse.candy.lore.Denek239Book;
import com.photonpulse.candy.lore.JournalLootInjector;
import com.photonpulse.candy.lore.YirtikSayfa;
import com.photonpulse.candy.net.CamscreenTetikleyici;
import com.photonpulse.candy.net.IntensityBroadcaster;
import com.photonpulse.candy.psikoloji.PsikolojiTakip;
import com.photonpulse.candy.real.RealCandy;
import com.photonpulse.candy.ruya.RuyaSistemi;
import com.photonpulse.candy.sessizlik.SessizlikYoneticisi;
import com.photonpulse.candy.sanity.SanityController;
import com.photonpulse.candy.sign.SignSystem;
import com.photonpulse.candy.simulation.Discontent;
import com.photonpulse.candy.simulation.ShadowTreeManager;
import com.photonpulse.candy.simulation.SimulationEngine;
import com.photonpulse.candy.simulation.SimulationManager;
import com.photonpulse.candy.dimension.AnomalyPatches;
import com.photonpulse.candy.dimension.DunyaSekillendirici;
import com.photonpulse.candy.dimension.DunyaKurulu;
import com.photonpulse.candy.dimension.AntiMirrorLottery;
import com.photonpulse.candy.dimension.GlitchFlicker;
import com.photonpulse.candy.dimension.MirrorAtmosphere;
import com.photonpulse.candy.dimension.MirrorGirisi;
import com.photonpulse.candy.tempo.MobSpawnBooster;
import com.photonpulse.candy.tempo.TickAccelerator;
import com.photonpulse.candy.travma.BaseSabotaji;
import com.photonpulse.candy.travma.OlumYerleri;
import com.photonpulse.candy.travma.PetOlumu;
import com.photonpulse.candy.travma.TotemIptali;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.platform.KayitKapisi;
import com.photonpulse.candy.platform.SunucuBaglanti;
import com.photonpulse.candy.platform.SunucuYasamDongusu;
import net.minecraft.core.registries.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Candy — kendini bulmaya çalışan bir varlık.
 *
 * Mod ana entry point. Tüm sistemler burada register edilir.
 * Sıralama önemli: Personality → Observation → Persona → Sign → Disguise → Clone
 */
public class Candy {

    public static final String MOD_ID = "candy";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static void onInitialize() {
        LOG.info("[Candy] uyanıyor...");

        // v8.4: Sürüm/komşu tespiti her şeyden önce — alt sistemler guvenliMi()'ye bakıyor.
        com.photonpulse.candy.uyum.SurumKapisi.register();

        // v0.7: Custom entity — Candy'nin doppelganger'ı (player-shaped, 40 HP)
        // Kayıtlar KayitKapisi üzerinden: Fabric'te çağrıldığı anda çalışır,
        // NeoForge'da ilgili RegisterEvent'e kadar bekler. NeoForge'da mod
        // kurucusu çalıştığında BuiltInRegistries zaten donmuş oluyor.
        KayitKapisi.kaydet(Registries.ENTITY_TYPE, CandyEntities::register);
        KayitKapisi.kaydet(Registries.MOB_EFFECT,
            com.photonpulse.candy.item.CandyEfektler::register); // v3.6 İmanın Gücü efekti

        // v1.9.2: Custom sound events (murmur)
        KayitKapisi.kaydet(Registries.SOUND_EVENT, CandySounds::register);

        // Sunucu yaşam döngüsü
        SunucuYasamDongusu.SERVER_STARTED.register(server -> {
            com.photonpulse.candy.beyin.BeyinDeposu.sunucuBaslat(); // v4.0 cihaz-basi beyin
            com.photonpulse.candy.beyin.BeyinMotoru.dunyaIdAyarla(
                com.photonpulse.candy.beyin.DunyaDogrulayici.dunyaKimligi(server));
            PersonalityManager.sunucuBaslat(server);
            TreeGrowth.sunucuBaslat(server);
            SimulationManager.sunucuBaslat(server);
            ShadowTreeManager.sunucuBaslat(server);
            SignSystem.sunucuBaslat(server);
        });
        SunucuYasamDongusu.SERVER_STOPPING.register(server -> {
            PersonalityManager.sunucuKapan();
            com.photonpulse.candy.beyin.BeyinMotoru.sunucuKapan();
            TreeGrowth.sunucuKapan();
            SimulationManager.sunucuKapan();
            ShadowTreeManager.sunucuKapan();
            // Per-server state cleanup (single-player world switching için kritik)
            SignSystem.sunucuKapan();
            CloneManager.sunucuKapan();
            RealCandy.sunucuKapan();
            TickAccelerator.sunucuKapan();
            SanityController.sunucuKapan();
            SpectateSwap.sunucuKapan();
            BossEventler.sunucuKapan();
            FakeMesajlar.sunucuKapan();
            MesaleKirici.sunucuKapan();
            SesTaklit.sunucuKapan();
            EnvanterKaristirici.sunucuKapan();
            KalpHizi.sunucuKapan();
            HayaletOyuncular.sunucuKapan();
            CamscreenTetikleyici.sunucuKapan();
            CandyChatOkur.sunucuKapan();
            SessizlikYoneticisi.sunucuKapan();
            BhootAmbient.sunucuKapan();
            CandyKahkaha.sunucuKapan();
            KanEvi.sunucuKapan();
            Dikit.sunucuKapan();
            Kan.sunucuKapan();
            TotemIptali.sunucuKapan();
            RuyaSistemi.sunucuKapan();
            AnomalyPatches.sunucuKapan();
            GlitchFlicker.sunucuKapan(server);   // bekleyen glitch bloklarını geri yükle
            com.photonpulse.candy.net.BlackoutGlitch.sunucuKapan();
            com.photonpulse.candy.meta.OrumcekYonlendirme.sunucuKapan();
            com.photonpulse.candy.meta.DonanimBilgisi.sunucuKapan();
            com.photonpulse.candy.meta.SehirBilgisi.sunucuKapan();
            com.photonpulse.candy.meta.UygulamaBilgisi.sunucuKapan();   // v8.1
            com.photonpulse.candy.meta.SaatBilgisi.sunucuKapan();       // v9.1.0
            com.photonpulse.candy.yuz.EkranHaritasi.sunucuKapan();      // v9.1.0
            com.photonpulse.candy.meta.GoruntuTetik.sunucuKapan();      // v8.1
            com.photonpulse.candy.meta.ArkaBakis.sunucuKapan();         // v8.2
            com.photonpulse.candy.kacis.KacisSensoru.sunucuKapan();
        com.photonpulse.candy.mob.MobKoordinasyon.sunucuKapan();
        com.photonpulse.candy.sifre.TuzakYayini.sunucuKapan();
        com.photonpulse.candy.meta.KonumKaydirma.sunucuKapan();
        com.photonpulse.candy.arkadas.ArkadasYonetici.sunucuKapan();     // v8.2
            // v8: JSON manager yazimlari bitsin (hepsiniKaydet cagrilarindan SONRA).
            com.photonpulse.candy.core.JsonDepo.kapanisBekle();
        });

        // Oyuncu bağlantı yaşam döngüsü
        SunucuBaglanti.JOIN.register((handler, server) -> {
            var player = handler.player;
            var tp = PersonalityManager.get(player);
            LOG.info("[Candy] oyuncu geldi: {} (kurulum: {})",
                player.getName().getString(), tp.kurulumTamam);

            // v8.2: dünya Candy çalışmadan oynandı mı? (tek oyunculuda saat yalnızca
            // oyuncu oynarken ilerler; büyük boşluk = mod kaldırılıp geri konmuş)
            com.photonpulse.candy.kacis.KacisSensoru.katilis(player);

            // DEV TEST — -Dcandy.autosetup=true ise kurulumu (tabela cevaplama) atla.
            // Sadece test botları içindir; üretimde flag kapalı → hiç etkisi yok.
            if (Boolean.getBoolean("candy.autosetup") && !tp.kurulumTamam) {
                tp.kurulumTamam = true;
                tp.kurulumTamamTick = server.overworld().getGameTime();
                LOG.info("[Candy] AUTOSETUP — kurulum otomatik tamamlandı: {}",
                    player.getName().getString());
            }

            // v1.3: İlk join'de Denek 239 kitapları (TR + EN) slot 0 ve 1'e
            if (!tp.denek239Verildi) {
                server.execute(() -> {
                    // Player o anda disconnect etmişse verilmez, flag da set edilmez —
                    // sonraki join'de tekrar dene.
                    if (player.isRemoved()) return;
                    var trKitap = Denek239Book.olustur(true);
                    var enKitap = Denek239Book.olustur(false);
                    var inv = player.getInventory();
                    if (inv.getItem(0).isEmpty()) inv.setItem(0, trKitap);
                    else player.addItem(trKitap);
                    if (inv.getItem(1).isEmpty()) inv.setItem(1, enKitap);
                    else player.addItem(enKitap);
                    tp.denek239Verildi = true;
                    com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "denek_239");
                    LOG.info("[Candy] Denek 239 kitapları verildi: {}", player.getName().getString());
                });
            }

            // v3.5: İlk join'de 1 Kuran-ı Kerim
            if (!tp.kuranVerildi) {
                server.execute(() -> {
                    if (player.isRemoved()) return;
                    player.addItem(com.photonpulse.candy.item.KuranKitap.olustur());
                    tp.kuranVerildi = true;
                    LOG.info("[Candy] Kuran-ı Kerim verildi: {}", player.getName().getString());
                });
            }
        });

        SunucuBaglanti.DISCONNECT.register((handler, server) -> {
            var uid = handler.player.getUUID();
            // Önce tp'yi güncel tut — cleanup diske yazacak, kayıp olmasın
            var tp = PersonalityManager.get(uid);
            tp.sonAyrilmaTick = server.overworld().getGameTime();
            // v8.2: son görülen dünya tick'ini yaz — sonraki katılış buna bakacak
            com.photonpulse.candy.kacis.KacisSensoru.ayrilis(handler.player);
            // Diske yaz + tüm per-player cache'leri temizle (MP'de birikmesin)
            TreeGrowth.oyuncuAyrildi(uid);
            PersonalityManager.oyuncuAyrildi(uid);
            ObservationEngine.oyuncuAyrildi(uid);
            DerinAnaliz.oyuncuAyrildi(uid);
            com.photonpulse.candy.core.IcDusunce.oyuncuAyrildi(uid);
            com.photonpulse.candy.travma.OlumYerleri.oyuncuAyrildi(uid);
            com.photonpulse.candy.core.ZihinselProva.oyuncuAyrildi(uid);
            com.photonpulse.candy.dimension.AynaSistemi.oyuncuAyrildi(uid); // v3.2 Ayna
            com.photonpulse.candy.item.KuranSistemi.oyuncuAyrildi(uid);    // v3.5 Kuran
            com.photonpulse.candy.beyin.BeyinMotoru.oyuncuAyrildi(uid);   // v4.0 beyin
            com.photonpulse.candy.beyin.vagus.Vagus.oyuncuAyrildi(uid);   // v8.0 Vagus
            com.photonpulse.candy.net.BlackoutGlitch.oyuncuAyrildi(uid);
            com.photonpulse.candy.meta.OrumcekYonlendirme.oyuncuAyrildi(uid);
            com.photonpulse.candy.meta.DonanimBilgisi.oyuncuAyrildi(uid);
            com.photonpulse.candy.meta.SehirBilgisi.oyuncuAyrildi(uid);
            com.photonpulse.candy.meta.UygulamaBilgisi.oyuncuAyrildi(uid);  // v8.1
            com.photonpulse.candy.meta.SaatBilgisi.oyuncuAyrildi(uid);      // v9.1.0
            com.photonpulse.candy.yuz.EkranHaritasi.oyuncuAyrildi(uid);     // v9.1.0
            com.photonpulse.candy.meta.GoruntuTetik.oyuncuAyrildi(uid);     // v8.1
            com.photonpulse.candy.meta.ArkaBakis.oyuncuAyrildi(uid);        // v8.2
            FearDetection.oyuncuAyrildi(uid);
            SignSystem.oyuncuAyrildi(uid);
            CloneManager.oyuncuAyrildi(uid);
            RealCandy.oyuncuAyrildi(uid);
            SimulationManager.oyuncuAyrildi(uid);
            ShadowTreeManager.oyuncuAyrildi(uid);
            Discontent.oyuncuAyrildi(uid);
            SanityController.oyuncuAyrildi(uid);
            SpectateSwap.oyuncuAyrildi(uid);
            BossEventler.oyuncuAyrildi(uid);
            FakeMesajlar.oyuncuAyrildi(uid);
            MesaleKirici.oyuncuAyrildi(uid);
            SesTaklit.oyuncuAyrildi(uid);
            EnvanterKaristirici.oyuncuAyrildi(uid);
            KalpHizi.oyuncuAyrildi(uid);
            HayaletOyuncular.oyuncuAyrildi(uid);
            CamscreenTetikleyici.oyuncuAyrildi(uid);
            BhootAmbient.oyuncuAyrildi(uid);
            CandyKahkaha.oyuncuAyrildi(uid);
            LOG.info("[Candy] oyuncu ayrıldı: {} — hafıza yazıldı",
                handler.player.getName().getString());
        });

        // Sistem kayıtları — sıra önemli
        ObservationEngine.register();
        DerinAnaliz.register();             // v3.0 Analiz 2.0 — 14 boyutlu derin zekâ
        com.photonpulse.candy.core.IcDusunce.register(); // v3.1 niyet çıkarımı + iç ses
        com.photonpulse.candy.core.ZihinselProva.register(); // v3.1 hayali test + karar motoru
        com.photonpulse.candy.lore.TeshisRaporu.register(); // v3.2 klinik teşhis raporu (idea 7)
        com.photonpulse.candy.beyin.BeyinMotoru.register(); // v4.0 beyin (agactan ONCE)
        com.photonpulse.candy.beyin.vagus.Vagus.register();  // v8.0 Vagus (yerel gozlemci)
        TreeGrowth.register();
        SignSystem.register();
        DisguiseSystem.register();
        CloneManager.register();
        FearDetection.register();
        SanityController.register();
        RealCandy.register();

        // v0.3: Simulation + ShadowTree katmanı
        SimulationEngine.register();
        ShadowTreeManager.register();
        Discontent.register();

        // v0.4: Yas sistemi + geçmiş oyuncu günlükleri
        GriefSystem.register();
        JournalLootInjector.register();

        // v0.5: Veri açlığı — mob spawn boost + tick hızlandırma
        MobSpawnBooster.register();
        TickAccelerator.register();

        // v0.7: SpectateSwap — ara sıra kamera Candy'e kilitlenir
        SpectateSwap.register();

        // v0.9+: Psikoloji takip — kalıcı ruh sağlığı sistemi
        PsikolojiTakip.register();

        // v1.1: Boss eventler — Bekci / Yansıma / Fısıltı
        BossEventler.register();

        // v1.4: Yeni özellikler
        FakeMesajlar.register();            // sahte join/leave
        MesaleKirici.register();            // mağara meşale patlaması
        YirtikSayfa.register();             // sandık loot yırtık sayfa
        SesTaklit.register();               // player sesleri mimic
        EnvanterKaristirici.register();     // AFK sonrası slot swap
        KarartmaSistemi.register();         // gece + mağara atmosferi karartması
        RuyaSistemi.register();             // bed'de kabus (%40)
        KalpHizi.register();                // düşük HP + korku'da Candy yaklaşır
        HayaletOyuncular.register();        // Steve & Alex — görünmez hayalet oyuncular
        IntensityBroadcaster.register();    // v1.5.0-mixin: server → client intensity sync
        CamscreenTetikleyici.register();    // v1.6.0-mixin: kotuluk-driven jump scare
        CandyChatOkur.register();           // v1.7.1: Candy chat okur ve tepki verir
        HastalikYoneticisi.register();      // v1.8.0: 8 psikolojik hastalık sistemi
        SessizlikYoneticisi.register();     // v1.9.0: build-up + payoff sessizlik protokolü
        FisiltiSistemi.register();          // v1.9.1: ambient fısıltı + klavye + click sesleri
        com.photonpulse.candy.arkadas.TanidikFisilti.register();  // v9.0.0: kişisel fısıltı — Candy seni hatırlıyor
        EndingWatcher.register();           // v1.9.2: candyOzguven=100 → ending
        BhootAmbient.register();            // v1.9.3: bhoot MASTER kategori ambient
        CandyKahkaha.register();            // v1.9.6: özgüven ≥ 60'ta üstünlük kahkahası
        KanEvi.register();                  // v1.9.7: gizemli Kan Evi generator
        Dikit.register();                   // v1.9.8: deepslate dikit + Blood tabanı
        Kan.register();                     // v1.9.9: kan izi (redstone_wire + "Kan" item)

        // ─── v3.0 Faz 1: Boss'suz mega update ───
        OlumYerleri.register();             // Phase 3.6: ölüm yerleri travma hafızası
        PetOlumu.register();                // Phase 3.7: her 4 dk %7 pet ölümü
        // Phase 3.8: villager kill — mevcut HayaletOyuncular.koyYagmala içine gömüldü
        BaseSabotaji.register();            // Phase 3.9: uzaktayken base'i sessizce boz
        // Phase 3.10: Command Lock — CommandManagerMixin ile mixin
        TotemIptali.register();             // Phase 3.11: totem iptal + kick + rejoin (LivingEntityMixin)
        // Phase 3.13: Yatak bekçisi — RuyaSistemi v2.0 içinde upgrade edildi

        // ─── v3.0 Faz 1: Dimension katmanı ───
        MirrorGirisi.register();            // Phase 2: Mirror/Anti-Mirror teleport akışı
        com.photonpulse.candy.dimension.AynaSistemi.register(); // v3.2 Ayna: kopya + düzenek + takas
        AntiMirrorLottery.register();       // Phase 4: 5 sn lottery + mercy reset

        // ─── v7: Kurul Dünyaları — 11 beyin her 5dk "seni atayım mı, nereye?" karar verir ───
        KayitKapisi.kaydet(Registries.BLOCK,
            com.photonpulse.candy.dimension.CandyBloklar::register); // candy:me bloğu (ME dünyası)
        DunyaSekillendirici.register();     // 8 dünyanın desenini chunk üretiminde damgalar
        DunyaKurulu.register();             // kurul kararı + DunyaGecisi ışınlama/dönüş
        AnomalyPatches.register();          // Phase 3.5: %10 overworld chunk anomaly

        // ─── v3.0 Faz 1.1: Rendering polish ───
        MirrorAtmosphere.register();        // DARKNESS + ash + smoke Mirror/Anti-Mirror
        GlitchFlicker.register();           // Mirror'da blok flicker (glitch texture yerine)

        // ─── v3.0: Yüz Haritası — kimlik korkusu ───
        com.photonpulse.candy.yuz.YuzHaritasi.register();

        // ─── v9.1.0: Ekran Haritası — F12'de o anki kare haritaya basılır ───
        com.photonpulse.candy.yuz.EkranHaritasi.register();

        // ─── v9.1.0: Saat Farkındalığı — hayaletler oyuncunun GERÇEK saatini bilir ───
        com.photonpulse.candy.meta.SaatBilgisi.register();

        // ─── v3.0: Mod Farkındalığı — Candy kurulu modları biliyor ───
        com.photonpulse.candy.meta.ModFarkindaligi.register();

        // ─── v3.0: Blackout Glitch — 0.1-0.3sn ekran karartma ───
        com.photonpulse.candy.net.BlackoutGlitch.register();

        // ─── v3.0: Örümcek Yönlendirme — Spider-Man göndermesi ───
        com.photonpulse.candy.meta.OrumcekYonlendirme.register();

        // ─── v3.0: Donanım Farkındalığı — hayaletler senin PC parçanı söyler ───
        com.photonpulse.candy.meta.DonanimBilgisi.register();
        com.photonpulse.candy.meta.SehirBilgisi.register();

        // ─── v8.1: İkilik şifre + ışık + görüntü müdahalesi ───
        com.photonpulse.candy.meta.UygulamaBilgisi.register();   // beyaz liste, RAM-only
        com.photonpulse.candy.meta.GoruntuTetik.register();      // FOV/render, çıkışta geri
        com.photonpulse.candy.sifre.Karanlik.register();         // şifre → ışık -1
        com.photonpulse.candy.sifre.SifreMotoru.register();       // dağıtım döngüsü

        // ─── v8.2: geri besleme kanalları ───
        // Mod'un 11 beyinli Kurul'u aylardır çalışıyordu ama çıkarımın oyuncuya dönen
        // bir kanalı yoktu. Bu dört kayıt o kanalları açıyor: ölçüm (ArkaBakis,
        // KacisSensoru) ve gösterim (Altyazi, RutinTekrari — ZihinselProva çağırıyor).
        com.photonpulse.candy.meta.ArkaBakis.register();         // arkaya bakma ekseni
        com.photonpulse.candy.kacis.KacisSensoru.register();     // kaçış arayışı ekseni
        com.photonpulse.candy.altyazi.Altyazi.register();        // altyazı + pencere başlığı

        // ─── v8.3: dünyaya uzanan el ───
        com.photonpulse.candy.mob.MobKoordinasyon.register();    // öfke -> mob davranışı
        com.photonpulse.candy.sifre.TuzakYayini.register();      // herkese aynı sahte şifre
        com.photonpulse.candy.meta.KonumKaydirma.register();     // sessiz küçük kaydırma
        com.photonpulse.candy.arkadas.ArkadasYonetici.register();// Arkadaş

        // Yalnızca -Dcandy.debug=true ile; normal oyunda hiç kaydedilmiyor.
        com.photonpulse.candy.dev.CandyDevKomut.register();

        // ─── v3.1: True RealCandy Boss ───
        KayitKapisi.kaydet(Registries.RECIPE_SERIALIZER,
            com.photonpulse.candy.boss.GizemliKitapRecipe::register);
        com.photonpulse.candy.boss.BossManager.register();
        KayitKapisi.kaydet(Registries.RECIPE_SERIALIZER,
            com.photonpulse.candy.item.KuranRecipe::register);  // v3.5 Kuran-ı Kerim tarifi
        com.photonpulse.candy.item.KuranSistemi.register();  // v3.5 Ayetel Kürsi + İmanın Gücü

        LOG.info("[Candy] tüm aşamalar aktif — v8.3.0 (Arkadaş | Perde | Şifre | Vagus)");
    }
}
