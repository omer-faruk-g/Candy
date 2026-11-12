package com.photonpulse.candy.sign;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.dialog.DialogReaksiyon;
import com.photonpulse.candy.simulation.ShadowTree;
import com.photonpulse.candy.simulation.ShadowTreeManager;
import com.photonpulse.candy.tree.TreeBranch;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.tree.TreeNode;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * SignSystem — tabela okuma & yerleştirme.
 *
 * İki iş yapar:
 *  1. Oyuncunun yakınındaki tabelaları tarar, "Candy'nin sorusuna cevap" var mı
 *     bakar. Varsa cevabı analiz eder ve tabelaCevaplari'na ekler.
 *  2. Belirli sıklıklarla (oyuncu bakmadığında) yeni tabelalar yerleştirir.
 *
 * NOT: Mixin YOK — tabela okumak için sadece world.getBlockEntity kullanılır.
 */
public class SignSystem {

    private static final int TARAMA_ARALIGI = 40;   // 2 saniye
    private static final int YAZMA_ARALIGI  = 200;  // 10 saniye — kurulum daha hızlı ilerlesin
    private static int tickSayaci = 0;
    private static final Random RNG = new Random();

    // Hangi oyuncu, hangi tabelayı zaten okudu (aynı cevabı 2 kez analiz etmeyelim)
    private static final Map<UUID, Map<TabelaAnahtar, Long>> okunmusTabelalar = new HashMap<>();

    /**
     * Tarama tamponu — her taramada temizlenip yeniden doldurulur, tahsis edilmez.
     * Yalnızca sunucu thread'inden kullanılır (tüm SignSystem gibi).
     */
    private static final List<BlockPos> adaylar = new ArrayList<>(32);

    // 12000 tick (10 dk) geçen okuma kayıtları silinir — uzun oturumda birikmesin
    private static final long OKUNMUS_UNUT_TICK = 12000L;

    // Candy'nin yerleştirdiği tabelaların konumları → ORİJİNAL metin
    // Oyuncu üzerine yazınca metin değişir → cevap olarak kabul edilir.
    // LinkedHashMap: ekleme sırasını korur, MAKS_TAKIP dolarsa en eskisi düşer.
    private static final int MAKS_TAKIP_TABELA = 500;
    private static final Map<TabelaAnahtar, TabelaKaydi> candyTabelalari = new LinkedHashMap<>();

    /** Tabelanın ÖMRÜ — bu kadar tick dokunulmazsa yok olur (600 tick = 30 saniye). */
    private static final long OMUR_TICK = 600L;
    /** Ömür süpürmesi sıklığı — 30 s'lik bir süre için 1 s çözünürlük kâfi. */
    private static final int OMUR_SUPURME_ARALIGI = 20;
    /**
     * Chunk yüklü olmadığı için bloğu kaldıramadığımız kaydın ne kadar sonra tamamen
     * düşürüleceği. İhmal zaten 30 s'de işlenmiş olur; bu yalnızca temizlik üst sınırı.
     */
    private static final long KAYIT_SERT_TAVAN = OMUR_TICK + 6000L;   // +5 dk
    /** Kaç ihmal başarımı tetikler. */
    private static final int OFKE_BASARIM_ESIGI = 20;

    /**
     * Tabela kimliği — <b>dünya + konum</b>.
     *
     * v7'de anahtar yalnızca {@code BlockPos} idi. Kurul her 5 dakikada oyuncuyu 8 dünya
     * arasında taşıdığından Koridor'daki (100,64,100) ile overworld'deki (100,64,100) aynı
     * girdiye çakışıyordu: bir dünyadaki oyuncu tabelası başka dünyada "Candy'nin" sanılıyor,
     * cevabı analiz edilmiyordu. {@code sunucuKapan()} bunu kapanışta temizliyordu ama kök
     * neden tek oturum içinde de yaşıyordu.
     */
    private record TabelaAnahtar(ResourceKey<Level> dunya, BlockPos poz) {}

    /**
     * Bir Candy tabelasının sicili.
     *
     * Record değil kasıtlı: {@code konulduTick} sahibi çevrimdışıyken İLERİ kaydırılır
     * (sayaç durur), yani her süpürmede değişiyor. Record olsa 500 tabela × saniyede 1
     * süpürme = saniyede 500 gereksiz nesne olurdu.
     */
    private static final class TabelaKaydi {
        final UUID sahip;
        final String metin;
        /** Ömür sayacının başlangıcı; sahip çevrimdışıyken ileri kaydırılır. */
        long konulduTick;
        /** Süpürmenin bu kaydı en son gördüğü tick (çevrimdışı kaydırması için). */
        long sonGorulduTick;
        /** İhmal bir kez sayıldı mı (chunk yüklenmediği için blok hâlâ duruyor olabilir). */
        boolean ihmalSayildi;

        TabelaKaydi(UUID sahip, String metin, long tick) {
            this.sahip = sahip;
            this.metin = metin;
            this.konulduTick = tick;
            this.sonGorulduTick = tick;
        }
    }

    // Cevap analiz edilmiş tabelalar (per-player). Aynı tabela ikinci kez okunmasın:
    // orjinal silindikten sonra 30 sn sonra tekrar cevap sanılıp analiz ediliyordu.
    private static final Map<UUID, Set<TabelaAnahtar>> analizEdilen = new HashMap<>();

    /**
     * Kurulum sorusu konduğu anda yakında zaten duran yabancı tabelalar (anahtar → o anki metin).
     * Bunlar soruya cevap sayılmaz; metni sonradan değişirse sayılır.
     */
    private static final Map<TabelaAnahtar, String> soruOncesiTabelalar = new HashMap<>();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(SignSystem::onTick);
        Candy.LOG.info("[Candy] SignSystem aktif");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        okunmusTabelalar.remove(uuid);
        analizEdilen.remove(uuid);
    }

    /**
     * Dış sistemlerin (ör. ModFarkindaligi) Candy tabelası yerleştirmesi için public API.
     * Oyuncunun yakınında uygun boş yer bulur, mesajı 4 satıra sarar, tabelayı koyar.
     * @return yerleştirildiyse true
     */
    public static boolean candyTabelaYerlestir(ServerPlayer player, Component mesaj) {
        BlockPos hedef = yakinBosYer(player, 6, 14);
        if (hedef == null) return false;
        Component[] satir = wrapText(mesaj, 15, 4);
        yerlestir((ServerLevel) player.level(), hedef, player, satir, true);
        PersonalityManager.get(player).sonMudahaleTick = player.level().getGameTime();
        return true;
    }

    /**
     * Sunucu kapanırken per-server state'i temizle. Single-player'da JVM sıfırlanmadığı için
     * dünya A → dünya B geçişinde eski koordinatlar yeni dünyada false-positive yaratıyordu:
     * dünya B'de aynı koordinattaki oyuncu tabelası "Candy'nin" sanılıp cevap analiz edilmiyordu.
     */
    /** Sicil dosyası: <world>/candy/tabelalar.tsv — sicil yalnız bellekte kalırsa yeniden açılışta
     *  Candy tabelaları ömrü bitince kaybolmaz ve "serbest cevap" sanılıp tepki zinciri doğurur. */
    private static java.nio.file.Path sicilDosyasi = null;

    public static void sunucuBaslat(MinecraftServer server) {
        candyTabelalari.clear();
        sicilDosyasi = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("candy").resolve("tabelalar.tsv");
        if (!java.nio.file.Files.exists(sicilDosyasi)) return;
        try {
            for (String satir : java.nio.file.Files.readAllLines(sicilDosyasi)) {
                String[] p = satir.split("\t", 7);
                if (p.length < 7) continue;
                var dunya = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(p[0]));
                TabelaKaydi k = new TabelaKaydi(UUID.fromString(p[2]), p[6], Long.parseLong(p[3]));
                k.sonGorulduTick = Long.parseLong(p[4]);
                k.ihmalSayildi = "1".equals(p[5]);
                candyTabelalari.put(new TabelaAnahtar(dunya, BlockPos.of(Long.parseLong(p[1]))), k);
            }
            Candy.LOG.info("[Candy] tabela sicili yüklendi: {}", candyTabelalari.size());
        } catch (Exception e) {
            Candy.LOG.warn("[Candy] tabela sicili okunamadı: {}", e.getMessage());
        }
    }

    private static void sicilYaz() {
        if (sicilDosyasi == null) return;
        StringBuilder sb = new StringBuilder();
        for (var e : candyTabelalari.entrySet()) {
            TabelaKaydi k = e.getValue();
            var key = e.getKey().dunya();
            sb.append(key.location()).append('\t')
              .append(e.getKey().poz().asLong()).append('\t')
              .append(k.sahip).append('\t').append(k.konulduTick).append('\t')
              .append(k.sonGorulduTick).append('\t').append(k.ihmalSayildi ? '1' : '0').append('\t')
              .append(k.metin.replace('\t', ' ').replace('\n', ' ')).append('\n');
        }
        try {
            java.nio.file.Files.createDirectories(sicilDosyasi.getParent());
            java.nio.file.Files.writeString(sicilDosyasi, sb.toString());
        } catch (Exception e) {
            Candy.LOG.warn("[Candy] tabela sicili yazılamadı: {}", e.getMessage());
        }
    }

    /** Candy'nin kendi cümleleri (her iki dil) — sicili kaybolmuş eski tabelaları tanımak için. */
    private static Set<String> candyCumleleri = null;

    private static String normalize(String s) {
        return s.replace('|', ' ').replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean candyCumlesiMi(String metin) {
        if (candyCumleleri == null) {
            candyCumleleri = new HashSet<>();
            for (String dil : new String[]{"en_us", "tr_tr"}) {
                try (var in = SignSystem.class.getResourceAsStream("/assets/candy/lang/" + dil + ".json")) {
                    if (in == null) continue;
                    var obj = com.google.gson.JsonParser.parseReader(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    for (var e : obj.entrySet()) {
                        String k = e.getKey();
                        if (k.startsWith("candy.reaction.") || k.startsWith("candy.setup.")
                            || k.startsWith("candy.observation.") || k.startsWith("candy.rutin.")
                            || k.startsWith("candy.attribution.")) {
                            String v = normalize(e.getValue().getAsString());
                            if (!v.isEmpty() && !v.contains("%")) candyCumleleri.add(v);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        String m = normalize(metin);
        if (m.isEmpty()) return false;
        for (String c : candyCumleleri) {
            if (m.equals(c)) return true;
            // Kurulum sorusu + ipucu satırı / 15x3 kırpılmış uzun cümle
            if (c.length() >= 12 && (m.startsWith(c) || (m.length() >= 12 && c.startsWith(m)))) return true;
        }
        return false;
    }

    public static void sunucuKapan() {
        sicilYaz();
        sicilDosyasi = null;
        candyTabelalari.clear();
        okunmusTabelalar.clear();
        analizEdilen.clear();
        tickSayaci = 0;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;

        if (tickSayaci % TARAMA_ARALIGI == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tabelalariTara(player);
            }
        }

        if (tickSayaci % YAZMA_ARALIGI == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                belkiTabelaYaz(player);
            }
        }

        if (tickSayaci % OMUR_SUPURME_ARALIGI == 0) {
            omurSupur(server);
        }
    }

    /**
     * ÖMÜR SÜPÜRMESİ — 30 saniyedir dokunulmamış Candy tabelalarını yok eder ve ihmali sayar.
     *
     * <p>Dört ayrı durumu ayırt eder, çünkü üçü ihmal DEĞİLDİR:</p>
     * <ul>
     *   <li><b>Sahip çevrimdışı</b> → sayaç durur. Yokluk, yok saymak değil.</li>
     *   <li><b>Tabela kırılmış</b> → kayıt sessizce düşer, ihmal sayılmaz. Kırmak bir
     *       tepkidir; oyuncu Candy'yi görmezden gelmemiş.</li>
     *   <li><b>Chunk yüklü değil</b> → oyuncu dokunmadan uzaklaşmış: ihmal SAYILIR
     *       (kimse kıramaz, tabela orada duruyor), ama blok kaldırılamaz; kayıt
     *       {@link #KAYIT_SERT_TAVAN}'a kadar tutulup tekrar denenir.</li>
     *   <li><b>Tabela yerinde</b> → ihmal sayılır, blok yok edilir.</li>
     * </ul>
     */
    private static void omurSupur(MinecraftServer server) {
        if (candyTabelalari.isEmpty()) return;

        Iterator<Map.Entry<TabelaAnahtar, TabelaKaydi>> it = candyTabelalari.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TabelaAnahtar, TabelaKaydi> e = it.next();
            TabelaAnahtar anahtar = e.getKey();
            TabelaKaydi kayit = e.getValue();

            ServerLevel world = server.getLevel(anahtar.dunya());
            if (world == null) continue;                 // dünya yüklü değil — kaydı tut
            final long now = world.getGameTime();

            ServerPlayer sahip = server.getPlayerList().getPlayer(kayit.sahip);
            if (sahip == null) {
                // ÇEVRİMDIŞI: geçen süreyi sayaca EKLEME — başlangıcı ileri kaydır.
                kayit.konulduTick += Math.max(0L, now - kayit.sonGorulduTick);
                kayit.sonGorulduTick = now;
                continue;
            }
            kayit.sonGorulduTick = now;

            if (now - kayit.konulduTick < OMUR_TICK) continue;   // süre dolmadı

            if (!world.isLoaded(anahtar.poz())) {
                // Oyuncu çevrimiçi ama tabelanın chunk'ı yüklü değil: dokunmadan uzaklaşmış.
                if (!kayit.ihmalSayildi) {
                    ihmalIsle(sahip);
                    kayit.ihmalSayildi = true;
                }
                if (now - kayit.konulduTick > KAYIT_SERT_TAVAN) it.remove();
                continue;
            }

            BlockEntity be = world.getBlockEntity(anahtar.poz());
            if (!(be instanceof SignBlockEntity)) {
                it.remove();                  // KIRILMIŞ → nötr, ihmal sayılmaz
                continue;
            }

            if (!kayit.ihmalSayildi) ihmalIsle(sahip);
            yokEt(world, anahtar.poz());
            it.remove();
        }
    }

    /**
     * Tabelayı sessizce değil, <i>hissedilir</i> biçimde kaldırır: duman + boğuk tahta sesi.
     * Candy'nin cümlesinin kaybolması bir olay olmalı, fark edilmeyen bir temizlik değil.
     */
    private static void yokEt(ServerLevel world, BlockPos pos) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        try {
            world.sendParticles(ParticleTypes.SMOKE, x, y, z, 8, 0.15, 0.2, 0.15, 0.01);
            world.playSound(null, x, y, z, SoundEvents.WOOD_BREAK,
                SoundSource.BLOCKS, 0.35f, 0.55f);
        } catch (Throwable ignored) {}
        world.removeBlock(pos, false);
    }

    /**
     * Bir ihmali işler: sayacı artırır, öfkeyi büyütür, eşikte başarımı verir.
     *
     * <p>Öfke ŞİDDET değil VARYANS çarpanıdır — {@code Kurul} üyelerinin keşif gürültüsünü
     * ve {@code Hakem}'in keşif eşiğini ölçekler. Sonuç: Candy daha sert değil, daha
     * tahmin edilemez olur. 40 ihmalde doyar.</p>
     */
    private static void ihmalIsle(ServerPlayer sahip) {
        TruePersonality tp = PersonalityManager.get(sahip);
        tp.ihmalSayisi++;
        tp.candyOfke = Math.min(1.0, tp.candyOfke + 0.025);
        if (tp.ihmalSayisi == OFKE_BASARIM_ESIGI) {
            com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(sahip, "candy_kizgin");
            Candy.LOG.info("[Candy] {} 20 tabelayı yok saydı — öfke eşiği aşıldı",
                sahip.getName().getString());
        }
    }

    /**
     * Oyuncu bir Candy tabelasıyla ETKİLEŞTİ (yazıyı silip yeniden yazdı) → tabela yok olur
     * ve öfke bir tık geri çekilir. Konuşmaya başlayınca Candy sakinleşir.
     */
    private static void etkilesimdenYokEt(ServerLevel world, BlockPos pos, TruePersonality tp) {
        tp.ofkeAzalt(0.05);   // v8.3: Arkadaş yası tabanının altına inmez
        if (world.isLoaded(pos) && world.getBlockEntity(pos) instanceof SignBlockEntity) {
            yokEt(world, pos);
        }
    }

    /**
     * Oyuncunun 16 blok yakınındaki tabelaları tarar.
     * Eğer oyuncu bir tabelaya yazı yazdıysa (ve Candy tarafından yerleştirilmiş bir
     * "soru tabelasının" hemen yanında/üstündeyse) cevap olarak analiz edilir.
     */
    private static void tabelalariTara(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        BlockPos merkez = player.blockPosition();
        TruePersonality tp = PersonalityManager.get(player);
        ResourceKey<Level> dunya = world.dimension();
        Map<TabelaAnahtar, Long> okunmus =
            okunmusTabelalar.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

        // Eski kayıtları buda — sonsuz büyümesin
        long unutEsigi = world.getGameTime() - OKUNMUS_UNUT_TICK;
        okunmus.entrySet().removeIf(e -> e.getValue() < unutEsigi);

        topla(world, merkez);
        for (int i = 0; i < adaylar.size(); i++) {
            tabelaIncele(player, tp, world, dunya, adaylar.get(i), okunmus);
        }
        adaylar.clear();
    }

    /**
     * Menzildeki tabela konumlarını {@link #adaylar} tamponuna toplar.
     *
     * <p>v8 OPTİMİZASYON: eskiden 25×7×25 = <b>4375</b> adet {@code BlockPos} tahsisi +
     * 4375 {@code getBlockEntity} noktasal sorgusu yapılıyordu — oyuncu başına 2 saniyede
     * bir. 25×25'lik alan en fazla ~16 chunk'a değer ve tipik chunk'ta 0-5 block entity
     * var, yani aynı sonuç ~16 map gezintisiyle çıkıyor.</p>
     *
     * <p>İki incelik: (1) {@code getChunk(x, z, FULL, false)} chunk ÜRETMEYE zorlamaz —
     * {@code getChunk(x, z)} zorlardı ve tarama dünyayı büyütürdü. (2) Adaylar tamponlanır,
     * çünkü inceleme sırasında blok silinebiliyor ve chunk'ın block entity map'i üzerinde
     * gezerken silmek {@code ConcurrentModificationException} atardı.</p>
     */
    private static void topla(ServerLevel world, BlockPos merkez) {
        adaylar.clear();
        final int R = 12;
        final int xMin = merkez.getX() - R, xMax = merkez.getX() + R;
        final int zMin = merkez.getZ() - R, zMax = merkez.getZ() + R;
        final int yMin = merkez.getY() - 3,  yMax = merkez.getY() + 3;

        for (int cx = xMin >> 4; cx <= (xMax >> 4); cx++) {
            for (int cz = zMin >> 4; cz <= (zMax >> 4); cz++) {
                ChunkAccess c = world.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(c instanceof LevelChunk wc)) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : wc.getBlockEntities().entrySet()) {
                    if (!(e.getValue() instanceof SignBlockEntity)) continue;
                    BlockPos p = e.getKey();
                    if (p.getX() < xMin || p.getX() > xMax) continue;
                    if (p.getZ() < zMin || p.getZ() > zMax) continue;
                    if (p.getY() < yMin || p.getY() > yMax) continue;
                    adaylar.add(p.immutable());
                }
            }
        }
    }

    /** Tek bir tabelayı inceler: Candy'nin mi, cevap mı, etkileşim mi? */
    private static void tabelaIncele(ServerPlayer player, TruePersonality tp,
                                     ServerLevel world, ResourceKey<Level> dunya,
                                     BlockPos p, Map<TabelaAnahtar, Long> okunmus) {
        if (!(world.getBlockEntity(p) instanceof SignBlockEntity sign)) return;
        final TabelaAnahtar anahtar = new TabelaAnahtar(dunya, p);

        // Kısa cooldown (3 sn) — cevap algılama gecikmesini engelle
        final long now = world.getGameTime();
        Long lastRead = okunmus.get(anahtar);
        if (lastRead != null && now - lastRead < 60) return;

        String metin = tabelaMetni(sign);
        if (metin.isBlank()) return;

        // v8.1: şifre kontrolü analiz önbelleğinden ÖNCE — oyuncu daha önce sayılmış bir
        // tabelanın üstüne jetonu yazmış olabilir. SifreIzleyici kendi "çözüldü" bitmask'ini
        // tuttuğu için tekrar tetiklenme riski yok.
        com.photonpulse.candy.sifre.SifreIzleyici.kontrol(player, tp, metin, "tabela");

        // Cevap zaten analiz edildiyse tekrar okuma
        Set<TabelaAnahtar> analiz = analizEdilen.computeIfAbsent(
            player.getUUID(), k -> new HashSet<>());
        if (analiz.contains(anahtar)) {
            okunmus.put(anahtar, now);
            return;
        }

        TabelaKaydi kayit = candyTabelalari.get(anahtar);

        if (kayit == null) {
            // Candy'nin koyduğu tabela değil.
            // KURULUM sırasında: Candy tabelasına YAKIN (≤5 blok) ise o soruya cevap say.
            // Böylece oyuncu yanına yeni bir tabela koyup cevaplayabilir.
            if (!tp.kurulumTamam) {
                // Sicili kaybolmuş eski Candy tabelası oyuncunun cevabı DEĞİLDİR (canlı testte
                // "I took a note." tabelası 1. soruyu, eski soru tabelası 2. soruyu "cevapladı").
                if (candyCumlesiMi(metin)) {
                    yokEt(world, p);
                    analiz.add(anahtar);
                    okunmus.put(anahtar, now);
                    Candy.LOG.info("[Candy] yetim Candy tabelası kaldırıldı: {}", metin);
                    return;
                }
                // Soru konduğunda zaten orada duran ve metni değişmemiş tabela da cevap değildir
                // (sandık etiketi vb.) — oyuncu yeni tabela koymalı ya da metni değiştirmeli.
                if (metin.equals(soruOncesiTabelalar.get(anahtar))) {
                    okunmus.put(anahtar, now);
                    return;
                }
                TabelaAnahtar yakinCandy = null;
                for (TabelaAnahtar ca : candyTabelalari.keySet()) {
                    // Dünya kontrolü ŞART: başka dünyadaki aynı koordinat "yakın" değildir.
                    if (!ca.dunya().equals(dunya)) continue;
                    if (ca.poz().distSqr(p) <= 25) {   // 5 blok
                        yakinCandy = ca;
                        break;
                    }
                }
                if (yakinCandy != null) {
                    SignTemplates.cevapAnaliz(metin, tp);
                    Candy.LOG.info("[Candy] yakın tabela cevabı alındı: {}", metin);
                    candyTabelalari.remove(yakinCandy);
                    // ETKİLEŞİM: oyuncu cevapladı → Candy'nin soru tabelası yok olur
                    etkilesimdenYokEt(world, yakinCandy.poz(), tp);
                    analiz.add(yakinCandy);
                    analiz.add(anahtar);
                    okunmus.put(anahtar, now);
                    reaksiyonYerlestir(player, tp, metin);
                    return;
                }
                // Yakında Candy tabelası yok — kullanıcının dekoratif tabelası, sayma
                okunmus.put(anahtar, now);
                return;
            }
            // Sicili kaybolmuş eski Candy tabelası (v10.0.1 öncesi): cevap değil — kaybolsun,
            // yoksa her açılışta tepki tabelası doğurup çoğalıyordu.
            if (candyCumlesiMi(metin)) {
                yokEt(world, p);
                analiz.add(anahtar);
                okunmus.put(anahtar, now);
                Candy.LOG.info("[Candy] yetim Candy tabelası kaldırıldı: {}", metin);
                return;
            }
            // Kurulum sonrası her tabela cevap materyali olabilir (bir kez)
            SignTemplates.cevapAnaliz(metin, tp);
            Candy.LOG.info("[Candy] serbest tabela cevabı: {}", metin);
            analiz.add(anahtar);
            okunmus.put(anahtar, now);
            reaksiyonYerlestir(player, tp, metin);
            return;
        }

        // Candy'nin koyduğu tabela
        if (kayit.metin.equals(metin)) {
            // İçerik hala orijinal — oyuncu henüz cevaplamamış.
            // NOT: düzenleme ekranını açıp AYNI metinle kapatmak etkileşim DEĞİLDİR;
            // istek "yazı silip yazma" diyor ve bu karşılaştırma onu bedava veriyor.
            okunmus.put(anahtar, now);
            return;
        }

        // Oyuncu Candy'nin tabelasını değiştirdi — cevap (ve ETKİLEŞİM)
        SignTemplates.cevapAnaliz(metin, tp);
        Candy.LOG.info("[Candy] kurulum cevabı alındı: {}", metin);
        candyTabelalari.remove(anahtar);
        analiz.add(anahtar);
        okunmus.put(anahtar, now);
        // Metin OKUNDUKTAN sonra yok et — cevap kaybolmasın
        etkilesimdenYokEt(world, p, tp);
        // Diyalog reaksiyonu — Candy oyuncuya cevap versin
        reaksiyonYerlestir(player, tp, metin);
    }

    /** Soru konduğu an yakındaki yabancı tabelaların metnini kaydeder (cevap sayılmasınlar). */
    private static void soruOncesiniKaydet(ServerLevel world, BlockPos soru) {
        if (soruOncesiTabelalar.size() > 2048) soruOncesiTabelalar.clear();
        topla(world, soru);
        for (int i = 0; i < adaylar.size(); i++) {
            BlockPos p = adaylar.get(i);
            if (p.equals(soru)) continue;
            TabelaAnahtar a = new TabelaAnahtar(world.dimension(), p);
            if (candyTabelalari.containsKey(a)) continue;
            if (world.getBlockEntity(p) instanceof SignBlockEntity s) {
                soruOncesiTabelalar.put(a, tabelaMetni(s));
            }
        }
        adaylar.clear();
    }

    /**
     * Rastgele bir olasılıkla, oyuncunun görmediği bir yere tabela yerleştir.
     * Candy sabırlı — sık yerleştirmiyor.
     */
    private static void belkiTabelaYaz(ServerPlayer player) {
        TruePersonality tp = PersonalityManager.get(player);

        // Kurulum tamamlanmadıysa kurulum sorusu koy
        if (!tp.kurulumTamam) {
            String soru = SignTemplates.siradakiKurulumSorusu(tp);
            if (soru == null) {
                tp.kurulumTamam = true;
                tp.kurulumTamamTick = player.level().getGameTime();
                com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "denek");
                Candy.LOG.info("[Candy] {} için kurulum tamamlandı", player.getName().getString());
                return;
            }
            BlockPos hedef = yakinBosYer(player, 4, 8);
            if (hedef != null) {
                // Soruyu 3 satıra wrap, son satırda hint. Uzun sorular otomatik böl.
                Component[] soruSatir = wrapText(Component.translatable(soru), 15, 3);
                yerlestir((ServerLevel) player.level(), hedef, player, new Component[]{
                    soruSatir[0], soruSatir[1], soruSatir[2],
                    // DÜZ METİN şart: vanilla, çevrilebilir (translatable) satır içeren tabelada
                    // düzenleme ekranını açmaz (SignBlock.hasEditableText) — oyuncu sağ tıklayıp
                    // soruyu cevaplayamıyordu (canlı testte 6/6 sağ tık ekransız kaldı).
                    Component.literal(Component.translatable(SignTemplates.CEVAPLA_HINT).getString())
                }, true);
                soruOncesiniKaydet((ServerLevel) player.level(), hedef);
                tp.sonMudahaleTick = player.level().getGameTime();
                Candy.LOG.info("[Candy] kurulum sorusu yerleştirildi: {}", soru);
            }
            return;
        }

        // Kurulum sonrası — düşük olasılıkla kişisel/gözlem tabelası.
        // Psikoloji kötüyse ihtimal artar (agresivlik çarpanı 1-2.5x).
        double agresivlik = Psikoloji.agresivlikCarpani(tp);
        if (RNG.nextDouble() > 0.25 * agresivlik) return;

        // %15 ihtimalle ShadowTree'nin baskın etkileşim dalından — Candy "diğer üsluptan" konuşur
        TreeNode baskin;
        ShadowTree shadow = ShadowTreeManager.getIfExists(player.getUUID());
        if (shadow != null && RNG.nextDouble() < 0.15) {
            baskin = shadow.tree.baskinAlt(TreeBranch.ETKILESIM);
        } else {
            baskin = TreeGrowth.get(player.getUUID()).baskinAlt(TreeBranch.ETKILESIM);
        }

        Component metin = RNG.nextDouble() < 0.4
            ? SignTemplates.kisiselAtif(tp, baskin)
            : SignTemplates.gozlemUret(tp, baskin);

        BlockPos hedef = yakinBosYer(player, 8, 16);
        if (hedef != null) {
            // Tüm 4 satıra wrap
            Component[] satir = wrapText(metin, 15, 4);
            yerlestir((ServerLevel) player.level(), hedef, player, satir, true);
            tp.sonMudahaleTick = player.level().getGameTime();
        }
    }

    /**
     * Rastgele bir yönde uygun boş yer bul.
     * Önce oyuncunun Y'sinde ve ±3 Y'de dener (mağara/yeraltı için). Bulamazsa topY'e düşer.
     * Böylece kullanıcı yeraltındayken tabelalar yerüstüne yerleşmez, hep göremediği yerde kalmaz.
     */
    private static BlockPos yakinBosYer(ServerPlayer player, int minMesafe, int maxMesafe) {
        ServerLevel world = (ServerLevel) player.level();
        BlockPos merkez = player.blockPosition();
        for (int deneme = 0; deneme < 20; deneme++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double mesafe = minMesafe + RNG.nextDouble() * (maxMesafe - minMesafe);
            int x = merkez.getX() + (int)(Math.cos(angle) * mesafe);
            int z = merkez.getZ() + (int)(Math.sin(angle) * mesafe);

            // 1) Oyuncunun Y seviyesi (mağara/yeraltı için kritik)
            int y = merkez.getY();
            BlockPos p = new BlockPos(x, y, z);
            if (uygunTabelaYeri(world, p)) return p;

            // 2) ±3 Y aralığında dene
            for (int dy = 1; dy <= 3; dy++) {
                p = new BlockPos(x, y + dy, z);
                if (uygunTabelaYeri(world, p)) return p;
                p = new BlockPos(x, y - dy, z);
                if (uygunTabelaYeri(world, p)) return p;
            }

            // 3) Son çare: yüzey Y'si (yerüstü açık alan için)
            int topY = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            if (topY != y) {   // Zaten denediysek atla
                p = new BlockPos(x, topY, z);
                if (uygunTabelaYeri(world, p)) return p;
            }
        }
        return null;
    }

    private static boolean uygunTabelaYeri(ServerLevel world, BlockPos p) {
        return world.getBlockState(p).isAir()
            && world.getBlockState(p.below()).isRedstoneConductor(world, p.below());
    }

    /**
     * Oyuncunun cevabına Candy'nin reaksiyonu — 3-6 blok yakına tabela yerleştir.
     * Reaksiyon null dönerse sessiz kalır.
     */
    private static void reaksiyonYerlestir(ServerPlayer player, TruePersonality tp, String cevap) {
        Component reaksiyon = DialogReaksiyon.reaksiyonUret(cevap, tp);
        if (reaksiyon == null) return;
        BlockPos hedef = yakinBosYer(player, 3, 6);
        if (hedef == null) return;
        // 4 satıra wrap — reaksiyon uzunsa alt satırlara aksın
        Component[] satir = wrapText(reaksiyon, 15, 4);
        yerlestir((ServerLevel) player.level(), hedef, player, satir, true);
        tp.sonMudahaleTick = player.level().getGameTime();
        Candy.LOG.info("[Candy] diyalog reaksiyonu: {}", reaksiyon.getString());
        // v1.9.14: yalanci başarımı — Candy "yalan söylüyorsun" tarzı bir reaction verdiyse
        String key = reaksiyon.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
            ? tc.getKey() : "";
        if (key.startsWith("candy.reaction.no.")) {
            com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "yalanci");
        }
    }

    /**
     * Server-side word-wrap helper. Text.translatable → resolved string → kelime boundary'lerinden
     * böl → maxLineChars limit ile greedy fill → maxLines satır Text array döner.
     * Boş satırlar Text.empty() ile doldurulur.
     *
     * NOT: getString() server dil dosyasından resolve eder (fallback en_us). MP mixed language
     * senaryoda tüm oyuncular server-language görür. Single-player'da player'ın dili kullanılır.
     */
    private static Component[] wrapText(Component src, int maxLineChars, int maxLines) {
        String raw = src.getString();
        String[] words = raw.split("\\s+");
        List<Component> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxLineChars) {
                current.append(' ').append(word);
            } else {
                lines.add(Component.literal(current.toString()));
                if (lines.size() >= maxLines) { current.setLength(0); break; }
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0 && lines.size() < maxLines) {
            lines.add(Component.literal(current.toString()));
        }
        while (lines.size() < maxLines) lines.add(Component.empty());
        return lines.toArray(new Component[0]);
    }

    /**
     * Belirtilen konuma özel meşe tabelası yerleştir. Tabela player'a doğru döner
     * — böylece oyuncu tabela metnini hep görür (backface değil).
     */
    private static void yerlestir(ServerLevel world, BlockPos pos, ServerPlayer player,
                                  Component[] satirlar, boolean candyMi) {
        // Rotation: 0=güney, 4=batı, 8=kuzey, 12=doğu (16 adım tam tur)
        double dx = player.getX() - (pos.getX() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        double angle = Math.atan2(-dx, dz);
        int rotation = ((int) Math.round(angle * 8.0 / Math.PI) + 16) & 15;

        BlockState state = Blocks.OAK_SIGN.defaultBlockState()
            .setValue(BlockStateProperties.ROTATION_16, rotation);
        world.setBlockAndUpdate(pos, state);
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) return;

        SignText metin = new SignText();
        for (int i = 0; i < Math.min(4, satirlar.length); i++) {
            metin = metin.setMessage(i, satirlar[i]);
        }
        sign.setText(metin, true);
        // Waxed olmadığından emin ol — oyuncu sağ tıklayıp edit UI'sını açabilsin.
        sign.setWaxed(false);

        if (candyMi) {
            if (candyTabelalari.size() >= MAKS_TAKIP_TABELA) {
                Iterator<TabelaAnahtar> it = candyTabelalari.keySet().iterator();
                if (it.hasNext()) { it.next(); it.remove(); }
            }
            candyTabelalari.put(
                new TabelaAnahtar(world.dimension(), pos.immutable()),
                new TabelaKaydi(player.getUUID(), metinBirlestir(satirlar), world.getGameTime()));
        }

        sign.setChanged();
        world.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
    }

    /**
     * yerlestir'de kullanılan Text satırlarını, tabelaMetni'nin ürettiği format ile
     * BİREBİR aynı şekilde tek string'e çevirir. Front + "|" + Back (back placement'ta boş).
     *
     * NOT: Sağ tık BACK yüzü de edit'liyor MC 1.20+'da. Sadece front okursak, arka yüzü
     * düzenleyen oyuncunun cevabı algılanmıyordu. Şimdi iki yüz de kontrol edilir.
     */
    private static String metinBirlestir(Component[] satirlar) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(4, satirlar.length);
        for (int i = 0; i < limit; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(satirlar[i].getString());
        }
        // Back yüzü placement'ta boş; "|" delimiter ile ayır — tabelaMetni ile symmetric.
        return sb.toString().trim() + "|";
    }

    /**
     * Bir SignBlockEntity'nin FRONT + BACK metinlerini tek string'e çevirir.
     * Format: "front-text|back-text". Boş back → "front-text|".
     */
    private static String tabelaMetni(SignBlockEntity sign) {
        return yuzMetin(sign.getFrontText()) + "|" + yuzMetin(sign.getBackText());
    }

    private static String yuzMetin(SignText t) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            Component line = t.getMessage(i, false);
            if (line != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(line.getString());
            }
        }
        return sb.toString().trim();
    }
}
