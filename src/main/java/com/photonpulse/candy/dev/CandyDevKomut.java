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
import com.photonpulse.candy.sifre.IkilikFisilti;
import com.photonpulse.candy.sifre.TuzakYayini;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    public static void register() {
        if (!DEBUG) return;   // normal oyunda komut HİÇ kaydedilmiyor

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("candy")
                .executes(c -> durum(c.getSource()))
                .then(CommandManager.literal("durum")
                    .executes(c -> durum(c.getSource())))
                .then(CommandManager.literal("perde")
                    .then(CommandManager.argument("kademe",
                            IntegerArgumentType.integer(0, Perde.MAKS_KADEME))
                        .executes(c -> perde(c.getSource(),
                            IntegerArgumentType.getInteger(c, "kademe")))))
                .then(CommandManager.literal("ofke")
                    .then(CommandManager.argument("deger",
                            DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(c -> ofke(c.getSource(),
                            DoubleArgumentType.getDouble(c, "deger")))))
                .then(CommandManager.literal("tuzak")
                    .executes(c -> tuzak(c.getSource())))
                .then(CommandManager.literal("ikilik")
                    .executes(c -> ikilik(c.getSource())))
                .then(CommandManager.literal("arkadas")
                    .executes(c -> arkadas(c.getSource())))));

        Candy.LOG.warn("[Candy] DEV KOMUTU AKTIF — /candy (yalnizca -Dcandy.debug=true)");
    }

    // ─── durum: "niye hicbir sey olmuyor" sorusunun cevabi ───
    private static int durum(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) { src.sendError(Text.literal("oyuncu gerekli")); return 0; }
        TruePersonality tp = PersonalityManager.get(p);
        if (tp == null) { src.sendError(Text.literal("profil yok")); return 0; }

        Kurul kurul = BeyinMotoru.kurul(p.getUuid());
        double kal = Perde.kalibrasyon(kurul);
        ServerWorld w = (ServerWorld) p.getWorld();

        yaz(src, "── Candy durumu ──", Formatting.GOLD);
        yaz(src, String.format("perde kademe : %d / %d", tp.perdeKademe, Perde.MAKS_KADEME),
            Formatting.WHITE);
        yaz(src, String.format("kalibrasyon  : %.3f  (esikler 0.56 / 0.64 / 0.72)", kal),
            Formatting.WHITE);
        yaz(src, String.format("ofke %.2f | ozguven %.1f | paranoya %.1f | meta %.1f",
            tp.candyOfke, tp.candyOzguven, tp.paranoyaSkoru, tp.metaFarkindalik),
            Formatting.WHITE);
        yaz(src, String.format("oynanan      : %.1f dk", tp.toplamOynananTick / 1200.0),
            Formatting.WHITE);
        yaz(src, String.format("tuzak        : %d gonderildi / %d cozuldu (ilgi %.2f)",
            tp.tuzakGonderilen, tp.tuzakCozulen, tp.tuzakIlgisi), Formatting.WHITE);
        yaz(src, String.format("arkadas      : yas %.2f | karsilasma %d | oldurdu %d | dunyada olu: %s",
            tp.arkadasYasi, tp.arkadasKarsilasma, tp.arkadasOldurdu,
            ArkadasKaydi.olduMu(w) ? "EVET" : "hayir"), Formatting.WHITE);

        yaz(src, "── v8.3 kapilari ──", Formatting.GOLD);
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

    private static void kapi(ServerCommandSource src, String ad, boolean acik, String sart) {
        src.sendFeedback(() -> Text.literal(
            (acik ? "  [ACIK]  " : "  [KAPALI] ") + ad + "  " + sart)
            .formatted(acik ? Formatting.GREEN : Formatting.DARK_GRAY), false);
    }

    private static void yaz(ServerCommandSource src, String s, Formatting f) {
        src.sendFeedback(() -> Text.literal(s).formatted(f), false);
    }

    // ─── kapi acma ───
    private static int perde(ServerCommandSource src, int kademe) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;
        TruePersonality tp = PersonalityManager.get(p);
        if (tp == null) return 0;
        tp.perdeKademe = kademe;   // Perde yalnizca ARTIRIYOR, bu deger kalici
        yaz(src, "perde kademe -> " + kademe, Formatting.GREEN);
        return 1;
    }

    private static int ofke(ServerCommandSource src, double deger) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;
        TruePersonality tp = PersonalityManager.get(p);
        if (tp == null) return 0;
        tp.candyOfke = deger;
        yaz(src, String.format("candyOfke -> %.2f", deger), Formatting.GREEN);
        return 1;
    }

    // ─── mekanikleri kendi yolundan tetikle ───
    private static int tuzak(ServerCommandSource src) {
        boolean ok = TuzakYayini.yayinla(src.getServer());
        yaz(src, ok ? "tuzak yayinlandi" : "tuzak yayinlanamadi (oyuncu yok?)",
            ok ? Formatting.GREEN : Formatting.RED);
        return ok ? 1 : 0;
    }

    private static int ikilik(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;
        String duz = IkilikFisilti.fisildat(p, PersonalityManager.get(p));
        yaz(src, duz == null ? "fisilti gonderilemedi" : "fisilti: \"" + duz + "\"",
            duz == null ? Formatting.RED : Formatting.GREEN);
        return duz == null ? 0 : 1;
    }

    private static int arkadas(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) return 0;
        ServerWorld w = (ServerWorld) p.getWorld();

        if (ArkadasKaydi.olduMu(w)) {
            yaz(src, "Arkadas bu dunyada OLDURULMUS — geri gelmiyor. "
                + "Yeni dunya gerekiyor (kalici olum calisiyor demektir).", Formatting.RED);
            return 0;
        }
        ArkadasEntity a = CandyEntities.ARKADAS.create(
            (net.minecraft.world.World) w, SpawnReason.EVENT);
        if (a == null) { src.sendError(Text.literal("olusturulamadi")); return 0; }

        // 8 blok once: hemen gorunsun ama uzerine dogmasin.
        double yon = Math.toRadians(p.getYaw());
        a.refreshPositionAndAngles(
            p.getX() - Math.sin(yon) * 8.0, p.getY(), p.getZ() + Math.cos(yon) * 8.0,
            p.getYaw() + 180f, 0f);
        w.spawnEntity(a);
        yaz(src, "Arkadas dogdu (8 blok onde)", Formatting.GREEN);
        return 1;
    }
}
