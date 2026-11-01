package com.photonpulse.candy.sifre;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;

/**
 * TuzakYayini (v8.3) — <b>herkese aynı</b> ikilik mesaj. Çözülebilir, ve çözülünce
 * hiçbir şeye yaramaz.
 *
 * <h3>NİYE GERÇEK ŞİFRENİN TERSİ</h3>
 * {@link CihazKilidi} ile üretilen gerçek şifre <b>cihaza bağlı</b>: iki oyuncunun
 * jetonları farklıdır, cevabı paylaşmak işe yaramaz. Bu yayın bilinçli olarak onun
 * <b>tam tersi</b>: tek bir ikilik dizi, bütün oyunculara aynı, paylaşmak serbest.
 *
 * <p>Tuzağın çalışma biçimi tam da bu. Oyuncular diziyi birbirine yollar, beraber
 * çözer, heyecanlanır — ve altında <i>hiçbir şey</i> yoktur. Ödül yok, bit açılmıyor,
 * ışık düşmüyor, başarım yok. Candy'nin bu yayından tek kazancı, kimin uğraştığını
 * görmesi.</p>
 *
 * <p>Oyuncu bir daha hangi şifrenin gerçek olduğunu bilemez. Gerçek şifreyi de
 * tuzak sanıp görmezden gelebilir. İşleyen şey budur, mesajın içeriği değil.</p>
 *
 * <h3>NİYE LANG DEĞİL SABİT ASCII</h3>
 * Düz metin lang dosyasından gelse ikilik dizi istemcinin diline göre değişirdi ve
 * "herkese aynı" özelliği kırılırdı — tuzağın bütün esprisi aynı diziyi paylaşmakta.
 * Bu yüzden düz metin sınıf içinde sabit ASCII; çeviri yalnızca yayını <i>çerçeveleyen</i>
 * cümlede var.
 *
 * <h3>ÇÖZÜLÜNCE</h3>
 * {@code candyOzguven} <b>düşer</b> (-4): tuzağı görülen Candy biraz küçülür. Öfke
 * değişmez — tuzağa düşmek Candy'yi kızdırmaz, zaten istediği oydu. Ve oyuncuya giden
 * tek mesaj, hiçbir şey vermediğini söyleyen tek satırdır.
 */
public final class TuzakYayini {

    private TuzakYayini() {}

    /**
     * Tuzak düz metinleri. Kasıtlı olarak <b>anlamsız ama tetikleyici</b>: oyuncu bir
     * şey bulduğunu sanacak kadar anlamlı, hiçbir yere götürmeyecek kadar boş.
     * Koordinat, sayı, yön ya da isim <b>yok</b> — hiçbiri aranacak bir ipucu
     * olmasın; biri bile olsa oyuncular haftalarca o koordinatı kazardı.
     */
    private static final String[] DUZ_METIN = {
        "I SEE YOU",
        "NOTHING HERE",
        "YOU LOOKED",
        "IT WAS NOT A DOOR",
        "THE SAME ROOM AGAIN",
        "COUNT THEM",
        "NO ONE SENT THIS",
        "STILL AWAKE",
        "THIS IS NOT THE ONE",
        "KEEP DIGGING"
    };

    /** En az bu kadar tick oynanmadan yayın yapılmaz (~1 saat). */
    private static final long ILK_GECIKME = 72_000L;

    /** İki yayın arası en az bu kadar tick (~2 saat oyun süresi). */
    private static final long SOGUMA = 144_000L;

    /** Rastgelelik: soğuma dolduktan sonra her kontrolde bu ihtimalle yayın. */
    private static final double IHTIMAL = 0.25;

    /** Kontrol sıklığı (tick). 600 = 30 sn. */
    private static final int ARALIK = 600;

    private static int sayac = 0;
    private static long sonYayin = 0L;
    private static long tickSayaci = 0L;

    /** Şu an havada olan tuzağın düz metni; yok ise null. */
    private static volatile String aktifDuzMetin = null;

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(TuzakYayini::onTick);
        Candy.LOG.info("[Candy] TuzakYayini aktif — herkese aynı ikilik mesaj");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (++sayac < ARALIK) return;
        sayac = 0;
        try {
            denemeYap(server);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] TuzakYayini hatası: {}", t.toString());
        }
    }

    private static void denemeYap(MinecraftServer server) {
        if (tickSayaci < ILK_GECIKME) return;
        if (sonYayin != 0L && tickSayaci - sonYayin < SOGUMA) return;

        var oyuncular = server.getPlayerList().getPlayers();
        if (oyuncular.isEmpty()) return;

        // Kapı: en az bir oyuncuda perde açılmış olmalı — Kurul kendini kanıtlamadan
        // Candy sahte şifre yaymıyor.
        boolean hakVar = false;
        for (ServerPlayer p : oyuncular) {
            TruePersonality tp = PersonalityManager.get(p);
            if (tp != null && tp.perdeKademe >= 1) { hakVar = true; break; }
        }
        if (!hakVar) return;
        if (Math.random() >= IHTIMAL) return;

        yayinla(server);
    }

    /** Yayını hemen yapar. Deney modunda {@code ZihinselProva} buradan çağırır. */
    public static boolean yayinla(MinecraftServer server) {
        if (server == null) return false;
        var oyuncular = server.getPlayerList().getPlayers();
        if (oyuncular.isEmpty()) return false;

        String duz = DUZ_METIN[(int) (Math.random() * DUZ_METIN.length)];
        String ikilik = IkilikMetin.ikiliyeCevir(duz);
        if (ikilik.isEmpty()) return false;

        aktifDuzMetin = duz;
        sonYayin = tickSayaci;

        Component cerceve = Component.translatable("candy.tuzak.yayin")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        Component govde = Component.literal(ikilik).withStyle(ChatFormatting.DARK_GRAY);

        for (ServerPlayer p : oyuncular) {
            p.displayClientMessage(cerceve, false);
            p.displayClientMessage(govde, false);
            TruePersonality tp = PersonalityManager.get(p);
            if (tp != null) { tp.tuzakGonderilen++; tp.tuzakIlgisiGuncelle(); }
        }

        // Düz metin loglanır — yönetici neyin yayıldığını görsün. Oyuncuya asla.
        Candy.LOG.info("[Candy] tuzak yayını: \"{}\" ({} oyuncu)", duz, oyuncular.size());
        return true;
    }

    /**
     * Oyuncunun yazdığı metin aktif tuzağın cevabı mı? Evetse tuzak "çözülmüş" sayılır.
     *
     * <p>{@link SifreIzleyici}'den <b>önce</b> çağrılmalı: tuzak cevabı gerçek şifre
     * kontrolüne girmemeli, yoksa yanlış satır sayılıp {@code TUZAK} sonucu üretir ve
     * Candy'nin özgüveni yükselir — tam tersi olmalı.</p>
     *
     * @return tuzak çözüldüyse {@code true} (çağıran başka kontrol yapmamalı)
     */
    public static boolean kontrol(ServerPlayer player, TruePersonality tp, String metin) {
        String duz = aktifDuzMetin;
        if (duz == null || metin == null || tp == null) return false;
        String n = metin.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        if (n.isEmpty()) return false;
        String hedef = duz.replace(" ", "");
        if (!n.contains(hedef)) return false;

        tp.tuzakCozulen++;
        tp.tuzakIlgisiGuncelle();
        // Tuzağı görülen Candy küçülür. Öfke DEĞİŞMEZ: tuzağa düşülmesi onu kızdırmaz.
        tp.candyOzguven = Math.max(0.0, tp.candyOzguven - 4.0);
        tp.metaFarkindalik = Math.min(100.0, tp.metaFarkindalik + 2.0);

        player.displayClientMessage(Component.translatable("candy.tuzak.bos")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);

        Candy.LOG.info("[Candy] tuzak çözüldü ({}) — hiçbir ödül verilmedi",
            player.getGameProfile().getName());
        return true;
    }

    public static void sunucuKapan() {
        aktifDuzMetin = null;
        sonYayin = 0L;
        tickSayaci = 0L;
        sayac = 0;
    }
}
