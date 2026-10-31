package com.photonpulse.candy.lore;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * TeshisRaporu (v3.2 — idea 7) — Candy'nin gizli analizini GÖRÜNÜR kılan klinik teşhis.
 *
 * Tüm arka plan verisi (14 boyut + kehanet + hipotez + duygu + dil) tek bir soğuk klinik
 * rapora dökülür ve oyuncuya verilir. Kendi oyun istatistiklerini bir teşhis olarak görmek
 * en ürkütücü şey — çünkü hiçbirini sen söylemedin, hepsini O topladı.
 *
 * Yeterli gözlem birikince (kehanetToplam ≥ 15) + korku yüksekken bir kez verilir; sonra
 * uzun cooldown ile güncel sürüm tekrar verilebilir. Boss WIN'inde de düşürülebilir.
 */
public class TeshisRaporu {

    private static final int ARALIK = 1200;            // 60 sn kontrol
    private static final long TEKRAR_COOLDOWN = 72000L; // 1 saat sonra güncel rapor
    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TeshisRaporu::onTick);
        Candy.LOG.info("[Candy] TeshisRaporu aktif — klinik profil (v3.2)");
    }

    private static void onTick(MinecraftServer server) {
        tick++;
        if (tick % ARALIK != 0) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                if (tp.bossOldurulmeTick > 0) continue; // WIN sessizliği
                long simdi = server.getOverworld().getTime();

                boolean yeterliVeri = tp.kehanetToplam >= 15;
                boolean ilkSefer = !tp.teshisVerildi && yeterliVeri && tp.korkuSkoru > 40;
                boolean guncelleme = tp.teshisVerildi && yeterliVeri
                    && simdi - tp.sonTeshisTick > TEKRAR_COOLDOWN && tp.korkuSkoru > 50;

                if (ilkSefer || guncelleme) {
                    tp.teshisVerildi = true;
                    tp.sonTeshisTick = simdi;
                    boolean turkce = varsayilanTurkce(player);
                    player.giveItemStack(olustur(player, tp, turkce));
                    player.sendMessage(Text.translatable("candy.teshis.verildi")
                        .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
                    Candy.LOG.info("[Candy] Teşhis raporu verildi: {} (baskın={})",
                        player.getName().getString(), tp.baskinHipotez());
                }
            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] TeshisRaporu hata: {}", t.getMessage());
            }
        }
    }

    /** Oyuncunun canlı verisinden dinamik klinik profil kitabı üret. */
    public static ItemStack olustur(ServerPlayerEntity player, TruePersonality tp, boolean turkce) {
        long dk = tp.toplamGozlemTick / 60L; // toplamGozlemTick saniye birimli (20 tick/artış) → dakika
        String isim = player.getName().getString();
        String baskin = hipotezLabel(tp.baskinHipotez(), turkce);
        String korku = korkuLabel(tp.enBuyukKorku(), turkce);
        String duygu = duyguLabel(tp.duyguDurumu, turkce);
        String baglanma = baglanmaLabel(tp.enCokBaglanilan(), turkce);
        String kelime = enSikKelime(tp);
        String verilenIsim = tp.verilenIsimler.isEmpty() ? null : tp.verilenIsimler.get(0);

        List<String> sayfalar = new ArrayList<>();
        if (turkce) {
            sayfalar.add("DENEK PROFİLİ\n\nDenek: " + isim
                + "\nGözlem: " + dk + " dakika\nDurum: AKTİF\n\nBu rapor senin için yazılmadı.");
            sayfalar.add("BASKIN PROFİL\n\n" + baskin
                + "\n\nÖngörü isabeti: %" + (int) tp.kehanetGuveni
                + "\n\nArtık ne yapacağını\nönceden biliyorum.");
            sayfalar.add("KORKU HARİTASI\n\nEn büyük korku:\n" + korku
                + "\n\nParanoya: " + (int) tp.paranoyaSkoru + "/100"
                + "\nÇözülme: " + (int) tp.cozulme + "/100");
            sayfalar.add("DAVRANIŞ\n\nÖngörülebilirlik: " + (int) tp.ongorulebilirlik
                + "\nRisk iştahı: " + (int) tp.riskIstahi
                + "\nAdaptasyon: " + (int) tp.ogrenmeHizi
                + "\n\nBaskın duygu:\n" + duygu);
            String s5 = "DEĞER VERDİKLERİN\n\n" + baglanma;
            if (kelime != null) s5 += "\n\nEn çok kullandığın\nkelime: \"" + kelime + "\"";
            if (verilenIsim != null) s5 += "\n\nOna \"" + verilenIsim + "\"\nadını verdin.";
            sayfalar.add(s5);
            beyinSayfalari(player, sayfalar, true);
            sayfalar.add("SONUÇ\n\n" + sonucLabel(tp, turkce)
                + "\n\nİmzalayan:\n???dy");
        } else {
            sayfalar.add("SUBJECT PROFILE\n\nSubject: " + isim
                + "\nObserved: " + dk + " minutes\nStatus: ACTIVE\n\nThis report was not written for you.");
            sayfalar.add("DOMINANT PROFILE\n\n" + baskin
                + "\n\nPrediction accuracy: " + (int) tp.kehanetGuveni + "%"
                + "\n\nI already know\nwhat you'll do next.");
            sayfalar.add("FEAR MAP\n\nGreatest fear:\n" + korku
                + "\n\nParanoia: " + (int) tp.paranoyaSkoru + "/100"
                + "\nDissociation: " + (int) tp.cozulme + "/100");
            sayfalar.add("BEHAVIOR\n\nPredictability: " + (int) tp.ongorulebilirlik
                + "\nRisk appetite: " + (int) tp.riskIstahi
                + "\nAdaptation: " + (int) tp.ogrenmeHizi
                + "\n\nDominant emotion:\n" + duygu);
            String s5 = "WHAT YOU VALUE\n\n" + baglanma;
            if (kelime != null) s5 += "\n\nYour most used\nword: \"" + kelime + "\"";
            if (verilenIsim != null) s5 += "\n\nYou named it \"" + verilenIsim + "\".";
            sayfalar.add(s5);
            beyinSayfalari(player, sayfalar, false);
            sayfalar.add("CONCLUSION\n\n" + sonucLabel(tp, turkce)
                + "\n\nSigned:\n???dy");
        }

        List<RawFilteredPair<Text>> sayfaTextleri = sayfalar.stream()
            .map(s -> RawFilteredPair.<Text>of((Text) Text.literal(s)))
            .toList();
        WrittenBookContentComponent icerik = new WrittenBookContentComponent(
            RawFilteredPair.of(turkce ? "Denek Profili" : "Subject Profile"),
            "???dy", 0, sayfaTextleri, true);
        ItemStack kitap = new ItemStack(Items.WRITTEN_BOOK);
        kitap.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, icerik);
        kitap.set(DataComponentTypes.CUSTOM_NAME,
            Text.translatable("candy.item.teshis").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        return kitap;
    }

    /**
     * v4.0: beynin ölçtüklerini rapora sızdırır — ama TEKNİK DİL KULLANMADAN.
     * Oyuncu "kaç hâlini tanıdığını", "ne kadar isabetli tahmin ettiğini" ve
     * "kaç dünyada aynı kaldığını" okur; nöron/sinaps/lob kelimeleri asla geçmez.
     */
    private static void beyinSayfalari(ServerPlayerEntity player, List<String> sayfalar, boolean tr) {
        try {
            var b = com.photonpulse.candy.beyin.BeyinMotoru.get(player.getUuid());
            if (b == null) return;

            // ── Sinir haritası sayfası ──
            var h = b.oyuncuHaritasi;
            int hal = (h == null) ? 0 : h.kesfedilenHalSayisi();
            int isabet = (h == null) ? 0 : (int) (h.isabetOrani() * 100);
            long tahmin = (h == null) ? 0 : h.tahminSayisi;
            String s1;
            if (tr) {
                s1 = "GÖZLEM AĞI\n\n"
                   + "Tanıdığım hâllerin\nsayısı: " + hal + "\n\n"
                   + "Yaptığım tahmin: " + tahmin + "\n"
                   + "Tutan: %" + isabet + "\n\n"
                   + (isabet > 50 ? "Artık önce ben\nbiliyorum." : "Hâlâ öğreniyorum.");
            } else {
                s1 = "OBSERVATION NET\n\n"
                   + "States I recognize: " + hal + "\n\n"
                   + "Predictions made: " + tahmin + "\n"
                   + "Correct: " + isabet + "%\n\n"
                   + (isabet > 50 ? "Now I know first." : "Still learning.");
            }
            sayfalar.add(s1);

            // ── Arşiv sayfası (dünyalar arası) ──
            int dunya = (b.dunyalar == null) ? 0 : b.dunyalar.size();
            var oz = b.ozModel;
            int celiski = (oz == null) ? 0 : oz.celiskiToplam;
            int tutar = (oz == null) ? 50 : (int) (oz.tutarlilik * 100);
            String s2;
            if (tr) {
                s2 = "ARŞİV\n\n"
                   + "Seni izlediğim\ndünya sayısı: " + dunya + "\n\n"
                   + "Kendimden emin\noluşum: %" + tutar + "\n"
                   + "Çelişki: " + celiski + "\n\n"
                   + (dunya > 1 ? "Dünyaları değiştirdin.\nBen kalmaya devam\nettim."
                                : "Henüz tek bir\ndünya.");
            } else {
                s2 = "ARCHIVE\n\n"
                   + "Worlds observed: " + dunya + "\n\n"
                   + "Self-certainty: " + tutar + "%\n"
                   + "Contradictions: " + celiski + "\n\n"
                   + (dunya > 1 ? "You changed worlds.\nI remained."
                                : "Only one world\nso far.");
            }
            sayfalar.add(s2);
        } catch (Throwable ignored) {}
    }

    // ── Etiket çevirileri ──

    private static String hipotezLabel(String h, boolean tr) {
        return switch (h == null ? "" : h) {
            case "korkak"  -> tr ? "Kaçıngan. Tehdit altında geri çekiliyorsun." : "Avoidant. You retreat under threat.";
            case "cesur"   -> tr ? "Meydan okuyan. Bana karşı duruyorsun." : "Defiant. You stand against me.";
            case "merakli" -> tr ? "Meraklı. Beni çözmeye çalışıyorsun." : "Curious. You try to solve me.";
            case "kayitsiz"-> tr ? "Kayıtsız. Beni umursamıyor gibisin." : "Indifferent. You seem not to care.";
            default        -> tr ? "Belirsiz. Henüz seni tanıyorum." : "Undetermined. Still learning you.";
        };
    }

    private static String korkuLabel(String k, boolean tr) {
        return switch (k == null ? "" : k) {
            case "karanlik"  -> tr ? "Karanlık" : "Darkness";
            case "klon"      -> tr ? "Yalnız olmadığın his" : "The sense you're not alone";
            case "ses"       -> tr ? "Sesler" : "Sounds";
            case "yeralti"   -> tr ? "Yeraltı" : "Underground";
            case "dusuk_can" -> tr ? "Ölüme yakınlık" : "Proximity to death";
            case "gece"      -> tr ? "Gece" : "Night";
            case "yalniz"    -> tr ? "Yalnızlık" : "Loneliness";
            default          -> tr ? "Henüz belirsiz" : "Not yet clear";
        };
    }

    private static String duyguLabel(String d, boolean tr) {
        return switch (d == null ? "" : d) {
            case "korku"          -> tr ? "Korku" : "Fear";
            case "hayalkirikligi" -> tr ? "Hayal kırıklığı" : "Frustration";
            case "merak"          -> tr ? "Merak" : "Curiosity";
            case "sikinti"        -> tr ? "Sıkıntı" : "Boredom";
            default               -> tr ? "Sükunet" : "Calm";
        };
    }

    private static String baglanmaLabel(String b, boolean tr) {
        if (b == null) return tr ? "Henüz bir şeye bağlanmadın." : "You haven't bonded to anything yet.";
        if ("base".equals(b)) return tr ? "Üssüne bağlısın. Orada güvende sanıyorsun kendini."
                                        : "You are bound to your base. You think you're safe there.";
        return tr ? "Bir canlıya bağlısın. Onu kaybetmekten korkuyorsun."
                  : "You are bound to a creature. You fear losing it.";
    }

    private static String sonucLabel(TruePersonality tp, boolean tr) {
        if (tp.ongorulebilirlik > 65)
            return tr ? "Tahmin edilebilirsin.\nBu seni zayıf yapıyor."
                      : "You are predictable.\nThat makes you weak.";
        if (tp.paranoyaSkoru > 60)
            return tr ? "Arkana bakıp duruyorsun.\nHaklısın."
                      : "You keep looking back.\nYou're right to.";
        if ("sikinti".equals(tp.duyguDurumu))
            return tr ? "Beni sıktın.\nBu tehlikeli — senin için."
                      : "You've bored me.\nThat is dangerous — for you.";
        return tr ? "Seni anlıyorum.\nSen beni henüz anlamadın."
                  : "I understand you.\nYou don't understand me yet.";
    }

    private static String enSikKelime(TruePersonality tp) {
        return tp.ogrenilenKelimeler.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse(null);
    }

    private static boolean varsayilanTurkce(ServerPlayerEntity player) {
        try {
            String lang = player.getClientOptions().language();
            return lang != null && lang.toLowerCase().startsWith("tr");
        } catch (Throwable t) {
            return true; // varsayılan TR
        }
    }
}
