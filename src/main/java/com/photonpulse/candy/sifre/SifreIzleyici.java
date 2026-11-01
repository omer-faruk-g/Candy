package com.photonpulse.candy.sifre;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * SifreIzleyici (v8.1) — oyuncu şifreyi çözüp sohbete ya da tabelaya yazdı mı?
 *
 * <h3>ÜÇ SONUÇ, ÜÇ FARKLI ÇIKARIM</h3>
 * <ul>
 *   <li><b>Gerçek jeton</b> → oyuncu çözdü. Candy'nin özgüveni yükselir (bilinmek onu
 *       güçlendirir), ışık bir kademe düşer.</li>
 *   <li><b>Tuzak satır</b> → çözdü ama hangisinin doğru olduğunu bilmiyor; üçünü de
 *       deniyor. Bu <i>ısrar</i> sinyali: merak yüksek, korku düşük.</li>
 *   <li><b>Hiç</b> → bir şey olmaz.</li>
 * </ul>
 *
 * <h3>NEDEN TUZAK SATIR AYRI SAYILIYOR</h3>
 * Dosyadan çıkan üç satır birbirinin aynısı görünüyor. Oyuncunun üçünü de yazması
 * bulmacayı anladığını ama kuralını bilmediğini gösterir — çözmüş olmaktan farklı bir
 * davranış, o yüzden farklı besleniyor. Doğru jetonu ilk denemede yazan oyuncu ise
 * ya şanslı ya dikkatli; ikisi de özgüveni besler.
 */
public final class SifreIzleyici {

    private SifreIzleyici() {}

    /** {@link #kontrol} sonucu. */
    public enum Sonuc { YOK, TUZAK, COZULDU }

    /**
     * Metinde jeton arar ve bulursa durumu günceller.
     *
     * <p>Yalnızca <b>dağıtılmış</b> indeksler aranır; dağıtılmamış bir jetonu tahmin
     * etmek mümkün değil ama arama maliyetini de boşa harcamayalım. Çözülmüş indeks
     * tekrar sayılmaz, yani aynı jetonu tabelaya on kez yazmak ışığı on kademe düşürmez.</p>
     *
     * @param kaynak log için: "sohbet" ya da "tabela"
     */
    public static Sonuc kontrol(ServerPlayer player, TruePersonality tp,
                                String metin, String kaynak) {
        if (metin == null || metin.isBlank()) return Sonuc.YOK;
        SifreAyar ayar = SifreAyar.al();
        if (!ayar.acik) return Sonuc.YOK;
        if (tp.sifreVerilen == 0) return Sonuc.YOK;
        if (!(player.level() instanceof ServerLevel dunya)) return Sonuc.YOK;

        final long tohum = dunya.getSeed();
        boolean tuzak = false;

        for (int i = 0; i < IkilikMetin.MAKS_SIFRE; i++) {
            if ((tp.sifreVerilen & (1 << i)) == 0) continue;
            final int gercek = IkilikMetin.gercekSatir(tohum, i);
            final String[] satirlar = IkilikMetin.satirlar(tohum, i);
            for (int s = 0; s < satirlar.length; s++) {
                if (!IkilikMetin.icerir(metin, satirlar[s])) continue;
                if (s == gercek) {
                    if ((tp.sifreCozulen & (1 << i)) != 0) return Sonuc.YOK;   // zaten sayıldı
                    cozuldu(player, tp, ayar, i, kaynak);
                    return Sonuc.COZULDU;
                }
                tuzak = true;
            }
        }

        if (tuzak) {
            israr(player, tp, kaynak);
            return Sonuc.TUZAK;
        }
        return Sonuc.YOK;
    }

    private static void cozuldu(ServerPlayer player, TruePersonality tp,
                                SifreAyar ayar, int indeks, String kaynak) {
        tp.sifreCozulen |= (1 << indeks);

        // Bilinmek Candy'yi güçlendirir — CandyChatOkur'un "Candy bahsi" kalıbıyla aynı yön.
        tp.candyOzguven = Math.min(100, tp.candyOzguven + 12);
        tp.psikolojiKotulugu = Math.min(100, tp.psikolojiKotulugu + 4);
        // Öfke DEĞİL: oyuncu ilgilendi, ihmal etmedi. Öfke ihmalden doğuyor.
        tp.ofkeAzalt(0.05);   // v8.3: Arkadaş yası tabanının altına inmez

        tp.isikDusumu = Math.min(ayar.maksIsikDusumu, tp.isikDusumu + ayar.isikDusumu);
        Karanlik.gonder(player, tp.isikDusumu);

        Candy.LOG.info("[Candy] şifre #{} çözüldü ({}) [{}] — ışık kademesi {}",
            indeks, kaynak, player.getName().getString(), tp.isikDusumu);
    }

    private static void israr(ServerPlayer player, TruePersonality tp, String kaynak) {
        // Merak var, korku yok: oyuncu kaçmıyor, deşifre etmeye çalışıyor.
        tp.candyOzguven = Math.min(100, tp.candyOzguven + 3);
        tp.korkuSkoru = Math.max(0, tp.korkuSkoru - 2);
        Candy.LOG.debug("[Candy] şifre tuzak satırı denendi ({}) [{}]",
            kaynak, player.getName().getString());
    }
}
