package com.photonpulse.candy.mob;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MobKoordinasyon (v8.3) — Candy öfkelendikçe dünyadaki mob'ları <b>oyuncu bazlı</b>
 * yönetir. Kurul'un ve Vagus'un oyun dünyasına ilk doğrudan eli.
 *
 * <h3>NİYE "GÜÇLENDİRME" DEĞİL, "KOORDİNASYON"</h3>
 * İlk akla gelen şey mob'un canını ve hasarını yükseltmek. Bu <b>yanlış</b> olurdu:
 * <ul>
 *   <li>Görünmez bir difficulty slider'dır — oyuncu 4 vuruşta ölen zombinin 7 vuruş
 *       dayanmasını "mod bozuk" diye okur, "Candy kızdı" diye okumaz.</li>
 *   <li>Survival mekaniğidir, psikolojik korku değil. Modun bütün değeri Candy'nin
 *       oyuncuyu <i>tanıması</i>nda; sayı şişirmek o bilgiyi hiç kullanmaz.</li>
 *   <li>Ölçülemez: oyuncu ölünce bunun Candy'den mi şanssızlıktan mı olduğunu
 *       ayırt edemez, dolayısıyla Kurul da ödülü öğrenemez.</li>
 * </ul>
 *
 * <p>Burada yapılan şey mob'u <b>güçlendirmek değil, yönetmek</b>. Hiçbir öznitelik
 * değişmiyor — zombi hâlâ zombi, aynı can, aynı hasar. Değişen tek şey <i>davranış</i>:
 * hepsi aynı anda aynı oyuncuyu biliyor, ses çıkarmıyor, bakılmayan taraftan geliyor ve
 * birbirine saldırmıyor. Korku "zombi güçlendi"de değil, <b>"bir şey onları
 * yönetiyor"</b>da.</p>
 *
 * <h3>BEŞ MÜDAHALE</h3>
 * <ol>
 *   <li><b>Yönlendirme</b> — yarıçaptaki hostile mob'lara hedef olarak oyuncu verilir.</li>
 *   <li><b>Kör noktadan yaklaşma</b> — {@code arkayaBakma} ekseni (v8.2) burada
 *       karşılığını buluyor: oyuncunun <i>bakmadığı</i> tarafta olan mob hızlandırılır,
 *       görüş alanındaki yavaşlatılır. Candy ölçtüğü şeyi kullanıyor.</li>
 *   <li><b>Sessizlik</b> — yaklaşan mob {@code setSilent(true)}. Hırıltı yok, inleme yok.
 *       En ucuz ve en ürkütücü müdahale: oyuncu sesi duymaya alışmıştır.</li>
 *   <li><b>Hedef kilidi</b> — mob oyuncunun izini kaybetmiyor, hedef her turda yenilenir.</li>
 *   <li><b>Ateşkes</b> — mob'lar birbirini hedef almaz; aralarında kavga çıkmaz.</li>
 * </ol>
 *
 * <h3>AI'IN ELİ NEREDE</h3>
 * Şiddet {@code candyOfke} ile ölçekleniyor, yarıçap ve sessizlik eşiği ise
 * {@code vagusDarbe} ile — yani yerel LLM tahmini isabet ettiğinde (Vagus darbeyi 1.0'a
 * çeker) koordinasyon o oyuncuda genişliyor. Yeni bir LLM çıkış yolu açılmadı, var olan
 * kanal kullanıldı: Vagus hâlâ yalnızca sayı yazıyor, metin değil.
 *
 * <h3>KAPI</h3>
 * {@code perdeKademe >= 1}. Kurul kendini kanıtlamadan dünyaya el uzatmıyor.
 */
public final class MobKoordinasyon {

    private MobKoordinasyon() {}

    /** Kaç tick'te bir tarama. 20 = saniyede bir; mob hedefi bu sıklıkta yenilenir. */
    private static final int ARALIK = 20;

    /** Taban yarıçap (blok). Öfke ve Vagus darbesi bunu büyütür. */
    private static final double TABAN_YARICAP = 12.0;

    /** En geniş yarıçap — bundan ötesi performans ve "her yerden geliyor" saçmalığı. */
    private static final double MAKS_YARICAP = 40.0;

    /** Bu öfkenin altında hiçbir şey yapılmaz. */
    private static final double OFKE_ESIGI = 0.18;

    /** Sessizleştirme bu birleşik şiddetten sonra başlar. */
    private static final double SESSIZLIK_ESIGI = 0.45;

    /** Kör nokta hızlandırması: bakılmayan taraftaki mob'un navigasyon hız çarpanı. */
    private static final double KOR_NOKTA_HIZ = 1.25;

    /** Görüş alanı yavaşlatması — oyuncu baktığı sürece mob yaklaşmakta isteksiz. */
    private static final double GORUS_HIZ = 0.75;

    /** "Oyuncu buraya bakıyor" sayılan yarı açı (derece). */
    private static final double GORUS_YARI_ACI = 55.0;

    /** Sessizleştirdiğimiz mob'lar — ateşkes bozulunca geri açmak için. */
    private static final Map<UUID, Boolean> sessizlestirilen = new ConcurrentHashMap<>();

    private static int sayac = 0;

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(MobKoordinasyon::onTick);
        Candy.LOG.info("[Candy] MobKoordinasyon aktif — öfke mob davranışını yönetiyor");
    }

    private static void onTick(MinecraftServer server) {
        if (++sayac < ARALIK) return;
        sayac = 0;
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                oyuncuIcin(player);
            }
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] MobKoordinasyon tick hatası: {}", t.toString());
        }
    }

    private static void oyuncuIcin(ServerPlayer player) {
        TruePersonality tp = PersonalityManager.get(player);
        if (tp == null) return;
        if (tp.perdeKademe < 1) return;

        double ofke = Math.max(0.0, Math.min(1.0, tp.candyOfke));
        if (ofke < OFKE_ESIGI) return;

        // Vagus darbesi yarıçapı ve şiddeti birlikte büyütüyor.
        double darbe = Math.max(0.0, Math.min(1.0, tp.vagusDarbe));
        double siddet = Math.min(1.0, ofke * 0.8 + darbe * 0.35);
        double yaricap = Math.min(MAKS_YARICAP, TABAN_YARICAP + siddet * 26.0);

        if (!(player.level() instanceof ServerLevel world)) return;

        AABB kutu = player.getBoundingBox().inflate(yaricap, yaricap * 0.6, yaricap);
        List<Monster> moblar = world.getEntitiesOfClass(
            Monster.class, kutu, m -> m.isAlive() && !m.isRemoved());
        if (moblar.isEmpty()) return;

        int yonlendirilen = 0;
        for (Monster mob : moblar) {
            // Candy'nin kendi varlıkları koordine edilmez — onların kendi mantığı var.
            if (com.photonpulse.candy.clone.CloneManager.klonMu(mob)) continue;
            if (mob instanceof com.photonpulse.candy.arkadas.ArkadasEntity) continue;

            // 1 + 4: yönlendirme ve hedef kilidi (her turda yenilenir, iz kaybolmaz)
            mob.setTarget(player);
            mob.setPersistenceRequired();
            yonlendirilen++;

            // 5: ateşkes — mob'lar birbirine saldırmasın
            LivingEntity hedef = mob.getTarget();
            if (hedef != null && !(hedef instanceof ServerPlayer)) {
                mob.setTarget(player);
            }

            // 3: sessizlik
            sessizlikAyarla(mob, siddet >= SESSIZLIK_ESIGI);

            // 2: kör noktadan yaklaşma
            korNoktaHizi(player, mob, tp, siddet);
        }

        if (yonlendirilen > 0 && Candy.LOG.isDebugEnabled()) {
            Candy.LOG.debug("[Candy] koordinasyon: {} mob -> {} (şiddet {})",
                yonlendirilen, player.getGameProfile().getName(), String.format("%.2f", siddet));
        }
    }

    /**
     * Mob'u susturur ya da sesini geri verir.
     *
     * <p>Geri verme önemli: öfke düşünce mob'un kalıcı olarak sessiz kalması
     * oyuncuya "bu mod ses sistemini bozdu" dedirtir. Yalnızca <i>bizim</i>
     * susturduklarımız geri açılıyor — zaten sessiz olan (ör. başka bir mod ya da
     * {@code /data} ile susturulmuş) mob'a dokunulmaz.</p>
     */
    private static void sessizlikAyarla(Mob mob, boolean sessizOlsun) {
        UUID id = mob.getUUID();
        if (sessizOlsun) {
            if (!mob.isSilent()) {
                mob.setSilent(true);
                sessizlestirilen.put(id, Boolean.TRUE);
            }
        } else if (sessizlestirilen.remove(id) != null) {
            mob.setSilent(false);
        }
    }

    /**
     * Oyuncunun bakmadığı taraftaki mob'u hızlandırır, baktığı taraftakini yavaşlatır.
     *
     * <p>{@code arkayaBakma} yüksekse (oyuncu sürekli arkasını kolluyor) etki
     * <b>azalır</b> — o oyuncuda kör nokta zaten küçük, orada ısrar etmek boşa kürek.
     * Eksen düşükse (hiç dönmeyen oyuncu) etki tam uygulanır. Yani ölçüm doğrudan
     * stratejiyi değiştiriyor.</p>
     */
    private static void korNoktaHizi(ServerPlayer player, Mob mob,
                                     TruePersonality tp, double siddet) {
        Vec3 bakis = player.getViewVector(1.0f).normalize();
        Vec3 fark = mob.position().subtract(player.position());
        double uzunluk = fark.length();
        if (uzunluk < 1.0e-4) return;
        Vec3 yon = fark.scale(1.0 / uzunluk);

        // Yatay düzlemde açı — dikey bakış kör noktayı belirlemiyor.
        double nokta = bakis.x * yon.x + bakis.z * yon.z;
        double yatayNorm = Math.sqrt(bakis.x * bakis.x + bakis.z * bakis.z)
            * Math.sqrt(yon.x * yon.x + yon.z * yon.z);
        if (yatayNorm < 1.0e-4) return;
        double cosAci = Math.max(-1.0, Math.min(1.0, nokta / yatayNorm));
        boolean gorusIcinde = Math.toDegrees(Math.acos(cosAci)) <= GORUS_YARI_ACI;

        // Sürekli arkasını kollayan oyuncuda kör nokta avantajı sönüyor.
        double korNoktaGucu = 1.0 - Math.max(0.0, Math.min(1.0, tp.arkayaBakma));
        double carpan = gorusIcinde
            ? 1.0 - (1.0 - GORUS_HIZ) * siddet
            : 1.0 + (KOR_NOKTA_HIZ - 1.0) * siddet * korNoktaGucu;

        try {
            mob.getNavigation().setSpeedModifier(carpan);
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(player, carpan);
            }
        } catch (Throwable ignored) {
            // Bazı mob'ların navigasyonu yoktur (ör. Shulker). Sessizce atla.
        }
    }

    /** Oyuncu çıkınca iz bırakmayalım. */
    public static void sunucuKapan() {
        sessizlestirilen.clear();
        sayac = 0;
    }
}
