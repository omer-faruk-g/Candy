# Candy v10.1.0 — Canlı Kanıt Matrisi (12 Kasım 2026)

> Canlı turlar v10.1.0 koduyla yapıldı (sürüm numarası değişmeden önce jar adı 10.0.1'di; kod aynı). Dört düğüm v10.1.0 jar adıyla yeniden koşturuldu.

**Yöntem:** Oyun gerçekten açıldı (Minecraft 1.21.7 Fabric, prod jar). İstemci tarafındaki `CandyOtoTest` betiği komut verdi, tıkladı, slot seçti, ekran görüntüsü aldı. Kanıt = oyun logundaki satır + ekran görüntüsü. Ek olarak 4 sürüm düğümü (1.21.7 / 1.21.11 × Fabric / NeoForge) güncel kodla koşturuldu.

**Sınır:** Bu bir *gözlem* kanıtıdır, matematiksel garanti değildir. Kanıtlanan, "bu mekanik bu koşullarda en az bir kez beklendiği gibi çalıştı" demektir. Rastgele/uzun vadeli dallar ve hata yolları aşağıda tek tek açıklanmıştır.

**Ekran görüntüleri:** `…/5b0e50cb-…/scratchpad/prod-canli/screenshots/ototest-c3-*.png … c7-*.png` (30+ adet)

## 1. Kilit mekanikler (elle doğrulandı)

| Mekanik | Durum | Kanıt (log satırı) | Ekran |
|---|---|---|---|
| Kuran sağ-tık (Ayetel Kürsi) + sol-tık çile | ✅ GEÇTİ | `Kuran sol-tık[Dev] → çile başladı` ×2, envanter: Kuran tüketildi (`elde: Air`) | c3-kuran-oku-0/1, c3-kuran-cile-0/1 |
| İmanın Gücü (çile sonu ödül) | ✅ GEÇTİ | `İmanın Gücü verildi: Dev` ×2, sohbet `✦ İmanın Gücü ✦` | c3-kuran-cile-* |
| Hayalet köy hijack (düzeltme #1) | ✅ GEÇTİ | `Hayalet[Alex] köy hijack @ bell=(1005,201,990) kapılar=(1002..),(1007..)` | c3-sprint-* |
| Hayalet köylü öldürme | ✅ GEÇTİ | `Hayalet[Steve] köylü ÖLDÜRDÜ @ (1008,201,1012) (fake zombie)` ×2, `köylü hasar` ×3 | c4-sprint-3/6 |
| Hayalet koordinat bırakma | ✅ GEÇTİ | `Hayalet[Alex] koordinat bıraktı @ (1121,80,1167)`, sohbet `<Steve> 817 201 791 our base is here` | — |
| Hayalet tabela gaslighting (düzeltme #2) | ✅ GEÇTİ | 2. koşu logu | — |
| Hayalet diğer aksiyonlar | ✅ GEÇTİ | `donanim`, `saat`, `sehir`, `mob`, `Hayalet ölüm`, joined ×11 | — |
| Mirror girişi (klon öldürür → Mirror) | ✅ GEÇTİ | `Overworld candy-kill → Mirror bekletiyor`, `Mirror teleport` | — |
| Mirror ölüm → Anti-Mirror | ✅ GEÇTİ | `Mirror ölüm → Anti-Mirror bekletiyor`, `Anti-Mirror teleport … + platform` | — |
| Anti-Mirror piyango (50 → ölüm + kick) | ✅ GEÇTİ | lottery=50 → ölüm → kick (2. koşu) | — |
| Anti-Mirror → gerçek dünya dönüşü | ✅ GEÇTİ | `Anti-Mirror → gerçek dünya dönüş: Dev @(1021, 201, 1020)` | — |
| Ayna geri dönüşü (**hata bulundu + düzeltildi**) | ✅ GEÇTİ | Önce `AYNA GERİ → overworld @(1020, -64, 1020)` (oyuncu dünyanın dibine ışınlanıyordu). Düzeltmeden sonra `@(1020, 201, 1020)` ×9 | c3-sprint-0 |
| Gerçek Candy atması (korku ≥ 92) | ✅ GEÇTİ | `Gerçek Candy göründü: Dev atılıyor` → bağlantı kesildi | — |
| Totem iptali + kick | ✅ GEÇTİ | `The totem is useless. I'm here.` → `TotemIptali: Dev — 0 totem silindi, 3sn'de kick` → kick `The lie of immortality. Come back.` | c4-totem-1/2 |
| Totem silme döngüsü (ek totemler) | ✅ GEÇTİ | `TotemIptali: Dev — 2 totem silindi` ve ardından sayım `No items were found on player Dev` | c5-totem2 |
| Totem sonrası yeniden giriş | ⚠️ DOLAYLI | Mesaj `You won't get another chance.` görüldü. Aynı kod bloğundaki can=1 / açlık=0 ayarı canlı ölçülemedi (ölçüm 30 sn sonra alındı, can 20 idi). | — |
| İkilik fısıltı — 4 kanal | ✅ GEÇTİ | `ikilik fısıltı [CHAT]`, `[ALTYAZI]`, `[CAMSCREEN]`, `[BASLIK]` (I SEE YOU, YOU AGAIN, DONT LOOK, NOT ALONE, STILL HERE, I KNOW) | c6-ikilik-0..9 |
| Pencere başlığı değiştirme | ✅ GEÇTİ | `Pencere başlığı değişti (idx 7)` | (başlık çubuğu ekran görüntüsünde çıkmaz) |
| Kalp hızı / Kalp krizi / Teşhis | ✅ GEÇTİ | `Kalp krizi → Dev` (çok kez), KalpHizi + teşhis raporu (2. koşu) | — |
| YatakBekçisi, SpectateSwap, PortalKarışıklığı | ✅ GEÇTİ | 2. koşu logu | — |
| BaseSabotaj, PetÖlümü, Yas (+ ikinci ölüm), TreeGrowth | ✅ GEÇTİ | 2. koşu logu (`blok sessizce kayboldu`, `Yas başladı`, `Travma sonrası büyüme`) | — |
| ShadowState, ELITE klon, Silahlanma emekliliği | ✅ GEÇTİ | `ShadowState patladı`, ELITE spawn, `Silahlanma 'YAKIN_KLON' emekliye ayrıldı` | — |
| Şifre #0-2, tuzak, dil modeli, modsuz oynama tespiti | ✅ GEÇTİ | 2. koşu logu | — |
| Beyin / ZihinselProva / Kurul | ✅ GEÇTİ | `ZihinselProva … eylem=…` (10 eylem türünün hepsi seçildi: YAKIN_KLON 111, RUTIN 87, KARANLIK 87, KLON_ARKA 60, IKILIK 58, SORU 57, SES 47, SESSIZLIK 46, ALTYAZI 44, BASLIK 39), `kurul: 11/11 üye` | — |
| Meşale kırıcı (mağara) | ✅ GEÇTİ | Mağara turu (y=41, üstü kapalı, meşaleli): `MesaleKirici aktif — mağara meşale patlaması` → `MesaleKirici: Dev çevresinde 12 meşale patladı` | c7-* |

## 2. Sürüm düğümleri (v10.1.0, 12 Kas 21:49–21:54)

| Düğüm | Sonuç | `[Candy]` satırı | ERROR/Exception — nedeni |
|---|---|---|---|
| 1.21.7 Fabric | ✅ BİTTİ (93 sn) | 187 | 0 |
| 1.21.7 NeoForge | ✅ BİTTİ (84 sn) | 175 | 1 — `mektup yollanamadı: ConnectException` (test ortamında posta sunucusu yok) |
| 1.21.11 Fabric | ✅ BİTTİ (84 sn) | 191 | 10 — hepsi Mojang oturum sunucusu 401 / kullanıcı özellikleri (çevrimdışı test hesabı) |
| 1.21.11 NeoForge | ✅ BİTTİ (80 sn) | 184 | 3 — eski dünyadan `minecraft:chain` tarifi, mektup ConnectException |

**Moddan kaynaklanan hata: 0.**

## 3. Kanıtlanamayan / görülmeyen kalemler — tek tek neden

| Kalem | Neden |
|---|---|
| Arkadaş fısıltısı | En az **2 oyuncu** ister (`oyuncular.size() < 2 → return`). Tek oyunculu testte tetiklenemez. |
| Beyin göçü (`BeyinGocu`), beyin "topolojisi değişti" | Yalnız **eski sürümden kaydedilmiş beyin** yüklenirken çalışır (sürüm yükseltme yolu). |
| Beyin lobu körelmesi (`Enerji`) | Çok uzun süre (binlerce süpürme) kullanılmayan lob ister; test süresine sığmaz. |
| ME yüz boyama (`MeYuz`) | Ortama bağlı; 1.21.9+ sürümlerinde bilinçli olarak kapalı. |
| Klavye ışığı (kapalı dalları), KonfigMüdahale "geri açıldı", Vagus | Ortama/ayara bağlı: Windows + donanım, kullanıcı ayarı, kurulu Vagus modeli. |
| Eski boss silindi | Yeni boss fazı başlarken eski boss'un hâlâ yaşıyor olmasını ister; bu sıra testte oluşmadı. |
| Pencere başlığı "geri alındı" | Dünyadan çıkışta çalışır; test `#bitti` ile JVM'i doğrudan kapattığı için görülmedi. |
| Klon şablonu sıfırlama (`DunyaKlon.sifirla`) | **Hiçbir yerden çağrılmıyor** (ölü kod), logu da debug seviyesinde. |
| `… okunamadı / yazılamadı / hata: {}` satırları (~60 adet) | Hata/yedek yolları. Görülmemeleri = **o hata hiç oluşmadı**. |
| Logsuz 82 sınıf | Kendi log satırı yok (veri sınıfları, paketler, mixin'ler); dolaylı olarak üstteki mekaniklerin içinde çalıştılar. |

## 4. Bu çalışmada yapılan kod değişiklikleri (commit EDİLMEDİ)

- **Hata düzeltmesi:** Yüklü olmayan chunk'ta `getHeight` en alt Y'yi döndürüyor, oyuncu y=-64'e ışınlanıyordu. Dönüşten önce chunk zorla yükleniyor: `AynaSistemi`, `MirrorGirisi`, `BossManager`, `DunyaGecisi`. Canlı doğrulandı.
- **Hayalet düzeltmeleri #1 (köy hijack) ve #2 (tabela).** Canlı doğrulandı.
- `PencereBasligi` ve `IkilikFisilti` log satırları `debug` → `info` (kanıtlanabilsin diye; davranış değişmedi).
- Test altyapısı: `CandyOtoTest` adımları, `CandyDevKomut`.

## 5. Otomatik kapsam tablosu (tüm loglar, 348 / 509 log satırı görüldü)

Özet: GEÇTİ 94, KISMİ 25, GÖRÜLMEDİ 5, LOGSUZ 82, SADECE-DEBUG 12

| Sınıf | Durum | Görülen log satırı | Görülmeyen satırlar |
|---|---|---|---|
| `Candy` | GEÇTİ | 7/7 |  |
| `altyazi/Altyazi` | GEÇTİ | 1/1 |  |
| `arkadas/ArkadasEntity` | GEÇTİ | 1/1 |  |
| `arkadas/ArkadasFisilti` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `arkadas/ArkadasKaydi` | KISMİ | 1/3 | [Candy] Arkadaş kaydı okunamadı, ölü sayılıyor: {}; [Candy] Arkadaş ölümü yazılamadı: {} |
| `arkadas/ArkadasYonetici` | GEÇTİ | 2/2 |  |
| `arkadas/TanidikFisilti` | GEÇTİ | 1/1 |  |
| `audio/BhootAmbient` | GEÇTİ | 1/1 |  |
| `audio/CandyKahkaha` | GEÇTİ | 2/2 |  |
| `audio/CandySounds` | GEÇTİ | 1/1 |  |
| `audio/FisiltiSistemi` | GEÇTİ | 1/1 |  |
| `audio/SesGonder` | LOGSUZ | - |  |
| `audio/SesTaklit` | GEÇTİ | 2/2 |  |
| `basarim/CandyBasarimlar` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `beyin/Aort` | LOGSUZ | - |  |
| `beyin/Beyin` | LOGSUZ | - |  |
| `beyin/BeyinCikti` | LOGSUZ | - |  |
| `beyin/BeyinDeposu` | KISMİ | 1/5 | [Candy] üye {} topolojisi degisti — kunye goc ediyor: {}; [Candy] beyin yapısı tutarsız, yeniden inşa: {}; [Candy] beyin yüklenemedi ({}), yeniden inşa edilecek: {}; [Candy] beyin yazıcısı zamanında bitmedi |
| `beyin/BeyinGirdi` | LOGSUZ | - |  |
| `beyin/BeyinGocu` | GÖRÜLMEDİ | 0/2 | [Candy] beyin göç etti: {} dünya, {} tahmin, {} Q güncelleme; [Candy] beyin göçü kısmen başarısız ({}), taze beyinle devam |
| `beyin/BeyinInsaci` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `beyin/BeyinMotoru` | KISMİ | 9/11 | [Candy] üye {}: NaN sizintisi yakalandi ve temizlendi; [Candy] UYARI: ham lob std {} / zaman içi std {} — beyin ses |
| `beyin/BeyinOlcer` | GEÇTİ | 1/1 |  |
| `beyin/Demet` | LOGSUZ | - |  |
| `beyin/Dogrulama` | LOGSUZ | - |  |
| `beyin/DunyaDogrulayici` | KISMİ | 2/3 | [Candy] DOĞRULAMA: model DÜNYAYA BAĞIMLIYMIŞ (brier {} vs te |
| `beyin/Enerji` | GÖRÜLMEDİ | 0/1 | [Candy] beyin: {} lobu KÖRELDİ (önem {}, {} süpürme aç) |
| `beyin/GolgeArsivi` | LOGSUZ | - |  |
| `beyin/Hakem` | LOGSUZ | - |  |
| `beyin/IcDuyu` | LOGSUZ | - |  |
| `beyin/Iddia` | LOGSUZ | - |  |
| `beyin/IkiliKodlayici` | LOGSUZ | - |  |
| `beyin/Karar` | LOGSUZ | - |  |
| `beyin/KararIzi` | LOGSUZ | - |  |
| `beyin/Kurul` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `beyin/Lob` | LOGSUZ | - |  |
| `beyin/LobDurum` | LOGSUZ | - |  |
| `beyin/OyuncuHaritasi` | LOGSUZ | - |  |
| `beyin/OzModel` | LOGSUZ | - |  |
| `beyin/Perde` | LOGSUZ | - |  |
| `beyin/Plastisite` | LOGSUZ | - |  |
| `beyin/UyeSicili` | LOGSUZ | - |  |
| `beyin/ZamanIzi` | LOGSUZ | - |  |
| `beyin/vagus/Vagus` | KISMİ | 4/6 | [Candy] Vagus ayarla kapatılmış — gözlemci yok; [Candy] Vagus yaklastı — kalibrasyon {} (n={}) |
| `beyin/vagus/VagusAyar` | KISMİ | 1/3 | [Candy] Vagus ayarı okunamadı ({}) — varsayılan kullanılıyor; [Candy] Vagus adresi yerel değil ({}) — {} kullanılıyor |
| `beyin/vagus/VagusIstemci` | GÖRÜLMEDİ | 0/1 | [Candy] Vagus modeli bulunamadı: {} — Vagus kapalı |
| `beyin/vagus/VagusOzet` | LOGSUZ | - |  |
| `beyin/vagus/VagusSezgi` | LOGSUZ | - |  |
| `boss/BossCountdown` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `boss/BossEventler` | GEÇTİ | 4/4 |  |
| `boss/BossManager` | GEÇTİ | 4/4 |  |
| `boss/GizemliKitap` | LOGSUZ | - |  |
| `boss/GizemliKitapRecipe` | GEÇTİ | 1/1 |  |
| `boss/TrueRealCandyEntity` | KISMİ | 2/3 | [Candy] eski boss silindi (faz {}) |
| `chat/CandyChatOkur` | GEÇTİ | 2/2 |  |
| `chat/FakeMesajlar` | GEÇTİ | 3/3 |  |
| `client/AltyaziState` | LOGSUZ | - |  |
| `client/ArkadasRenderer` | LOGSUZ | - |  |
| `client/BossCountdownState` | LOGSUZ | - |  |
| `client/CamscreenState` | LOGSUZ | - |  |
| `client/CandyClient` | GEÇTİ | 2/2 |  |
| `client/CandyClientNet` | KISMİ | 1/2 | [Candy] Dış kanal paketi reddedildi (anahtar: {}) |
| `client/CandyDoppelRenderer` | LOGSUZ | - |  |
| `client/CandyMixinConfig` | KISMİ | 1/4 | [Candy] Config yüklenemedi, defaults: {}; [Candy] config '{}' {} karakterden uzun, yok sayıldı; [Candy] Config yazılamadı: {} |
| `client/CandyOtoTest` | KISMİ | 33/43 | [Candy][ototest] ek sira okunamadi: {}; [Candy][ototest] ekran yazilamadi: {}; [Candy][ototest] ekran alinamadi: {}; [Candy][ototest] tus -> {} {}; [Candy][ototest] sifrecoz: satir yok (dosya {}, {} satir); [Candy][ototest] sifrecoz hata: { |
| `client/DonanimGonder` | GEÇTİ | 1/1 |  |
| `client/EkranGonder` | KISMİ | 2/3 | [Candy] ekranHaritasiTus=\"{}\" tanınmadı — F12'ye düşüldü |
| `client/GoruntuMudahale` | KISMİ | 1/6 | [Candy] görüntü yedeği bulundu — geçen oturum temiz kapanmam; [Candy] görüntü yedeği yazılamadı, müdahale iptal: {}; [Candy] görüntü ayarlanamadı: {}; [Candy] görüntü yedeği okunamadı: {}; [Candy] görüntü geri alınamadı: {} |
| `client/HastalikState` | LOGSUZ | - |  |
| `client/IkilikEkranState` | LOGSUZ | - |  |
| `client/IntensityState` | LOGSUZ | - |  |
| `client/KacisGonder` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `client/KaranlikState` | LOGSUZ | - |  |
| `client/KlavyeIsigi` | KISMİ | 4/10 | [Candy] Klavye ışığı kapalı (klavyeIsigi: false); [Candy] Klavye ışığı atlandı — yalnızca Windows ({}); [Candy] Klavye ışığı yarıda kalan darbeden geri alındı; [Candy] Klavye ışığı atlandı — tuşa basılamıyor: {}; [Candy] Ön plan denetimi ya |
| `client/KonfigMudahale` | KISMİ | 1/2 | [Candy] KonfigMudahale: '{}' geri açıldı |
| `client/MeYuz` | GÖRÜLMEDİ | 0/2 | [Candy] ME yuz boyama 1.21.9+ surumlerinde devre disi (atlas; [Candy] ME: bloklar oyuncunun yüzüne boyandı |
| `client/MeYuzHesap` | LOGSUZ | - |  |
| `client/MektupGonder` | GEÇTİ | 2/2 |  |
| `client/PencereBasligi` | KISMİ | 1/2 | [Candy] Pencere başlığı geri alındı |
| `client/SaatGonder` | GEÇTİ | 1/1 |  |
| `client/SehirGonder` | GEÇTİ | 1/1 |  |
| `client/TrueRealCandyRenderer` | LOGSUZ | - |  |
| `client/UygulamaGonder` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `clone/CloneManager` | GEÇTİ | 5/5 |  |
| `core/DerinAnaliz` | GEÇTİ | 2/2 |  |
| `core/FearDetection` | GEÇTİ | 1/1 |  |
| `core/IcDusunce` | GEÇTİ | 4/4 |  |
| `core/JsonDepo` | GÖRÜLMEDİ | 0/1 | [Candy] JSON yazıcısı zamanında bitmedi |
| `core/Kehanet` | GEÇTİ | 2/2 |  |
| `core/ObservationEngine` | GEÇTİ | 1/1 |  |
| `core/PersonalityManager` | GEÇTİ | 3/3 |  |
| `core/Psikoloji` | LOGSUZ | - |  |
| `core/TruePersonality` | LOGSUZ | - |  |
| `core/ZihinselProva` | GEÇTİ | 3/3 |  |
| `dev/CandyDevKomut` | GEÇTİ | 5/5 |  |
| `dialog/DialogReaksiyon` | LOGSUZ | - |  |
| `dimension/AnomalyPatches` | GEÇTİ | 2/2 |  |
| `dimension/AntiMirrorLottery` | GEÇTİ | 3/3 |  |
| `dimension/AynaSistemi` | GEÇTİ | 4/4 |  |
| `dimension/CandyBloklar` | GEÇTİ | 1/1 |  |
| `dimension/CandyDimensions` | LOGSUZ | - |  |
| `dimension/DunyaGecisi` | KISMİ | 2/3 | [Candy] kurul dünyası yüklenmemiş: {} |
| `dimension/DunyaKlon` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `dimension/DunyaKurulu` | GEÇTİ | 3/3 |  |
| `dimension/DunyaSekillendirici` | GEÇTİ | 1/1 |  |
| `dimension/GlitchFlicker` | GEÇTİ | 2/2 |  |
| `dimension/KurulDunya` | LOGSUZ | - |  |
| `dimension/MirrorAtmosphere` | GEÇTİ | 1/1 |  |
| `dimension/MirrorGirisi` | GEÇTİ | 7/7 |  |
| `disguise/DisguiseSystem` | GEÇTİ | 2/2 |  |
| `ending/EndingWatcher` | GEÇTİ | 2/2 |  |
| `entity/CandyDoppelEntity` | LOGSUZ | - |  |
| `entity/CandyEntities` | GEÇTİ | 1/1 |  |
| `entity/SpectateSwap` | GEÇTİ | 2/2 |  |
| `envanter/EnvanterKaristirici` | GEÇTİ | 2/2 |  |
| `environment/KarartmaSistemi` | GEÇTİ | 1/1 |  |
| `environment/MesaleKirici` | GEÇTİ | 2/2 |  |
| `evler/Dikit` | GEÇTİ | 2/2 |  |
| `evler/Kan` | GEÇTİ | 2/2 |  |
| `evler/KanEvi` | GEÇTİ | 2/2 |  |
| `grief/GriefSystem` | GEÇTİ | 3/3 |  |
| `hastalik/HastalikYoneticisi` | KISMİ | 5/6 | [Candy] Kalp krizi hata, fallback health=0: {} |
| `hastalik/PsikolojikHastalik` | LOGSUZ | - |  |
| `hayalet/HayaletOyuncular` | GEÇTİ | 25/25 |  |
| `item/CandyEfektler` | GEÇTİ | 1/1 |  |
| `item/KuranKitap` | LOGSUZ | - |  |
| `item/KuranRecipe` | GEÇTİ | 1/1 |  |
| `item/KuranSistemi` | GEÇTİ | 3/3 |  |
| `kacis/KacisPayload` | LOGSUZ | - |  |
| `kacis/KacisSensoru` | GEÇTİ | 2/2 |  |
| `kalp/KalpHizi` | GEÇTİ | 2/2 |  |
| `lore/Denek239Book` | LOGSUZ | - |  |
| `lore/JournalLootInjector` | GEÇTİ | 2/2 |  |
| `lore/JournalTexts` | LOGSUZ | - |  |
| `lore/TeshisRaporu` | GEÇTİ | 2/2 |  |
| `lore/YirtikSayfa` | GEÇTİ | 2/2 |  |
| `meta/ArkaBakis` | GEÇTİ | 1/1 |  |
| `meta/DonanimBilgisi` | GEÇTİ | 2/2 |  |
| `meta/GoruntuTetik` | GEÇTİ | 2/2 |  |
| `meta/KonumKaydirma` | GEÇTİ | 2/2 |  |
| `meta/ModFarkindaligi` | GEÇTİ | 2/2 |  |
| `meta/OrumcekYonlendirme` | GEÇTİ | 2/2 |  |
| `meta/SaatBilgisi` | GEÇTİ | 2/2 |  |
| `meta/SehirBilgisi` | GEÇTİ | 2/2 |  |
| `meta/UygulamaBilgisi` | GEÇTİ | 2/2 |  |
| `meta/UygulamaListesi` | LOGSUZ | - |  |
| `mixin/CommandManagerMixin` | GEÇTİ | 3/3 |  |
| `mixin/LivingEntityMixin` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `mixin/WorldSetBlockMixin` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `mixin/client/CameraMixin` | LOGSUZ | - |  |
| `mixin/client/ChatHudMixin` | LOGSUZ | - |  |
| `mixin/client/DeathScreenMixin` | LOGSUZ | - |  |
| `mixin/client/DebugHudMixin` | LOGSUZ | - |  |
| `mixin/client/FogRendererMixin` | LOGSUZ | - |  |
| `mixin/client/InGameHudMixin` | LOGSUZ | - |  |
| `mixin/client/LightmapTextureManagerMixin` | LOGSUZ | - |  |
| `mixin/client/SpriteContentsAccessor` | LOGSUZ | - |  |
| `mob/MobKoordinasyon` | GEÇTİ | 1/1 |  |
| `net/AltyaziPayload` | LOGSUZ | - |  |
| `net/BaslikPayload` | LOGSUZ | - |  |
| `net/BildirimPayload` | LOGSUZ | - |  |
| `net/BlackoutGlitch` | GEÇTİ | 2/2 |  |
| `net/BossCountdownPayload` | LOGSUZ | - |  |
| `net/CamscreenPayload` | LOGSUZ | - |  |
| `net/CamscreenTetikleyici` | GEÇTİ | 2/2 |  |
| `net/CandyIntensityPayload` | LOGSUZ | - |  |
| `net/DonanimPayload` | LOGSUZ | - |  |
| `net/EkranPayload` | LOGSUZ | - |  |
| `net/GoruntuPayload` | LOGSUZ | - |  |
| `net/HastalikPayload` | LOGSUZ | - |  |
| `net/IkilikEkranPayload` | LOGSUZ | - |  |
| `net/IntensityBroadcaster` | GEÇTİ | 1/1 |  |
| `net/KaranlikPayload` | LOGSUZ | - |  |
| `net/SaatPayload` | LOGSUZ | - |  |
| `net/SehirPayload` | LOGSUZ | - |  |
| `net/UygulamaPayload` | LOGSUZ | - |  |
| `psikoloji/Ozguven` | LOGSUZ | - |  |
| `psikoloji/PsikolojiTakip` | GEÇTİ | 1/1 |  |
| `real/RealCandy` | GEÇTİ | 2/2 |  |
| `rutin/RutinKaydi` | LOGSUZ | - |  |
| `rutin/RutinTekrari` | GEÇTİ | 1/1 |  |
| `ruya/RuyaSistemi` | GEÇTİ | 2/2 |  |
| `sanity/SanityController` | GEÇTİ | 1/1 |  |
| `sessizlik/SessizlikState` | LOGSUZ | - |  |
| `sessizlik/SessizlikYoneticisi` | GEÇTİ | 3/3 |  |
| `shadow/ShadowState` | GEÇTİ | 1/1 |  |
| `sifre/CihazKilidi` | KISMİ | 1/4 | [Candy] cihaz anahtarı bozuk ({} bayt), yenisi üretiliyor; [Candy] cihaz anahtarı okunamadı: {}; [Candy] cihaz anahtarı yazılamadı, oturumluk kullanılıyor: { |
| `sifre/IkilikFisilti` | GEÇTİ | 1/1 |  |
| `sifre/IkilikMetin` | LOGSUZ | - |  |
| `sifre/Karanlik` | GEÇTİ | 1/1 |  |
| `sifre/SifreAyar` | KISMİ | 1/2 | [Candy] Şifre ayarı okunamadı ({}) — varsayılan kullanılıyor |
| `sifre/SifreDagitici` | KISMİ | 1/5 | [Candy] harita şifresi bırakılamadı: {}; [Candy] masaüstü şifresi bırakılamadı: {}; [Candy] ayardaki masaüstü yolu klasör değil: {}; [Candy] şifre kitabı verilemedi: {} |
| `sifre/SifreIzleyici` | GEÇTİ | 1/1 |  |
| `sifre/SifreMotoru` | GEÇTİ | 1/1 |  |
| `sifre/TuzakYayini` | GEÇTİ | 3/3 |  |
| `sign/SignSystem` | KISMİ | 11/13 | [Candy] tabela sicili okunamadı: {}; [Candy] tabela sicili yazılamadı: {} |
| `sign/SignTemplates` | LOGSUZ | - |  |
| `simulation/Bastirma` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `simulation/Discontent` | GEÇTİ | 1/1 |  |
| `simulation/ShadowTree` | LOGSUZ | - |  |
| `simulation/ShadowTreeManager` | GEÇTİ | 2/2 |  |
| `simulation/SimulatedPersonality` | LOGSUZ | - |  |
| `simulation/SimulationEngine` | GEÇTİ | 1/1 |  |
| `simulation/SimulationManager` | SADECE-DEBUG | - | log yalniz debug seviyesinde (oyun loguna basilmaz) |
| `tempo/MobSpawnBooster` | GEÇTİ | 1/1 |  |
| `tempo/TickAccelerator` | GEÇTİ | 2/2 |  |
| `travma/BaseSabotaji` | GEÇTİ | 6/6 |  |
| `travma/OlumYerleri` | GEÇTİ | 5/5 |  |
| `travma/PetOlumu` | GEÇTİ | 5/5 |  |
| `travma/TotemIptali` | KISMİ | 2/3 | [Candy] TotemIptali hata: {} |
| `tree/PersonalityTree` | LOGSUZ | - |  |
| `tree/TreeBranch` | LOGSUZ | - |  |
| `tree/TreeGrowth` | GEÇTİ | 4/4 |  |
| `tree/TreeNode` | LOGSUZ | - |  |
| `uyum/SurumKapisi` | KISMİ | 3/4 | [Candy] SurumKapisi: {} test edilmedi ama {} ailesine yakın. |
| `yuz/EkranHaritasi` | KISMİ | 2/3 | [Candy] EkranHaritasi harita ver hata: {} |
| `yuz/HaritaPaleti` | LOGSUZ | - |  |
| `yuz/YuzHaritasi` | KISMİ | 2/3 | [Candy] YuzHaritasi harita ver hata: {} |
