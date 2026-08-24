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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

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
    private static final Map<UUID, Map<BlockPos, Long>> okunmusTabelalar = new HashMap<>();

    // 12000 tick (10 dk) geçen okuma kayıtları silinir — uzun oturumda birikmesin
    private static final long OKUNMUS_UNUT_TICK = 12000L;

    // Candy'nin yerleştirdiği tabelaların konumları → ORİJİNAL metin
    // Oyuncu üzerine yazınca metin değişir → cevap olarak kabul edilir.
    // LinkedHashMap: ekleme sırasını korur, MAKS_TAKIP dolarsa en eskisi düşer.
    private static final int MAKS_TAKIP_TABELA = 500;
    private static final Map<BlockPos, String> candyTabelalari = new LinkedHashMap<>();

    // Cevap analiz edilmiş tabelalar (per-player). Aynı tabela ikinci kez okunmasın:
    // orjinal silindikten sonra 30 sn sonra tekrar cevap sanılıp analiz ediliyordu.
    private static final Map<UUID, Set<BlockPos>> analizEdilen = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SignSystem::onTick);
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
    public static boolean candyTabelaYerlestir(ServerPlayerEntity player, Text mesaj) {
        BlockPos hedef = yakinBosYer(player, 6, 14);
        if (hedef == null) return false;
        Text[] satir = wrapText(mesaj, 15, 4);
        yerlestir((ServerWorld) player.getWorld(), hedef, player, satir, true);
        PersonalityManager.get(player).sonMudahaleTick = player.getWorld().getTime();
        return true;
    }

    /**
     * Sunucu kapanırken per-server state'i temizle. Single-player'da JVM sıfırlanmadığı için
     * dünya A → dünya B geçişinde eski koordinatlar yeni dünyada false-positive yaratıyordu:
     * dünya B'de aynı koordinattaki oyuncu tabelası "Candy'nin" sanılıp cevap analiz edilmiyordu.
     */
    public static void sunucuKapan() {
        candyTabelalari.clear();
        okunmusTabelalar.clear();
        analizEdilen.clear();
        tickSayaci = 0;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;

        if (tickSayaci % TARAMA_ARALIGI == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tabelalariTara(player);
            }
        }

        if (tickSayaci % YAZMA_ARALIGI == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                belkiTabelaYaz(player);
            }
        }
    }

    /**
     * Oyuncunun 16 blok yakınındaki tabelaları tarar.
     * Eğer oyuncu bir tabelaya yazı yazdıysa (ve Candy tarafından yerleştirilmiş bir
     * "soru tabelasının" hemen yanında/üstündeyse) cevap olarak analiz edilir.
     */
    private static void tabelalariTara(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos merkez = player.getBlockPos();
        TruePersonality tp = PersonalityManager.get(player);
        Map<BlockPos, Long> okunmus = okunmusTabelalar.computeIfAbsent(player.getUuid(), k -> new HashMap<>());

        // Eski kayıtları buda — sonsuz büyümesin
        long unutEsigi = world.getTime() - OKUNMUS_UNUT_TICK;
        okunmus.entrySet().removeIf(e -> e.getValue() < unutEsigi);

        int R = 12;
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    BlockPos p = merkez.add(dx, dy, dz);
                    BlockEntity be = world.getBlockEntity(p);
                    if (!(be instanceof SignBlockEntity sign)) continue;

                    // Kısa cooldown (3 sn) — cevap algılama gecikmesini engelle
                    long now = world.getTime();
                    Long lastRead = okunmus.get(p);
                    if (lastRead != null && now - lastRead < 60) continue;

                    String metin = tabelaMetni(sign);
                    if (metin.isBlank()) continue;

                    // Cevap zaten analiz edildiyse tekrar okuma
                    Set<BlockPos> analiz = analizEdilen.computeIfAbsent(
                        player.getUuid(), k -> new HashSet<>());
                    if (analiz.contains(p)) {
                        okunmus.put(p, now);
                        continue;
                    }

                    String orjinal = candyTabelalari.get(p);

                    if (orjinal == null) {
                        // Candy'nin koyduğu tabela değil.
                        // KURULUM sırasında: Candy tabelasına YAKIN (≤5 blok) ise o soruya cevap say.
                        // Böylece oyuncu yanına yeni bir tabela koyup cevaplayabilir.
                        if (!tp.kurulumTamam) {
                            BlockPos yakinCandy = null;
                            for (BlockPos cp : candyTabelalari.keySet()) {
                                if (cp.getSquaredDistance(p) <= 25) {   // 5 blok
                                    yakinCandy = cp;
                                    break;
                                }
                            }
                            if (yakinCandy != null) {
                                SignTemplates.cevapAnaliz(metin, tp);
                                Candy.LOG.info("[Candy] yakın tabela cevabı alındı: {}", metin);
                                candyTabelalari.remove(yakinCandy);
                                analiz.add(yakinCandy);
                                analiz.add(p);
                                okunmus.put(p, now);
                                reaksiyonYerlestir(player, tp, metin);
                                continue;
                            }
                            // Yakında Candy tabelası yok — kullanıcının dekoratif tabelası, sayma
                            okunmus.put(p, now);
                            continue;
                        }
                        // Kurulum sonrası her tabela cevap materyali olabilir (bir kez)
                        SignTemplates.cevapAnaliz(metin, tp);
                        Candy.LOG.info("[Candy] serbest tabela cevabı: {}", metin);
                        analiz.add(p);
                        okunmus.put(p, now);
                        reaksiyonYerlestir(player, tp, metin);
                        continue;
                    }

                    // Candy'nin koyduğu tabela
                    if (orjinal.equals(metin)) {
                        // İçerik hala orijinal — oyuncu henüz cevaplamamış
                        okunmus.put(p, now);
                        continue;
                    }

                    // Oyuncu Candy'nin tabelasını değiştirdi — cevap
                    SignTemplates.cevapAnaliz(metin, tp);
                    Candy.LOG.info("[Candy] kurulum cevabı alındı: {}", metin);
                    candyTabelalari.remove(p);
                    analiz.add(p);
                    okunmus.put(p, now);
                    // Diyalog reaksiyonu — Candy oyuncuya cevap versin
                    reaksiyonYerlestir(player, tp, metin);
                }
            }
        }
    }

    /**
     * Rastgele bir olasılıkla, oyuncunun görmediği bir yere tabela yerleştir.
     * Candy sabırlı — sık yerleştirmiyor.
     */
    private static void belkiTabelaYaz(ServerPlayerEntity player) {
        TruePersonality tp = PersonalityManager.get(player);

        // Kurulum tamamlanmadıysa kurulum sorusu koy
        if (!tp.kurulumTamam) {
            String soru = SignTemplates.siradakiKurulumSorusu(tp);
            if (soru == null) {
                tp.kurulumTamam = true;
                tp.kurulumTamamTick = player.getWorld().getTime();
                com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "denek");
                Candy.LOG.info("[Candy] {} için kurulum tamamlandı", player.getName().getString());
                return;
            }
            BlockPos hedef = yakinBosYer(player, 4, 8);
            if (hedef != null) {
                // Soruyu 3 satıra wrap, son satırda hint. Uzun sorular otomatik böl.
                Text[] soruSatir = wrapText(Text.translatable(soru), 15, 3);
                yerlestir((ServerWorld) player.getWorld(), hedef, player, new Text[]{
                    soruSatir[0], soruSatir[1], soruSatir[2],
                    Text.translatable(SignTemplates.CEVAPLA_HINT)
                }, true);
                tp.sonMudahaleTick = player.getWorld().getTime();
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
        ShadowTree shadow = ShadowTreeManager.getIfExists(player.getUuid());
        if (shadow != null && RNG.nextDouble() < 0.15) {
            baskin = shadow.tree.baskinAlt(TreeBranch.ETKILESIM);
        } else {
            baskin = TreeGrowth.get(player.getUuid()).baskinAlt(TreeBranch.ETKILESIM);
        }

        Text metin = RNG.nextDouble() < 0.4
            ? SignTemplates.kisiselAtif(tp, baskin)
            : SignTemplates.gozlemUret(tp, baskin);

        BlockPos hedef = yakinBosYer(player, 8, 16);
        if (hedef != null) {
            // Tüm 4 satıra wrap
            Text[] satir = wrapText(metin, 15, 4);
            yerlestir((ServerWorld) player.getWorld(), hedef, player, satir, true);
            tp.sonMudahaleTick = player.getWorld().getTime();
        }
    }

    /**
     * Rastgele bir yönde uygun boş yer bul.
     * Önce oyuncunun Y'sinde ve ±3 Y'de dener (mağara/yeraltı için). Bulamazsa topY'e düşer.
     * Böylece kullanıcı yeraltındayken tabelalar yerüstüne yerleşmez, hep göremediği yerde kalmaz.
     */
    private static BlockPos yakinBosYer(ServerPlayerEntity player, int minMesafe, int maxMesafe) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos merkez = player.getBlockPos();
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
            int topY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z);
            if (topY != y) {   // Zaten denediysek atla
                p = new BlockPos(x, topY, z);
                if (uygunTabelaYeri(world, p)) return p;
            }
        }
        return null;
    }

    private static boolean uygunTabelaYeri(ServerWorld world, BlockPos p) {
        return world.getBlockState(p).isAir()
            && world.getBlockState(p.down()).isSolidBlock(world, p.down());
    }

    /**
     * Oyuncunun cevabına Candy'nin reaksiyonu — 3-6 blok yakına tabela yerleştir.
     * Reaksiyon null dönerse sessiz kalır.
     */
    private static void reaksiyonYerlestir(ServerPlayerEntity player, TruePersonality tp, String cevap) {
        Text reaksiyon = DialogReaksiyon.reaksiyonUret(cevap, tp);
        if (reaksiyon == null) return;
        BlockPos hedef = yakinBosYer(player, 3, 6);
        if (hedef == null) return;
        // 4 satıra wrap — reaksiyon uzunsa alt satırlara aksın
        Text[] satir = wrapText(reaksiyon, 15, 4);
        yerlestir((ServerWorld) player.getWorld(), hedef, player, satir, true);
        tp.sonMudahaleTick = player.getWorld().getTime();
        Candy.LOG.info("[Candy] diyalog reaksiyonu: {}", reaksiyon.getString());
        // v1.9.14: yalanci başarımı — Candy "yalan söylüyorsun" tarzı bir reaction verdiyse
        String key = reaksiyon.getContent() instanceof net.minecraft.text.TranslatableTextContent tc
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
    private static Text[] wrapText(Text src, int maxLineChars, int maxLines) {
        String raw = src.getString();
        String[] words = raw.split("\\s+");
        List<Text> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxLineChars) {
                current.append(' ').append(word);
            } else {
                lines.add(Text.literal(current.toString()));
                if (lines.size() >= maxLines) { current.setLength(0); break; }
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0 && lines.size() < maxLines) {
            lines.add(Text.literal(current.toString()));
        }
        while (lines.size() < maxLines) lines.add(Text.empty());
        return lines.toArray(new Text[0]);
    }

    /**
     * Belirtilen konuma özel meşe tabelası yerleştir. Tabela player'a doğru döner
     * — böylece oyuncu tabela metnini hep görür (backface değil).
     */
    private static void yerlestir(ServerWorld world, BlockPos pos, ServerPlayerEntity player,
                                  Text[] satirlar, boolean candyMi) {
        // Rotation: 0=güney, 4=batı, 8=kuzey, 12=doğu (16 adım tam tur)
        double dx = player.getX() - (pos.getX() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        double angle = Math.atan2(-dx, dz);
        int rotation = ((int) Math.round(angle * 8.0 / Math.PI) + 16) & 15;

        BlockState state = Blocks.OAK_SIGN.getDefaultState()
            .with(Properties.ROTATION, rotation);
        world.setBlockState(pos, state);
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) return;

        SignText metin = new SignText();
        for (int i = 0; i < Math.min(4, satirlar.length); i++) {
            metin = metin.withMessage(i, satirlar[i]);
        }
        sign.setText(metin, true);
        // Waxed olmadığından emin ol — oyuncu sağ tıklayıp edit UI'sını açabilsin.
        sign.setWaxed(false);

        if (candyMi) {
            if (candyTabelalari.size() >= MAKS_TAKIP_TABELA) {
                Iterator<BlockPos> it = candyTabelalari.keySet().iterator();
                if (it.hasNext()) { it.next(); it.remove(); }
            }
            candyTabelalari.put(pos.toImmutable(), metinBirlestir(satirlar));
        }

        sign.markDirty();
        world.updateListeners(pos, sign.getCachedState(), sign.getCachedState(), 3);
    }

    /**
     * yerlestir'de kullanılan Text satırlarını, tabelaMetni'nin ürettiği format ile
     * BİREBİR aynı şekilde tek string'e çevirir. Front + "|" + Back (back placement'ta boş).
     *
     * NOT: Sağ tık BACK yüzü de edit'liyor MC 1.20+'da. Sadece front okursak, arka yüzü
     * düzenleyen oyuncunun cevabı algılanmıyordu. Şimdi iki yüz de kontrol edilir.
     */
    private static String metinBirlestir(Text[] satirlar) {
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
            Text line = t.getMessage(i, false);
            if (line != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(line.getString());
            }
        }
        return sb.toString().trim();
    }
}
