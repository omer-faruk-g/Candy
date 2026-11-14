package com.photonpulse.candy.arkadas;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/**
 * ArkadasFisilti (v8.3) — çok oyunculuda Candy'nin aracı: oyuncuları birbirine
 * düşürmeye çalışan <b>özel</b> mesajlar.
 *
 * <h3>NİYE ÇOK OYUNCULUDA YAVAŞLATMA DEĞİL</h3>
 * Tek oyuncuda Arkadaş fiziksel baskı kuruyor (Slowness, yol kesme). Çok oyuncuda bu
 * işe yaramaz: üç kişi varsa biri yavaşlasa diğerleri yardım eder, baskı dağılır.
 * Kalabalığın gerçek zayıflığı <b>güven</b>. Candy'nin en verimli hamlesi kimseye
 * dokunmadan ikisinin arasına girmek.
 *
 * <h3>NASIL ÇALIŞIYOR</h3>
 * Mesaj <b>özel</b>: yalnızca tek bir oyuncu görüyor. Tam da bu yüzden işliyor —
 * oyuncu chat'te gördüğünü arkadaşına söylediğinde arkadaşı "ben öyle bir şey
 * görmedim" diyor, ve ikisinden biri yalan söylüyor gibi görünüyor. Candy hiçbir
 * şeyi uydurmak zorunda değil; yalnızca kanıtı asimetrik hâle getiriyor.
 *
 * <h3>METİN VE GİZLİLİK</h3>
 * Mesajlar lang anahtarı + <b>oyuncu adı</b> argümanıyla gönderiliyor. Oyuncu adı çok
 * oyunculuda zaten herkese görünen bir bilgi (tab listesi, chat, ölüm mesajları), o
 * yüzden v8.1 gizlilik kuralını kırmıyor: kural şehir, donanım, dosya yolu ve
 * uygulama adı gibi <i>oyun dışı</i> veriler için.
 *
 * <p>Ayrı bir custom payload kurulmadı: {@code Text.translatable(anahtar, ad)} sunucudan
 * gidiyor, istemci kendi lang dosyasından çözüyor — dialog sisteminin zaten kullandığı
 * yol. İndeks taşıyan bir payload kurmak aynı sonucu veren fazladan bir kanal olurdu.</p>
 *
 * <h3>SINIR</h3>
 * Mesajlar hiçbir zaman <b>başka bir oyuncunun ağzından</b> konuşmuyor: Candy "Ali
 * şöyle dedi" demiyor, "Ali'nin nereye gittiğini biliyor musun" diyor. Sahte chat
 * satırı üretmek oyuncular arasında gerçek bir kavga çıkarır ve mod sunuculardan
 * yasaklanır; şüphe ekmek yeterli ve geri alınabilir.
 */
public final class ArkadasFisilti {

    private ArkadasFisilti() {}

    /** lang'deki {@code candy.arkadas.fisilti.N} sayısı. */
    public static final int FISILTI_SAYISI = 10;

    /** İki fısıltı arası en az bu kadar tick (~5 dk). */
    private static final long SOGUMA = 6_000L;

    private static long sonFisilti = 0L;
    private static long tickSayaci = 0L;

    /** Her tick {@link ArkadasYonetici} tarafından çağrılır. */
    static void tickIlerlet() {
        tickSayaci++;
    }

    /**
     * Rastgele bir oyuncuya, rastgele bir başkası hakkında özel fısıltı yollar.
     *
     * @return fısıltı gönderildiyse {@code true}
     */
    public static boolean dene(MinecraftServer server) {
        if (server == null) return false;
        if (tickSayaci - sonFisilti < SOGUMA) return false;

        List<ServerPlayer> oyuncular = server.getPlayerList().getPlayers();
        if (oyuncular.size() < 2) return false;   // kalabalık yoksa araç yok

        int a = (int) (Math.random() * oyuncular.size());
        int b = (int) (Math.random() * oyuncular.size());
        if (a == b) b = (b + 1) % oyuncular.size();

        ServerPlayer alici = oyuncular.get(a);
        ServerPlayer hakkinda = oyuncular.get(b);

        TruePersonality tp = PersonalityManager.get(alici);
        if (tp == null) return false;
        // Kapı: Candy bu oyuncuyu bir parça tanımadan kışkırtmıyor.
        if (tp.perdeKademe < 1) return false;

        int indeks = (int) (Math.random() * FISILTI_SAYISI);
        alici.displayClientMessage(Component.translatable(
                "candy.arkadas.fisilti." + indeks,
                hakkinda.getGameProfile().getName())
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);

        tp.paranoyaSkoru = Math.min(100.0, tp.paranoyaSkoru + 3.0);
        tp.metaFarkindalik = Math.min(100.0, tp.metaFarkindalik + 1.0);
        sonFisilti = tickSayaci;

        Candy.LOG.info("[Candy] Arkadaş fısıltısı: {} -> {} hakkında (idx {})",
            alici.getGameProfile().getName(), hakkinda.getGameProfile().getName(), indeks);
        return true;
    }

    public static void sunucuKapan() {
        sonFisilti = 0L;
        tickSayaci = 0L;
    }
}
