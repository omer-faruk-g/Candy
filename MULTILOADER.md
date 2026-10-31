# Çok loader portu — mimari kararı

> Durum: **karar verildi, kod taşınmadı.** Bu dosya portun temelini belgeliyor.
> Candy şu an yalnızca Fabric'te çalışıyor (1.21.7 ve 1.21.11).

## Karar

Loader matrisi **Stonecutter'ın kendi branch mekanizmasıyla** kurulacak. Architectury
Loom'a, `common` / `fabric` / `neoforge` diye ayrı kaynak kümelerine ve proje
yapısının yeniden düzenlenmesine **gerek yok.**

Kaynak tarafındaki loader ayrımı **ince bir platform katmanıyla** yapılacak: 30 Fabric
API sınıfını saran bir avuç dosya. Loader direktifleri yalnızca o katmanda duracak.

## Neden — doğrulama yöntemi

Stonecutter'ın belgeleri bu soruyu yanıtlamıyor, hatta yanlış yöne gönderiyor:

- `stonecutter.kikugie.dev` AI tarayıcılarına **kasten saçma içerik** servis ediyor
  (arılar ve çiçekler hakkında anlamsız metin). Belge olarak kullanılamaz.
- `0.9` dalındaki README, Architectury desteğini **"Planned"** başlığı altında
  işaretlenmemiş bir madde olarak gösteriyor: *"Project tree management (for
  Architectury support)"*. Buna bakıp "branch yok" sonucuna varmak kolay — ve yanlış.

Bu yüzden karar belgeye değil, **sınıf dosyalarına** dayandırıldı. Gradle önbelleğindeki
`stonecutter-0.9.8.jar` açılıp `javap` ile okundu. Bulunan API:

| Sınıf / metot | Ne sağlıyor |
|---|---|
| `TreeBuilder.branch(String, Action<BranchBuilder>)` | Loader başına bir dal |
| `TreeBuilder extends BranchBuilder` | Her dalın kendi sürüm listesi var |
| `BranchBuilder.vers(ad, mcSürümü)` | Dal içinde MC düğümü |
| `BranchBuilder.branchScript` | **Dal başına ayrı build script** → loader başına ayrı Loom kurulumu |
| `TreeBuilder.mapBuilds` | Dal+düğüm → dizin eşlemesi |
| `TreeScheme$Branched`, `ProjectBranch`, `BranchInfo` | Dallı ağaç 0.9.8'de gerçekten var |
| `StonecutterBuildExtension.constants(...)` | Kaynakta `//? if fabric {` tipi koşullar |
| `.swaps(...)`, `.replacements(...)`, `.dependencies(...)` | Dal başına metin/bağımlılık ayrımı |

Yani ihtiyacımız olan her şey 0.9.8'de **sevk edilmiş durumda**, sadece belgelenmemiş.
Architectury'nin "planlanıyor" olması bizi engellemiyor, çünkü Architectury'ye
ihtiyacımız yok.

Bu, Architectury Loom yolunun elenmesinin de sebebi: Architectury Loom ile Stonecutter
ikisi de Loom'u sarıyor, uyumlu oldukları **varsayılamaz**, ve bu projede Loom
**1.17.2'ye sabitlenmiş** — loom 1.18+ JVM 25 istiyor, makinede Java 21 var. İki Loom
sarmalayıcısını uzlaştırmayı denemek, hiç gerekmeyen bir risk olurdu.

## Matris

```
branch("fabric")   → vers("1.21.7"), vers("1.21.11")
branch("neoforge") → vers("1.21.7"), vers("1.21.11")
branch("forge")    → vers("1.21.7"), vers("1.21.11")     # son sırada, bkz. "Sıra"
```

2 MC sürümü × 3 loader = 6 derleme hedefi. Her dalın `branchScript`'i kendi loader
eklentisini ve bağımlılıklarını kurar; kökteki `gradle.properties` sürümden bağımsız
anahtarları (`mod_version`, `maven_group`, `archives_base_name`) tutmaya devam eder.

## Kaynak tarafı — kritik nokta

Ölçüm (`src/main/java` içinde, bu kararın alındığı gün):

| | |
|---|---|
| Toplam Java dosyası | 206 |
| `net.fabricmc.fabric.api` import eden | **86** |
| Ayrı Fabric API sınıfı | **30** |
| `net.fabricmc.api` / `.loader` kullanan | 22 |
| Mixin sınıfı | 11 |
| Giriş noktası (`ModInitializer` / `ClientModInitializer`) | 4 |

En ağır bağımlılıklar:

```
65  ServerTickEvents            7  ServerPlayConnectionEvents
16  PayloadTypeRegistry         5  ServerLivingEntityEvents
13  ServerPlayNetworking        5  ClientPlayNetworking
                                4  ClientPlayConnectionEvents
```

sonra `AttackEntityCallback`, `ServerMessageEvents`, `PlayerBlockBreakEvents`,
`LootTableEvents`, `ServerPlayerEvents`, `EntitySleepEvents`, `ClientTickEvents`,
`FabricDefaultAttributeRegistry`, `UseItemCallback`, `UseEntityCallback`,
`UseBlockCallback`.

**86 dosyaya `//? if fabric` / `//? if neoforge` direktifi koymak yanlış çözüm.**
Gövdeler ikiye katlanır, her değişiklik iki yerde yapılmak zorunda kalır ve kod
okunamaz hale gelir.

Doğru çözüm, araya `com.photonpulse.candy.platform` paketi koymak:

- olay kaydı (tick, bağlantı, ölüm, vuruş, blok kırma, loot, uyku) için sarmalayıcı
- ağ (payload kaydı + sunucu/istemci gönderimi) için sarmalayıcı
- kayıt (entity, item, attribute) için sarmalayıcı

**Loader direktifleri yalnızca bu katmanın içinde yaşar.** 86 çağrı yerinin yaptığı tek
değişiklik import satırını değiştirmek — mekanik ve betikle yapılabilir. 65 adet
`ServerTickEvents` kullanımı tek bir sarmalayıcıya iner ve bir daha loader'ı görmez.

Bunun bedeli dürüstçe şu: sarmalayıcı, iki loader'ın olay modellerinin **kesişimini**
sunabilir. NeoForge'un olay otobüsü, ağ modeli (`RegisterPayloadHandlersEvent`) ve
kayıt modeli Fabric'ten yapısal olarak farklı; bire bir karşılığı olmayan her olay için
ya katmanda gerçek bir uyarlama yazılacak ya da o özelliğin o loader'da kapalı olduğu
yazılacak. Tahminle köprü kurulmayacak.

## Sıra

**NeoForge önce, Forge sonra.** 1.21+ modlu ekosistem fiilen NeoForge'ta. LexForge
1.21.7–1.21.11 için mevcut (Forge dosya sayfası 1.21.11 için 61.2.4'ü en son ve önerilen
derleme olarak listeliyor), ama ikinci sırada. Üç loader'ı birden açmaya çalışmak üç
yarım iş üretir.

NeoForge yeşil olduktan sonra, Forge'un gerçekten istenip istenmediği yeniden
sorgulanacak — çoğu 1.21+ oyuncusu için NeoForge yeterli olabilir.

## Doğrulanmamış — porta başlamadan önce bakılacak

- NeoForge 26.1+ **Java 25** istiyor. Hedefimiz 1.21.11 → NeoForge 21.11 hattı ve onun
  Java 21 ile çalıştığı varsayılıyor; **doğrulanmadı.** Java 21 sınırı bu projede
  Loom sabitlemesinin de sebebi, o yüzden bu soru kritik.
- NeoForge'un 1.21.7 için bir yapısı var mı — 1.21.11 için 21.11 hattı yayınlandı,
  1.21.7 ayrıca kontrol edilmeli.
- `branchScript` ile her dalın farklı bir loader eklentisi (Loom vs NeoGradle/ModDevGradle)
  kullanabildiği, Stonecutter'ın tek `pluginManagement` bloğu altında doğrulanmalı.
- 11 mixin: NeoForge kendi mixin önyüklemesini kullanıyor; `candy.mixins.json` yanına
  loader başına yapılandırma ve `neoforge.mods.toml` gerekecek.

## Bozulmayacaklar

- **Fabric her commit'te yeşil kalacak.** `./gradlew build` iki MC düğümünde de jar
  üretmeye devam etmeli. Depoyu derlenmez halde bırakan ara commit yok.
- Stonecutter'ın deneyle bulunmuş iki yapısal kuralı (ayrıntı `BUILD.md`'de):
  aktif düğüm `src/`'yi yerinde derler ve replacement'lar orada koşmaz, yani `src/`
  her zaman aktif sürümün adlarını taşır; düz metin replacement alıcı tipini bilmez,
  desenler alıcıya çapalanır.
- API iddiaları `javap` ile doğrulanır, tahmin edilmez. Bu projede 42 derleme hatası
  böyle kapatıldı.
- `//? if` direktiflerinin içine javadoc konmaz — javadoc `*/` içerir ve etkin olmayan
  dalın blok yorumunu erken kapatır.
- `MeYuz` 1.21.9+ üzerinde bilerek kapalı (doku atlası GPU yolu değişti, ayrıntı
  `BUILD.md`'de). Bu portun kapsamı dışında, canlandırılmaya çalışılmayacak.
- README'deki gizlilik sınırları gevşetilmeyecek: telemetri yok, beyaz liste beyaz
  liste kalır, `uygulamaSezgisi: false` süreç okumasını tamamen ve geri dönüşsüz
  kapatır. Platform katmanı yazılırken bu davranış korunacak.
- README gereksinimler tablosu gerçeği söyleyecek. Desteklenmeyen bir loader
  destekleniyormuş gibi yazılmayacak.
