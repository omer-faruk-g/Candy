package com.photonpulse.candy.disguise;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * DisguiseSystem — Candy başka canlılara bürünür.
 *
 * Fabric-only yaklaşım (Mixin YOK):
 *  1. Bir vanilla mob'un NBT'sine "candy_disguised" etiketi ekleriz.
 *  2. Her tick etiketli mob'ları tararız.
 *  3. Etiketli mob'lar oyuncuya BAKMAYA devam eder (normalde kaçarlar).
 *     Kaçmaya çalıştıklarında hareketlerini iptal ederiz.
 *
 * Tespit yöntemi yok — sadece hissetmek var. Çünkü mob "normal davranmıyor."
 */
public class DisguiseSystem {

    private static final int TARAMA_ARALIGI = 20;   // saniyede bir — daha güçlü "bakma" hissi
    private static final int BURUNME_KONTROL_ARALIGI = 400;  // 20 saniyede bir bürünme dene
    private static final double BURUNME_IHTIMAL = 0.6;   // %60 — spawn'ı daha görünür kılalım
    private static final int MAKS_MESAFE = 32;
    private static int tickSayaci = 0;
    private static final Random RNG = new Random();

    // Hangi mob türlerine bürünebilir
    private static final Set<EntityType<?>> BURUNULEBILIR_TURLER = Set.of(
        EntityType.SHEEP,
        EntityType.COW,
        EntityType.PIG,
        EntityType.CHICKEN,
        EntityType.VILLAGER
    );

    private static final String NBT_ETIKET = "candy_disguised";

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DisguiseSystem::onTick);
        Candy.LOG.info("[Candy] DisguiseSystem aktif");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;

        if (tickSayaci % TARAMA_ARALIGI == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                brulmusMoblariGuncelle(player);
            }
        }

        if (tickSayaci % BURUNME_KONTROL_ARALIGI == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                belkiBurunelim(player);
            }
        }
    }

    /**
     * Oyuncuya yakın bürünmüş mob'ları güncelle.
     * Bunlar "normal" davranmıyor — oyuncuya bakıyor, hareket etmiyor, kaçmıyor.
     */
    private static void brulmusMoblariGuncelle(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Box arama = new Box(player.getBlockPos()).expand(MAKS_MESAFE);
        List<LivingEntity> yakinlar = world.getEntitiesByClass(LivingEntity.class, arama, e -> true);

        for (LivingEntity mob : yakinlar) {
            if (!mob.getCommandTags().contains(NBT_ETIKET)) continue;

            if (mob instanceof MobEntity me) {
                // Bürünmüş — oyuncuya doğrudan bak.
                // NOT: LookControl.lookAt AI'ya bir "hedef" verir ama vanilla AI
                // bunu her tick override edebiliyordu. Doğrudan yaw/pitch set edilir.
                double dx = player.getX() - me.getX();
                double dy = player.getEyeY() - me.getEyeY();
                double dz = player.getZ() - me.getZ();
                double distXZ = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
                me.setYaw(yaw);
                me.setBodyYaw(yaw);
                me.setHeadYaw(yaw);
                me.setPitch(pitch);
                me.getLookControl().lookAt(player, 60, 60);

                // Kaçmaya çalışıyorsa iptal et
                Vec3d velocity = me.getVelocity();
                if (velocity.horizontalLengthSquared() > 0.001) {
                    me.setVelocity(0, velocity.y, 0);
                }
                if (!me.getNavigation().isIdle()) {
                    me.getNavigation().stop();
                }
            }

            // Panik göstergesi — oyuncu bu mob'a bakıyorsa korku yavaşça yükselir.
            // Kurulum sırasında korku eşiği 10'da kilitli — bu artışı boşa harcamayalım.
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;

            double bakisAcisi = mob.getPos().subtract(player.getPos()).normalize()
                                    .dotProduct(player.getRotationVector().normalize());
            if (bakisAcisi > 0.9 && player.distanceTo(mob) < 10) {
                tp.korkuSkoru = Math.min(100, tp.korkuSkoru + 0.005);
            }
        }
    }

    /**
     * Yeni bir hayvana/köylüye bürünelim mi? Düşük olasılık.
     */
    private static void belkiBurunelim(ServerPlayerEntity player) {
        TruePersonality tp = PersonalityManager.get(player);
        if (!tp.kurulumTamam) return;
        // Psikoloji kötüyse bürünme sıklığı artar
        double agresivlik = Psikoloji.agresivlikCarpani(tp);
        if (RNG.nextDouble() > Math.min(1.0, BURUNME_IHTIMAL * agresivlik)) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        Box arama = new Box(player.getBlockPos()).expand(MAKS_MESAFE);
        List<LivingEntity> adaylar = world.getEntitiesByClass(LivingEntity.class, arama,
            e -> BURUNULEBILIR_TURLER.contains(e.getType())
                 && !e.getCommandTags().contains(NBT_ETIKET));

        if (adaylar.isEmpty()) return;

        LivingEntity secilen = adaylar.get(RNG.nextInt(adaylar.size()));
        secilen.addCommandTag(NBT_ETIKET);
        Candy.LOG.info("[Candy] {} bir {} olarak bürünüyor",
            player.getName().getString(), secilen.getType().toString());
    }

    /**
     * Bir varlığın Candy tarafından bürünmüş olup olmadığını kontrol et.
     * Dışarıdan (örn. ClientDisguiseRenderer veya CloneSystem) çağrılabilir.
     */
    public static boolean brulmusMu(LivingEntity entity) {
        return entity.getCommandTags().contains(NBT_ETIKET);
    }
}
