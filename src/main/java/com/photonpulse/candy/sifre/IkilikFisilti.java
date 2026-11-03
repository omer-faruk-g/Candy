package com.photonpulse.candy.sifre;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * IkilikFisilti (v8.3) — 0 ve 1'lerden oluşan "I see you" fısıltısı,
 * <b>oyun penceresinin içinde</b>.
 *
 * <h3>NİYE TERMİNAL DEĞİL</h3>
 * İstek "arka planda terminal ile 0 ve 1 yazsın"dı. Terminal açılmadı, çünkü bu kuralı
 * koyan kullanıcının kendisi:
 *
 * <blockquote>"Aldığım önemler sayesinde hem daha güvenli hem de daha korkunç olacak.
 * Gidip brokenscript gibi de terminal falan açıp kod çalıştırmak çok mantıksız."</blockquote>
 *
 * <p>Gerekçe hâlâ geçerli ve teknik olarak da doğru: terminal açmak işletim sistemi
 * düzeyinde bir süreç doğurur, antivirüs tarafından davranışsal olarak işaretlenir,
 * modun dağıtımını zorlaştırır ve oyuncunun ilk tepkisi korku değil <i>"bu mod virüs
 * mü"</i> olur. Korkunun sürmesi için oyuncunun oyunda kalması gerekir; pencereden
 * çıkan her şey büyüyü bozar ve oyuncuyu modu kaldırmaya iter.</p>
 *
 * <p>Aynı estetik — siyah üzerine monospace 0/1 — pencere içinde dört kanalla veriliyor:
 * <ol>
 *   <li><b>Pencere başlığı</b> ({@link com.photonpulse.candy.client.PencereBasligi}) —
 *       görev çubuğunda ikilik yazı. Terminalin verdiği "oyun dışından geliyor"
 *       hissinin aslında tek gerçek kaynağı buydu ve bu zaten elimizde.</li>
 *   <li><b>Camscreen</b> — tam ekran siyah, ortasında yalnızca 0 ve 1.</li>
 *   <li><b>Altyazı kanalı</b> (v8.2) — köşede ikilik.</li>
 *   <li><b>Chat</b> — gri, italik, tek blok.</li>
 * </ol>
 *
 * <p>Kullanıcı kendi kuralını geri almak isterse gerçek terminal eklenebilir; o karar
 * onun, ve bu sınıf o zaman da aynı metni üretmeye devam eder.</p>
 *
 * <h3>METİN</h3>
 * Düz metinler {@link IkilikMetin#FISILTI} içinde, <b>ortak kodda</b> ve sabit ASCII —
 * dil dosyasından gelmiyor. İkilik dizi her oyuncuda aynı uzunlukta olmalı, yoksa "kod
 * gibi" görünen şey dile göre şekil değiştirir ve kalıbı bozar. Dizi ortak olduğu için
 * sunucu yalnızca indeks yollar, istemci metni kendisi çevirir; iki taraf ayrışamaz.
 */
public final class IkilikFisilti {

    private IkilikFisilti() {}

    /** Kanal seçimi. */
    public enum Kanal { CHAT, CAMSCREEN, ALTYAZI, BASLIK }

    /**
     * Rastgele bir fısıltıyı rastgele bir kanaldan yollar.
     *
     * @return gönderilen düz metin, ya da gönderilemezse {@code null}
     */
    public static String fisildat(ServerPlayer player, TruePersonality tp) {
        if (player == null) return null;
        Kanal[] kanallar = Kanal.values();
        return fisildat(player, tp, kanallar[(int) (Math.random() * kanallar.length)]);
    }

    public static String fisildat(ServerPlayer player, TruePersonality tp, Kanal kanal) {
        if (player == null || kanal == null) return null;
        int indeks = (int) (Math.random() * IkilikMetin.FISILTI.length);
        String duz = IkilikMetin.FISILTI[indeks];

        switch (kanal) {
            case CHAT -> {
                // Chat'te ikilik diziyi doğrudan yazıyoruz: oyuncuya giden sohbet
                // metni zaten serbest (dialog sistemi de öyle). Protokol kuralı
                // custom payload'lar için — bu bir chat mesajı, payload değil.
                String ikilik = IkilikMetin.fisiltiIkilik(indeks);
                if (ikilik.isEmpty()) return null;
                player.displayClientMessage(Component.literal(ikilik).withStyle(ChatFormatting.DARK_GRAY), false);
            }
            case CAMSCREEN -> com.photonpulse.candy.altyazi.Altyazi.ikilikEkran(
                player, indeks, 3500,
                com.photonpulse.candy.net.IkilikEkranPayload.KANAL_TAM_EKRAN);
            case ALTYAZI -> com.photonpulse.candy.altyazi.Altyazi.ikilikEkran(
                player, indeks, 3000,
                com.photonpulse.candy.net.IkilikEkranPayload.KANAL_KOSE);
            case BASLIK -> com.photonpulse.candy.altyazi.Altyazi.baslikIkilik(player);
        }

        if (tp != null) {
            tp.metaFarkindalik = Math.min(100.0, tp.metaFarkindalik + 1.0);
            tp.paranoyaSkoru = Math.min(100.0, tp.paranoyaSkoru + 1.5);
        }
        Candy.LOG.debug("[Candy] ikilik fısıltı [{}]: \"{}\"", kanal, duz);
        return duz;
    }
}
