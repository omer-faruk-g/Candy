package com.photonpulse.candy.dev;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.arkadas.ArkadasEntity;
import com.photonpulse.candy.arkadas.ArkadasKaydi;
import com.photonpulse.candy.beyin.BeyinMotoru;
import com.photonpulse.candy.beyin.Kurul;
import com.photonpulse.candy.beyin.Perde;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.entity.CandyEntities;
import com.photonpulse.candy.hayalet.HayaletOyuncular;
import com.photonpulse.candy.meta.SaatBilgisi;
import com.photonpulse.candy.net.BildirimPayload;
import com.photonpulse.candy.platform.SunucuAg;
import com.photonpulse.candy.sifre.IkilikFisilti;
import com.photonpulse.candy.sifre.SifreDagitici;
import com.photonpulse.candy.sifre.TuzakYayini;
import com.photonpulse.candy.platform.KomutKayit;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * CandyDevKomut — <b>yalnızca test</b> için {@code /candy} komutu.
 *
 * <h3>NİYE VAR</h3>
 * v8.3'ün beş mekaniğinin tamamı {@code perdeKademe} + {@code candyOfke} kapısının
 * arkasında. Kademe ise takvimle değil <b>Kurul'un isabetiyle</b> açılıyor: en az 40
 * tahmin ve ortalama Brier kalibrasyonu 0,56 / 0,64 / 0,72. Bu doğru tasarım ama test
 * edilemez bir tasarım — birkaç saat oynamadan Arkadaş'ı, konum kaydırmayı ya da ikilik
 * fısıltıyı hiç göremezsin. Bu komut o kapıyı <b>test için</b> açıyor.
 *
 * <h3>NİYE OYUNCUYA SIZMAZ</h3>
 * <ul>
 *   <li>Yalnızca {@code -Dcandy.debug=true} ile başlatılmış JVM'de kaydediliyor.
 *       Sistem özelliğini oyun içinden değiştirmek mümkün değil, sunucuda bile.</li>
 *   <li>Hiç {@code requires(...)} yok, yani izin seviyesi 0. Bu bilinçli:
 *       {@code CommandManagerMixin} seviye-0'ı geçemeyen her komutu iptal ediyor,
 *       dolayısıyla {@code /candy} kendi kilidimize takılmasın diye izinsiz.
 *       Zaten komut debug JVM'i dışında <b>hiç var olmuyor</b>.</li>
 * </ul>
 *
 * <h3>NİYE EYLEM ZORLAMA DEĞİL, KAPI AÇMA</h3>
 * {@code /candy perde} ve {@code /candy ofke} dışındaki her şey mekanikleri kendi
 * <b>gerçek</b> kod yolundan geçiriyor. Doğrudan "Arkadaş'ı ışınla" demek mekaniği
 * değil kendi test kancamı test etmek olurdu; kapıyı açıp sistemin kendi kararını
 * vermesini beklemek gerçek davranışı gösteriyor.
 */
public final class CandyDevKomut {

    private CandyDevKomut() {}

    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");

    private static int lanDogrulamaKapat(CommandSourceStack src) {
        src.getServer().setUsesAuthentication(false);
        Candy.LOG.info("[Candy][dev] LAN: oturum doğrulaması kapatıldı (çevrimdışı test hesabı)");
        src.sendSuccess(() -> Component.literal("[dev] LAN doğrulaması kapalı"), false);
        return 1;
    }

    public static void register() {
        if (!DEBUG) return;   // normal oyunda komut HİÇ kaydedilmiyor

        KomutKayit.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(Commands.literal("candy")
                .executes(c -> durum(c.getSource()))
                .then(Commands.literal("durum")
                    .executes(c -> durum(c.getSource())))
                // LAN testi: çevrimdışı test hesabı (Dev2) Mojang doğrulamasına takılmasın.
                .then(Commands.literal("lan")
                    .executes(c -> lanDogrulamaKapat(c.getSource())))
                .then(Commands.literal("perde")
                    .then(Commands.argument("kademe",
                            IntegerArgumentType.integer(0, Perde.MAKS_KADEME))
                        .executes(c -> perde(c.getSource(),
                            IntegerArgumentType.getInteger(c, "kademe")))))
                .then(Commands.literal("ofke")
                    .then(Commands.argument("deger",
                            DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(c -> ofke(c.getSource(),
                            DoubleArgumentType.getDouble(c, "deger")))))
                // Test iskelesi: uzun birikim isteyen ön koşulları (korku, baskı, klon sayısı…)
                // doğrudan kur; tetikleyici ve etki yolu mekaniğin KENDİ kodu olarak kalır.
                .then(Commands.literal("alan")
                    .then(Commands.argument("ad",
                            com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(Commands.argument("deger", DoubleArgumentType.doubleArg())
                            .executes(c -> alan(c.getSource(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(c, "ad"),
                                DoubleArgumentType.getDouble(c, "deger"))))))
                .then(Commands.literal("tuzak")
                    .executes(c -> tuzak(c.getSource())))
                .then(Commands.literal("ikilik")
                    .executes(c -> ikilik(c.getSource())))
                .then(Commands.literal("sifre")
                    .executes(c -> sifre(c.getSource())))
                .then(Commands.literal("arkadas")
                    .executes(c -> arkadas(c.getSource())))
                .then(Commands.literal("mektup")
                    .executes(c -> mektup(c.getSource())))
                // Boss'u Gizemli Kitap'la yenmenin sonucu (sağ tık etkileşimi komutla
                // taklit edilemiyor). Kötü son komutsuz: geri sayımı /tick sprint bitirir.
                .then(Commands.literal("son")
                    .then(Commands.literal("kazan")
                        .executes(c -> sonKazan(c.getSource()))))
                .then(Commands.literal("saat")
                    .executes(c -> saat(c.getSource()))
                    .then(Commands.literal("sil")
                        .executes(c -> saatSil(c.getSource()))))));

        Candy.LOG.warn("[Candy] DEV KOMUTU AKTIF — /candy (yalnizca -Dcandy.debug=true)");
    }

    // ─── durum: "niye hicbir sey olmuyor" sorusunun cevabi ───
    private static int durum(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("oyuncu gerekli")); return 0; }
        TruePersonality tp = PersonalityManager.get(p);
        if (tp == null) { src.sendFailure(Component.literal("profil yok")); return 0; }

        Kurul kurul = BeyinMotoru.kurul(p.getUUID());
        double kal = Perde.kalibrasyon(kurul);
        ServerLevel w = (ServerLevel) p.level();

        yaz(src, "── Candy durumu ──", ChatFormatting.GOLD);
        yaz(src, String.format("perde kademe : %d / %d", tp.perdeKademe, Perde.MAKS_KADEME),
            ChatFormatting.WHITE);
        yaz(src, String.format("kalibrasyon  : %.3f  (esikler 0.56 / 0.64 / 0.72)", kal),
            ChatFormatting.WHITE);
        yaz(src, String.format("ofke %.2f | ozguven %.1f | paranoya %.1f | meta %.1f",
            tp.candyOfke, tp.candyOzguven, tp.paranoyaSkoru, tp.metaFarkindalik),
            ChatFormatting.WHITE);
        yaz(src, String.format("oynanan      : %.1f dk", tp.toplamOynananTick / 1200.0),
            ChatFormatting.WHITE);
        yaz(src, String.format("tuzak        : %d gonderildi / %d cozuldu (ilgi %.2f)",
            tp.tuzakGonderilen, tp.tuzakCozulen, tp.tuzakIlgisi), ChatFormatting.WHITE);
        yaz(src, String.format("arkadas      : yas %.2f | karsilasma %d | oldurdu %d | dunyada olu: %s",
            tp.arkadasYasi, tp.arkadasKarsilasma, tp.arkadasOldurdu,
            ArkadasKaydi.olduMu(w) ? "EVET" : "hayir"), ChatFormatting.WHITE);

        yaz(src, "── v8.3 kapilari ──", ChatFormatting.GOLD);
        kapi(src, "C1 mob koordinasyon", tp.perdeKademe >= 1 && tp.candyOfke >= 0.18,
            "kademe>=1 & ofke>=0.18");
        kapi(src, "C2 tuzak yayini    ", tp.perdeKademe >= 1,
            "kademe>=1 (+ ilk 60 dk gecikme)");
        kapi(src, "C3 konum kaydirma  ", tp.perdeKademe >= 2 && tp.candyOfke >= 0.30,
            "kademe>=2 & ofke>=0.30");
        kapi(src, "C4 ikilik fisilti  ", tp.perdeKademe >= 2,
            "kademe>=2 (ZihinselProva secerse)");
        kapi(src, "C5 arkadas         ",
            tp.perdeKademe >= 2 && tp.candyOfke >= 0.35 && !ArkadasKaydi.olduMu(w),
            "kademe>=2 & ofke>=0.35 & olmemis");
        return 1;
    }

    private static void kapi(CommandSourceStack src, String ad, boolean acik, String sart) {
        String satir = (acik ? "  [ACIK]  " : "  [KAPALI] ") + ad + "  " + sart;
        src.sendSuccess(() -> Component.literal(satir)
            .withStyle(acik ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
        Candy.LOG.info("[Candy][dev] {}", satir);
    }

    private static void yaz(CommandSourceStack src, String s, ChatFormatting f) {
        src.sendSuccess(() -> Component.literal(s).withStyle(f), false);
        // Otomatik koşumda sohbeti okuyacak göz yok; aynı satır log'a da düşsün.
        // Yalnızca debug JVM'inde çalışıyor, normal oyunda bu sınıf hiç yüklenmiyor.
        Candy.LOG.info("[Candy][dev] {}", s);
    }

    // ─── kapi acma ───
    private static int perde(CommandSourceStack src, int kademe) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        TruePersonality tp = PersonalityManager.get(p);
        if (tp == null) return 0;
        tp.perdeKademe = kademe;   // Perde yalnizca ARTIRIYOR, bu deger kalici
        yaz(src, "perde kademe -> " + kademe, ChatFormatting.GREEN);
        return 1;
    }

    private static int ofke(CommandSourceStack src, double deger) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        TruePersonality tp = PersonalityManager.get(p);
        if (tp == null) return 0;
        tp.candyOfke = deger;
        yaz(src, String.format("candyOfke -> %.2f", deger), ChatFormatting.GREEN);
        return 1;
    }

    private static int alan(CommandSourceStack src, String ad, double deger) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        TruePersonality tp = PersonalityManager.get(p);
        if (tp == null) return 0;
        try {
            java.lang.reflect.Field f = TruePersonality.class.getField(ad);
            Class<?> t = f.getType();
            if (t == double.class) f.setDouble(tp, deger);
            else if (t == float.class) f.setFloat(tp, (float) deger);
            else if (t == int.class) f.setInt(tp, (int) deger);
            else if (t == long.class) f.setLong(tp, (long) deger);
            else if (t == boolean.class) f.setBoolean(tp, deger != 0);
            else { yaz(src, "alan tipi desteklenmiyor: " + t.getSimpleName(), ChatFormatting.RED); return 0; }
            yaz(src, "alan " + ad + " -> " + f.get(tp), ChatFormatting.GREEN);
            return 1;
        } catch (ReflectiveOperationException e) {
            yaz(src, "alan yok: " + ad, ChatFormatting.RED);
            return 0;
        }
    }

    // ─── mekanikleri kendi yolundan tetikle ───
    private static int tuzak(CommandSourceStack src) {
        boolean ok = TuzakYayini.yayinla(src.getServer());
        yaz(src, ok ? "tuzak yayinlandi" : "tuzak yayinlanamadi (oyuncu yok?)",
            ok ? ChatFormatting.GREEN : ChatFormatting.RED);
        return ok ? 1 : 0;
    }

    private static int ikilik(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        String duz = IkilikFisilti.fisildat(p, PersonalityManager.get(p));
        yaz(src, duz == null ? "fisilti gonderilemedi" : "fisilti: \"" + duz + "\"",
            duz == null ? ChatFormatting.RED : ChatFormatting.GREEN);
        return duz == null ? 0 : 1;
    }

    // --- test kancasi: sifre dagitimini elden tetikle ---
    /**
     * {@code /candy sifre} -- {@code SifreDagitici}'yi ELDEN calistirir.
     *
     * <p>Atlanan tek sey {@code SifreMotoru}'nun seyreltmesi ({@code kurulumTamam},
     * baski esigi, %2'lik zar): bunlar "ne zaman" sorusunun cevabi, mekanigin kendisi
     * degil. Kanal kapilari ({@code acik}, {@code haritaDosyasi},
     * {@code masaustuDosyasi}, {@code kitap}) hic atlanmiyor -- dosyalar gercek
     * yoldan yaziliyor.</p>
     */
    private static int sifre(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        int indeks = SifreDagitici.dagit(p, PersonalityManager.get(p));
        yaz(src, indeks < 0
                ? "sifre dagitilamadi (kapi kapali ya da soguma)"
                : "sifre #" + indeks + " dagitildi",
            indeks < 0 ? ChatFormatting.RED : ChatFormatting.GREEN);
        return indeks < 0 ? 0 : 1;
    }

    // ─── v10.0.0 dis kanal: ihtimal + ~30 dk soguma + kademe kapisini atla ───
    /**
     * {@code /candy mektup} — {@code TanidikFisilti}'nin dis kanal paketini ELDEN yollar.
     *
     * <p>Atlanan tek sey SUNUCU tarafindaki seyreltme (perde kademesi, %25 ihtimal,
     * ~30 dk soguma): bunlar "ne zaman" sorusunun cevabi, mekanigin kendisi degil.
     * Istemci tarafi hic atlanmiyor — paket gercek yoldan gidiyor, satiri istemci kendi
     * dilinde cozuyor ve mektubu gondermeye {@code MektupGonder} kendi config kapilariyla
     * karar veriyor. Yani config bos ise bu komut da hicbir ag istegi dogurmaz.</p>
     */
    private static int mektup(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("oyuncu gerekli")); return 0; }

        String dunya = src.getServer().getWorldData().getLevelSettings().levelName();
        if (dunya == null) dunya = "";
        try {
            SunucuAg.send(p, new BildirimPayload("candy.tanidik.dunya", dunya));
        } catch (Throwable t) {
            src.sendFailure(Component.literal("payload yollanamadi: " + t.getMessage()));
            return 0;
        }
        yaz(src, "BildirimPayload yollandi: candy.tanidik.dunya arg=\"" + dunya + "\"",
            ChatFormatting.GREEN);
        yaz(src, "Karar istemcide: mektup + mektupEposta + mektupUc ucu de dolu degilse "
            + "TEK ag istegi bile cikmaz.", ChatFormatting.GRAY);
        return 1;
    }

    // ─── son kazan: Gizemli Kitap'la boss'u yenmenin sonucu ───
    private static int sonKazan(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("oyuncu gerekli")); return 0; }
        // Yakındaki boss varsa kitabın sağ tıkta çağırdığı yolun aynısı: faz 8 değilse reddeder,
        // faz 8'de boss ölür ve die() -> BossManager.win zinciri işler.
        var yakin = p.level().getEntitiesOfClass(com.photonpulse.candy.boss.TrueRealCandyEntity.class,
            p.getBoundingBox().inflate(128), e -> true);
        if (!yakin.isEmpty()) {
            // Faz 8'deki boss öncelikli (birden fazla varsa yanlışını seçmesin)
            var boss = yakin.stream().filter(b -> b.getFaz() == 8).findFirst().orElse(yakin.get(0));
            boolean oldu = boss.gizemliKitapKullanildi(p);
            Candy.LOG.info("[Candy][dev] son: kitap faz {} -> {}", boss.getFaz(), oldu ? "WIN" : "reddedildi");
            return oldu ? 1 : 0;
        }
        com.photonpulse.candy.boss.BossManager.win(p);
        Candy.LOG.info("[Candy][dev] son: boss yok, dogrudan kazan -> {}", p.level().dimension().location());
        return 1;
    }

    // ─── v9.1.0 saat: join paketi geldi mi, diyalog dogru dakikayi mi veriyor ───
    private static int saat(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("oyuncu gerekli")); return 0; }

        int dk = SaatBilgisi.dakika(p);
        String metin = SaatBilgisi.metin(p);
        yaz(src, "SaatBilgisi: dakika=" + dk + " metin=" + metin
            + " dilim=" + SaatBilgisi.dilim(p)
            + (dk < 0 ? "   <- istemci BILDIRMEDI" : ""),
            dk < 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE);

        boolean steve = HayaletOyuncular.devSaatMesaji(src.getServer(), "Steve");
        boolean alex = HayaletOyuncular.devSaatMesaji(src.getServer(), "Alex");
        yaz(src, "saat diyalogu: Steve=" + (steve ? "GONDERILDI" : "atlandi->eski yol")
            + "  Alex=" + (alex ? "GONDERILDI" : "atlandi->eski yol"),
            (steve && alex) ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return 1;
    }

    /** {@code /candy saat sil} — istemci saati hic bildirmemis gibi yapar (geri dusme testi). */
    private static int saatSil(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("oyuncu gerekli")); return 0; }
        SaatBilgisi.devSil(p);
        yaz(src, "saat kaydi silindi — bundan sonra eski yola dusulmeli",
            ChatFormatting.GREEN);
        return 1;
    }

    private static int arkadas(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        ServerLevel w = (ServerLevel) p.level();

        if (ArkadasKaydi.olduMu(w)) {
            yaz(src, "Arkadas bu dunyada OLDURULMUS — geri gelmiyor. "
                + "Yeni dunya gerekiyor (kalici olum calisiyor demektir).", ChatFormatting.RED);
            return 0;
        }
        ArkadasEntity a = CandyEntities.ARKADAS.create(
            (net.minecraft.world.level.Level) w, EntitySpawnReason.EVENT);
        if (a == null) { src.sendFailure(Component.literal("olusturulamadi")); return 0; }

        // 8 blok once: hemen gorunsun ama uzerine dogmasin.
        double yon = Math.toRadians(p.getYRot());
        a.snapTo(
            p.getX() - Math.sin(yon) * 8.0, p.getY(), p.getZ() + Math.cos(yon) * 8.0,
            p.getYRot() + 180f, 0f);
        w.addFreshEntity(a);
        yaz(src, "Arkadas dogdu (8 blok onde)", ChatFormatting.GREEN);
        return 1;
    }
}
