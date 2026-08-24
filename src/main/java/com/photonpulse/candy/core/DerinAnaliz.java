package com.photonpulse.candy.core;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * DerinAnaliz (v3.0 — Analiz 2.0) — Candy'nin derin zekâsı.
 *
 * ObservationEngine ham sinyalleri toplar; bu sınıf onlardan üst-seviye ÇIKARIM yapar:
 * öngörülebilirlik, kişisel korku tetikleyicileri, bağlanmalar, Candy'ye tepki, rutin,
 * yetenek, çözülme, risk, güven, öğrenme, tilt, konfor alanı, ölüm tepkisi, gerçek zaman.
 *
 * Tüm hesap server-side; mixin yok. Her saniye çalışır.
 */
public class DerinAnaliz {

    private static final int ARALIK = 20; // 1 sn
    private static int tick = 0;

    // Per-player geçici cache'ler
    private static final Map<UUID, Vec3d> oncekiPoz = new HashMap<>();
    private static final Map<UUID, Float> oncekiCan = new HashMap<>();
    private static final Map<UUID, Double> oncekiKorku = new HashMap<>();
    private static final Map<UUID, Vec3d> oncekiYon = new HashMap<>();
    private static final Map<UUID, Float> oncekiYaw = new HashMap<>();   // v3.2 paranoya

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DerinAnaliz::onTick);

        // Yetenek — isabetli vuruş sayacı
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (player instanceof ServerPlayerEntity sp && target instanceof LivingEntity) {
                PersonalityManager.get(sp).toplamVurus++;
            }
            return ActionResult.PASS;
        });

        // Ölüm — konum + tepki takibi + yetenek
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            TruePersonality tp = PersonalityManager.get(player);
            tp.toplamOlum++;
            BlockPos p = player.getBlockPos();
            tp.sonOlumKonumu = new int[]{p.getX(), p.getY(), p.getZ()};
            tp.sonOlumTick = player.getWorld().getTime();
            tp.olumSonrasiTepki = "bilinmiyor"; // dönüşe göre tekrar belirlenecek
            // Ölüm = büyük çözülme sıçraması
            tp.cozulme = Math.min(100, tp.cozulme + 25);
        });

        // Chat analizi — (9) güven + (11) tilt. Dil-nötr: küfür listesi yok.
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.CHAT_MESSAGE.register(
            (message, sender, params) -> {
                try {
                    TruePersonality tp = PersonalityManager.get(sender);
                    String metin = message.getContent().getString();
                    String kucuk = metin.toLowerCase();

                    // (9) Steve/Alex'ten bahsediyorsa hayaletlerle ilgileniyor → güven artar
                    if (kucuk.contains("steve") || kucuk.contains("alex")) {
                        tp.hayaletGuveni = Math.min(100, tp.hayaletGuveni + 4);
                    }

                    // (11) Ajitasyon: büyük harf oranı yüksek veya çok ünlem → tilt
                    long harf = metin.chars().filter(Character::isLetter).count();
                    long buyuk = metin.chars().filter(Character::isUpperCase).count();
                    long unlem = metin.chars().filter(c -> c == '!').count();
                    if ((harf >= 4 && buyuk > harf * 0.6) || unlem >= 2) {
                        tp.tiltSeviyesi = Math.min(100, tp.tiltSeviyesi + 8);
                    }
                } catch (Throwable ignored) {}
            });

        // Yeni oturum = geç saat uyarısı için yeni şans. (Aksi halde bayrak kalıcı
        // kalıp mesaj o dünyada bir daha ASLA gösterilmiyordu.)
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
            (handler, sender, server) -> {
                TruePersonality tp = PersonalityManager.get(handler.player);
                tp.gecSaatUyarisiVerildi = false;
                tp.oturumBaslangicTick = 0L; // ilk tick'te yeniden kurulur
            });

        Candy.LOG.info("[Candy] DerinAnaliz aktif — 14 boyutlu oyuncu zekâsı (v3.0)");
    }

    private static void onTick(MinecraftServer server) {
        tick++;
        if (tick % ARALIK != 0) return;

        int oyuncuSayisi = server.getPlayerManager().getPlayerList().size();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                UUID id = player.getUuid();
                ServerWorld world = (ServerWorld) player.getWorld();
                BlockPos pos = player.getBlockPos();
                Vec3d simdiPoz = player.getPos();

                if (tp.oturumBaslangicTick == 0) tp.oturumBaslangicTick = world.getTime();

                // Hız + yön değişimi
                Vec3d oncekiP = oncekiPoz.get(id);
                double hiz = (oncekiP == null) ? 0 : simdiPoz.distanceTo(oncekiP);
                Vec3d yon = (oncekiP == null) ? Vec3d.ZERO : simdiPoz.subtract(oncekiP);
                Vec3d oncekiY = oncekiYon.getOrDefault(id, Vec3d.ZERO);
                double yonDegisim = (oncekiY.lengthSquared() > 0.0001 && yon.lengthSquared() > 0.0001)
                    ? 1.0 - (yon.normalize().dotProduct(oncekiY.normalize()))
                    : 0.0; // 0=aynı yön, 2=tam ters
                oncekiPoz.put(id, simdiPoz);
                if (yon.lengthSquared() > 0.0001) oncekiYon.put(id, yon);

                long timeOfDay = world.getTimeOfDay() % 24000L;
                boolean gece = timeOfDay > 13000L && timeOfDay < 23000L;
                int saatDilimi = (int) (timeOfDay / 4000L); // 0-5
                int isik = world.getLightLevel(pos);
                float canOran = player.getHealth() / player.getMaxHealth();
                boolean yeralti = player.getBlockY() < 50;

                // Entity taramalarını tick başına BİR kez yap (aşağıda tekrar kullanılır)
                boolean klonYakin = klonYakinMi(world, player, 24);
                boolean hostileYakin = hostileYakinMi(world, player, 12);

                // Baskın aktivite
                String aktivite = baskinAktivite(tp);

                // ── (5) Rutin ──
                tp.saatlikRutin.put(saatDilimi, aktivite);

                // ── (1) Öngörülebilirlik ──
                int imza = Objects.hash(pos.getX() >> 4, pos.getZ() >> 4, aktivite, saatDilimi);
                tp.davranisImzalari.add(imza);
                if (tp.davranisImzalari.size() > 30) tp.davranisImzalari.remove(0);
                long tekrar = tp.davranisImzalari.stream().filter(x -> x == imza).count();
                double ongoruHedef = Math.min(100, (tekrar / (double) tp.davranisImzalari.size()) * 100);
                tp.ongorulebilirlik = TruePersonality.kayma(tp.ongorulebilirlik, ongoruHedef, 0.1);

                // Öngörülebilirlik yüksekse Candy sıkılır → özgüven artar (senin felsefen)
                if (tp.ongorulebilirlik > 70) {
                    tp.candyOzguven = Math.min(100, tp.candyOzguven + 0.05);
                }

                // ── (2) Tetikleyici Profili — korku sıçradı mı? ──
                double oncKorku = oncekiKorku.getOrDefault(id, tp.korkuSkoru);
                double korkuArtis = tp.korkuSkoru - oncKorku;
                oncekiKorku.put(id, tp.korkuSkoru);
                if (korkuArtis > 1.5) {
                    // O an ne vardı? Bağlamı tetikleyici olarak kaydet
                    if (isik <= 4) tetikle(tp, "karanlik", korkuArtis);
                    if (yeralti) tetikle(tp, "yeralti", korkuArtis);
                    if (gece) tetikle(tp, "gece", korkuArtis);
                    if (canOran < 0.4) tetikle(tp, "dusuk_can", korkuArtis);
                    if (oyuncuSayisi <= 1) tetikle(tp, "yalniz", korkuArtis * 0.5);
                    if (klonYakin) tetikle(tp, "klon", korkuArtis);
                }

                // ── (4) Candy'ye tepki — yakında klon varsa davranışı örnekle ──
                if (klonYakin) {
                    if (player.isSprinting() && hiz > 0.15)
                        tp.candydenKacma = TruePersonality.kayma(tp.candydenKacma, 1.0, 0.05);
                    else if (hiz < 0.02)
                        tp.candydeDonma = TruePersonality.kayma(tp.candydeDonma, 1.0, 0.05);
                    else if (tp.saldirOrani > 0.4)
                        tp.candyyeSaldiri = TruePersonality.kayma(tp.candyyeSaldiri, 1.0, 0.05);
                    else
                        tp.candyGormezden = TruePersonality.kayma(tp.candyGormezden, 1.0, 0.05);
                }

                // ── (3) Bağlanma Haritası — yakındaki pet / base ──
                baglanmaGuncelle(tp, world, player);

                // ── (6) Yetenek + hasar örneklemesi ──
                float oncCan = oncekiCan.getOrDefault(id, player.getHealth());
                if (player.getHealth() < oncCan - 0.5f) tp.alinanHasarOlayi++;
                oncekiCan.put(id, player.getHealth());
                // Yetenek skoru: çok vuruş + az ölüm + az hasar alma = yüksek
                double yetenekHedef = 50
                    + Math.min(30, tp.toplamVurus * 0.2)
                    - Math.min(30, tp.toplamOlum * 5)
                    - Math.min(20, tp.alinanHasarOlayi * 0.1);
                tp.yetenekSkoru = TruePersonality.kayma(tp.yetenekSkoru,
                    Math.max(0, Math.min(100, yetenekHedef)), 0.05);

                // ── (7) Çözülme — korku yüksek + düzensiz hareket = artar ──
                double cozulmeHedef = (tp.korkuSkoru * 0.4) + (yonDegisim * 30) + (tp.tiltSeviyesi * 0.3);
                tp.cozulme = TruePersonality.kayma(tp.cozulme, Math.min(100, cozulmeHedef), 0.03);

                // ── (8) Risk İştahı — düşük canla tehlikede aktif ──
                if (canOran < 0.4 && (yeralti || hostileYakin) && hiz > 0.05) {
                    tp.riskIstahi = TruePersonality.kayma(tp.riskIstahi, 100, 0.02);
                } else if (canOran > 0.8) {
                    tp.riskIstahi = TruePersonality.kayma(tp.riskIstahi, 40, 0.005);
                }

                // ── (11) Tilt — düzensiz hareket + düşük ilerleme ──
                if (yonDegisim > 1.2 && hiz > 0.1) {
                    tp.tiltSeviyesi = Math.min(100, tp.tiltSeviyesi + 0.5);
                } else {
                    tp.tiltSeviyesi = Math.max(0, tp.tiltSeviyesi - 0.2);
                }

                // ── (12) Konfor Alanı — sakin, gündüz, hasarsız, hareketsiz ──
                if (hiz < 0.05 && !gece && canOran > 0.9 && !hostileYakin) {
                    tp.konforSkoru = Math.min(100, tp.konforSkoru + 0.5);
                    if (tp.konforAlani == null || tp.konforSkoru > 60) {
                        tp.konforAlani = new int[]{pos.getX(), pos.getY(), pos.getZ()};
                    }
                } else {
                    tp.konforSkoru = Math.max(0, tp.konforSkoru - 0.1);
                }

                // ── (13) Ölüm sonrası tepki ──
                if (tp.sonOlumKonumu != null) {
                    long gecen = world.getTime() - tp.sonOlumTick;
                    double dOlum = pos.getSquaredDistance(
                        tp.sonOlumKonumu[0], tp.sonOlumKonumu[1], tp.sonOlumKonumu[2]);
                    if (gecen < 3600 && dOlum < 100) { // 3 dk içinde 10 blok yakın
                        tp.olumSonrasiTepki = "intikamci";
                    } else if (gecen > 12000 && "bilinmiyor".equals(tp.olumSonrasiTepki)) {
                        tp.olumSonrasiTepki = "kacinan";
                    }
                }

                // ══ v3.2 Bilişsel Motor v4 ek boyutları ══

                // ── (idea 5) Paranoya — arkana/etrafına bakma sıklığı ──
                float yaw = player.getYaw();
                Float oncYaw = oncekiYaw.get(id);
                oncekiYaw.put(id, yaw);
                double yawDelta = (oncYaw == null) ? 0
                    : Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(yaw - oncYaw));
                boolean arkayaBakti = yawDelta > 60; // ani büyük bakış dönüşü
                if (arkayaBakti && (gece || isik <= 4 || klonYakin)) {
                    tp.paranoyaSkoru = Math.min(100, tp.paranoyaSkoru + 3.0);
                } else {
                    tp.paranoyaSkoru = Math.max(0, tp.paranoyaSkoru - 0.15);
                }

                // ── (idea 11) Duygu Aynası — affect vektörü ──
                tp.duyguKorku = TruePersonality.kayma(tp.duyguKorku,
                    Math.min(100, tp.korkuSkoru * 0.7 + tp.cozulme * 0.3), 0.05);
                tp.duyguHayalKirikligi = TruePersonality.kayma(tp.duyguHayalKirikligi,
                    Math.min(100, tp.tiltSeviyesi * 0.8 + Math.min(40, tp.toplamOlum * 4)), 0.05);
                tp.duyguMerak = TruePersonality.kayma(tp.duyguMerak,
                    Math.min(100, tp.kesfetOrani * 60 + (100 - tp.ongorulebilirlik) * 0.4), 0.05);
                double sikintiHedef = (tp.ongorulebilirlik > 60 && tp.korkuSkoru < 30 && hiz < 0.08)
                    ? 80 : 0; // öngörülebilir + korkusuz + sakin = sıkıntı (horror'un düşmanı)
                tp.duyguSikinti = TruePersonality.kayma(tp.duyguSikinti, sikintiHedef, 0.03);
                tp.duyguGuncelle();
                // Ayna: sıkıntı varsa Candy taktik değiştirir (tırmanış hızlanır)
                if ("sikinti".equals(tp.duyguDurumu))
                    tp.candyOzguven = Math.min(100, tp.candyOzguven + 0.06);

                // ── (idea 6) Meta-farkındalık — oyuncu Candy'yi test ediyor mu ──
                // Klon yakın + sakin (kaçmıyor) + korkusuz = inceliyor/deniyor
                if (klonYakin && hiz < 0.03 && tp.korkuSkoru < 45) {
                    tp.metaFarkindalik = Math.min(100, tp.metaFarkindalik + 1.5);
                } else {
                    tp.metaFarkindalik = Math.max(0, tp.metaFarkindalik - 0.1);
                }
                if (tp.metaFarkindalik > 70 && world.getTime() - tp.sonMetaYorum > 2400
                        && tp.bossOldurulmeTick == 0 && Math.random() < 0.03) {
                    tp.sonMetaYorum = world.getTime();
                    player.sendMessage(net.minecraft.text.Text.translatable(
                            "candy.meta." + (int) (Math.random() * 2))
                        .formatted(net.minecraft.util.Formatting.DARK_GRAY,
                                   net.minecraft.util.Formatting.ITALIC), false);
                    Candy.LOG.info("[Candy] Meta-farkındalık[{}] → 'seni test ediyorum' ({}%)",
                        player.getName().getString(), (int) tp.metaFarkindalik);
                }

                // Yırtık sayfa koleksiyonu — 5 farklı varyant toplandıysa başarım.
                // (10 sn'de bir, hafif envanter taraması)
                if (tick % 200 == 0) yirtikSayfaKontrol(player);

                // ── (14) Gerçek zaman farkındalığı ──
                int gercekSaat = java.time.LocalTime.now().getHour();
                tp.sonGercekSaat = gercekSaat;
                // Gece yarısı-05 arası oynuyorsan, oturumda bir kez, nadir + korku yüksekse
                if (gercekSaat >= 0 && gercekSaat <= 4
                    && !tp.gecSaatUyarisiVerildi
                    && tp.bossOldurulmeTick == 0   // WIN sessizliğinde susma
                    && tp.korkuSkoru > 40
                    && Math.random() < 0.02) {
                    tp.gecSaatUyarisiVerildi = true;
                    player.sendMessage(net.minecraft.text.Text.translatable("candy.analiz.gec_saat")
                        .formatted(net.minecraft.util.Formatting.DARK_GRAY,
                                   net.minecraft.util.Formatting.ITALIC), false);
                }

            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] DerinAnaliz hata: {}", t.getMessage());
            }
        }
    }

    // ── Yardımcılar ──

    private static String baskinAktivite(TruePersonality tp) {
        double max = tp.kesfetOrani; String a = "kesfet";
        if (tp.kacOrani > max) { max = tp.kacOrani; a = "kac"; }
        if (tp.saldirOrani > max) { max = tp.saldirOrani; a = "saldir"; }
        if (tp.donOrani > max) { max = tp.donOrani; a = "don"; }
        return a;
    }

    /** Tetikleyici ağırlığı. Sınırsız birikmesin diye tavan uygulanır — sadece
     *  ORAN önemli (hangi korku baskın), mutlak değer değil. */
    private static final double TETIKLEYICI_TAVAN = 500.0;

    private static void tetikle(TruePersonality tp, String tur, double siddet) {
        double yeni = tp.korkuTetikleyicileri.merge(tur, siddet, Double::sum);
        if (yeni > TETIKLEYICI_TAVAN) {
            // Hepsini yarıya indir — oranlar korunur, değerler taşmaz
            tp.korkuTetikleyicileri.replaceAll((k, v) -> v * 0.5);
        }
    }

    /** Envanterde 5 farklı "Yırtık Sayfa (x/5)" varsa yirtik_parcalar başarımı. */
    private static void yirtikSayfaKontrol(ServerPlayerEntity player) {
        try {
            var bulunan = new java.util.HashSet<String>();
            var inv = player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                var st = inv.getStack(i);
                if (st.isEmpty() || st.getItem() != net.minecraft.item.Items.PAPER) continue;
                var isim = st.get(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
                if (isim == null) continue;
                String s = isim.getString();
                if (s.startsWith("Yırtık Sayfa")) bulunan.add(s);
            }
            if (bulunan.size() >= 5) {
                com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "yirtik_parcalar");
            }
        } catch (Throwable ignored) {}
    }

    private static boolean klonYakinMi(ServerWorld world, ServerPlayerEntity player, double r) {
        Box box = new Box(player.getBlockPos()).expand(r);
        return !world.getEntitiesByClass(CandyDoppelEntity.class, box, e -> e.isAlive()).isEmpty();
    }

    private static boolean hostileYakinMi(ServerWorld world, ServerPlayerEntity player, double r) {
        Box box = new Box(player.getBlockPos()).expand(r);
        return !world.getEntitiesByClass(HostileEntity.class, box, e -> e.isAlive()).isEmpty();
    }

    private static void baglanmaGuncelle(TruePersonality tp, ServerWorld world, ServerPlayerEntity player) {
        Box box = new Box(player.getBlockPos()).expand(8);
        // Pet'ler (köpek/kedi/papağan)
        var petler = world.getEntitiesByClass(
            net.minecraft.entity.passive.TameableEntity.class, box,
            t -> t.isTamed() && t.isOwner(player));
        for (var pet : petler) {
            tp.baglanmalar.merge(pet.getUuidAsString(), 1.0, Double::sum);
            // v3.2 (idea 9) Dil Modeli — oyuncunun pet'ine verdiği isim
            var isim = pet.getCustomName();
            if (isim != null) {
                String s = isim.getString();
                if (!s.isBlank() && !tp.verilenIsimler.contains(s)) {
                    tp.verilenIsimler.add(s);
                    if (tp.verilenIsimler.size() > 20) tp.verilenIsimler.remove(0);
                }
            }
        }
        // Atlar
        var atlar = world.getEntitiesByClass(
            net.minecraft.entity.passive.AbstractHorseEntity.class, box,
            h -> h.isTame() && h.getOwnerReference() != null
                 && player.getUuid().equals(h.getOwnerReference().getUuid()));
        for (var at : atlar) {
            tp.baglanmalar.merge(at.getUuidAsString(), 1.0, Double::sum);
        }
        // Base yakınlığı
        if (tp.baseKonum != null) {
            double dBase = player.getBlockPos().getSquaredDistance(
                tp.baseKonum[0], tp.baseKonum[1], tp.baseKonum[2]);
            if (dBase < 12 * 12) tp.baglanmalar.merge("base", 1.0, Double::sum);
        }

        // Sınırla: ölen/kaybolan pet UUID'leri sonsuza dek birikmesin.
        // 40'ı aşınca en düşük ağırlıklı yarısını at ("base" korunur).
        if (tp.baglanmalar.size() > 40) {
            var sirali = new java.util.ArrayList<>(tp.baglanmalar.entrySet());
            sirali.sort(java.util.Map.Entry.comparingByValue());
            for (int i = 0; i < sirali.size() / 2; i++) {
                String k = sirali.get(i).getKey();
                if (!"base".equals(k)) tp.baglanmalar.remove(k);
            }
        }
    }

    /** Hayalet güveni dış-hook (HayaletOyuncular çağırır). delta +/- */
    public static void hayaletGuvenNudge(ServerPlayerEntity player, double delta) {
        TruePersonality tp = PersonalityManager.get(player);
        tp.hayaletGuveni = Math.max(0, Math.min(100, tp.hayaletGuveni + delta));
    }

    /** Öğrenme hızı dış-hook — Candy numarasına adapte olduysa çağrılır. */
    public static void ogrenmeGuncelle(ServerPlayerEntity player, boolean adapteOldu) {
        TruePersonality tp = PersonalityManager.get(player);
        double hedef = adapteOldu ? 100 : 0;
        tp.ogrenmeHizi = TruePersonality.kayma(tp.ogrenmeHizi, hedef, 0.1);
    }

    /** Tilt dış-hook — küfür/agresif chat için CandyChatOkur çağırır. */
    public static void tiltNudge(ServerPlayerEntity player, double delta) {
        TruePersonality tp = PersonalityManager.get(player);
        tp.tiltSeviyesi = Math.max(0, Math.min(100, tp.tiltSeviyesi + delta));
    }

    public static void oyuncuAyrildi(UUID uuid) {
        oncekiPoz.remove(uuid);
        oncekiCan.remove(uuid);
        oncekiKorku.remove(uuid);
        oncekiYon.remove(uuid);
        oncekiYaw.remove(uuid);
    }
}
