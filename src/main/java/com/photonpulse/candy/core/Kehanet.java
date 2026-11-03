package com.photonpulse.candy.core;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Kehanet (v3.2) — Candy'nin öngörü motoru. Reaktiften ÖNGÖRÜCÜYE geçiş.
 *
 * IcDusunce her niyet güncellemesinde {@link #gozlemle} çağırır. Bu sınıf:
 *   1. Önceki öngörüyü gerçek niyetle karşılaştırır → isabet oranı (kehanetGuveni).
 *   2. Niyet geçiş tablosunu (Markov) öğrenir — "kaziyor'dan sonra ne geliyor".
 *   3. Bir sonraki niyeti ÖNCEDEN yazar (kehanetNiyet). Tuttuğunda ve güven yüksekse
 *      ifşa eder: "bunu yapacağını yazmıştım." → korku, çünkü önceden bilinmek ürkütücü.
 *   4. Şaşırma (idea 4): emin olduğu bir öngörü tutmazsa dikkat sıçrar (sasirmaSeviyesi);
 *      Candy "bunu beklemiyordum" der ve modeli daha hızlı günceller.
 *
 * Tamamen server-side, canlı akıl (öngörü persist edilir, hesap anlık).
 */
public class Kehanet {

    private static final Random RNG = new Random();
    private static final long IFSA_COOLDOWN = 2400L;   // min 2 dk iki ifşa arası
    private static final long SASIRMA_COOLDOWN = 1200L;
    private static final double MIN_GECIS = 2.0;        // öngörü için min birikmiş kanıt

    // Şaşırma yorumu spam engeli (per-player, canlı)
    private static final java.util.Map<java.util.UUID, Long> sonSasirmaYorum = new java.util.HashMap<>();

    /**
     * IcDusunce'den her niyet güncellemesinde (~2 sn) çağrılır.
     * @param gercekNiyet o an gözlenen gerçek niyet ("bilinmiyor" ise atla)
     */
    public static void gozlemle(ServerPlayer player, TruePersonality tp,
                                String gercekNiyet, long simdi) {
        if (gercekNiyet == null || "bilinmiyor".equals(gercekNiyet)) return;

        // Sadece niyet DEĞİŞTİĞİNDE değerlendir. Aynı niyet sürerken (örn. uzun kazma)
        // bekleyen "sonraki niyet" öngörüsünü her 2 sn'de tekrar puanlamak onu haksız yere
        // ıskaladı sayar, güveni çökertir ve şaşırma spam'i yapardı.
        boolean gecis = tp.oncekiNiyetKehanet != null
            && !tp.oncekiNiyetKehanet.isEmpty()
            && !tp.oncekiNiyetKehanet.equals(gercekNiyet);
        if (!gecis) {
            tp.oncekiNiyetKehanet = gercekNiyet; // ilk gözlem / aynı niyet — sessizce kaydet
            return;
        }

        // 1) Önceki öngörüyü PUANLA (bu geçişi doğru bildi mi?)
        if (tp.kehanetNiyet != null && !tp.kehanetNiyet.isEmpty()) {
            boolean tuttu = tp.kehanetNiyet.equals(gercekNiyet);
            tp.kehanetToplam++;
            if (tuttu) tp.kehanetIsabet++;
            tp.kehanetGuveni = 100.0 * tp.kehanetIsabet / Math.max(1, tp.kehanetToplam);

            if (tuttu) {
                // Tuttu + güven yüksek + emin öngörü + cooldown → ifşa
                if (tp.kehanetGuveni > 55 && tp.kehanetOlasilik > 0.5
                        && simdi - tp.sonKehanetIfsa > IFSA_COOLDOWN
                        && tp.bossOldurulmeTick == 0
                        && RNG.nextDouble() < 0.30) {
                    tp.sonKehanetIfsa = simdi;
                    ifsaEt(player, gercekNiyet);
                }
            } else {
                // Şaşırma: ne kadar emindiysek o kadar büyük öngörü hatası (idea 4)
                double sasirma = tp.kehanetOlasilik * 100.0;
                tp.sasirmaSeviyesi = Math.min(100, tp.sasirmaSeviyesi + sasirma * 0.6);
                if (tp.sasirmaSeviyesi > 55) {
                    java.util.UUID id = player.getUUID();
                    long sonY = sonSasirmaYorum.getOrDefault(id, -99999L);
                    if (simdi - sonY > SASIRMA_COOLDOWN && tp.bossOldurulmeTick == 0
                            && RNG.nextDouble() < 0.25) {
                        sonSasirmaYorum.put(id, simdi);
                        player.displayClientMessage(Component.translatable("candy.sasirma." + RNG.nextInt(2))
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
                        Candy.LOG.info("[Candy] Kehanet ŞAŞIRMA[{}] öngörü={} gerçek={}",
                                player.getName().getString(), tp.kehanetNiyet, gercekNiyet);
                    }
                }
            }
        }

        // 2) Markov geçişini öğren: oncekiNiyet -> gercekNiyet
        String anahtar = tp.oncekiNiyetKehanet + ">" + gercekNiyet;
        tp.niyetGecisleri.merge(anahtar, 1.0, Double::sum);
        if (tp.niyetGecisleri.size() > 60) {   // eski geçişler sönsün, tablo şişmesin
            tp.niyetGecisleri.replaceAll((k, v) -> v * 0.9);
            tp.niyetGecisleri.entrySet().removeIf(e -> e.getValue() < 0.5);
        }

        // 3) YENİ öngörü + kaydı ilerlet
        tahminEt(tp, gercekNiyet, simdi);
        tp.oncekiNiyetKehanet = gercekNiyet;
        tp.sasirmaSeviyesi = Math.max(0, tp.sasirmaSeviyesi - 1.5);
    }

    /** Markov tablosundan "suanki niyet"in en olası ardılını öngör. */
    private static void tahminEt(TruePersonality tp, String suanki, long simdi) {
        double toplam = 0, enIyiW = 0;
        String enIyi = "";
        String onEk = suanki + ">";
        for (var e : tp.niyetGecisleri.entrySet()) {
            if (!e.getKey().startsWith(onEk)) continue;
            toplam += e.getValue();
            if (e.getValue() > enIyiW) {
                enIyiW = e.getValue();
                enIyi = e.getKey().substring(onEk.length());
            }
        }
        if (enIyi.isEmpty() || toplam < MIN_GECIS) {
            tp.kehanetNiyet = "";   // yeterli veri yok
            tp.kehanetOlasilik = 0.0;
            return;
        }
        tp.kehanetNiyet = enIyi;
        tp.kehanetOlasilik = enIyiW / toplam;   // 0-1
        tp.kehanetTick = simdi;
    }

    private static void ifsaEt(ServerPlayer player, String niyet) {
        String key = switch (niyet) {
            case "kaziyor", "insa_ediyor", "kaciyor", "savasiyor",
                 "bekliyor", "donuyor", "kesfediyor" -> "candy.kehanet." + niyet;
            default -> "candy.kehanet.genel";
        };
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
        CandyBasarimlar.tetikle(player, "zihnimi_okudun");
        Candy.LOG.info("[Candy] Kehanet İFŞA[{}] niyet={} (güven={}%)",
                player.getName().getString(), niyet, String.format("%.0f", player == null ? 0 :
                        PersonalityManager.get(player).kehanetGuveni));
    }

    public static void oyuncuAyrildi(java.util.UUID uuid) {
        sonSasirmaYorum.remove(uuid);
    }
}
