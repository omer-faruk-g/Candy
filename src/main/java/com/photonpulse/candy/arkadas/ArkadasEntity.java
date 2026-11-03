package com.photonpulse.candy.arkadas;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * ArkadasEntity (v8.3) — Candy'nin dünyadaki eli. Oyuncu gibi görünür, oyuncu gibi
 * davranır; teni baştan aşağı siyahtır ve bir "Arkadaş" adı taşır.
 *
 * <h3>NİYE DOPPEL DEĞİL, YENİ BİR VARLIK</h3>
 * {@link com.photonpulse.candy.entity.CandyDoppelEntity} oyuncunun <i>kopyası</i>:
 * onun skinini takar, kısa ömürlüdür, görülmek için vardır. Arkadaş'ın işi tam tersi —
 * <b>kalıcı</b>, kendi kimliği var, ve bir şeyler <i>yapıyor</i>: kazıyor, yol kesiyor,
 * dövüşüyor. İkisini aynı sınıfta toplamak tag'lerle ayrılan iki ayrı AI demekti;
 * doppel'in tag listesi (BEKCI/YANSIMA/AYNA/EVRIMLI/ELITE) zaten o yüzden şişmiş.
 *
 * <h3>NİYE HostileEntity</h3>
 * Doppel'deki aynı sebep: Zombie/Skeleton türetilse iron golem'in yerleşik hedef
 * listesine girerdi ve köydeki golem Arkadaş'ı kendiliğinden döverdi. HostileEntity
 * nötr tabandır.
 *
 * <h3>OYUNCUNUN YAPABİLDİKLERİ</h3>
 * Kapsam açıkça şu: yürüme/pathfinding, kapı açma, blok <b>kırma</b> (sertliğe göre
 * süre, uygun alet eline geçer), blok <b>koyma</b> (yol kesme/kapatma), <b>melee</b>
 * dövüş. Envanter yönetimi, crafting, fırın/sandık kullanımı ve nişan alıp atış
 * <b>yok</b> — bunlar oyuncu simülasyonunun kendi başına bir proje olan kısmı ve
 * yarısı yapılmış hâli "bot takılmış" gibi görünür, korkutucu değil.
 *
 * <h3>ÖLÜM</h3>
 * {@link #die} öfkeyi kalıcı hâle getirir ({@code arkadasYasi}) ve
 * {@link ArkadasKaydi} üzerinden dünyaya "bir daha gelmeyecek" diye yazar. Oyuncu
 * Candy'yi bir kez gerçekten yaralayabiliyor — ve Candy bunu unutmuyor.
 */
public class ArkadasEntity extends Monster {

    /** Kazma ilerlemesi tutulan blok; yok ise null. */
    private BlockPos kaziBlok = null;
    private float kaziIlerleme = 0f;

    /** Blok koyma soğuması (tick). */
    private int koymaSogumasi = 0;

    /** Kapı açma soğuması (tick). */
    private int kapiSogumasi = 0;

    /** Yavaşlatma uygulaması için sayaç. */
    private int yavaslatmaSayaci = 0;

    /** Candy'nin bu turda ne yapmasını istediği — {@link ArkadasYonetici} yazar. */
    private volatile Gorev gorev = Gorev.IZLE;

    /** Arkadaş'ın görevleri. Kurul seçiyor; varsayılan yalnızca izlemek. */
    public enum Gorev {
        /** Hiçbir şey yapma, yalnızca yakında dur ve bak. En ürkütücü varsayılan. */
        IZLE,
        /** Oyuncuya doğru kaz — duvarın arkasından gelen ses. */
        KAZ,
        /** Oyuncunun yolunu blokla. */
        KES,
        /** Saldır. */
        SALDIR
    }

    public ArkadasEntity(EntityType<? extends ArkadasEntity> type, Level world) {
        super(type, world);
        this.setPersistenceRequired();
        this.setCustomNameVisible(true);
        this.setCustomName(Component.literal("Arkadaş"));
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createArkadasAttributes() {
        // Oyuncu değerleri: 20 HP, oyuncu hızı. KASITLI olarak şişirilmedi —
        // öldürülebilir olması mekaniğin bütün anlamı (bkz. onDeath).
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 64.0)
            .add(Attributes.ARMOR, 2.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void registerGoals() {
        // Melee goal her zaman kayıtlı ama yalnızca SALDIR görevinde hedef veriliyor;
        // hedefsiz MeleeAttackGoal çalışmaz, ayrı bir anahtar gerekmiyor.
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 32.0f));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6));
        // targetSelector KASITLI boş: hedef yalnızca ArkadasYonetici verir.
        // ActiveTargetGoal eklemek Arkadaş'ı sıradan bir hostile mob yapardı —
        // oyuncu onu "yeni bir zombi" diye okur, Candy'nin eli olarak okumaz.
    }

    public void gorevVer(Gorev g) {
        this.gorev = (g == null) ? Gorev.IZLE : g;
        if (this.gorev != Gorev.SALDIR) {
            this.setTarget(null);
        }
    }

    public Gorev gorev() {
        return gorev;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel world)) return;
        if (koymaSogumasi > 0) koymaSogumasi--;
        if (kapiSogumasi > 0) kapiSogumasi--;

        try {
            Player hedefO = yakinOyuncu(world);
            if (hedefO == null) {
                kaziBlok = null;
                return;
            }

            kapiAc(world);

            switch (gorev) {
                case KAZ -> kazmayaDevam(world, hedefO);
                case KES -> yolKes(world, hedefO);
                case SALDIR -> {
                    if (hedefO instanceof LivingEntity l) this.setTarget(l);
                }
                case IZLE -> {
                    kaziBlok = null;
                    // Durup bakmak: navigasyonu kes, yüzü oyuncuya çevir.
                    if (this.distanceToSqr(hedefO) < 36.0) {
                        this.getNavigation().stop();
                        this.lookAt(hedefO, 30.0f, 30.0f);
                    }
                }
            }

            yavaslatmaUygula(world, hedefO);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Arkadaş tick hatası: {}", t.toString());
        }
    }

    private Player yakinOyuncu(ServerLevel world) {
        return world.getNearestPlayer(this, 64.0);
    }

    /**
     * TEK OYUNCU yavaşlatması.
     *
     * <p>İstek "tek kişiyse kişiyi yavaşlatsın"dı. Kalıcı Slowness vermiyoruz:
     * sürekli yavaş oyuncu kısa sürede mekaniği "sakatlık" olarak okur ve oynamayı
     * bırakır. Bunun yerine <b>yakınlık</b> şartlı, kısa süreli ve ambient-parçacıksız
     * Slowness I — oyuncu "bacaklarım ağırlaştı" der, sebebini göremez.</p>
     *
     * <p>Çok oyuncuda hiç uygulanmıyor: orada Candy'nin aracı yavaşlatma değil
     * {@link ArkadasFisilti}.</p>
     */
    private void yavaslatmaUygula(ServerLevel world, Player oyuncu) {
        if (++yavaslatmaSayaci < 20) return;
        yavaslatmaSayaci = 0;
        if (world.getServer().getPlayerList().getPlayerCount() != 1) return;
        if (!(oyuncu instanceof ServerPlayer sp)) return;
        if (sp.isCreative() || sp.isSpectator()) return;
        if (this.distanceToSqr(sp) > 256.0) return;         // 16 blok

        sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.SLOWNESS,
            60,      // 3 sn — yakınlık sürdükçe yenilenir, bırakınca hemen geçer
            0,       // Slowness I
            true,    // ambient: HUD'da parlak ikon yerine soluk
            false,   // parçacık YOK — sebebi görünmesin
            false)); // ikon YOK
    }

    /**
     * Oyuncuya doğru kazar. Sertliğe göre süre harcıyor ve uygun aleti eline alıyor —
     * sesi ve ritmi bir oyuncunun kazmasından ayırt edilemesin.
     */
    private void kazmayaDevam(ServerLevel world, Player oyuncu) {
        BlockPos hedefYon = yonBloku(oyuncu);
        if (hedefYon == null) return;

        if (!hedefYon.equals(kaziBlok)) {
            kaziBlok = hedefYon;
            kaziIlerleme = 0f;
            aletSec(world.getBlockState(hedefYon));
        }

        BlockState st = world.getBlockState(kaziBlok);
        if (st.isAir() || st.getCollisionShape(world, kaziBlok).isEmpty()) {
            kaziBlok = null;
            return;
        }
        // Kırılmaz blokları hiç denemesin (bedrock, barrier, obsidian elle kazılamaz
        // sayılmadı ama sertliği çok yüksek; -1 kırılmaz demek).
        float sertlik = st.getDestroySpeed(world, kaziBlok);
        if (sertlik < 0f) {
            kaziBlok = null;
            return;
        }

        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        kaziIlerleme += 1.0f / Math.max(1.0f, sertlik * 12.0f);
        if (kaziIlerleme >= 1.0f) {
            world.destroyBlock(kaziBlok, true, this);
            kaziBlok = null;
            kaziIlerleme = 0f;
        }
    }

    /** Oyuncu yönünde, kendi göz hizasındaki ilk katı blok. */
    private BlockPos yonBloku(Player oyuncu) {
        net.minecraft.world.phys.Vec3 yon = oyuncu.position().subtract(this.position());
        if (yon.lengthSqr() < 1.0e-4) return null;
        yon = yon.normalize();
        BlockPos bas = this.blockPosition().above();
        BlockPos ileri = bas.offset(
            (int) Math.round(Math.signum(yon.x)), 0, (int) Math.round(Math.signum(yon.z)));
        if (ileri.equals(bas)) return null;
        return ileri;
    }

    /** Blok sertliğine uygun aleti eline alır. Envanteri yok; alet "beliriyor". */
    private void aletSec(BlockState st) {
        ItemStack alet;
        if (st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)) {
            alet = new ItemStack(Items.IRON_PICKAXE);
        } else if (st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)) {
            alet = new ItemStack(Items.IRON_AXE);
        } else if (st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) {
            alet = new ItemStack(Items.IRON_SHOVEL);
        } else {
            alet = ItemStack.EMPTY;
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, alet);
    }

    /**
     * Oyuncunun yolunu keser: önüne bir blok koyar.
     *
     * <p>Koyulan blok oyuncunun <b>çevresinden</b> seçiliyor (etrafındaki en sık taş
     * türü değil, basitçe toprak/taş) — çünkü oyuncunun hiç görmediği bir blok
     * ("neden burada mavi beton var") mekaniği oyuna değil moda bağlar. Tanıdık blok
     * daha ürkütücü: "bunu ben koymadım".</p>
     */
    private void yolKes(ServerLevel world, Player oyuncu) {
        if (koymaSogumasi > 0) return;
        if (this.distanceToSqr(oyuncu) > 400.0) return;     // 20 blok

        net.minecraft.world.phys.Vec3 yon = oyuncu.getViewVector(1.0f);
        BlockPos onu = oyuncu.blockPosition().offset(
            (int) Math.round(Math.signum(yon.x) * 2), 0,
            (int) Math.round(Math.signum(yon.z) * 2));

        // Ayak ve baş hizası boş olmalı; oyuncunun İÇİNE blok koymak onu sıkıştırır
        // ve boğulma hasarı verir — bu bir hata gibi görünür, korku değil.
        if (onu.equals(oyuncu.blockPosition())) return;
        BlockState s0 = world.getBlockState(onu);
        if (!s0.isAir() && !s0.canBeReplaced()) return;
        if (!world.getBlockState(onu.below()).isRedstoneConductor(world, onu.below())) return;

        Block koy = world.getBlockState(onu.below()).getBlock();
        if (koy == Blocks.AIR) koy = Blocks.STONE;
        world.setBlockAndUpdate(onu, koy.defaultBlockState());
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        koymaSogumasi = 60;
    }

    /** Önündeki kapıyı açar — kapalı kapı Arkadaş'ı durdurmasın. */
    private void kapiAc(ServerLevel world) {
        if (kapiSogumasi > 0) return;
        BlockPos p = this.blockPosition();
        for (net.minecraft.core.Direction d
                : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos k = p.relative(d);
            BlockState st = world.getBlockState(k);
            if (st.getBlock() instanceof DoorBlock door) {
                if (!st.getValue(DoorBlock.OPEN)) {
                    door.setOpen(this, world, st, k, true);
                    kapiSogumasi = 40;
                    return;
                }
            }
        }
    }

    /**
     * Ölüm — mekaniğin ağırlık merkezi.
     *
     * <p>İki şey oluyor ve ikisi de <b>kalıcı</b>:</p>
     * <ol>
     *   <li>Öldüren oyuncunun {@code arkadasYasi} ekseni 1.0'a çıkıyor. Bu eksen
     *       {@code candyOfke} için taban: öfke bir daha o değerin altına inmiyor.
     *       Candy'nin diğer bütün duyguları sönümlenir; bu sönmez.</li>
     *   <li>{@link ArkadasKaydi} dünyaya "öldürüldü" yazıyor ve bir daha spawn
     *       edilmiyor. Oyuncu Candy'yi gerçekten yaralayabildi — <i>bir kez</i>.</li>
     * </ol>
     *
     * <p>Geri gelmemesi önemli: tekrar doğan bir düşman "boss fight" olur, kaybı
     * hissedilmez. Bir daha gelmeyen bir şeyi öldürmek ise oyuncuda kalıcı bir
     * tereddüt bırakıyor — ve Candy'de kalıcı bir öfke.</p>
     */
    @Override
    public void die(DamageSource source) {
        try {
            if (this.level() instanceof ServerLevel world) {
                ArkadasKaydi.olduruldu(world);

                ServerPlayer katil = null;
                if (source.getEntity() instanceof ServerPlayer sp) katil = sp;

                // Yas bütün oyunculara yazılıyor: Arkadaş dünyada tekti, kaybı
                // yalnız öldürene değil Candy'nin o dünyadaki bütün ilişkilerine ait.
                for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) {
                    TruePersonality tp = PersonalityManager.get(p);
                    if (tp == null) continue;
                    tp.arkadasYasi = 1.0;
                    tp.candyOfke = Math.min(1.0, Math.max(tp.candyOfke, tp.ofkeTabani()));
                    tp.paranoyaSkoru = Math.min(100.0, tp.paranoyaSkoru + 10.0);
                    if (p == katil) {
                        tp.arkadasOldurdu = 1;
                        tp.candyOzguven = Math.max(0.0, tp.candyOzguven - 10.0);
                    }
                    p.displayClientMessage(Component.translatable("candy.arkadas.oldu")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY,
                                   net.minecraft.ChatFormatting.ITALIC), false);
                }

                Candy.LOG.info("[Candy] Arkadaş öldürüldü ({}) — bir daha gelmeyecek",
                    katil == null ? "bilinmeyen" : katil.getGameProfile().getName());
            }
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Arkadaş ölüm işlenemedi: {}", t.toString());
        }
        super.die(source);
    }

    /** Arkadaş eşya düşürmez — ölümünden kazanç çıkmasın. */
    @Override
    protected void dropFromLootTable(ServerLevel world, DamageSource source, boolean causedByPlayer) {
        // Kasıtlı boş.
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;   // Kalıcı. Uzaklaşmak kurtarmıyor.
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putString("candyArkadasGorev", gorev.name());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        try {
            gorev = Gorev.valueOf(view.getStringOr("candyArkadasGorev", Gorev.IZLE.name()));
        } catch (Throwable t) {
            gorev = Gorev.IZLE;
        }
    }
}
