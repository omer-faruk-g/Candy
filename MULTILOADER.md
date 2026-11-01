# Çok loader portu — mapping göçü yapıldı, NeoForge hâlâ yok

> Durum: **kaynak Mojang resmî adlarına göçtü, Fabric iki MC düğümünde de yeşil.
> NeoForge düğümleri ve araç zinciri kuruldu, ama NeoForge HENÜZ JAR ÜRETMİYOR** —
> 101 dosya platform katmanına taşınmadı.
>
> Bu dosya üç katman taşıyor: (1) portun ilk denemesinde ölçülen engeller,
> (2) o engellerden birinin nasıl kaldırıldığı — mapping göçü, (3) sıradaki adım.
> Aşağıdaki her iddia `javap`, maven metadata, üretilmiş mapping tablosu veya
> gerçek bir Gradle koşusuyla doğrulandı — hiçbiri belgeden alınmadı.

## Özet: nerede duruyoruz

| Yol | Engel | Durum |
|---|---|---|
| **ModDevGradle / NeoGradle** | Mapping duvarı: 143 dosyada 156 Yarn sınıf adı | **Engel kaldırıldı** — kaynak Mojang adlarında, araç zinciri kurulu. Kalan: platform katmanı |
| **Architectury Loom** | Upstream remap çakışması: `InGameHud.renderHotbar` | **Artık ilgisiz** — Yarn'ı korumaya ihtiyaç yok |
| **Stonecutter dalları** | Dal başına ayrı `src/` ağacı → 206 dosya loader başına kopya | Elendi, portun amacını bozuyor |

Mapping göçünün ikinci kazancı: **26.x engeli de kalktı.** Yarn eşlemeleri 1.21.11'de
duruyor, 26.x Mojang resmî adları istiyor. Kaynak artık onları taşıdığı için 26.x
düğümü eklemek *sıradan sürüm işi* oldu — ad göçü değil. Bu, projenin benimsenmesindeki
baskın değişkendi.
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

## Mapping duvarı — ilk ölçüm (tarihsel)

> Bu bölüm engeli *nasıl ölçtüğümüzü* kaydediyor. Engel sonradan kaldırıldı;
> çözümü aşağıdaki "Mapping göçü" bölümünde.

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

**MDG yolu o turda bu yüzden elendi** — tahminle değil, ölçümle. Göç yapıldıktan
sonra bu gerekçe ortadan kalktı.

## Architectury Loom — denendi, kırık, artık ilgisiz (tarihsel)

> Bu yol Yarn adlarını korumak için gerekliydi. Kaynak Mojang adlarına göçtüğü için
> **bu yola bir daha dönülmeyecek.** Kayıt, aynı duvara iki kez çarpmamak için duruyor.

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

## Mapping göçü — duvar nasıl yıkıldı

Yukarıdaki iki engelden biri seçildi ve kaldırıldı: kaynak Yarn adlarından
**Mojang resmî adlarına** göçtü. Architectury Loom'un kırıklığı artık bizi
ilgilendirmiyor, çünkü Yarn'ı korumaya ihtiyaç kalmadı.

### 1. Önce tablo: göç mekanik mi, elle mi?

Göçe başlamadan önce sorulması gereken soru buydu — 156 sınıf adını elle yeniden
adlandırmak kapsamı tamamen değiştirirdi. Yarn ↔ Mojang tablosu **obfuscate
(`official`) ad alanı üzerinden** kuruldu, intermediary üzerinden değil; iki taraf da
official'a karşı yayınlanıyor:

```
Yarn    mappings.tiny           : official -> intermediary -> named(Yarn)   (tiny v2)
Mojang  client.txt + server.txt : named(Mojang) -> official                 (ProGuard)
```

İkisi de Loom'un kendi indirdiği dosyalar (`~/.gradle/caches/fabric-loom/`). Üye
birleştirmesi **ada göre değil**, `(official owner, official name, official
descriptor)` üçlüsüne göre yapıldı; Mojang ProGuard dosyası imzaları Mojang *tip*
adlarıyla yazdığı için parametre/dönüş tipleri sınıf haritasıyla official
descriptor'a geri çevrildi. Yani hiçbir üye tahminle eşleşmedi.

| | 1.21.7 | 1.21.11 |
|---|---|---|
| Sınıf | 9468 / 9468 (%100) | 10275 / 10275 (%100) |
| Metot | 52324 / 52359 (%99.93) | 56905 / 56905 (%100) |
| Alan | 42411 / 42411 (%100) | 45248 / 45248 (%100) |

1.21.7'de birleşmeyen 35 metot, derleyicinin ürettiği **kovaryant bridge**
metotları; ProGuard sentetik üyeleri listelemez. Her birinin adı bridge olmayan
aşırı yüklemesi üzerinden tabloda zaten var — kayıp yok.

**Üç bağımsız doğrulama:** (a) yukarıdaki birleşme oranı; (b) önceki oturumun
arch-loom denemesinin ürettiği 4 ad alanlı `mappings-mojang.tiny` ile karşılaştırma
— tamamen ayrı bir kod yolu, **19 743 sınıf + 109 229 metot + 87 659 alan
uyuşuyor, 0 farklı**; (c) `javap`, gerçek Mojang adlı jar
(`minecraft-merged-mojang.jar`) — `InGameHud`→`Gui`, `Entity.getPos`→`position()`,
`Entity.getWorld`→`level()`, `Registries`→`BuiltInRegistries` hepsi doğrulandı.

**Candy'nin gerçek kullanımını kapsama:**

| | |
|---|---|
| Sınıf | **156 / 156**, kapsanmayan **0** |
| Metot adı (MC metodu olan) | 454 ayrı ad, eşlenmeyen **0** |
| Alan adı | 445 ayrı ad, eşlenmeyen **0** |

Ama metot tarafında 244 ad **Minecraft'ın tamamında belirsiz**, 74'ü
**Candy'nin kendi import ettiği tiplerde bile belirsiz** (`add` →
`add`/`atLowerCornerWithOffset`/`offset`, `getBlockPos` →
`getBlockAt`/`getBlockPosition`, `getEquippedStack` →
`getEquipmentIfRenderable`/`getItemBySlot`); alanlarda 28 (`FACING` →
`DIRECTION`/`FACING`, `pitch` → `xRot`/`pitch`). Yani tablo **tam**, ama düz metin
(sed) göçü **güvenli değil** — `BUILD.md`'nin alıcı-tipi uyarısının 100 kat
büyümüş hâli. Göç tip-duyarlı olmak zorundaydı.

### 2. Göçü Loom kendisi yaptı

Fabric Loom 1.17.2'nin içinde **`migrateMappings`** task'ı var ve tam bu iş için:

```
./gradlew :<sürüm>:migrateMappings --mappings "net.minecraft:mappings:<sürüm>"
```

- **Tip duyarlı.** `MigrateSourceCodeMappingsService` **Mercury**'yi
  (`org.cadixdev.mercury.Mercury`, Eclipse JDT tabanlı kaynak remapper) sürüyor.
  Alıcı tipini gerçekten çözüyor — yukarıdaki ~102 belirsiz ad bizim değil,
  derleyicinin problemi oldu.
- **Mojang official'ı hedef olarak biliyor:** argüman `net.minecraft:mappings:` ile
  başlıyorsa `LayeredMappingSpecBuilderImpl.buildOfficialMojangMappings()`
  çağrılıyor. Argüman projenin MC sürümüyle bitmek zorunda.

Elle kurulan tablo yine de gerekliydi — Mercury'nin ulaşamadığı yerler ve her
adımda doğrulama oracle'ı için.

**Mercury'nin iki sınırı, ikisi de ölçüldü:**

1. **Kütüphane metodunu ezen bildirimleri yeniden adlandırmıyor.** Gövdedeki
   `super.x()` çağrısını çeviriyor ama `public void x(` imzasını bırakıyor.
   1.21.7'de 13 derleme hatası tam bundan çıktı: `getTexture`→`getTextureLocation`
   (3 renderer), `updateRenderState`→`extractRenderState` (3 renderer),
   `craft`→`assemble` (2 recipe); 1.21.11 pasif dalında ayrıca `render`→`submit`
   (2 renderer). Hepsi `@Override` taşıdığı için derleyici *hepsini* yakaladı —
   sessiz kayıp yok, ayrıca ayrı bir tarama ile de arandı ve bulunamadı.
2. **`//? if` direktiflerini import bloğunda düşürüyor.** JDT import bloğunu
   yeniden yazdırıyor; `CandyDoppelRenderer` (8→6) ve `TrueRealCandyRenderer`
   (6→4) direktiflerini kaybetti, elle geri kondu. Gövde içindeki direktifler
   (`MeYuz` 7→7, `CommandManagerMixin` 2→2) sağ kaldı.

**Pasif dallar** (yorum içine alınmış `//? if` dalları) Mercury için görünmez.
Çözüm: `migrateMappings` bir de **1.21.11'in Stonecutter üretilmiş ağacında**
koşturuldu (orada `>=1.21.9` dalı aktif), sonra iki çıktı birleştirildi. Yani iki
dalın da adlarını derleyici çözdü, elle çevrilmedi.

### 3. `replacements` kuralları yeniden yazıldı

Kural listesi tahminle değil **ölçümle** çıkarıldı: iki düğümün göçürülmüş ağaçları
birebir karşılaştırıldı, listeye yalnızca gerçekten farklılaşan semboller girdi.

- **Silinen 5 kural** — `Entity.getWorld`/`getPos` ikilisi Yarn'da 1.21.9'da yeniden
  adlandırılmıştı, Mojang iki sürümde de `level()`/`position()` diyor. Onlar Yarn'ın
  kendi yeniden adlandırmasıydı, Minecraft'ın değil.
- **Eklenen 10 kural** — Yarn'ın sakladığı gerçek yeniden adlandırmalar:
  `ResourceLocation`→`Identifier` (70 kullanım), `PlayerRenderState`→`AvatarRenderState`,
  `ResourceKey.location()`→`identifier()` (üç alıcı da çapalandı) ve 6 paket taşıması.

Buradan çıkan ve `BUILD.md`'ye de yazılan kural: **pasif dal içindeki bir token
replacement kuralı kapsamındaysa, aktif sürümün ad alanında yazılmak zorunda** —
çünkü aktif düğüm `src/`'yi yerinde derler ve replacement orada hiç koşmaz.

### 4. Mixin'ler bayt kodu seviyesinde doğrulandı

Projede refmap dosyası yok; Loom 1.17 mixin anotasyonlarını `remapJar` sırasında
doğrudan intermediary'ye çeviriyor. Teslim edilen jar'ın bayt kodu okundu:
**11 mixin'in hepsinin hedefi, Yarn kaynağının hedeflediği sembolün aynısına
çözülüyor** (`method_30092`, `method_1835`, `method_3211`, `method_42596`,
`method_1753`, `method_1812`, `method_19325`, `method_19321`, `field_18717`,
`field_18718`, `field_40540` + 7 sınıf). Göç mixin hedeflerini kaydırmadı.

### 5. Göçün gerçek maliyeti: okunabilirlik ve eskiyen yorumlar

Yarn adları Mojang adlarından **belirgin biçimde daha okunabilir**
(`BipedEntityModel` → `HumanoidModel` katlanır, ama `getEquippedStack` →
`getItemBySlot`, `mobTick` → `customServerAiStep`, `isSneaking` →
`isShiftKeyDown` okuyucuyu yavaşlatır). Bu, göçün bedeli; teknik bir kazanç
karşılığında kabul edildi.

Bunun ikinci etkisi: projedeki yorum ve açıklamaların çoğu Yarn adlarına atıf
yapıyordu. **9 mixin dosyasının yorumları düzeltildi** (hedef adını yazdıkları için
yanlış olmaları doğrudan yanıltıcıydı). Kalanlar düzeltilmedi; aşağıdaki liste
hangi dosyaların yorumlarının eskidiğini söylüyor.

Tarama ölçütü: yorum içinde geçen, **en az iki kelimeli CamelCase/camelCase**
tanımlayıcılar — tek kelimeli adlar (`player`, `world`, `state`) genel İngilizce
sözcüklerle çakıştığı için elendi. Stonecutter pasif dalları hariç.

| Dosya | Eskiyen atıf | Örnekler |
|---|---|---|
| `client/CandyDoppelRenderer.java` | **17** | `BipedEntityModel`→`HumanoidModel`, `SkinTextures`→`PlayerSkin`, `equippedHeadStack`→`headEquipment`, `updateRenderState`→`extractRenderState` |
| `core/TruePersonality.java` | 5 | `asLong`→`convertToLong`, `onDeath`→`die` |
| `arkadas/ArkadasEntity.java` | 3 | `ActiveTargetGoal`→`NearestAttackableTargetGoal`, `HostileEntity`→`Monster` |
| `client/ArkadasRenderer.java` | 3 | `MobEntityRenderer`→`MobRenderer`, `skinTextures`→`skin` |
| `entity/CandyDoppelEntity.java` | 3 | `HostileEntity`→`Monster`, `mobTick`→`customServerAiStep` |
| `meta/OrumcekYonlendirme.java` | 3 | `CaveSpiderEntity`→`CaveSpider`, `SpiderEntity`→`Spider` |
| `audio/SesGonder.java` | 2 | `PlaySoundS2CPacket`→`ClientboundSoundPacket`, `playSoundToPlayer`→`playNotifySound` |
| `boss/TrueRealCandyEntity.java` | 2 | `BossBar`→`BossEvent` |
| `sanity/SanityController.java` | 2 | `RegistryEntry`→`Holder`, `StatusEffect`→`MobEffect` |
| `boss/BossEventler.java` | 1 | `RegistryEntry`→`Holder` |
| `boss/GizemliKitapRecipe.java` | 1 | `SpecialCraftingRecipe`→`CustomRecipe` |
| `client/AltyaziState.java` | 1 | `SubtitlesHud`→`SubtitleOverlay` |
| `client/MeYuz.java` | 1 | `toString`→`describe` |
| `client/TrueRealCandyRenderer.java` | 1 | `SkinTextures`→`PlayerSkin` |
| `core/FearDetection.java` | 1 | `isSneaking`→`isShiftKeyDown` |
| `core/JsonDepo.java` | 1 | `toJson`→`asJson` |
| `core/ObservationEngine.java` | 1 | `isSneaking`→`isShiftKeyDown` |
| `disguise/DisguiseSystem.java` | 1 | `lookAt`→`setLookAt` |
| `item/KuranRecipe.java` | 1 | `SpecialCraftingRecipe`→`CustomRecipe` |
| `real/RealCandy.java` | 1 | `RegistryEntry`→`Holder` |
| `tempo/TickAccelerator.java` | 1 | `TickManager`→`TickRateManager` |
| `travma/PetOlumu.java` | 1 | `TameableEntity`→`TamableAnimal` |

**22 dosya, 53 atıf.** Yorumlar davranışı etkilemiyor; bu liste bir borç kaydı,
bir hata listesi değil. `client/CandyDoppelRenderer.java` tek başına üçte birini
taşıyor — temizliğe oradan başlanmalı.

## NeoForge, ModDevGradle ile — araç zinciri kuruldu, port bitmedi

> Durum: **NeoForge düğümleri Gradle'da var ve Minecraft+NeoForge classpath'i
> çözülüyor. NeoForge hâlâ jar ÜRETMİYOR.** Kalan iş 101 dosyanın platform
> katmanına taşınması.

### Düğüm yapısı — dal yok, dört düğüm

| Düğüm dizini | MC | loader | NeoForge |
|---|---|---|---|
| `versions/1.21.7` | 1.21.7 | fabric | — |
| `versions/1.21.11` | 1.21.11 | fabric | — |
| `versions/1.21.7-neoforge` | 1.21.7 | neoforge | `21.7.25-beta` |
| `versions/1.21.11-neoforge` | 1.21.11 | neoforge | `21.11.48` |

`vers(<düğüm adı>, <MC sürümü>)` ile düğüm dizini adı sürümden ayrıldı; böylece
`replacements` kurallarının `current.parsed >= '1.21.9'` eşiği NeoForge
düğümlerinde de aynen çalışıyor.

**Stonecutter dalı (branch) kullanılmadı** — dal başına ayrı `src/` ağacı üretilir,
yani 206 dosya loader başına kopyalanırdı. Dört düğüm **tek** `src/main/java`
ağacını paylaşıyor.

**Tek merkez `build.gradle`** iki loader'ı da çeviriyor: eklentiler `apply false`
ile classpath'e alınıp düğümün `loader` özelliğine göre uygulanıyor. İkinci bir
build betiği **bilinçli olarak yok** — `replacements` listesi tek yerde kalmak
zorunda, iki kopya arasındaki herhangi bir fark aktif düğüm değiştiğinde
paylaşılan `src/` ağacını yarım çevrilmiş bırakırdı.

Araçlar: **ModDevGradle 2.0.148** (Java 17 bytecode → JDK 21'de çalışır, plugin
portal'dan çözülüyor). `stonecutter.constants.match(loader, 'fabric', 'neoforge')`
eklendi, yani kaynakta `//? if fabric {` / `//? if neoforge {` kullanılabilir.

### Platform katmanı: loader başına kaynak ağacı

```
src/main/java      206 dosya — TEK, paylaşılan
src/fabric/java    Fabric uygulaması      } ayni paket, ayni sinif adlari,
src/neoforge/java  NeoForge uygulamasi    } farkli govdeler
```

Her düğüm `sourceSets.main.java.srcDir "src/${loader}/java"` ile yalnızca kendi
loader'ının ağacını derliyor. **Tasarım kuralı: platform sınıfları, Candy'nin
kaynağının zaten kullandığı çağrı biçimini birebir korur** — ör. `SunucuTick`
sınıfı `END_SERVER_TICK.register(X::onTick)` biçimini aynen sunuyor. Böylece 101
dosyada yalnızca **import satırı** değişecek, gövdeler değişmeyecek.

> **Dikkat:** bu iki ağaçta `//?` direktifi ve replacement **koşmuyor**. Oradaki
> kod iki MC sürümünde de aynı derlenen sembolleri kullanmak zorunda.

Doğrulanan iki bilinmeyen (derleyiciyle, tahminle değil):

- Stonecutter'ın üretilmiş ağacı ile ek `srcDir` **birlikte çalışıyor** — Fabric
  iki düğümde de yeşil, platform sınıfı jar'da.
- MDG'nin classpath'i **NeoForge'u da veriyor** — `net.neoforged.bus.api.SubscribeEvent`,
  `net.neoforged.fml.common.EventBusSubscriber`,
  `net.neoforged.neoforge.event.tick.ServerTickEvent.Post` hepsi çözülüyor, 0 hata.

### Kalan iş — ölçülmüş envanter

NeoForge düğümünde `compileJava` **100 hata** veriyor ve hatalarının **hiçbiri
`net.minecraft` değil**. Mapping göçünün kanıtı bu: Minecraft tarafı tamamen
çözülüyor, kalan her hata `net.fabricmc.*`.

`src/main/java` içinde `net.fabricmc` import eden **101 dosya**, 28 ayrı tip:

| Tip | Dosya | Çağrı biçimi | NeoForge karşılığı |
|---|---|---|---|
| `ServerTickEvents` | **65** | 68 × `END_SERVER_TICK.register(X::onTick)` — hepsi tek kalıp | `ServerTickEvent.Post` (**yazıldı**) |
| `PayloadTypeRegistry` | 16 | 18 × `playS2C().register(ID, CODEC)`, 8 × `playC2S()` | `RegisterPayloadHandlersEvent` + `PayloadRegistrar` |
| `Environment` / `EnvType` | 14 | `@Environment(EnvType.CLIENT)` | `@OnlyIn(Dist.CLIENT)` — anotasyon, metotla sarılamaz → `//? if fabric` |
| `ServerPlayNetworking` | 13 | 14 × `send`, 4 × `registerGlobalReceiver` | `PacketDistributor` / `PayloadRegistrar` |
| `FabricLoader` | 8 | `getConfigDir` ×5, `isModLoaded` ×2, `getGameDir`, `getAllMods`, `getModContainer` | `FMLPaths`, `ModList` |
| `ServerPlayConnectionEvents` | 7 | join/disconnect | `PlayerEvent.PlayerLoggedIn/Out` |
| `ServerLivingEntityEvents` | 5 | — | `LivingDeathEvent` vb. |
| `ClientPlayNetworking` | 5 | 9 × `registerGlobalReceiver`, 4 × `send`, 4 × `canSend` | aynı payload kaydı |
| `ClientPlayConnectionEvents` | 4 | — | `ClientPlayerNetworkEvent` |
| `AttackEntityCallback` | 3 | — | `AttackEntityEvent` |
| diğer 18 tip | 1–2'şer | — | mod bus olayları |
| `ModInitializer` / `ClientModInitializer` | 2 | 4 giriş noktası | `@Mod` sınıfı + `Dist` ayrımı |

**İyi haber, ölçülmüş:** 101 dosyanın **60'ı** yalnızca üç şeye ihtiyaç duyuyor
(`ServerTickEvents` + `Environment` + `FabricLoader`), 44'ü yalnızca
`ServerTickEvents`'e. Yani uzun kuyruk üç küçük platform parçasıyla kapanıyor.

**Payload sınıfları hiç değişmeyecek** — zaten vanilla `CustomPacketPayload` +
`StreamCodec` kullanıyorlar (Fabric'in değil Minecraft'ın tipleri). NeoForge'un
`PayloadRegistrar`'ı tam bu ikiliyi alıyor. Tek fark *kayıt yeri*: Fabric'te statik
`register()` çağrısı, NeoForge'da `RegisterPayloadHandlersEvent` içinde — platform
sınıfı kayıtları kuyruğa alıp olay geldiğinde boşaltarak biçim uyumunu koruyabilir.

**Mixin'ler:** her iki loader da SpongePowered Mixin kullanıyor, `candy.mixins.json`
olduğu gibi yeniden kullanılabilir; NeoForge'da `neoforge.mods.toml` içinde
`[[mixins]]` ile bildiriliyor. Fabric'te Loom hedefleri intermediary'ye remap
ediyor, NeoForge'da remap yok (Mojang adları zaten çalışma zamanı adları) — yani
**aynı kaynak iki tarafta da doğru hedefi tutuyor.**

### Sıradaki adımlar (sırayla, her adımda Fabric yeşil)

1. **Uzun kuyruk** — `SunucuTick`'in yanına `Platform` (FabricLoader karşılığı) ve
   `@Environment` için `//? if fabric` direktifleri; 60 dosyanın import satırları.
2. **Ağ katmanı** — `CandyAg` platform sınıfı: payload kaydı, `send`, `canSend`,
   `registerGlobalReceiver`. 16 + 13 + 5 dosya.
3. **Olay katmanı** — kalan 18 tip, her biri 1–2 dosya.
4. **Giriş noktası** — `@Mod` sınıfı + `META-INF/neoforge.mods.toml` (mixin
   bildirimi dahil). `processResources` toml'u da genişletiyor (yazıldı).
5. **Jar** — `./gradlew :1.21.7-neoforge:build`, sonra 1.21.11.
6. **Ancak jar üretince** `README.md` ve `BUILD.md` tablolarına NeoForge yazılır.

**Forge (LexForge) bu turda yok** — NeoForge yeşile dönmeden açılmayacak.

## Bu göçte ne değişti, ne değişmedi

**Değişti:** `src/main/java` içindeki 206 dosyanın tamamı Mojang adlarına geçti
(147 dosya diff'te), `build.gradle` mappings bloğu ve `replacements` listesi,
`versions/*/gradle.properties` içindeki `yarn_mappings` ölü config olarak işaretlendi,
`BUILD.md` ve bu dosya.

**Değişmedi:** davranış. Platform katmanı **yazılmadı**, 86 dosyanın `fabric.api`
import'ları **değişmedi** (göç yalnızca `net.minecraft` adlarını kapsıyor),
`MeYuz`'un 1.21.9+ üzerindeki kapalı durumu **olduğu gibi**, gizlilik sınırları
**olduğu gibi**, README gereksinimler tablosu **olduğu gibi** (NeoForge hâlâ yok,
öyle de yazıyor).

**Fabric yeşil:** `./gradlew build` iki düğümde de jar üretiyor —
`versions/1.21.7/build/libs/candy-8.4.1+1.21.7.jar`,
`versions/1.21.11/build/libs/candy-8.4.1+1.21.11.jar`.

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

Bu ölçüm hâlâ geçerli — ve mapping duvarı artık aşıldığı için **asıl iş listesi
bu oldu**: kaynak tarafındaki iş gerçekten ince bir katman. Engel kaynak kodunda
değildi, mapping araç zincirindeydi.

Tablodaki "Ayrı Yarn `net.minecraft` sınıfı | 156" satırı artık tarihsel: o 156 ad
göçürüldü, kaynak Mojang adlarını taşıyor.

## Değişmeyen sınırlar

- **Fabric her commit'te yeşil kalır.** Depoyu derlenmez halde bırakan ara commit yok.
- **Mapping tek:** her düğüm Mojang resmî adlarını kullanır
  (`loom.officialMojangMappings()`). Yarn'a dönülmez; dönmek 26.x hattını yeniden
  kapatır.
- **Yeni MC sembolü eklenirken ad Mojang tarafından, `javap` ile alınır** — Yarn
  belgelerinden çevrilmez.
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
