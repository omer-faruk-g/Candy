package com.photonpulse.candy.beyin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * IkiliKodlayici — beynin bağlantı haritasını kompakt ikili formatta yazar/okur.
 *
 * NEDEN İKİLİ? 87k sinapsı Gson ile yazmak yaklaşık 4-10 MB ve ana thread'de 150-400 ms
 * demekti — görünür bir donma. Üçlü paket ile aynı veri ~300 KB'a iner.
 *
 * PAKET v2 (4 byte / sinaps, tek writeInt — byte siniri kaydirmasi yok):
 * <pre>
 *   bit  0..16   kaynak indeksi  17 bit (adres uzayi 48 + 102.400 &lt; 131.072)
 *   bit 17..28   agirlik         12 bit (±4 araligi 4096 adima nicelenir, adim ≈ 0,00195)
 *   bit     29   norogenez ile dogdu (GOLGE arsivi + v3 delta formati icin)
 *   bit 30..31   ayrilmis
 * </pre>
 * v1 (3 byte, 12-bit indeks) 4096 noronda tikaniyordu; v2 ile 131k noron adreslenebilir.
 * Niceleme hatası (±0,001) öğrenme için tamamen görünmezdir; Oja güncellemesi zaten
 * bundan çok daha büyük adımlarla çalışır.
 *
 * {@code sinapsIz} kasten YAZILMAZ — kısa ömürlüdür, birkaç süpürmede yeniden dolar (%25 tasarruf).
 */
public class IkiliKodlayici {

    private static final int SIHIR = 0x43414E44;   // "CAND"
    private static final int FORMAT = 2;
    private static final float AGIRLIK_ARALIK = 4.0f;

    private IkiliKodlayici() {}

    public static void yaz(Beyin b, OutputStream ham) throws IOException {
        // BEST_SPEED: nicelenmis agirlik verisi zaten neredeyse sikismiyor; level 1 ayni
        // boyutu ~4x hizli uretir (yazici thread 200 ms yerine 50 ms mesgul).
        try (DataOutputStream o = new DataOutputStream(new HizliGzip(ham, 32768))) {
            o.writeInt(SIHIR);
            o.writeInt(FORMAT);
            o.writeInt(b.noronSayisi);
            o.writeInt(b.sinapsSayisi);
            // v2 basligi — v3 (tohum + delta) formatina gecerken kirilma olmasin diye
            // simdiden ayrildi.
            o.writeFloat(b.olcek);
            o.writeLong(b.topolojiTohumu);
            o.writeInt(b.topolojiSurumu);
            o.writeInt(0);   // yedek

            // Noron blogu — aktiflik YAZILMAZ (kisa omurlu, bir supurmede yeniden dolar)
            for (int i = 0; i < b.noronSayisi; i++) {
                o.writeFloat(b.esik[i]);
                o.writeByte(b.lobId[i]);
                o.writeByte(b.katman[i]);
            }
            // CSR offsetleri
            for (int i = 0; i <= b.noronSayisi; i++) o.writeInt(b.girisBasi[i]);

            // Sinapslar — 4 byte paket
            for (int j = 0; j < b.sinapsSayisi; j++) {
                int kaynak = b.kaynak[j] & 0x1FFFF;          // 17 bit
                int q = nicele(b.agirlik[j]) & 0x0FFF;       // 12 bit
                o.writeInt(kaynak | (q << 17));
            }
        }
    }

    /**
     * İkili bağlantı haritasını okur ve beyne yükler.
     * @return başarılıysa true; format/boyut uyuşmazsa false (çağıran yeniden inşa etmeli)
     */
    public static boolean oku(Beyin b, InputStream ham) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(ham, 16384))) {
            if (in.readInt() != SIHIR) return false;
            int format = in.readInt();
            // v1 (3 byte / 12-bit indeks) artik desteklenmiyor: yeni topolojide noron i'nin
            // karsiligi yok, agirlik aktarimi anlamsiz. BeyinGocu kunye alanlarini tasir.
            if (format != FORMAT) return false;
            int n = in.readInt();
            int m = in.readInt();
            if (n <= 0 || n > Beyin.MAX_NORON || m <= 0 || m > Beyin.MAX_SINAPS) return false;
            float okOlcek = in.readFloat();
            long okTohum = in.readLong();
            int okTopoloji = in.readInt();
            in.readInt();   // yedek

            float[] esik = new float[n];
            byte[] lobId = new byte[n];
            byte[] katman = new byte[n];
            for (int i = 0; i < n; i++) {
                esik[i] = in.readFloat();
                lobId[i] = in.readByte();
                katman[i] = in.readByte();
            }
            int[] girisBasi = new int[n + 1];
            for (int i = 0; i <= n; i++) girisBasi[i] = in.readInt();

            int[] kaynak = new int[m];
            float[] agirlik = new float[m];
            for (int j = 0; j < m; j++) {
                int paket = in.readInt();
                kaynak[j] = paket & 0x1FFFF;
                agirlik[j] = coz((paket >>> 17) & 0x0FFF);
            }
            float[] aktiflik = new float[n];   // kisa omurlu, sifirdan dogar
            b.olcek = okOlcek;
            b.topolojiTohumu = okTohum;
            b.topolojiSurumu = okTopoloji;

            b.noronSayisi = n;
            b.sinapsSayisi = m;
            b.aktiflik = aktiflik;
            b.esik = esik;
            b.lobId = lobId;
            b.katman = katman;
            b.girisBasi = girisBasi;
            b.kaynak = kaynak;
            b.agirlik = agirlik;
            b.sinapsIz = new float[m];   // iz diske yazılmaz, sıfırdan doğar
            return true;
        }
    }

    /** GZIP cikisi, sikistirma seviyesi 1 (BEST_SPEED). */
    private static final class HizliGzip extends GZIPOutputStream {
        HizliGzip(OutputStream out, int tampon) throws IOException {
            super(out, tampon);
            def.setLevel(java.util.zip.Deflater.BEST_SPEED);
        }
    }

    /** ±4 → 0..4095 */
    private static int nicele(float w) {
        if (!Float.isFinite(w)) w = 0f;
        w = Math.max(-AGIRLIK_ARALIK, Math.min(AGIRLIK_ARALIK, w));
        int q = Math.round((w + AGIRLIK_ARALIK) / (2f * AGIRLIK_ARALIK) * 4095f);
        return Math.max(0, Math.min(4095, q));
    }

    /** 0..4095 → ±4 */
    private static float coz(int q) {
        return (q / 4095f) * (2f * AGIRLIK_ARALIK) - AGIRLIK_ARALIK;
    }

    /** Beklenen ikili boyut (bayt, sıkıştırma öncesi) — planlama/izleme için. */
    public static int hamBoyut(Beyin b) {
        return 32 + b.noronSayisi * 6 + (b.noronSayisi + 1) * 4 + b.sinapsSayisi * 4;
    }
}
