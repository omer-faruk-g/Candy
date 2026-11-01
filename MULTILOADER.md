# Çok loader portu — mimari kararı ve ÖLÇÜLMÜŞ ENGEL

> Durum: **port denendi, iki yolun ikisi de engele çarptı. Kod taşınmadı.**
> Candy hâlâ yalnızca Fabric'te çalışıyor (1.21.7 ve 1.21.11).
>
> Bu dosya önce bir *karar* belgesiydi. Karar denendi; denemenin sonucu kararı
> değiştirdi. Aşağıdaki her iddia `javap`, maven metadata veya gerçek bir Gradle
> koşusuyla doğrulandı — hiçbiri belgeden alınmadı.

## Özet: port şu anda neden yapılamıyor

İki teknik yol var. İkisi de ölçüldü, ikisi de kapalı:

| Yol | Engel | Durum |
|---|---|---|
| **ModDevGradle / NeoGradle** | Mapping duvarı: 143 dosyada 156 Yarn sınıf adı, NeoForge Mojang adlarına derlenmiş | Kaynak göçü işi, build işi değil |
| **Architectury Loom** | Upstream remap çakışması: `InGameHud.renderHotbar` — Minecraft remap'i hiç tamamlanmıyor | **1.21.7 ve 1.21.11'de aynı şekilde patlıyor** |

Üçüncü bir yol (Stonecutter dalları) çalışıyor ama portun amacını bozuyor: dal başına
ayrı kaynak ağacı üretiyor, yani 206 dosya loader başına kopya.

## MULTILOADER.md'nin 4 açık sorusu — kapatıldı

### 1. NeoForge Java 21 ile çalışıyor mu? → **EVET**

Bytecode'dan doğrulandı (`major 65` = Java 21):

| Jar | Sınıf | Bytecode |
|---|---|---|
| `neoforge-21.11.48-universal.jar` | 1679 | **hepsi major 65** |
| `neoforge-21.7.25-beta-universal.jar` | 1557 | **hepsi major 65** |

Java 25 yalnızca **26.x** hattında (maven `<latest>` = `26.3.0.100`), o hat zaten
Yarn yüzünden kapsam dışı. Yani Java 21 sınırı portu engellemiyor — bu soru
kritikti ve cevabı olumlu.

### 2. İki MC sürümü için NeoForge yapısı var mı? → **EVET**

`maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`:

- **1.21.7** → `21.7.25-beta` (21.7 hattının *tamamı* beta)
- **1.21.11** → `21.11.48` (**stabil**, beta değil)

### 3. `branchScript` ile dal başına ayrı loader eklentisi → **ÇALIŞIYOR, AMA PLANI BOZUYOR**

Gerçek Gradle koşusuyla doğrulandı. Mekanizma var ve işliyor:

- Proje düzeni: **`<dal>/versions/<mc>/`** (ör. `fabric/versions/1.21.7`) —
  `versions/<dal>/<mc>` *değil*
- Düğümün build dosyası = **`<dal>/build.gradle`** → dal başına ayrı loader
  eklentisi gerçekten mümkün
- Bağlanma yolu: `ProjectDescriptor.setBuildFileName`
- `branchScript` dalın **kendi** build dosyasını belirliyor (varsayılan
  `<dal>/branch.gradle`), düğümlerin değil; düğümler `centralScript`'i dal
  dizinine göre çözüyor

**Ama:** her dalın **kendi `src/` ağacı** var.

```
aktif düğüm  → srcDirs = [node/src/main/java, fabric/src/main/java]      # yerinde
pasif düğüm  → srcDirs = [node/src/main/java, node/build/generated/...]  # üretilmiş
```

Dallar arasında paylaşılan tek bir kök `src/` **yok**, ve onu başka yere yönlendiren
bir API de yok: `StonecutterBuildExtension`'da kaynak kökü ayarı bulunmuyor,
`mapBuilds` yalnızca build script adını eşliyor. Yani dal mekanizması tam olarak bu
belgenin kaçınmak için kurulduğu **gövde ikileşmesini** üretiyor — 206 dosya loader
başına kopya, her değişiklik iki yerde.

Bu yüzden dallar kullanılmadı.

### 4. Mixin / loader metadata → **NeoForge kendi yapılandırmasını okuyor**

NeoForge'un kendi jar'ından doğrulandı: mixin'ler `neoforge.mods.toml` içinde
`[[mixins]] config = "..."` ile bildiriliyor; `modLoader="javafml"`,
`loaderVersion="[3,]"`. Yani `candy.mixins.json` yeniden kullanılabilir, yanına bir
`neoforge.mods.toml` gerekir. Bu soru port açıldığında kolay kısım.

## Bu belgenin KAÇIRDIĞI engel: mapping duvarı

Önceki sürüm kaynak tarafındaki işi "30 Fabric API sınıfını saran ince bir platform
katmanı" diye ölçtü. O ölçüm doğru ama **eksik**: Fabric API'nin yanında
**Minecraft'ın kendi adları** da loader'a göre değişiyor.

Candy'nin kaynağı **Yarn** adlarını taşıyor:

| | |
|---|---|
| `net.minecraft` import eden dosya | **143** / 206 |
| Ayrı Yarn sınıf adı | **156** |

NeoForge ise **Mojang resmî adlarına** karşı derlenmiş. `javap`,
`neoforge-21.11.48`:

```
LivingDeathEvent(net.minecraft.world.entity.LivingEntity,
                 net.minecraft.world.damagesource.DamageSource)
```

Candy'deki karşılıkları `net.minecraft.entity.LivingEntity` ve
`net.minecraft.entity.damage.DamageSource`. Yani ModDevGradle/NeoGradle yolu,
**143 dosyadaki her MC sembolünün yeniden adlandırılmasını** gerektiriyor — bu,
bu belgenin 26.x için "build işi değil, kaynak göçü işi" diyerek kapsam dışı
bıraktığı işin birebir aynısı.

**MDG yolu bu yüzden elendi** — tahminle değil, ölçümle.

## Architectury Loom: doğru yol, ama upstream'de kırık

Mapping duvarını aşmanın tek yolu, Yarn adlarını derlemede koruyup jar aşamasında
SRG'ye remap eden bir araç. Architectury Loom tam bunu yapıyor.

### Bu belgenin eleme gerekçesi iki noktada yanlıştı

1. *"Architectury Loom ile Stonecutter ikisi de Loom'u sarıyor, uyumlu oldukları
   varsayılamaz"* — **yanlış.** `stonecutter-0.9.8.jar` içinde loom'a **tek bir
   referans yok** (0 giriş). Stonecutter loader'dan tamamen bağımsız: proje ağacını
   ve kaynak direktiflerini yönetir, Loom'u sarmaz.
2. *"Loom 1.17.2'ye sabit, 1.18+ JVM 25 istiyor"* — Architectury Loom'un
   **en yeni hattı 1.17** (`1.17.493`; maven'de 1.18+ hattı **yok**) ve
   972 sınıfının **hepsi Java 21 bytecode**. Yani JVM 25 kısıtı bu yolda
   hiç devreye girmiyor.

### Doğrulanan yetenekler (`architectury-loom-1.17.493.jar`)

- `ModPlatform`: **FABRIC / NEOFORGE / FORGE / QUILT**
- Eklenti kimliği `dev.architectury.loom`; platform **`loom.platform`** Gradle
  özelliğiyle seçiliyor (`LoomGradleExtensionApiImpl` içinde okunuyor)
- Bağımlılık yapılandırmaları: **`neoForge`**, `forge` (`Constants$Configurations`)
- `LoomGradleExtensionAPI`: `isNeoForge()`, `neoForge(Action)`, `getPlatform()`
- SRG makinesi mevcut: `SrgProvider`, `PatchProvider`, `ForgeUserdevProvider`,
  `MethodInheritanceMappingsMigrator`

### Denenen mimari

Dallar kullanılmadan, **loader bir düğüm boyutu** olarak:

```
versions/1.21.7             loom.platform=fabric     (mevcut, adı değişmez)
versions/1.21.11            loom.platform=fabric     (mevcut, adı değişmez)
versions/1.21.7-neoforge    loom.platform=neoforge
versions/1.21.11-neoforge   loom.platform=neoforge
```

Tek paylaşılan kök `src/` korunur, **tek eklenti** yeter (aynı eklenti iki platformu
da kuruyor, koşullu eklenti uygulaması gerekmez), loader ayrımı
`stonecutter.constants` + `//? if fabric` / `//? if neoforge` ile ve yalnızca
platform katmanında yapılır. Mevcut fabric düğümlerinin yolları hiç değişmez.

Bu kurulum **kuruldu ve koşturuldu.**

### Nerede patlıyor

NeoForge kurulumu neredeyse sonuna kadar gidiyor — NeoForge indiriliyor, 10 MCP
adımı tamamlanıyor, jar'lar patch'leniyor, access transformer'lar uygulanıyor — ve
Minecraft'ı Yarn'a remap etme adımında duruyor:

```
:access transformed minecraft in 15s
:remapping minecraft (TinyRemapper, mojang -> official)
Mapping target name conflicts detected:
  METHODs net/minecraft/class_329/[renderHotbar, method_1759](...)V -> renderHotbar
There were unfixable conflicts.
> Failed to setup Minecraft, java.lang.RuntimeException: Failed to remap minecraft
```

`class_329` = `InGameHud`. Mojang tarafındaki `renderHotbar` ile intermediary
`method_1759` aynı hedef ada düşüyor ve TinyRemapper çakışmayı reddediyor.

**İki hedef sürümde de aynı:** 1.21.11'de `class_329/[renderHotbar, method_1759]`,
1.21.7'de aynı çakışmanın obfuscate hâli `fwz/[renderHotbar, p]`. Yani bu bir sürüm
sorunu değil, arch-loom 1.17.493'ün NeoForge yolundaki genel bir kırıklık — ve
1.17 en yeni hat olduğu için "daha yeni sürüme geç" diye bir çıkış yok.

`TinyRemapper$Builder.ignoreConflicts(boolean)` arch-loom içinde çağrılıyor, ama
bunu dışarıdan açan bir `loom.*` özelliği yok (mevcut bayrakların tam listesi
jar'dan çıkarıldı; `loom.dropNonIntermediateRootMethods` yalnızca Mojang mapping
spec'ine bakıyor, bu yola değil).

## Sıradaki adım ne olmalı

Sırası önem sırasına göre:

1. **Çakışmayı upstream'e bildir** (Architectury Loom). Reprodüksiyon elimizde:
   arch-loom 1.17.493 + NeoForge 21.7.25-beta/21.11.48 + Yarn, `loom.platform=neoforge`.
   Bu düzeltilirse yukarıdaki mimari olduğu gibi çalışmaya devam eder — kurulum
   zaten yazılmış durumda.
2. **Ya da Mojang mapping göçünü kabul et.** 143 dosya / 156 sınıf adı yeniden
   adlandırılır, `src/` Mojang adlarına geçer, Loom `officialMojangMappings()` ile
   Fabric'i de aynı adlarla derler (bu dal `build.gradle`'da **zaten var**, 26.x
   için yazılmıştı). O zaman MDG yolu açılır **ve 26.x engeli de kalkar** — iki iş
   tek seferde. Büyük ve riskli, ama tek seferlik ve mekanik.
3. **Forge (LexForge) ayrıca sorgulanmalı.** Bu oturumda hiç denenmedi: NeoForge
   yeşile dönmeden Forge'a geçmek üç yarım iş üretir. Ayrıca 1.21+ modlu ekosistem
   fiilen NeoForge'ta olduğundan, Forge'un gerçekten istenip istenmediği bir ürün
   kararı — teknik bir zorunluluk değil.

## Bozulmayanlar

Bu oturumda **kaynak kodu hiç değiştirilmedi** ve build kurulumuna dokunulmadı.
Fabric tarafı olduğu gibi duruyor: `./gradlew build` iki MC düğümünde de jar
üretmeye devam ediyor (`versions/1.21.7/build/libs/`,
`versions/1.21.11/build/libs/`). Platform katmanı yazılmadı, 86 dosyanın
import'ları değiştirilmedi, `MeYuz`'a dokunulmadı.

Değişen tek şey bu belge ve `BUILD.md`'nin loader bölümü.

## Ölçümler (referans olarak korundu)

`src/main/java` içinde:

| | |
|---|---|
| Toplam Java dosyası | 206 |
| `net.fabricmc.fabric.api` import eden | **86** |
| Ayrı `fabric.api` sınıfı | **25** (+ `net.fabricmc.api/.loader`'dan 5 sembol = 30) |
| `net.minecraft` import eden | **143** |
| Ayrı Yarn `net.minecraft` sınıfı | **156** |
| Mixin sınıfı | 11 |
| Giriş noktası | 4 |

Platform katmanının sarması gerekecek şeyler, kullanım sayısıyla — ve iyi haber:
çağrı kalıpları şaşırtıcı derecede düzenli.

```
68  ServerTickEvents.END_SERVER_TICK.register(X::onTick)   ← 68'in HEPSİ tek kalıp
16  PayloadTypeRegistry.playC2S()/playS2C().register(ID, CODEC)   ← 11 payload tipi
14  ServerPlayNetworking.send           4  ServerPlayNetworking.registerGlobalReceiver
 9  ClientPlayNetworking.registerGlobalReceiver   4 send   4 canSend
```

sonra `ServerPlayConnectionEvents`, `ServerLivingEntityEvents`,
`ClientPlayConnectionEvents`, `AttackEntityCallback`, `ServerMessageEvents`,
`PlayerBlockBreakEvents`, `LootTableEvents`, `ServerPlayerEvents`,
`EntitySleepEvents`, `ClientTickEvents`, `FabricDefaultAttributeRegistry`,
`UseItemCallback`, `UseEntityCallback`, `UseBlockCallback`, `AttackBlockCallback`,
`ServerLifecycleEvents`, `ServerChunkEvents`, `ServerEntityWorldChangeEvents`,
`CommandRegistrationCallback`, `ScreenEvents`, `EntityRendererRegistry`.

Kolay çıkan üç şey:

- **`FabricLoader` yalnızca 9 dosyada**: configDir, gameDir, `isModLoaded`,
  `getAllMods`, `getModContainer` — NeoForge'da hepsinin doğrudan karşılığı var.
- **Kayıt yalnızca 7 dosyada**, çoğu vanilla `Registry.register(Registries.X, ...)`.
  Fabric'e özgü olan sadece `FabricDefaultAttributeRegistry` (3 çağrı) ve
  `EntityRendererRegistry` (3 çağrı).
- **`@Environment(EnvType.CLIENT)` ×14** → NeoForge'da `@OnlyIn(Dist.CLIENT)`.
  Annotation olduğu için metotla sarılamaz; platform katmanı dışında direktif
  gerekecek **tek** yer burası.

Bu ölçüm hâlâ geçerli: mapping duvarı aşılırsa kaynak tarafındaki iş gerçekten
ince bir katman. Engel kaynak kodunda değil, mapping araç zincirinde.

## Değişmeyen sınırlar

- **Fabric her commit'te yeşil kalır.** Depoyu derlenmez halde bırakan ara commit yok.
- Stonecutter'ın deneyle bulunmuş iki yapısal kuralı (ayrıntı `BUILD.md`'de):
  aktif düğüm `src/`'yi yerinde derler ve replacement'lar orada koşmaz, yani `src/`
  her zaman aktif sürümün adlarını taşır; düz metin replacement alıcı tipini bilmez,
  desenler alıcıya çapalanır.
- API iddiaları `javap` ile doğrulanır, tahmin edilmez.
- `//? if` direktiflerinin içine javadoc konmaz — javadoc `*/` içerir ve etkin
  olmayan dalın blok yorumunu erken kapatır.
- `MeYuz` 1.21.9+ üzerinde bilerek kapalı (ayrıntı `BUILD.md`'de). Bu portun
  kapsamı dışında.
- Gizlilik sınırları gevşetilmez: telemetri yok, beyaz liste beyaz liste kalır,
  `uygulamaSezgisi: false` süreç okumasını tamamen ve geri dönüşsüz kapatır.
- README gereksinimler tablosu gerçeği söyler. **Desteklenmeyen bir loader
  destekleniyormuş gibi yazılmaz** — bu yüzden bu oturum README'ye NeoForge
  eklemedi.
