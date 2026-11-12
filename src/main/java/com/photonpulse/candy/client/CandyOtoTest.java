package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import com.photonpulse.candy.platform.IstemciTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * CandyOtoTest — <b>yalnızca test</b>: v9.1.0/v10.0.0 yollarını <b>klavye olmadan</b>
 * kendi gerçek kod yollarından koşturur.
 *
 * <h3>NİYE VAR</h3>
 * Mekaniklerin bir kısmı insan eline bağlı: ekran-haritası F12'ye, dış kanal da saatlerce
 * oynamaya (perde kademesi + ~30 dk soğuma + %25 ihtimal). Otomatik bir koşumda — CI'da,
 * uzaktan, ya da elin başka işte olduğu her an — hiçbiri denenemiyordu. Bu sınıf tuşa
 * basan ve komut yazan eli taklit ediyor, çıktıyı da tamamen log'a döküyor; böylece koşum
 * ekrana bakmadan değerlendirilebiliyor.
 *
 * <h3>NİYE GERÇEK YOL</h3>
 * <ul>
 *   <li>Çekim: {@link EkranGonder#devBasim()} yalnızca "tuşa basıldı" diyor; bekleme
 *       süresi, {@code canSend} ve GPU okuma kapısı aynen işliyor.</li>
 *   <li>Komutlar: {@code sendCommand} ile gidiyor — oyuncu sohbete yazmış gibi, aynı
 *       paket. Sunucu tarafı test kancası değil kendi Brigadier ağacı.</li>
 *   <li>Dış kanal: {@code /candy mektup} yalnızca <b>sunucu</b> seyreltmesini atlıyor
 *       (ihtimal/soğuma/kademe). İstemci kapıları ({@code mektup}, {@code mektupEposta},
 *       {@code mektupUc}) olduğu gibi duruyor, yani config boşsa tek istek bile çıkmaz.</li>
 * </ul>
 *
 * <h3>NİYE OYUNCUYA SIZMAZ</h3>
 * İki sistem özelliği birlikte gerekiyor: {@code -Dcandy.debug=true} <b>ve</b>
 * {@code -Dcandy.ototest=true}; ikisi de JVM başlangıcında verilir, oyun içinden
 * değiştirilemez. Normal oyunda sınıf hiç kayıt bile olmuyor.
 *
 * <h3>SIRA (dünyaya girdikten sonraki tick)</h3>
 * <pre>
 *    60  config durumu
 *    70  /difficulty peaceful — oyuncu ölmesin: ölüm ekranı LED kapısını kapatır
 *    80  /candy perde 2      — dış kanal kapısı (MIN_KADEME_DIS=2)
 *   100  /candy durum
 *   140  /candy saat         — join paketi geldi mi + diyalog hangi dakikayı veriyor
 *   160  klavye LED darbe(3) — Caps Lock 3 kez hızlı yanıp söner
 *   180  klavye LED kademe 10 — ~1,6 sn'de bir nabız
 *   200  1. çekim            — sunucu haritayı BASMALI
 *   300  2. çekim (5 sn)     — istemci 3 sn kapısı geçer, SUNUCU 15 sn kapısı TUTMALI
 *   380  /candy mektup       — dış kanal: istemci yerel uca POST atmalı
 *   460  /candy saat sil     — istemci saati hiç bildirmemiş gibi
 *   500  /candy saat         — artık ESKİ YOLA düşmeli
 *   520  klavye LED kademe 0 — nabız durur
 *   540  /candy sifre        — GERÇEK masaüstüne candy_NN.txt
 *   560  bitti
 *   her tick (560'a kadar): duraklatma menüsü odakta 2 sn açık kalırsa ESC — oyuncu döndü
 * </pre>
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public final class CandyOtoTest {

    private CandyOtoTest() {}

    private static final boolean AKTIF =
        Boolean.getBoolean("candy.debug") && Boolean.getBoolean("candy.ototest");

    /**
     * {@code -Dcandy.ototest.ek=<dosya>}: temel sıra bittikten sonra koşulacak ek adımlar.
     * Satır biçimi {@code <tick> <adım>}; adım bir komut ya da {@code #ekran <ad>},
     * {@code #log <metin>}, {@code #bitti}. Boş satır ve {@code //} yorum atlanır.
     */
    private static final java.util.TreeMap<Integer, java.util.List<String>> EK = ekOku();
    private static final int SON_TICK = EK.isEmpty() ? 560 : Math.max(560, EK.lastKey());

    private static java.util.TreeMap<Integer, java.util.List<String>> ekOku() {
        java.util.TreeMap<Integer, java.util.List<String>> m = new java.util.TreeMap<>();
        String yol = System.getProperty("candy.ototest.ek");
        if (!AKTIF || yol == null || yol.isBlank()) return m;
        try {
            for (String s : java.nio.file.Files.readAllLines(java.nio.file.Path.of(yol))) {
                s = s.strip();
                if (s.isEmpty() || s.startsWith("//")) continue;
                int b = s.indexOf(' ');
                if (b < 0) continue;
                int t = Integer.parseInt(s.substring(0, b));
                if (t <= 560) continue;   // temel sıranın tick'leri ona ait
                m.computeIfAbsent(t, k -> new java.util.ArrayList<>()).add(s.substring(b + 1).strip());
            }
            Candy.LOG.warn("[Candy][ototest] ek sira: {} tick, son tick {}", m.size(),
                m.isEmpty() ? "-" : m.lastKey());
        } catch (Exception e) {
            Candy.LOG.warn("[Candy][ototest] ek sira okunamadi: {}", e.toString());
        }
        return m;
    }

    private static void ekAdim(Minecraft mc, String a) {
        if (a.startsWith("#ekran")) {
            String ad = a.substring(6).strip().replaceAll("[^A-Za-z0-9_-]", "_");
            java.nio.file.Path hedef = mc.gameDirectory.toPath()
                .resolve("screenshots").resolve("ototest-" + ad + ".png");
            try {
                net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget(), kare -> {
                    try (kare) {
                        java.nio.file.Files.createDirectories(hedef.getParent());
                        kare.writeToFile(hedef);
                        Candy.LOG.warn("[Candy][ototest] ekran kaydedildi: {}", hedef);
                    } catch (Exception e) {
                        Candy.LOG.warn("[Candy][ototest] ekran yazilamadi: {}", e.toString());
                    }
                });
            } catch (Throwable t) {
                Candy.LOG.warn("[Candy][ototest] ekran alinamadi: {}", t.toString());
            }
        } else if (a.startsWith("#log")) {
            Candy.LOG.warn("[Candy][ototest] ADIM {}", a.substring(4).strip());
        } else if (a.equals("#bitti")) {
            Candy.LOG.warn("[Candy][ototest] SIRA BITTI (ek sira dahil)");
        } else if (a.startsWith("#sohbet ")) {
            // CANLI ETKİLEŞİM: gerçek sohbet paketi (komut değil) — CandyChatOkur/şifre/tuzak yolu
            String m = a.substring(8).strip();
            if (mc.player != null && mc.player.connection != null) mc.player.connection.sendChat(m);
            Candy.LOG.info("[Candy][ototest] sohbet -> {}", m);
        } else if (a.equals("#sol") || a.equals("#sag")) {
            // Gerçek fare tıkı: tuş kuyruğuna girer, Minecraft.handleKeybinds → startAttack/startUseItem
            com.mojang.blaze3d.platform.InputConstants.Key tus =
                com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(a.equals("#sol") ? 0 : 1);
            net.minecraft.client.KeyMapping.click(tus);
            Candy.LOG.info("[Candy][ototest] fare -> {}", a);
        } else if (a.startsWith("#tus ")) {
            // #tus <ileri|geri|sola|saga|zipla|egil|kos|kullan> <0|1> — tuşu basılı tut / bırak
            String[] q = a.substring(5).strip().split(" +");
            boolean bas = q.length > 1 && q[1].equals("1");
            net.minecraft.client.KeyMapping k = switch (q[0]) {
                case "ileri" -> mc.options.keyUp;
                case "geri" -> mc.options.keyDown;
                case "sola" -> mc.options.keyLeft;
                case "saga" -> mc.options.keyRight;
                case "zipla" -> mc.options.keyJump;
                case "egil" -> mc.options.keyShift;
                case "kos" -> mc.options.keySprint;
                case "kullan" -> mc.options.keyUse;
                default -> null;
            };
            if (k != null) k.setDown(bas);
            Candy.LOG.info("[Candy][ototest] tus -> {} {}", q[0], bas ? "BASILI" : "birakildi");
        } else if (a.startsWith("#sifrecoz")) {
            // CANLI ETKİLEŞİM: oyuncunun yapacağı şey — masaüstündeki en yeni candy_NN.txt'yi aç,
            // ikiliği çöz, N. satırı sohbete yaz. (#sifrecoz <0|1|2>)
            try {
                int n = Integer.parseInt(a.substring(9).strip());
                java.nio.file.Path ev = java.nio.file.Path.of(System.getProperty("user.home"));
                java.nio.file.Path yeniDosya = null;
                for (String kls : new String[]{"Desktop", "OneDrive/Desktop", "Masaüstü"}) {
                    java.nio.file.Path d = ev.resolve(kls);
                    if (!java.nio.file.Files.isDirectory(d)) continue;
                    try (var akis = java.nio.file.Files.newDirectoryStream(d, "candy_*.txt")) {
                        for (java.nio.file.Path f : akis) {
                            if (yeniDosya == null || java.nio.file.Files.getLastModifiedTime(f)
                                    .compareTo(java.nio.file.Files.getLastModifiedTime(yeniDosya)) > 0) yeniDosya = f;
                        }
                    }
                    if (yeniDosya != null) break;
                }
                java.util.List<String> cozuk = new java.util.ArrayList<>();
                if (yeniDosya != null) {
                    for (String satir : java.nio.file.Files.readAllLines(yeniDosya)) {
                        satir = satir.strip();
                        if (satir.isEmpty() || !satir.matches("[01 ]+")) continue;
                        StringBuilder sb = new StringBuilder();
                        for (String bayt : satir.split(" +")) sb.append((char) Integer.parseInt(bayt, 2));
                        cozuk.add(sb.toString());
                    }
                }
                if (n < cozuk.size() && mc.player != null) {
                    mc.player.connection.sendChat(cozuk.get(n));
                    Candy.LOG.info("[Candy][ototest] sifrecoz {} -> {} ({})", n, cozuk.get(n), yeniDosya.getFileName());
                } else {
                    Candy.LOG.warn("[Candy][ototest] sifrecoz: satir yok (dosya {}, {} satir)", yeniDosya, cozuk.size());
                }
            } catch (Exception e) {
                Candy.LOG.warn("[Candy][ototest] sifrecoz hata: {}", e.toString());
            }
        } else if (a.equals("#tabelagit")) {
            // CANLI ETKİLEŞİM: en yakın tabelayı bul, yüzünü dön, yürüyerek yanına git.
            hedefTabela = enYakinTabela(mc);
            Candy.LOG.info("[Candy][ototest] tabelagit -> {}", hedefTabela);
        } else if (a.startsWith("#yaz ")) {
            // Tabela düzenleme ekranı (#sag ile GERÇEKTEN açılmış olmalı) → metni yaz ve kapat.
            // Sunucu yalnızca ekranı açan oyuncunun paketini kabul eder (playerWhoMayEdit).
            String[] q = (a.substring(5) + "|||| ").split("[|]");
            String ekranAdi = mc.screen == null ? "yok" : mc.screen.getClass().getSimpleName();
            if (sonTabela != null && mc.player != null && mc.screen != null) {
                mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSignUpdatePacket(
                    sonTabela, true, q[0].strip(), q[1].strip(), q[2].strip(), q[3].strip()));
                mc.setScreen(null);
                Candy.LOG.info("[Candy][ototest] yaz -> {} @ {} (ekran {})", q[0].strip(), sonTabela, ekranAdi);
            } else {
                Candy.LOG.warn("[Candy][ototest] yaz: duzenleme ekrani acik degil (ekran {}, tabela {})",
                    ekranAdi, sonTabela);
            }
        } else if (a.startsWith("#slot ")) {
            // Gerçek hotbar tuşu (1-9). #slot bul <metin> → adı/bileşeni eşleşen eşyanın tuşuna bas.
            String q = a.substring(6).strip();
            int n = -1;
            if (q.startsWith("bul ") && mc.player != null) {
                String ara = q.substring(4).strip().toLowerCase(java.util.Locale.ROOT);
                for (int i = 0; i < 9; i++) {
                    var st = mc.player.getInventory().getItem(i);
                    if (st.isEmpty()) continue;
                    String tanim = (st + " " + st.getHoverName().getString() + " " + st.getComponentsPatch())
                        .toLowerCase(java.util.Locale.ROOT);
                    if (tanim.contains(ara)) { n = i + 1; break; }
                }
                // Hotbar'da yoksa ana envanterde ara: gercek "sayi tusuyla takas" tiklamasi (slot 1'e).
                for (int i = 9; n < 0 && i < 36; i++) {
                    var st = mc.player.getInventory().getItem(i);
                    if (st.isEmpty()) continue;
                    String tanim = (st + " " + st.getHoverName().getString() + " " + st.getComponentsPatch())
                        .toLowerCase(java.util.Locale.ROOT);
                    if (!tanim.contains(ara) || mc.gameMode == null) continue;
                    mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, 0,
                        net.minecraft.world.inventory.ClickType.SWAP, mc.player);
                    Candy.LOG.info("[Candy][ototest] envanter takasi: slot {} -> hotbar 1 ({})", i, ara);
                    n = 1;
                }
            } else if (q.equals("bos") && mc.player != null) {
                for (int i = 8; i >= 0; i--) if (mc.player.getInventory().getItem(i).isEmpty()) { n = i + 1; break; }
            } else {
                try { n = Integer.parseInt(q); } catch (NumberFormatException e) { n = -1; }
            }
            if (n >= 1 && n <= 9) {
                net.minecraft.client.KeyMapping.click(
                    com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(48 + n));
                Candy.LOG.info("[Candy][ototest] slot -> {} ({})", n, q);
            } else {
                Candy.LOG.warn("[Candy][ototest] slot: bulunamadi ({})", q);
            }
        } else if (a.equals("#envanter")) {
            StringBuilder sb = new StringBuilder();
            if (mc.player != null) for (int i = 0; i < 9; i++) {
                var st = mc.player.getInventory().getItem(i);
                if (!st.isEmpty()) sb.append(i + 1).append('=').append(st.getHoverName().getString())
                    .append('x').append(st.getCount()).append(' ');
            }
            Candy.LOG.info("[Candy][ototest] envanter: {}| elde: {} | can {} | konum {}", sb,
                mc.player == null ? "-" : mc.player.getMainHandItem().getHoverName().getString(),
                mc.player == null ? "-" : String.format("%.1f", mc.player.getHealth()),
                mc.player == null ? "-" : mc.player.blockPosition().toShortString());
        } else if (a.equals("#tabelasay")) {
            int say = 0;
            StringBuilder sb = new StringBuilder();
            if (mc.player != null && mc.level != null) {
                int cx = mc.player.blockPosition().getX() >> 4, cz = mc.player.blockPosition().getZ() >> 4;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    for (var e : mc.level.getChunk(cx + dx, cz + dz).getBlockEntities().entrySet()) {
                        if (e.getValue() instanceof net.minecraft.world.level.block.entity.SignBlockEntity) {
                            say++;
                            if (say <= 6) sb.append('[').append(e.getKey().toShortString()).append("] ");
                        }
                    }
                }
            }
            Candy.LOG.info("[Candy][ototest] tabela sayisi: {} {}", say, sb);
        } else if (a.equals("#cik")) {
            // Temiz kapanış: dünya kaydedilir, sunucu-kapanış kancaları (tabela sicili…) çalışır.
            Candy.LOG.warn("[Candy][ototest] CIKIS istendi — oyun temiz kapatiliyor");
            mc.stop();
        } else if (a.startsWith("#bak ")) {
            String[] q = a.substring(5).strip().split(" +");
            if (mc.player != null && q.length > 1) {
                mc.player.setYRot(Float.parseFloat(q[0]));
                mc.player.setXRot(Float.parseFloat(q[1]));
            }
            Candy.LOG.info("[Candy][ototest] bak -> {}", a.substring(5).strip());
        } else {
            komut(mc, a);
        }
    }

    /** #tabelagit hedefi (yürünürken) ve son varılan tabela (#yaz bunu kullanır). */
    private static net.minecraft.core.BlockPos hedefTabela = null;
    private static net.minecraft.core.BlockPos sonTabela = null;
    private static int yuruyusTick = 0;

    private static net.minecraft.core.BlockPos enYakinTabela(Minecraft mc) {
        net.minecraft.core.BlockPos en = null;
        double enMesafe = 24 * 24;
        int cx = mc.player.blockPosition().getX() >> 4, cz = mc.player.blockPosition().getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            var chunk = mc.level.getChunk(cx + dx, cz + dz);
            for (var e : chunk.getBlockEntities().entrySet()) {
                if (!(e.getValue() instanceof net.minecraft.world.level.block.entity.SignBlockEntity)) continue;
                double m = e.getKey().distToCenterSqr(mc.player.position());
                if (m < enMesafe) { enMesafe = m; en = e.getKey().immutable(); }
            }
        }
        return en;
    }

    /** Hedef tabelaya dön ve yürü; 2.2 bloğa gelince dur. Her tick çağrılır. */
    private static void yuruyusSur(Minecraft mc) {
        if (hedefTabela == null) return;
        double dx = hedefTabela.getX() + 0.5 - mc.player.getX();
        double dy = hedefTabela.getY() + 0.5 - mc.player.getEyeY();
        double dz = hedefTabela.getZ() + 0.5 - mc.player.getZ();
        double yatay = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, yatay)));
        boolean vardi = yatay < 2.2;
        mc.options.keyUp.setDown(!vardi);
        if (vardi || ++yuruyusTick > 200) {
            mc.options.keyUp.setDown(false);
            Candy.LOG.info("[Candy][ototest] tabelaya {} @ {} (mesafe {})",
                vardi ? "VARILDI" : "varilamadi", hedefTabela, String.format("%.1f", yatay));
            sonTabela = hedefTabela;
            hedefTabela = null;
            yuruyusTick = 0;
        }
    }

    /** Dünyada geçen tick; dünyadan çıkınca sıfırlanır. */
    private static int tick = -1;

    /** Dünya DIŞINDA geçen tick (menü/onay ekranları); dünyaya girince sıfırlanır. */
    private static int menuTick = -1;

    /** Son log'lanan menü ekranı — aynı ekranı tekrar tekrar yazmamak için. */
    private static String sonEkran = "";

    /** Duraklatma menüsü açık VE pencere odaktayken geçen tick; değilse -1. */
    private static int duraklatmaTick = -1;

    public static void register() {
        if (!AKTIF) return;
        IstemciTick.END_CLIENT_TICK.register(CandyOtoTest::istemciTick);
        Candy.LOG.warn("[Candy][ototest] AKTIF — dunyaya girince sira kendiliginden kosulacak");
    }

    private static void istemciTick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            tick = -1;
            menuSur(mc);
            return;
        }
        menuTick = -1;
        tick++;
        duraklatmaSur(mc);
        yuruyusSur(mc);

        switch (tick) {
            case 60 -> Candy.LOG.info(
                "[Candy][ototest] config: ekranHaritasi={} tus={} mektup={} epostaDolu={} ucDolu={} uc={}",
                CandyMixinConfig.ekranHaritasi,
                CandyMixinConfig.ekranHaritasiTus,
                CandyMixinConfig.mektup,
                dolu(CandyMixinConfig.mektupEposta),
                dolu(CandyMixinConfig.mektupUc),
                CandyMixinConfig.mektupUc);

            // Test dunyasindaki zombiler oyuncuyu oldurebiliyor (12:20:13'te oldu); olum
            // ekrani acikken klavye isigi kapisi kapali kalir ve LED adimi yarim gorunur.
            // Komut izni yoksa sunucu hata yazar, sira yine de devam eder.
            case 70 -> komut(mc, "difficulty peaceful");
            case 80 -> komut(mc, "candy perde 2");
            case 100 -> komut(mc, "candy durum");
            case 140 -> komut(mc, "candy saat");

            case 160 -> {
                Candy.LOG.warn("[Candy][ototest] ADIM klavye-LED: ŞİMDİ GERÇEK klavyendeki "
                    + "Caps Lock ışığına bak — 3 kez yanıp sönecek (oyun penceresi odaktayken)");
                KlavyeIsigi.darbe(3);
            }
            case 180 -> {
                Candy.LOG.warn("[Candy][ototest] ADIM klavye-LED: sürekli nabız (kademe 10) "
                    + "— LED hızlı aralıklarla yanıp sönmeye devam edecek");
                KlavyeIsigi.kademe(10);
            }

            case 200 -> {
                Candy.LOG.info("[Candy][ototest] ADIM ekran-1: cekim tetikleniyor — "
                    + "sunucu log'unda 'kare haritaya basildi' BEKLENIYOR");
                EkranGonder.devBasim();
            }

            case 300 -> {
                Candy.LOG.info("[Candy][ototest] ADIM ekran-2: 5 sn sonra ikinci cekim — "
                    + "istemci 3 sn kapisi GECER, sunucu 15 sn kapisi TUTMALI "
                    + "(ikinci harita BEKLENMIYOR)");
                EkranGonder.devBasim();
            }

            case 380 -> {
                Candy.LOG.info("[Candy][ototest] ADIM mektup: dis kanal tetikleniyor");
                komut(mc, "candy mektup");
            }

            case 460 -> {
                Candy.LOG.info("[Candy][ototest] ADIM saat-geri-dusme: kayit siliniyor");
                komut(mc, "candy saat sil");
            }
            case 500 -> komut(mc, "candy saat");
            case 520 -> {
                Candy.LOG.info("[Candy][ototest] ADIM klavye-LED: nabız durduruluyor (kademe 0)");
                KlavyeIsigi.kademe(0);
            }

            case 540 -> {
                Candy.LOG.warn("[Candy][ototest] ADIM masaustu-sifre: GERCEK masaustune "
                    + "candy_NN.txt birakiliyor — SILINMEYECEK");
                komut(mc, "candy sifre");
            }

            case 560 -> {
                // Ek sıra varsa koşucu burada durmasın: "SIRA BITTI" ek sıranın sonunda.
                if (EK.isEmpty()) Candy.LOG.warn("[Candy][ototest] SIRA BITTI");
                else Candy.LOG.warn("[Candy][ototest] temel sira tamam, ek sira basliyor");
            }

            default -> {
                java.util.List<String> ek = EK.get(tick);
                if (ek != null) for (String a : ek) ekAdim(mc, a);
            }
        }
    }

    /**
     * Dünya dışındayken ekranı <b>sürer</b>: hangi ekranda beklediğimizi log'a yazar ve
     * dünya açılışını kesen onay/uyarı ekranlarının ilk düğmesine basar.
     *
     * <p>Niye gerekli: {@code --quickPlaySingleplayer} dünyayı açmaya başlıyor, ama araya
     * bir onay ekranı girerse (deneysel bayrak/eksik datapack onayı, yedek-uyarısı,
     * sürüm uyarısı) oyun <b>sessizce</b> o ekranda duruyor — log'a tek satır düşmüyor.
     * Klavyesiz koşumda o tıklamayı atacak kimse yok, koşum sonsuza kadar bekliyordu.
     *
     * <p>Yalnız adı onay/uyarı ekranına benzeyen ekranlara dokunuyor; ana menüye,
     * dünya listesine, ayarlara karışmıyor.
     */
    private static void menuSur(Minecraft mc) {
        if (mc == null) return;
        menuTick++;
        Screen ekran = mc.screen;
        String ad = ekran == null ? "yok" : ekran.getClass().getSimpleName();
        if (!ad.equals(sonEkran)) {
            sonEkran = ad;
            Candy.LOG.info("[Candy][ototest] menu ekrani: {}", ad);
        }
        // 2 saniyede bir dene: ekran kurulurken ilk tick'te düğmeler henüz yok olabiliyor.
        if (ekran == null || menuTick % 40 != 0 || !onayEkrani(ekran, ad)) return;
        for (GuiEventListener c : ekran.children()) {
            if (c instanceof Button d) {
                Candy.LOG.warn("[Candy][ototest] onay ekrani '{}' -> '{}' dugmesine basiliyor",
                    ad, d.getMessage().getString());
                // 1.21.9'da girdi olaylari kayit oldu: onPress artik tetikleyen olayi istiyor.
                //? if >=1.21.9 {
                /*d.onPress(new net.minecraft.client.input.KeyEvent(
                    org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
                */
                //?}
                //? if <1.21.9 {
                d.onPress();
                //?}
                return;
            }
        }
        Candy.LOG.warn("[Candy][ototest] onay ekrani '{}' ama basilacak dugme bulunamadi", ad);
    }

    /**
     * Odak geri gelince duraklatma menüsünü kapatır — oyuncunun oyuna dönüp ESC'ye basması.
     *
     * <p>Niye gerekli: oyun odağı kaybedince ({@code pauseOnLostFocus}, vanilla varsayılanı
     * açık) yarım saniye içinde duraklatma menüsünü açıyor; menü açıkken klavye ışığı kapısı
     * da kapalı. Gerçek oyuncu dönünce ESC'ye basar, klavyesiz koşumda o eli bu metot
     * taklit ediyor: {@link Screen#onClose()} ESC'nin çağırdığı yolun aynısı. 2 sn bekliyor
     * ki ölçer "oyun önde ama menü açık, ışık yok" aralığını da görebilsin. Zaman damgası
     * kapatmadan ÖNCE alınıyor: ondan sonraki her yanıp sönme menü kapandıktan sonradır.
     * Sıra bitince (tick 560) menüye hiç dokunmuyor.
     */
    private static void duraklatmaSur(Minecraft mc) {
        if (tick >= SON_TICK || !(mc.screen instanceof PauseScreen) || !mc.isWindowActive()) {
            duraklatmaTick = -1;
            return;
        }
        if (++duraklatmaTick < 40) return;
        duraklatmaTick = -1;
        String an = String.format("%1$tT.%1$tL", System.currentTimeMillis());
        mc.screen.onClose();
        Candy.LOG.warn("[Candy][ototest] ADIM odak-donusu {}: duraklatma menusu odakta 2 sn "
            + "acikti -> ESC (oyuncu oyuna dondu)", an);
    }

    /** Ana menü/dünya listesi değil, "devam et?" diye soran ekran mı? */
    private static boolean onayEkrani(Screen ekran, String ad) {
        // Yayın jar'ında sınıf adları karışık (class_405 vb.): ad eşleşmesi yalnız dev'de tutar,
        // o yüzden bilinen onay ekranlarını önce tipten tanı.
        if (ekran instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
            || ekran instanceof net.minecraft.client.gui.screens.ConfirmScreen
            || ekran instanceof net.minecraft.client.gui.screens.AlertScreen) return true;
        return ad.contains("Confirm")
            || ad.contains("Experimental")
            || ad.contains("Backup")
            || ad.contains("Warning")
            || ad.contains("Alert")
            || ad.contains("Notice");
    }

    private static boolean dolu(String s) {
        return s != null && !s.isBlank();
    }

    /** Komutu oyuncu sohbete yazmış gibi yollar (aynı paket, aynı Brigadier yolu). */
    private static void komut(Minecraft mc, String k) {
        try {
            if (mc.player == null || mc.player.connection == null) {
                Candy.LOG.warn("[Candy][ototest] baglanti yok, komut atlandi: /{}", k);
                return;
            }
            mc.player.connection.sendCommand(k);
            Candy.LOG.info("[Candy][ototest] komut -> /{}", k);
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy][ototest] komut '/{}' yollanamadi: {}", k, t.getMessage());
        }
    }
}
