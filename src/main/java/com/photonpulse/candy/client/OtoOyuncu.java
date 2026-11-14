package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * OtoOyuncu — <b>yalnızca test</b> (CandyOtoTest {@code #oyna}): sıradan bir oyuncu gibi oynayan
 * basit bot. Mekanikler çoğunlukla oyuncu davranışından tetiklendiği için (kazma, inşa, ölüm,
 * uyku, bakış, sohbet, arkadaşa yaklaşma) komutla zorlamak yerine gerçekten "oynanması" gerekiyor.
 *
 * <p>Algoritma: her 60–200 tick'te ağırlıklı rastgele bir kip seçilir; kip her tick sürülür.
 * Hareket gerçek tuş durumuyla ({@code KeyMapping.setDown}), kazma/saldırı/kullanma ise
 * istemcinin fare tıkında çağırdığı {@code MultiPlayerGameMode} yollarıyla — sunucuya aynı paketler
 * gider. Pencere odakta olmasa da çalışır (iki istemcili koşu).
 *
 * <p>Öncelikler kipten önce gelir: ölüm ekranı → yeniden doğ; açlık &lt; 14 → ye; yakında düşman →
 * savaş; gece + yatak → uyu; başlangıç noktasından 48 bloktan uzak → geri dön.
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
final class OtoOyuncu {

    private OtoOyuncu() {}

    private enum Kip { GEZ, KAZ, INSA, SAVAS, BAK, ARKADAS, UYU, YE, ENVANTER, EGIL, SOHBET }

    private static final String[] LAFLAR = {
        "hey", "neredesin", "where are you", "did you see that", "something is behind me",
        "gece oluyor", "i heard something", "korkuyorum", "come here", "bu ses neydi",
        "candy?", "who placed this", "yalnizim", "lets build a house", "mağara buldum"
    };

    private static int kalan = 0;          // bot kaç tick daha oynayacak (0 = kapalı)
    private static Kip kip = Kip.GEZ;
    private static int kipTick = 0;
    private static java.util.Random r = new java.util.Random(1);
    private static Vec3 merkez = null;      // başlangıç noktası (tasma)
    private static Vec3 sonKonum = null;
    private static int takili = 0;
    private static BlockPos kazilan = null;
    private static int tickSay = 0;
    // istatistik
    private static int kirilan, konan, vurus, olum, uyku, yeme, sohbet, kipSay;
    private static double yol;

    static boolean aktif() { return kalan > 0; }

    static void baslat(Minecraft mc, int tick) {
        kalan = tick;
        r = new java.util.Random(mc.getUser().getName().hashCode());
        merkez = mc.player == null ? null : mc.player.position();
        kipSec(mc);
        Candy.LOG.info("[Candy][oyuncu] {} oynamaya basladi: {} tick, merkez {}", ad(mc), tick, merkez);
    }

    static void durdur(Minecraft mc, String neden) {
        if (kalan <= 0 && neden.equals("bitti")) return;
        kalan = 0;
        tuslariBirak(mc);
        ozet(mc, "DURDU (" + neden + ")");
    }

    /** Her istemci tick'inde CandyOtoTest çağırır. */
    static void sur(Minecraft mc) {
        if (kalan <= 0) return;
        if (--kalan == 0) { durdur(mc, "bitti"); return; }
        if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen && mc.player != null) {
            olum++;
            Candy.LOG.info("[Candy][oyuncu] {} oldu -> yeniden doguyor (olum {})", ad(mc), olum);
            mc.player.respawn();
            mc.setScreen(null);
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.screen != null && kip != Kip.ENVANTER && kip != Kip.UYU) return;   // tabela/ayar ekranı açık
        if (++tickSay % 1200 == 0) ozet(mc, "ara");

        Vec3 p = mc.player.position();
        if (sonKonum != null) {
            double d = p.distanceTo(sonKonum);
            if (d < 50) yol += d;            // ışınlanma sayılmaz
            takili = d < 0.02 && mc.options.keyUp.isDown() ? takili + 1 : 0;
        }
        sonKonum = p;
        if (merkez == null || p.distanceTo(merkez) > 400) merkez = p;   // boyut değişti/ışınlandı

        // öncelikler
        if (mc.player.getFoodData().getFoodLevel() < 14 && kip != Kip.YE && yemekSlotu(mc) >= 0) kipAyarla(mc, Kip.YE);
        else if (kip != Kip.SAVAS && kip != Kip.YE && enYakinDusman(mc, 10) != null) kipAyarla(mc, Kip.SAVAS);
        else if (kip == Kip.GEZ && gece(mc) && yatakSlotu(mc) >= 0 && r.nextInt(200) == 0) kipAyarla(mc, Kip.UYU);
        if (--kipTick <= 0) kipSec(mc);

        // suda batma: zıpla
        if (mc.player.isInWater()) mc.options.keyJump.setDown(true);

        switch (kip) {
            case GEZ -> gez(mc, false);
            case KAZ -> kaz(mc);
            case INSA -> insa(mc);
            case SAVAS -> savas(mc);
            case BAK -> {
                mc.options.keyUp.setDown(false);
                mc.player.setYRot(mc.player.getYRot() + (kipTick % 40 < 20 ? 9f : -9f));
                mc.player.setXRot((float) Math.sin(kipTick / 10.0) * 40f);
            }
            case ARKADAS -> arkadas(mc);
            case UYU -> uyu(mc);
            case YE -> ye(mc);
            case ENVANTER -> {
                if (kipTick == 50) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                if (kipTick == 5 && mc.screen != null) mc.setScreen(null);
            }
            case EGIL -> { mc.options.keyShift.setDown(true); gez(mc, true); }
            case SOHBET -> {
                if (kipTick == 1) {
                    String m = LAFLAR[r.nextInt(LAFLAR.length)];
                    mc.player.connection.sendChat(m);
                    sohbet++;
                    Candy.LOG.info("[Candy][oyuncu] {} sohbet: {}", ad(mc), m);
                }
            }
        }
    }

    // ---------------- kipler ----------------

    private static void kipSec(Minecraft mc) {
        // ağırlıklar: normal oyuncu çoğunlukla gezer/kazar/inşa eder
        int[] w = {30, 18, 12, 0, 8, 10, 0, 0, 4, 4, 5};
        int top = 0;
        for (int x : w) top += x;
        int s = r.nextInt(top), i = 0;
        while (s >= w[i]) s -= w[i++];
        kipAyarla(mc, Kip.values()[i]);
    }

    private static void kipAyarla(Minecraft mc, Kip yeni) {
        tuslariBirak(mc);
        if (mc.screen != null && kip == Kip.ENVANTER) mc.setScreen(null);
        kip = yeni;
        kipSay++;
        kipTick = switch (yeni) {
            case SOHBET -> 2;
            case ENVANTER -> 60;
            case UYU -> 400;
            case YE -> 60;
            case SAVAS -> 160;
            default -> 60 + r.nextInt(140);
        };
        if (mc.player != null) {
            if (yeni == Kip.GEZ || yeni == Kip.KAZ || yeni == Kip.INSA) mc.player.setYRot(r.nextFloat() * 360f - 180f);
            if (yeni == Kip.KAZ) mc.player.setXRot(r.nextBoolean() ? 50f : 15f);
        }
        kazilan = null;
        Candy.LOG.info("[Candy][oyuncu] {} kip -> {} ({} tick)", ad(mc), yeni, kipTick);
    }

    private static void gez(Minecraft mc, boolean yavas) {
        var pl = mc.player;
        if (merkez != null && pl.position().distanceTo(merkez) > 48) bakNoktaya(pl, merkez);   // tasma
        else if (r.nextInt(40) == 0) pl.setYRot(pl.getYRot() + r.nextFloat() * 90f - 45f);
        pl.setXRot(0f);
        mc.options.keyUp.setDown(true);
        mc.options.keySprint.setDown(!yavas && r.nextInt(3) == 0);
        boolean zipla = pl.horizontalCollision || pl.isInWater() || r.nextInt(80) == 0;
        mc.options.keyJump.setDown(zipla);
        if (takili > 30) { pl.setYRot(pl.getYRot() + 120f); takili = 0; }
    }

    private static void kaz(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        HitResult h = mc.hitResult;
        if (!(h instanceof BlockHitResult b) || h.getType() != HitResult.Type.BLOCK) {
            mc.player.setXRot(Math.min(80f, mc.player.getXRot() + 5f));
            return;
        }
        BlockPos pos = b.getBlockPos();
        if (kazilan != null && !kazilan.equals(pos) && mc.level.getBlockState(kazilan).isAir()) {
            kirilan++;
            mc.options.keyUp.setDown(true);   // kazılan yere doğru ilerle
        }
        if (mc.level.getBlockState(pos).getDestroySpeed(mc.level, pos) < 0) { kipTick = 0; return; }   // kırılmaz (bedrock)
        if (kazilan == null || !kazilan.equals(pos)) mc.gameMode.startDestroyBlock(pos, b.getDirection());
        else mc.gameMode.continueDestroyBlock(pos, b.getDirection());
        mc.player.swing(InteractionHand.MAIN_HAND);
        kazilan = pos;
    }

    private static void insa(Minecraft mc) {
        int s = blokSlotu(mc);
        if (s < 0) { kipTick = 0; return; }
        mc.player.getInventory().setSelectedSlot(s);
        mc.options.keyUp.setDown(false);
        if (kipTick % 2 == 0) mc.player.setYRot(mc.player.getYRot() + 7f);
        mc.player.setXRot(70f);
        if (kipTick % 8 != 0) return;
        if (r.nextInt(3) == 0) mc.options.keyJump.setDown(true);   // kule (pillar) ara sıra
        else mc.options.keyJump.setDown(false);
        if (mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            var sonuc = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, b);
            mc.player.swing(InteractionHand.MAIN_HAND);
            if (sonuc.consumesAction()) konan++;
        }
    }

    private static void savas(Minecraft mc) {
        LivingEntity hedef = enYakinDusman(mc, 16);
        if (hedef == null) { kipTick = Math.min(kipTick, 1); return; }
        int kilic = slotBul(mc, st -> st.getItem().toString().contains("sword"));
        if (kilic >= 0) mc.player.getInventory().setSelectedSlot(kilic);
        bakNoktaya(mc.player, hedef.getEyePosition());
        double d = mc.player.distanceTo(hedef);
        mc.options.keyUp.setDown(d > 2.5);
        mc.options.keyJump.setDown(mc.player.horizontalCollision);
        if (d < 3.2 && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
            mc.gameMode.attack(mc.player, hedef);
            mc.player.swing(InteractionHand.MAIN_HAND);
            vurus++;
            Candy.LOG.info("[Candy][oyuncu] {} vurdu: {} (can {})", ad(mc),
                hedef.getType().toShortString(), String.format("%.1f", hedef.getHealth()));
        }
    }

    private static void arkadas(Minecraft mc) {
        Player en = null;
        for (Player o : mc.level.players()) {
            if (o == mc.player) continue;
            if (en == null || o.distanceTo(mc.player) < en.distanceTo(mc.player)) en = o;
        }
        if (en == null) { kipTick = 0; return; }
        bakNoktaya(mc.player, en.getEyePosition());
        boolean uzak = mc.player.distanceTo(en) > 3;
        mc.options.keyUp.setDown(uzak);
        mc.options.keySprint.setDown(mc.player.distanceTo(en) > 12);
        mc.options.keyJump.setDown(uzak && mc.player.horizontalCollision);
        if (!uzak && kipTick % 40 == 0) mc.player.swing(InteractionHand.MAIN_HAND);   // el salla
    }

    private static void uyu(Minecraft mc) {
        if (mc.player.isSleeping()) {
            if (kipTick == 1) Candy.LOG.info("[Candy][oyuncu] {} uyandi", ad(mc));
            return;
        }
        if (!gece(mc)) { kipTick = 0; return; }
        mc.options.keyUp.setDown(false);
        mc.player.setXRot(60f);
        if (kipTick % 10 != 0 || !(mc.hitResult instanceof BlockHitResult b) || b.getType() != HitResult.Type.BLOCK) return;
        boolean yatak = mc.level.getBlockState(b.getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.BedBlock;
        if (!yatak) {
            int s = yatakSlotu(mc);
            if (s < 0) { kipTick = 0; return; }
            mc.player.getInventory().setSelectedSlot(s);
        }
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, b);
        if (yatak) {
            uyku++;
            Candy.LOG.info("[Candy][oyuncu] {} yataga yatti @ {}", ad(mc), b.getBlockPos().toShortString());
        } else {
            mc.player.setYRot(mc.player.getYRot() + 30f);   // yer yoksa dön, tekrar dene
        }
    }

    private static void ye(Minecraft mc) {
        int s = yemekSlotu(mc);
        if (s < 0 || mc.player.getFoodData().getFoodLevel() >= 20) { kipTick = 0; return; }
        mc.player.getInventory().setSelectedSlot(s);
        if (!mc.player.isUsingItem()) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            yeme++;
            Candy.LOG.info("[Candy][oyuncu] {} yiyor (aclik {})", ad(mc), mc.player.getFoodData().getFoodLevel());
        }
        mc.options.keyUse.setDown(true);   // basılı tut: yeme süresince
    }

    // ---------------- yardımcılar ----------------

    private static LivingEntity enYakinDusman(Minecraft mc, double menzil) {
        LivingEntity en = null;
        for (var e : mc.level.getEntitiesOfClass(Mob.class, mc.player.getBoundingBox().inflate(menzil))) {
            if (!e.isAlive() || !(e instanceof net.minecraft.world.entity.monster.Enemy)) continue;
            if (en == null || e.distanceTo(mc.player) < en.distanceTo(mc.player)) en = e;
        }
        return en;
    }

    private static boolean gece(Minecraft mc) {
        long t = mc.level.getDayTime() % 24000L;
        return t > 12542 && t < 23460;
    }

    private static int yemekSlotu(Minecraft mc) { return slotBul(mc, st -> st.has(DataComponents.FOOD)); }
    private static int yatakSlotu(Minecraft mc) { return slotBul(mc, st -> st.getItem() instanceof BedItem); }
    private static int blokSlotu(Minecraft mc) {
        return slotBul(mc, st -> st.getItem() instanceof BlockItem && !(st.getItem() instanceof BedItem)
            && !st.getItem().toString().contains("sign") && !st.getItem().toString().contains("torch"));
    }

    private static int slotBul(Minecraft mc, java.util.function.Predicate<ItemStack> k) {
        for (int i = 0; i < 9; i++) if (!mc.player.getInventory().getItem(i).isEmpty() && k.test(mc.player.getInventory().getItem(i))) return i;
        return -1;
    }

    private static void bakNoktaya(Player pl, Vec3 h) {
        double dx = h.x - pl.getX(), dy = h.y - pl.getEyeY(), dz = h.z - pl.getZ();
        pl.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        pl.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static void tuslariBirak(Minecraft mc) {
        if (mc.gameMode != null && kazilan != null) mc.gameMode.stopDestroyBlock();
        for (var k : new net.minecraft.client.KeyMapping[]{mc.options.keyUp, mc.options.keySprint,
            mc.options.keyJump, mc.options.keyShift, mc.options.keyUse}) k.setDown(false);
        if (mc.player != null && mc.player.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(mc.player);
    }

    private static void ozet(Minecraft mc, String etiket) {
        Candy.LOG.info("[Candy][oyuncu] {} OZET {}: kip {} | kirilan {} konan {} vurus {} olum {} uyku {} yeme {} sohbet {} | yol {} blok | can {} aclik {} | {}",
            ad(mc), etiket, kipSay, kirilan, konan, vurus, olum, uyku, yeme, sohbet, (int) yol,
            mc.player == null ? "-" : String.format("%.1f", mc.player.getHealth()),
            mc.player == null ? "-" : mc.player.getFoodData().getFoodLevel(),
            mc.player == null ? "-" : mc.player.blockPosition().toShortString() + " " + mc.level.dimension());
    }

    private static String ad(Minecraft mc) { return mc.getUser().getName(); }
}
