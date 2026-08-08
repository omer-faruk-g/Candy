package com.photonpulse.candy.sign;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.tree.TreeBranch;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.tree.TreeNode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
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
    private static final int YAZMA_ARALIGI  = 600;  // 30 saniye
    private static int tickSayaci = 0;
    private static final Random RNG = new Random();

    // Hangi oyuncu, hangi tabelayı zaten okudu (aynı cevabı 2 kez analiz etmeyelim)
    private static final Map<UUID, Map<BlockPos, Long>> okunmusTabelalar = new HashMap<>();

    // Candy'nin yerleştirdiği tabelaların konumları — NBT yerine hafızada tut
    // (Sunucu yeniden başlasa da yıkanmayan tek risk: yeni yerleştirilenler)
    private static final Set<BlockPos> candyTabelalari = new HashSet<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SignSystem::onTick);
        Candy.LOG.info("[Candy] SignSystem aktif");
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

        int R = 12;
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    BlockPos p = merkez.add(dx, dy, dz);
                    BlockEntity be = world.getBlockEntity(p);
                    if (!(be instanceof SignBlockEntity sign)) continue;

                    // Aynı tabelayı 30 saniyeden önce yeniden okuma
                    long now = world.getTime();
                    Long lastRead = okunmus.get(p);
                    if (lastRead != null && now - lastRead < 600) continue;

                    String metin = tabelaMetni(sign);
                    if (metin.isBlank()) continue;

                    // Candy'nin koyduğu tabelaları oyuncu cevabı olarak analiz etmeyelim
                    if (candyTabelalari.contains(p)) continue;

                    // Oyuncu yazmış — cevap olarak analiz et
                    SignTemplates.cevapAnaliz(metin, tp);
                    Candy.LOG.debug("[Candy] tabela cevabı alındı: {}", metin);
                    okunmus.put(p, now);
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
                Candy.LOG.info("[Candy] {} için kurulum tamamlandı", player.getName().getString());
                return;
            }
            BlockPos hedef = yakinBosYer(player, 4, 8);
            if (hedef != null) {
                yerlestir((ServerWorld) player.getWorld(), hedef, new String[]{
                    "", soru, "", "— cevapla —"
                }, true);
                Candy.LOG.info("[Candy] kurulum sorusu yerleştirildi: {}", soru);
            }
            return;
        }

        // Kurulum sonrası — düşük olasılıkla kişisel/gözlem tabelası
        if (RNG.nextDouble() > 0.25) return;

        TreeNode baskin = TreeGrowth.get(player.getUuid()).baskinAlt(TreeBranch.ETKILESIM);
        String metin = RNG.nextDouble() < 0.4
            ? SignTemplates.kisiselAtif(tp, baskin)
            : SignTemplates.gozlemUret(tp, baskin);

        BlockPos hedef = yakinBosYer(player, 8, 16);
        if (hedef != null) {
            yerlestir((ServerWorld) player.getWorld(), hedef, new String[]{
                "", metin, "", ""
            }, true);
        }
    }

    /**
     * Oyuncunun 8-16 blok uzağında, oyuncunun görmediği yönde bir boş yer bul.
     * Çok karmaşık raycast yerine basit heuristik: rastgele nokta seç, üstü hava mı bak.
     */
    private static BlockPos yakinBosYer(ServerPlayerEntity player, int minMesafe, int maxMesafe) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos merkez = player.getBlockPos();
        for (int deneme = 0; deneme < 10; deneme++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double mesafe = minMesafe + RNG.nextDouble() * (maxMesafe - minMesafe);
            int x = merkez.getX() + (int)(Math.cos(angle) * mesafe);
            int z = merkez.getZ() + (int)(Math.sin(angle) * mesafe);
            int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z);
            BlockPos p = new BlockPos(x, y, z);

            // Üstü hava, altı katı olsun
            if (world.getBlockState(p).isAir()
                && world.getBlockState(p.down()).isSolidBlock(world, p.down())) {
                return p;
            }
        }
        return null;
    }

    /**
     * Belirtilen konuma özel meşe tabelası yerleştir.
     * Candy'nin koyduğu tabelaların konumu {@link #candyTabelalari} setinde tutulur.
     */
    private static void yerlestir(ServerWorld world, BlockPos pos, String[] satirlar, boolean candyMi) {
        world.setBlockState(pos, Blocks.OAK_SIGN.getDefaultState());
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) return;

        SignText metin = new SignText();
        for (int i = 0; i < Math.min(4, satirlar.length); i++) {
            metin = metin.withMessage(i, Text.literal(satirlar[i]));
        }
        sign.setText(metin, true);   // ön yüz

        if (candyMi) {
            candyTabelalari.add(pos.toImmutable());
        }

        sign.markDirty();
        world.updateListeners(pos, sign.getCachedState(), sign.getCachedState(), 3);
    }

    /**
     * Bir SignBlockEntity'nin ön yüzündeki tüm metni tek stringde döner.
     */
    private static String tabelaMetni(SignBlockEntity sign) {
        SignText t = sign.getFrontText();
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
