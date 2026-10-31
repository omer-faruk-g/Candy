package com.photonpulse.candy.beyin;

import com.photonpulse.candy.core.TruePersonality;

/**
 * Aort — kurulun tek besleme damarı: <b>aynı bilgiyi, aynı anda, herkese</b>.
 *
 * <h3>NEDEN AYRI BİR ORGAN</h3>
 * On bir beyin bir komite oluşturur. Komitenin değeri üyelerin <i>bağımsız</i>
 * olmasından gelir; ama bağımsızlık ancak <b>girdi aynıysa</b> anlam taşır. Farklı anlara
 * bakan iki beynin farklı sonuca varması bir görüş ayrılığı değil, sadece bir
 * senkronizasyon hatasıdır. Aort bu yüzden vektörü <b>bir kez</b> üretir, dondurur ve
 * herkese dağıtır.
 *
 * <h3>ÜÇ KURAL</h3>
 * <ol>
 *   <li><b>Tek nabız:</b> vektör darbe başına bir kez üretilir. Üye 3 işlenirken oyuncu
 *       hareket etse bile üye 3 ile üye 0 aynı anı görür.</li>
 *   <li><b>Takma yok:</b> her üye kendi <i>kopyasını</i> alır. Ortak bir dizi paylaşmak,
 *       bir üyenin girdisine yazmasının diğerine ulaşması demekti — yani gizli bir
 *       iletişim kanalı. Kopya bunu yapısal olarak imkânsız kılar.</li>
 *   <li><b>Kanıtlanabilir eşitlik:</b> dağıtılan vektörün sağlaması tutulur. "Aynı bilgi
 *       gitti" bir varsayım değil, {@link #butunlukTuttu} ile sınanan bir olgudur.</li>
 * </ol>
 *
 * <h3>NE TAŞIMAZ</h3>
 * İÇ DUYU taşınmaz. {@link IcDuyu} her beynin kendi karar geçmişinden, kendi gölge
 * arşivinden, kendi zaman izinden üretilir — özeldir. Aort dış dünyayı taşır, iç dünyayı
 * değil. Bir üyenin iç durumunun diğerine ulaşacağı hiçbir yol yoktur.
 */
public class Aort {

    /** Bu nabızda dağıtılan dondurulmuş duyu vektörü. */
    private final float[] kaynak = new float[Beyin.GIRDI_SAYISI];
    /** Kaç kez pompalandı — üyelerin kararları bu numarayla eşleşir. */
    private long nabiz = 0L;
    /** Dağıtılan vektörün sağlaması. */
    private long saglama = 0L;
    /** Bu nabızda kaç üyeye ulaştı. */
    private int ulasan = 0;
    /** Bütünlük ihlali sayacı — sıfırdan farklıysa bir yerde sessiz bir hata var. */
    private long ihlal = 0L;
    /** Henüz ilk nabzını almamış üye sayısı (ihlal değil, sadece yeni doğmuş). */
    private long beklemede = 0L;

    public Aort() {}

    /**
     * Yeni bir nabız: duyu vektörünü üretir ve dondurur.
     *
     * @return üretilen nabız numarası
     */
    public long pompala(TruePersonality tp) {
        BeyinGirdi.topla(tp, kaynak);
        for (int i = 0; i < kaynak.length; i++) {
            if (!Float.isFinite(kaynak[i])) kaynak[i] = 0f;
        }
        saglama = hesaplaSaglama(kaynak);
        ulasan = 0;
        return ++nabiz;
    }

    /**
     * Dondurulmuş vektörü bir üyeye teslim eder — <b>kopyalayarak</b>.
     *
     * @param hedef üyenin kendi girdi dizisi (yeniden kullanılır, çöp üretilmez)
     */
    public void dagit(Beyin uye) {
        if (uye == null) return;
        if (uye.girdi == null || uye.girdi.length != Beyin.GIRDI_SAYISI) {
            uye.girdi = new float[Beyin.GIRDI_SAYISI];
        }
        System.arraycopy(kaynak, 0, uye.girdi, 0, Beyin.GIRDI_SAYISI);
        uye.nabiz = nabiz;
        ulasan++;
    }

    /**
     * Bir üyenin elindeki vektör hâlâ dağıtılanla aynı mı?
     *
     * Sıradan bir hata ayıklama yardımcısı değil: komitenin TÜM anlamı üyelerin aynı
     * bilgiye bakmasına dayanır. Bu tutmuyorsa hakemin ürettiği her sonuç geçersizdir,
     * ve bu tür bir bozulma hiçbir istisna fırlatmadan sessizce olur.
     */
    public boolean butunlukTuttu(Beyin uye) {
        if (uye == null || uye.girdi == null) return false;
        // HENÜZ NABIZ ALMAMIŞ üye bir ihlal DEĞİLDİR: arka planda yeni doğmuş ve ilk
        // pompayı beklemektedir. İkisini karıştırmak sayacı anlamsızlaştırır —
        // "ihlal" gerçekten yanlış bilgiyle karar veren üye demek olmalı, yoksa her
        // sunucu açılışında 11 sahte alarm üretir ve gerçek bir bozulma fark edilmez.
        if (uye.nabiz == 0L) { beklemede++; return false; }
        boolean tamam = uye.nabiz == nabiz && hesaplaSaglama(uye.girdi) == saglama;
        if (!tamam) ihlal++;
        return tamam;
    }

    public long nabiz() { return nabiz; }
    public int ulasan() { return ulasan; }
    public long ihlal() { return ihlal; }
    public long beklemede() { return beklemede; }

    /**
     * FNV-1a benzeri sağlama. Kayan noktanın ham bit deseni üzerinden çalışır: 0,0 ile
     * -0,0 farklı sayılır, çünkü burada aranan sayısal eşitlik değil bit eşitliğidir.
     */
    private static long hesaplaSaglama(float[] v) {
        long h = 0xcbf29ce484222325L;
        for (float f : v) {
            h ^= Float.floatToRawIntBits(f) & 0xFFFFFFFFL;
            h *= 0x100000001b3L;
        }
        return h;
    }
}
