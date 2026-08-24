package com.photonpulse.candy.yuz;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.MapColor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * YuzHaritasi (v3.0) — Kimlik korkusu.
 *
 * Oyuncunun skin'inin yüz bölümü (head face + hat overlay) Mojang'dan çekilir,
 * 128x128 kilitli bir haritaya çizilir, altına "I SEE YOU" yazılır ve sessizce
 * oyuncunun envanterine bırakılır. "Seni görüyorum" — Candy senin yüzünü aldı.
 *
 * Skin çekimi ağ işi → arka planda (async), sonuç server thread'inde uygulanır.
 * Skin yoksa (offline profil) jenerik bir yüz çizilir.
 */
public class YuzHaritasi {

    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");
    private static final int TICK_ARALIGI = DEBUG ? 400 : 4200;   // debug 20sn / normal 3.5 dk
    private static final long COOLDOWN = DEBUG ? 400L : 6000L;     // debug 20sn / normal 5 dk
    private static final double IHTIMAL = DEBUG ? 1.0 : 0.15;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // 5x7 bitmap font — sadece "I SEE YOU" için gerekli harfler
    private static final String[] F_I = {"11111","00100","00100","00100","00100","00100","11111"};
    private static final String[] F_S = {"01111","10000","10000","01110","00001","00001","11110"};
    private static final String[] F_E = {"11111","10000","10000","11110","10000","10000","11111"};
    private static final String[] F_Y = {"10001","10001","01010","00100","00100","00100","00100"};
    private static final String[] F_O = {"01110","10001","10001","10001","10001","10001","01110"};
    private static final String[] F_U = {"10001","10001","10001","10001","10001","10001","01110"};
    private static final String[] F_SP = {"00000","00000","00000","00000","00000","00000","00000"};

    // Önceden hesaplanmış palet (id -> {r,g,b, byte})
    private static int[][] PALET;

    public static void register() {
        paletHazirla();
        ServerTickEvents.END_SERVER_TICK.register(YuzHaritasi::onTick);
        Candy.LOG.info("[Candy] YuzHaritasi aktif — {} (v3.0)",
            DEBUG ? "DEBUG (20sn)" : "3.5 dk kontrol, 5 dk cooldown, %15");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTicks() % TICK_ARALIGI != 0) return;
        long simdi = server.getOverworld().getTime();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (tp.bossOldurulmeTick > 0) continue; // WIN 30dk sessizlik
            if (simdi - tp.sonYuzHaritasiTick < COOLDOWN) continue;
            if (Math.random() >= IHTIMAL) continue;

            tp.sonYuzHaritasiTick = simdi;
            olustur(player);
        }
    }

    /** Oyuncunun yüzünden harita üret ve envanterine bırak (async skin çekimi). */
    public static void olustur(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String skinUrl = skinUrlBul(player);

        // Ağ işi arka planda
        Thread t = new Thread(() -> {
            int[][] yuz;
            try {
                yuz = (skinUrl != null) ? skinYuzuCek(skinUrl) : jenerikYuz();
            } catch (Throwable ex) {
                yuz = jenerikYuz();
            }
            final int[][] yuzFinal = yuz;
            // Server thread'ine dön
            server.execute(() -> {
                if (player.isRemoved()) return;
                try {
                    haritaVer(player, yuzFinal);
                } catch (Throwable ex) {
                    Candy.LOG.warn("[Candy] YuzHaritasi harita ver hata: {}", ex.getMessage());
                }
            });
        }, "Candy-YuzHaritasi");
        t.setDaemon(true);
        t.start();
    }

    /** GameProfile textures property'sinden skin URL'sini çöz (yoksa null). */
    private static String skinUrlBul(ServerPlayerEntity player) {
        try {
            var props = player.getGameProfile().getProperties().get("textures");
            for (var prop : props) {
                String json = new String(Base64.getDecoder().decode(prop.value()),
                    java.nio.charset.StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonObject textures = root.getAsJsonObject("textures");
                if (textures != null && textures.has("SKIN")) {
                    return textures.getAsJsonObject("SKIN").get("url").getAsString();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Skin PNG'sini çek, yüz (8x8) + şapka overlay'i birleştir → 8x8 RGB. */
    private static int[][] skinYuzuCek(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        BufferedImage skin = ImageIO.read(new ByteArrayInputStream(resp.body()));
        if (skin == null) return jenerikYuz();

        int[][] yuz = new int[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int base = skin.getRGB(8 + i, 8 + j);
                int hat = (skin.getHeight() >= 16 && skin.getWidth() >= 48)
                    ? skin.getRGB(40 + i, 8 + j) : 0;
                int hatA = (hat >>> 24) & 0xFF;
                yuz[i][j] = (hatA > 128) ? (hat & 0xFFFFFF) : (base & 0xFFFFFF);
            }
        }
        return yuz;
    }

    /** Skin yoksa jenerik ürkütücü yüz — ten rengi + koyu gözler. */
    private static int[][] jenerikYuz() {
        int ten = 0xC08870, goz = 0x101010, agiz = 0x401010;
        int[][] y = new int[8][8];
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++) y[i][j] = ten;
        y[2][3] = goz; y[5][3] = goz;      // gözler
        y[3][6] = agiz; y[4][6] = agiz;    // ağız
        return y;
    }

    /** 8x8 yüzü 128x128 kilitli haritaya çiz + "I SEE YOU" ekle, envantere ver. */
    private static void haritaVer(ServerPlayerEntity player, int[][] yuz) {
        ServerWorld world = (ServerWorld) player.getWorld();
        MapIdComponent id = world.increaseAndGetMapId();
        MapState state = MapState.of((byte) 0, true, world.getRegistryKey());
        world.putMapState(id, state);

        byte[] c = state.colors; // 128*128

        // Arka plan — koyu kırmızı-siyah
        java.util.Arrays.fill(c, rgbToByte(0x0A0000));

        // Yüz: 8x8 → 96x96 (12x), x=16..111, y=6..101
        int olcek = 12, ox = 16, oy = 6;
        for (int mx = 0; mx < 96; mx++) {
            for (int my = 0; my < 96; my++) {
                int fx = mx / olcek, fy = my / olcek;
                int rgb = yuz[Math.min(7, fx)][Math.min(7, fy)];
                setPixel(c, ox + mx, oy + my, rgbToByte(rgb));
            }
        }

        // "I SEE YOU" — alt banda, kırmızı, ölçek 2
        byte kirmizi = rgbToByte(0xE01010);
        yaziyiCiz(c, "I SEE YOU", 108, 2, kirmizi);

        state.markDirty();

        ItemStack harita = new ItemStack(Items.FILLED_MAP);
        harita.set(DataComponentTypes.MAP_ID, id);
        harita.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal("???").formatted(Formatting.DARK_RED, Formatting.ITALIC));

        player.giveItemStack(harita);
        Candy.LOG.info("[Candy] YuzHaritasi: {} envanterine yüz haritası bırakıldı",
            player.getName().getString());
    }

    private static void setPixel(byte[] c, int x, int y, byte val) {
        if (x < 0 || x >= 128 || y < 0 || y >= 128) return;
        c[y * 128 + x] = val;
    }

    /** Metni 5x7 font ile ortalayarak çiz (verilen y satırından itibaren). */
    private static void yaziyiCiz(byte[] c, String metin, int y, int olcek, byte renk) {
        int harfGen = (5 + 1) * olcek; // 6px * ölçek
        int toplamGen = metin.length() * harfGen;
        int baslaX = (128 - toplamGen) / 2;
        int x = baslaX;
        for (char ch : metin.toCharArray()) {
            String[] font = fontBul(ch);
            for (int fy = 0; fy < 7; fy++) {
                for (int fx = 0; fx < 5; fx++) {
                    if (font[fy].charAt(fx) == '1') {
                        for (int dy = 0; dy < olcek; dy++)
                            for (int dx = 0; dx < olcek; dx++)
                                setPixel(c, x + fx * olcek + dx, y + fy * olcek + dy, renk);
                    }
                }
            }
            x += harfGen;
        }
    }

    private static String[] fontBul(char ch) {
        return switch (ch) {
            case 'I' -> F_I;
            case 'S' -> F_S;
            case 'E' -> F_E;
            case 'Y' -> F_Y;
            case 'O' -> F_O;
            case 'U' -> F_U;
            default -> F_SP;
        };
    }

    // ── Palet ──

    private static void paletHazirla() {
        java.util.List<int[]> liste = new java.util.ArrayList<>();
        for (int idx = 1; idx < 64; idx++) {
            MapColor mc;
            try { mc = MapColor.get(idx); } catch (Throwable e) { continue; }
            if (mc == null || mc == MapColor.CLEAR) continue;
            int rgb = mc.color; // 0xRRGGBB taban
            byte b = mc.getRenderColorByte(MapColor.Brightness.NORMAL);
            liste.add(new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, b});
        }
        PALET = liste.toArray(new int[0][]);
    }

    // RGB → map byte cache. Bir yüzde en fazla ~64 farklı renk var; cache olmadan
    // her piksel için tüm palet taranıyordu (~550 bin karşılaştırma/harita).
    private static final java.util.Map<Integer, Byte> RENK_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** RGB'yi en yakın map color byte'ına çevir (cache'li). */
    private static byte rgbToByte(int rgb) {
        Byte kayitli = RENK_CACHE.get(rgb);
        if (kayitli != null) return kayitli;

        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int enYakin = 0; long enKucuk = Long.MAX_VALUE;
        for (int[] p : PALET) {
            long dr = r - p[0], dg = g - p[1], db = b - p[2];
            long d = dr * dr + dg * dg + db * db;
            if (d < enKucuk) { enKucuk = d; enYakin = p[3]; }
        }
        byte sonuc = (byte) enYakin;
        if (RENK_CACHE.size() < 4096) RENK_CACHE.put(rgb, sonuc);
        return sonuc;
    }
}
