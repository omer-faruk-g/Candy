package com.photonpulse.candy.clone;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.entity.CandyEntities;
import com.photonpulse.candy.simulation.ShadowTree;
import com.photonpulse.candy.simulation.ShadowTreeManager;
import com.photonpulse.candy.tree.PersonalityTree;
import com.photonpulse.candy.tree.TreeBranch;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.tree.TreeNode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * CloneManager — Candy'nin klonları.
 *
 * KRİTİK: Gerçek Candy görünmez. Oyuncunun etkileşim ettiği her şey KLON.
 *
 * Her klon:
 *  - TruePersonality'nin O ANDAKİ snapshot'ı ile doğar
 *  - Bir vanilla mob (zombi/iskelet) formunda spawn olur
 *  - "candy_clone" NBT etiketi ile işaretlenir
 *  - Baskın benliğe göre isim alır ("Avcı", "Gözlemci"...)
 *
 * Snapshot mekaniği: klon doğduğu anki verilerle davranmaya devam eder.
 * TruePersonality büyümeye devam eder. Klon geride kalır.
 */
public class CloneManager {

    private static final int KONTROL_ARALIGI = 200;   // 10 saniye — daha görünür olsun
    private static final int MAKS_KLON_PER_OYUNCU = 2;
    // Korku eşiği düşürüldü — yeni oyuncu da klon görsün (eski 25 çok yüksekti)
    private static final double MIN_KORKU_ESIGI = 15.0;
    // Spawn olasılığı — eski %25 çok düşüktü
    private static final double SPAWN_IHTIMAL = 0.50;
    private static int tickSayaci = 0;
    private static final Random RNG = new Random();

    private static final String KLON_ETIKETI = "candy_clone";

    // Her oyuncu için aktif klon ID'leri
    private static final Map<UUID, List<UUID>> aktifKlonlar = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CloneManager::onTick);
        Candy.LOG.info("[Candy] CloneManager aktif");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % KONTROL_ARALIGI != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            klonKararVer(player);
        }
    }

    /**
     * Oyuncu için klon spawn edelim mi karar ver.
     * Koşullar:
     *  - Kurulum tamamlanmış olmalı
     *  - Korku skoru en az 25
     *  - Aktif klon sayısı MAKS_KLON_PER_OYUNCU'nun altında
     *  - Rastgele düşük olasılık (Candy sabırlı)
     */
    private static void klonKararVer(ServerPlayerEntity player) {
        TruePersonality tp = PersonalityManager.get(player);

        if (!tp.kurulumTamam) return;
        if (tp.korkuSkoru < MIN_KORKU_ESIGI) return;

        List<UUID> mevcut = aktifKlonlar.computeIfAbsent(player.getUuid(), k -> new java.util.ArrayList<>());

        // Ölmüş / uzaklaşmış klonları temizle
        ServerWorld world = (ServerWorld) player.getWorld();
        mevcut.removeIf(uuid -> {
            var e = world.getEntity(uuid);
            return e == null || !e.isAlive();
        });

        if (mevcut.size() >= MAKS_KLON_PER_OYUNCU) return;
        // v1.4: Grup agresivlik — yakında başka oyuncu varsa ortalama etkiler
        double agresivlik = Psikoloji.grupAgresivlikCarpani(player);
        if (RNG.nextDouble() > Math.min(1.0, SPAWN_IHTIMAL * agresivlik)) return;

        // Uygun bir konum bul — oyuncunun 20-40 blok ötesinde, karanlıkta tercih
        BlockPos hedef = uygunSpawnKonumu(player);
        if (hedef == null) return;

        LivingEntity klon = klonSpawn(world, hedef, tp, player);
        if (klon != null) {
            mevcut.add(klon.getUuid());
            tp.sonMudahaleTick = world.getTime();
            Candy.LOG.info("[Candy] klon spawn edildi: {} için @{}",
                player.getName().getString(), hedef);

            // v1.9.18 fix: otomatik klonlar da evrim sayacına dahil edilmeliydi
            tp.karsilastigiKlonSayisi++;
            if (klon instanceof com.photonpulse.candy.entity.CandyDoppelEntity doppel) {
                if (tp.karsilastigiKlonSayisi > 4) {
                    doppel.addCommandTag(com.photonpulse.candy.entity.CandyDoppelEntity.TAG_EVRIMLI);
                }
                if (tp.karsilastigiKlonSayisi > 3 && RNG.nextDouble() < 0.15) {
                    doppel.addCommandTag(com.photonpulse.candy.entity.CandyDoppelEntity.TAG_ELITE);
                    eliteEkipmanUret(doppel, player);
                    Candy.LOG.info("[Candy] ELITE klon (otomatik) → {}",
                        player.getName().getString());
                }
            }
        }
    }

    /**
     * ShadowState patlaması için: normal klon karar mekanizmasını atlayarak,
     * oyuncunun yakınına (5-8 blok) ve arkasına ani bir klon spawn eder.
     * Cooldown / korku eşiği kontrolü yoktur — çağıran karar verdi zaten.
     *
     * @return true: spawn başarılı; false: uygun konum bulunamadı.
     */
    public static boolean zorlaKlonSpawn(ServerPlayerEntity player, TruePersonality tp) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos hedef = arkaYakinKonum(player);
        if (hedef == null) return false;

        List<UUID> mevcut = aktifKlonlar.computeIfAbsent(player.getUuid(), k -> new java.util.ArrayList<>());
        // Ölmüş klonları temizle
        mevcut.removeIf(uuid -> {
            var e = world.getEntity(uuid);
            return e == null || !e.isAlive();
        });

        LivingEntity klon = klonSpawn(world, hedef, tp, player);
        if (klon == null) return false;

        mevcut.add(klon.getUuid());
        tp.sonMudahaleTick = world.getTime();
        Candy.LOG.info("[Candy] ZORLA klon: {} için @{} (ShadowState)",
            player.getName().getString(), hedef);
        // v1.9.3: ayna başarımı — ilk klon karşılaşması
        com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "ayna");

        // v1.9.17: Klon evrimi
        tp.karsilastigiKlonSayisi++;
        if (klon instanceof com.photonpulse.candy.entity.CandyDoppelEntity doppel) {
            // 5. klondan itibaren evrimli — blok kırıp koyabilir
            if (tp.karsilastigiKlonSayisi > 4) {
                doppel.addCommandTag(com.photonpulse.candy.entity.CandyDoppelEntity.TAG_EVRIMLI);
            }
            // 4. klondan itibaren %15 elite — envanter tarayıp ekipman üretir
            if (tp.karsilastigiKlonSayisi > 3 && RNG.nextDouble() < 0.15) {
                doppel.addCommandTag(com.photonpulse.candy.entity.CandyDoppelEntity.TAG_ELITE);
                eliteEkipmanUret(doppel, player);
                Candy.LOG.info("[Candy] ELITE klon spawn → envanter tarandı ({})",
                    player.getName().getString());
            }
        }
        return true;
    }

    /**
     * v1.9.17: Elite klon envanter scavenger.
     * Player envanterindeki materyalleri sayar, en iyi setle klon donatır.
     * Netherite > Diamond > Iron > Gold > Chainmail
     */
    private static void eliteEkipmanUret(com.photonpulse.candy.entity.CandyDoppelEntity klon,
                                          ServerPlayerEntity player) {
        var inv = player.getInventory();
        int nether = 0, diamond = 0, iron = 0, gold = 0;
        for (int i = 0; i < inv.size(); i++) {
            var stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            var item = stack.getItem();
            int c = stack.getCount();
            if (item == net.minecraft.item.Items.NETHERITE_INGOT
                || item == net.minecraft.item.Items.NETHERITE_SCRAP) nether += c;
            else if (item == net.minecraft.item.Items.DIAMOND) diamond += c;
            else if (item == net.minecraft.item.Items.IRON_INGOT) iron += c;
            else if (item == net.minecraft.item.Items.GOLD_INGOT) gold += c;
            // Set parçalarını da say — netherite/diamond helmet vs.
            if (item == net.minecraft.item.Items.NETHERITE_HELMET
                || item == net.minecraft.item.Items.NETHERITE_CHESTPLATE
                || item == net.minecraft.item.Items.NETHERITE_LEGGINGS
                || item == net.minecraft.item.Items.NETHERITE_BOOTS) nether += 6;
            else if (item == net.minecraft.item.Items.DIAMOND_HELMET
                || item == net.minecraft.item.Items.DIAMOND_CHESTPLATE
                || item == net.minecraft.item.Items.DIAMOND_LEGGINGS
                || item == net.minecraft.item.Items.DIAMOND_BOOTS) diamond += 6;
        }

        // En iyi seti seç
        var Items = net.minecraft.item.Items.class;
        net.minecraft.item.Item helm, chest, leg, boot, weapon;
        if (nether >= 4) {
            helm = net.minecraft.item.Items.NETHERITE_HELMET;
            chest = net.minecraft.item.Items.NETHERITE_CHESTPLATE;
            leg = net.minecraft.item.Items.NETHERITE_LEGGINGS;
            boot = net.minecraft.item.Items.NETHERITE_BOOTS;
            weapon = net.minecraft.item.Items.NETHERITE_SWORD;
        } else if (diamond >= 24) {
            helm = net.minecraft.item.Items.DIAMOND_HELMET;
            chest = net.minecraft.item.Items.DIAMOND_CHESTPLATE;
            leg = net.minecraft.item.Items.DIAMOND_LEGGINGS;
            boot = net.minecraft.item.Items.DIAMOND_BOOTS;
            weapon = net.minecraft.item.Items.DIAMOND_SWORD;
        } else if (iron >= 24) {
            helm = net.minecraft.item.Items.IRON_HELMET;
            chest = net.minecraft.item.Items.IRON_CHESTPLATE;
            leg = net.minecraft.item.Items.IRON_LEGGINGS;
            boot = net.minecraft.item.Items.IRON_BOOTS;
            weapon = net.minecraft.item.Items.IRON_SWORD;
        } else if (gold >= 24) {
            helm = net.minecraft.item.Items.GOLDEN_HELMET;
            chest = net.minecraft.item.Items.GOLDEN_CHESTPLATE;
            leg = net.minecraft.item.Items.GOLDEN_LEGGINGS;
            boot = net.minecraft.item.Items.GOLDEN_BOOTS;
            weapon = net.minecraft.item.Items.GOLDEN_SWORD;
        } else {
            helm = net.minecraft.item.Items.LEATHER_HELMET;
            chest = net.minecraft.item.Items.LEATHER_CHESTPLATE;
            leg = net.minecraft.item.Items.LEATHER_LEGGINGS;
            boot = net.minecraft.item.Items.LEATHER_BOOTS;
            weapon = net.minecraft.item.Items.STONE_SWORD;
        }

        klon.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(helm));
        klon.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new net.minecraft.item.ItemStack(chest));
        klon.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new net.minecraft.item.ItemStack(leg));
        klon.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new net.minecraft.item.ItemStack(boot));
        klon.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(weapon));
        // Drop chance sıfır — elite drop bırakmaz, bu şekilde farm olmaz
        for (var slot : net.minecraft.entity.EquipmentSlot.values()) {
            try { klon.setEquipmentDropChance(slot, 0.0f); } catch (Throwable ignored) {}
        }
    }

    /**
     * Oyuncunun ARKASINDA (bakış yönünün tersi) 5-8 blok mesafede uygun konum bul.
     * Görüş açısı dışına düşsün diye ±30° koni içinde arar.
     */
    private static BlockPos arkaYakinKonum(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos merkez = player.getBlockPos();

        // Oyuncunun yaw'ına göre "arka" yönü (radyan)
        double bakisRad = Math.toRadians(player.getYaw());
        // Arka = bakışın tersi = yaw + PI. Minecraft'ta yaw 0 = güney (z+), sin/cos'a göre:
        // ileri x = -sin(yaw), z = cos(yaw). Arka: x = sin(yaw), z = -cos(yaw).
        double arkaX = Math.sin(bakisRad);
        double arkaZ = -Math.cos(bakisRad);

        for (int deneme = 0; deneme < 12; deneme++) {
            double sapma = (RNG.nextDouble() - 0.5) * Math.PI / 3;   // ±30°
            double cosS = Math.cos(sapma);
            double sinS = Math.sin(sapma);
            double yonX = arkaX * cosS - arkaZ * sinS;
            double yonZ = arkaX * sinS + arkaZ * cosS;

            double mesafe = 5 + RNG.nextDouble() * 3;   // 5-8 blok
            int x = merkez.getX() + (int) Math.round(yonX * mesafe);
            int z = merkez.getZ() + (int) Math.round(yonZ * mesafe);

            // Önce oyuncu Y seviyesinde dene (mağara/yeraltı için), sonra ±3 Y
            BlockPos p = ariyorumUygunKonum(world, x, z, merkez.getY());
            if (p != null) return p;
        }
        return null;
    }

    /**
     * Verilen x,z için önce Y merkez, sonra ±3 Y aralığında uygun spawn konumu ara.
     * Bulamazsa topY dene (yerüstü fallback). Mağarada/yeraltında Y'yi oyuncuya yakın tutar.
     */
    private static BlockPos ariyorumUygunKonum(ServerWorld world, int x, int z, int merkezY) {
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos p = new BlockPos(x, merkezY + dy, z);
            if (uygunKonumMu(world, p)) return p;
            if (dy > 0) {
                p = new BlockPos(x, merkezY - dy, z);
                if (uygunKonumMu(world, p)) return p;
            }
        }
        // Fallback: yerüstü
        int topY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z);
        if (Math.abs(topY - merkezY) > 3) {
            BlockPos p = new BlockPos(x, topY, z);
            if (uygunKonumMu(world, p)) return p;
        }
        return null;
    }

    private static boolean uygunKonumMu(ServerWorld world, BlockPos p) {
        if (!world.getBlockState(p).isAir()) return false;
        if (!world.getBlockState(p.up()).isAir()) return false;
        return world.getBlockState(p.down()).isSolidBlock(world, p.down());
    }

    /**
     * Klonu spawn et. v0.7: Custom CandyDoppelEntity — Zombie/Skeleton DEĞİL.
     * Iron golem hedef almaz, 40 HP, 3 dmg, oyuncunun equipment'ini giyer.
     */
    private static LivingEntity klonSpawn(ServerWorld world, BlockPos pos, TruePersonality tp,
                                          ServerPlayerEntity owner) {
        PersonalityTree tree = TreeGrowth.get(tp.oyuncuId);
        TreeNode tepkiBaskin = tree.baskinAlt(TreeBranch.TEPKI);
        // %20 ihtimalle ShadowTree'nin TEPKI'si
        ShadowTree shadow = ShadowTreeManager.getIfExists(tp.oyuncuId);
        if (shadow != null && RNG.nextDouble() < 0.20) {
            TreeNode alt = shadow.tree.baskinAlt(TreeBranch.TEPKI);
            if (alt != null) tepkiBaskin = alt;
        }

        // Custom CandyDoppelEntity spawn
        Object spawned = CandyEntities.DOPPEL.spawn(world, pos, SpawnReason.MOB_SUMMONED);
        if (!(spawned instanceof CandyDoppelEntity klon)) return null;

        // Owner ata — mind control + client-side skin fetch için
        klon.setOwnerUuid(owner.getUuid());

        // Command tag'ler (GriefSystem entegrasyonu için)
        klon.addCommandTag(KLON_ETIKETI);
        klon.addCommandTag(KLON_ETIKETI + "_t" + tp.toplamGozlemTick);
        if (tepkiBaskin != null) {
            klon.addCommandTag(KLON_ETIKETI + "_dal_" + tepkiBaskin.id);
        }

        // Oyuncunun equipment'ini kopyala — armor + main hand + off hand
        equipmentKopyala(owner, klon);

        // İsim — italik gri, translatable format: "[%s]" → oyuncu adı
        klon.setCustomName(Text.translatable("candy.clone.name", owner.getName().getString())
            .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        klon.setCustomNameVisible(true);
        klon.setPersistent();

        // Zaten 40 HP full doldu — max health'e set
        klon.setHealth(klon.getMaxHealth());

        return klon;
    }

    /**
     * Oyuncunun armor + tam hotbar'ını klona kopyala.
     * Main hand olarak hotbar'ın EN İYİ silahını seçer (Kılıç > Balta > Trident > yay > tool).
     * Klon canı azalırsa hotbar'daki yemeğe geçip iyileşir (CandyDoppelEntity.yemekMantigi).
     */
    private static void equipmentKopyala(ServerPlayerEntity owner, CandyDoppelEntity klon) {
        // Armor slotları
        klon.equipStack(EquipmentSlot.HEAD, owner.getEquippedStack(EquipmentSlot.HEAD).copy());
        klon.equipStack(EquipmentSlot.CHEST, owner.getEquippedStack(EquipmentSlot.CHEST).copy());
        klon.equipStack(EquipmentSlot.LEGS, owner.getEquippedStack(EquipmentSlot.LEGS).copy());
        klon.equipStack(EquipmentSlot.FEET, owner.getEquippedStack(EquipmentSlot.FEET).copy());

        // Tam hotbar snapshot (9 slot) — klonun depoHotbar'ına
        List<ItemStack> hotbar = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            hotbar.add(owner.getInventory().getStack(i).copy());
        }
        klon.setDepoHotbar(hotbar);

        // Main hand: hotbar'daki EN İYİ silahı seç. Yoksa oyuncunun elindeki.
        ItemStack enIyi = klon.enIyiSilahiSec();
        if (enIyi.isEmpty()) enIyi = owner.getMainHandStack().copy();
        klon.equipStack(EquipmentSlot.MAINHAND, enIyi.copy());

        // Off hand
        klon.equipStack(EquipmentSlot.OFFHAND, owner.getOffHandStack().copy());

        // Klon eşyaları düşürmesin (öldüğünde item spam olmasın).
        EquipmentSlot[] slotlar = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };
        for (EquipmentSlot slot : slotlar) {
            klon.setEquipmentDropChance(slot, 0.0f);
        }
    }

    /**
     * Oyuncudan uzakta, ama görüş açısı dışında bir spawn konumu bul.
     */
    private static BlockPos uygunSpawnKonumu(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos merkez = player.getBlockPos();

        for (int deneme = 0; deneme < 15; deneme++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double mesafe = 20 + RNG.nextDouble() * 20;
            int x = merkez.getX() + (int)(Math.cos(angle) * mesafe);
            int z = merkez.getZ() + (int)(Math.sin(angle) * mesafe);

            // Oyuncunun Y seviyesinde (mağara için) veya yüzeyde ara
            BlockPos p = ariyorumUygunKonum(world, x, z, merkez.getY());
            if (p == null) continue;

            // Işık seviyesi çok yüksek değilse kabul et.
            // 12 ise: mağara/gece kesin geçer, gündüz gölgeli alan geçer, açık gündüz reddedilir.
            int isik = world.getLightLevel(p);
            if (isik > 12) continue;

            return p;
        }
        return null;
    }

    /**
     * Bir varlık Candy'nin klonu mu?
     */
    public static boolean klonMu(LivingEntity entity) {
        return entity.getCommandTags().contains(KLON_ETIKETI);
    }

    public static void oyuncuAyrildi(UUID uuid) {
        aktifKlonlar.remove(uuid);
    }

    /**
     * Sunucu kapanışı — per-server state sıfırlansın (single-player dünya değişimi).
     */
    public static void sunucuKapan() {
        aktifKlonlar.clear();
        tickSayaci = 0;
    }
}
