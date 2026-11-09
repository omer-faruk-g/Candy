package com.photonpulse.candy.client;

import com.google.gson.JsonObject;
import com.photonpulse.candy.Candy;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * MektupGonder (v9.2.0, istemci) — Candy'nin "dış kanalı": oyuncuya GERÇEK e-posta.
 *
 * <p>{@link KlavyeIsigi} ile aynı aile ("efekt oyun penceresinin dışına çıkar"), ama bir
 * adım daha ileri: etki oyunun oturumundan da çıkıp oyuncunun gelen kutusuna düşer.
 * Mesaj, {@link com.photonpulse.candy.arkadas.TanidikFisilti}'nın oyun-içi gözleminin
 * aynısıdır — yeni bir veri toplanmaz; yalnızca aynı cümle başka bir kanaldan gelir.</p>
 *
 * <h3>VERİ NEREYE GİDİYOR — ve nereye GİTMİYOR</h3>
 * <ul>
 *   <li>Oyuncunun e-posta adresi YALNIZCA bu istemcide, {@code config/candy-mixin.json}
 *       içinde durur. Oyuncu onu kendi yazar; Candy hiçbir yerden okuyup bulmaz.</li>
 *   <li>Adres, bağlanılan Minecraft sunucusuna <b>gitmez</b> ({@link
 *       com.photonpulse.candy.net.BildirimPayload} e-posta taşımıyor). Yalnızca config'e
 *       yazılan {@code mektupUc} adresine (yazarın kendi mektup sunucusu) gider.</li>
 *   <li>Giden tek şey: adres + çevrilmiş oyun-içi satır + o anki saat. Ekran görüntüsü
 *       YOK, konum YOK, donanım YOK, dosya YOK. Hiçbir yere kaydedilmez (istemci de
 *       göndermeden sonra tutmaz; soğuma dışında hafızada iz kalmaz).</li>
 * </ul>
 *
 * <h3>VARSAYILAN: KAPALI (çift kapı)</h3>
 * Taze kurulumda <b>hiçbir ağ isteği olmaz</b>. İki alanın İKİSİ de dolu olmadıkça kanal
 * ölüdür:
 * <ol>
 *   <li>{@code mektupEposta} — oyuncunun kendi adresi (boş = rıza yok = sus).</li>
 *   <li>{@code mektupUc} — yazarın mektup sunucusunun {@code https://} adresi (boş =
 *       nereye yollanacağı yok = sus).</li>
 * </ol>
 * E-postayı config'e yazmak rızanın ta kendisi. Durdurmak için adresi silmek ya da
 * {@code mektup: false} yetiyor.
 *
 * <h3>KÖTÜYE KULLANIMA KARŞI</h3>
 * Mod yalnızca config'teki TEK adrese yollar ve mesaj, modun kendi altyazısını taşır
 * (bunu gönderen Candy adlı Minecraft modudur + nasıl kapatılır). Başkasının adresini
 * yazıp onu korkutmaya karşı asıl kapı yazarın mektup sunucusunda: ilk yollamadan önce
 * "bu adrese Candy ulaşsın mı?" onayı (çift katılım). Gerekçe ve örnek: {@code araclar/mektup/}.
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public final class MektupGonder {

    private MektupGonder() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    private static final DateTimeFormatter SAAT = DateTimeFormatter.ofPattern("HH:mm");

    /** İstemci tarafı güvenlik freni: sunucu ne derse desin iki mektup arası en az bu kadar. */
    private static final long SOGUMA_MS = 20L * 60L * 1000L;
    private static volatile long sonGonderim = 0L;

    /** Dev koşumda sessiz vazgeçişler görünür olsun (normal oyunda debug seviyesinde kalır). */
    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");

    private static void iz(String msg, Object... arg) {
        if (DEBUG) Candy.LOG.warn("[Candy][mektup] " + msg, arg);
        else Candy.LOG.debug("[Candy][mektup] " + msg, arg);
    }

    /**
     * Sunucu "dış kanaldan şunu ver" dedi; satır zaten istemcide oyuncunun diline
     * çevrildi. Config izin veriyorsa e-postayı arka planda yollar; vermiyorsa sessizce
     * hiçbir şey yapmaz (ağ isteği bile açılmaz). Çağıran thread beklemez.
     */
    public static void belki(String satir) {
        iz("bildirim istemciye ulasti");
        if (!CandyMixinConfig.mektup) { iz("kapi: mektup=false"); return; }
        String eposta = CandyMixinConfig.mektupEposta == null ? "" : CandyMixinConfig.mektupEposta.trim();
        String uc = CandyMixinConfig.mektupUc == null ? "" : CandyMixinConfig.mektupUc.trim();
        if (eposta.isEmpty() || uc.isEmpty()) { iz("kapi: eposta/uc bos"); return; }  // çift kapı
        if (!epostaGecerli(eposta) || !uc.startsWith("https://")) { iz("kapi: gecersiz eposta/uc"); return; }
        if (satir == null || satir.isBlank()) { iz("kapi: bos satir"); return; }

        long simdi = System.currentTimeMillis();
        synchronized (MektupGonder.class) {
            if (simdi - sonGonderim < SOGUMA_MS) { iz("kapi: istemci sogumasi"); return; }
            sonGonderim = simdi;
        }

        final String govdeSatir = satir.trim();
        Thread t = new Thread(() -> yolla(uc, eposta, govdeSatir), "Candy-MektupGonder");
        t.setDaemon(true);
        t.start();
    }

    private static void yolla(String uc, String eposta, String satir) {
        try {
            // Sohbet satırları §-renk kodu taşıyor (§8§o); düz-metin e-postada çöp görünür,
            // temizle. getString() biçim kodlarını ayıklamaz, o yüzden burada ayıklıyoruz.
            satir = satir.replaceAll("§.", "").trim();
            String saat = LocalTime.now().format(SAAT);
            String konu  = Component.translatable("candy.mektup.konu").getString();
            String govde = satir
                + "\n\n" + Component.translatable("candy.mektup.saat", saat).getString()
                + "\n\n" + Component.translatable("candy.mektup.alt").getString();

            JsonObject o = new JsonObject();
            o.addProperty("to", eposta);
            o.addProperty("konu", konu);
            o.addProperty("govde", govde);

            HttpRequest req = HttpRequest.newBuilder(URI.create(uc))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "Candy-Mod")
                .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                .build();

            HttpResponse<Void> resp = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() / 100 == 2) {
                Candy.LOG.info("[Candy] Mektup dış kanala yollandı");
            } else {
                iz("sunucu {} dondu", resp.statusCode());
            }
        } catch (Throwable t) {
            // Ağ yok / adres yanlış / sunucu kapalı → sessizce vazgeç, oyun etkilenmez.
            iz("yollanamadi: {}: {}", t.getClass().getSimpleName(), t.getMessage());
        }
    }

    /** Dış kanalın kabul ettiği tek anahtar ailesi — {@code TanidikFisilti}'nin satırları. */
    private static final String ANAHTAR_ONEK = "candy.tanidik.";
    /** Argüman: tabela alıntısı (48) ya da dünya adı. Daha uzunu Candy'nin satırı değil. */
    private static final int ARG_MAKS = 64;
    /** Link ya da alan adı gibi görünen argüman maile hiç girmez. */
    private static final java.util.regex.Pattern LINK = java.util.regex.Pattern.compile(
        "(?i)(://|www\\.|@|[a-z0-9-]+\\.[a-z]{2,})");

    /**
     * Sunucudan gelen dış kanal paketi Candy'nin kendi fısıltısı mı? Anahtar
     * {@code candy.tanidik.*} ailesinden olmalı VE istemcinin dil dosyasında gerçekten
     * bulunmalı (bilinmeyen anahtar kendisi olarak çözülür, yani serbest metin olur);
     * argüman kısa olmalı ve link/alan adı/adres içermemeli.
     */
    public static boolean bildirimGuvenli(String anahtar, String arg) {
        if (anahtar == null || !anahtar.startsWith(ANAHTAR_ONEK)) return false;
        if (!net.minecraft.locale.Language.getInstance().has(anahtar)) return false;
        if (arg == null || arg.isEmpty()) return true;
        return arg.length() <= ARG_MAKS && !LINK.matcher(arg).find();
    }

    /** Kaba doğrulama: tek @, nokta içeren alan adı, boşluk yok. Amaç yazım hatasını elemek. */
    private static boolean epostaGecerli(String e) {
        int at = e.indexOf('@');
        if (at <= 0 || at != e.lastIndexOf('@') || at == e.length() - 1) return false;
        if (e.indexOf(' ') >= 0) return false;
        String alan = e.substring(at + 1);
        return alan.indexOf('.') > 0 && !alan.endsWith(".");
    }
}
