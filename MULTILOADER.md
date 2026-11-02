# Çok loader portu — mapping göçü yapıldı, NeoForge hâlâ yok

> Durum: **kaynak Mojang resmî adlarına göçtü, Fabric iki MC düğümünde de yeşil.
> NeoForge düğümleri ve araç zinciri kuruldu, ama NeoForge HENÜZ JAR ÜRETMİYOR** —
> platform katmanının 2/4'ü bitti (tick + ağ), `net.fabricmc` import eden
> **41 dosya** kaldı.
>
> Bu dosya üç katman taşıyor: (1) portun ilk denemesinde ölçülen engeller,
> (2) o engellerden birinin nasıl kaldırıldığı — mapping göçü, (3) sıradaki adım.
> Aşağıdaki her iddia `javap`, maven metadata, üretilmiş mapping tablosu veya
> gerçek bir Gradle koşusuyla doğrulandı — hiçbiri belgeden alınmadı.

## Özet: nerede duruyoruz

| Yol | Engel | Durum |
|---|---|---|
| **ModDevGradle / NeoGradle** | Mapping duvarı: 143 dosyada 156 Yarn sınıf adı | **Engel kaldırıldı** — kaynak Mojang adlarında, araç zinciri kurulu. Kalan: platform katmanı 2/4 bitti (268 → 148 hata) |
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

**NeoForge düğümleri varsayılan olarak KAPALI** (`gradle.properties` →
`candy.neoforge=false`). Gerekçe ölçümle bulundu: kökteki `./gradlew build`
**bütün** düğümleri derliyor — yalnızca aktif düğümü değil. Derlenmeyen bir düğüm
açık bırakılsa hem yerel build hem CI kırmızı olurdu, bu da "her commit'te Fabric
yeşil" kuralını bozardı. Çalışmak için `-Pcandy.neoforge=true`; port jar üretmeye
başlayınca varsayılan `true` yapılır.

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

### En büyük yan kazanç: 26.x artık mapping yüzünden engelli DEĞİL

Bunu ayrıca yazmak gerekiyor, çünkü **projenin benimsenmesindeki baskın değişken
loader değil sürüm kapsamı.** Pazar lideri `from-the-fog` 26.3'e kadar destekliyor.

Eski engel şuydu: Yarn eşlemeleri **1.21.11'de duruyor**, 26.x ise Mojang resmî
mapping istiyor. Yani Yarn'da kalındığı sürece 26.x **kalıcı olarak kapalıydı** — ve
açmanın bedeli 143 dosyadaki her MC sembolünün yeniden adlandırılmasıydı.

Kaynak artık Mojang adlarını taşıyor, yani **ad göçü tarafında yapılacak iş kalmadı** —
bu ölçümle doğrulandı (aşağıda). Ama "sıradan sürüm işi oldu" demek **yanlıştı**:
mapping engelinin arkasında ikinci bir engel duruyordu.

Bu, NeoForge portundan bağımsızdır: **NeoForge hiç bitmese bile 26.x'in mapping yolu
açık.** İki iş ayrı ayrı teslim edilebilir.

### Düzeltme: mapping engeli kalktı, JDK 25 engeli çıktı (ölçüm, 2026-11-02)

`feat/mc-26x` dalında Adım 1 (hedefi ölç) koşturuldu. Sonuç: **26.x bu makinede
derlenemiyor**, ve sebep mod tarafında değil.

Beş 26.x sürümü var (26.1, 26.1.1, 26.1.2, 26.2, 26.3 — en güncel 26.3, 2026-09-15).
**Fabric tarafında hiçbir eksik yok:** Loader 0.20.1 ve Fabric API beşi için de hazır
(26.3 → 0.165.0+26.3). Engel tamamen araç zincirinde:

- **26.x hattının tamamı Java 25 istiyor** (`javaVersion=25`, `java-runtime-epsilon`).
  1.21.7/1.21.11 → Java 21.
- **MC 26.x class dosyaları major 69** (Java 25) bayt kodu; 1.21.x → major 65. javac 21
  bunu okuyamıyor: `class file has wrong version 69.0, should be 65.0`. `--release 21`
  ile ve onsuz aynı.
- **Loom zaten yapılandırmada reddediyor.** Geçici `versions/26.3` düğümü denendi
  (commit edilmedi): `Minecraft 26.3 requires Java 25 but Gradle is using 21`.
- **26.x'i destekleyen loom hattı JVM 25 bayt kodu:** loom 1.17.2 ve 1.17.21 → major 65,
  1.18.1/1.18.4/1.19.3 → **major 69**. Yani loom 1.18+ JVM 21'deki Gradle daemon'ına
  yüklenemez bile.

Bu makinede yalnızca Corretto JDK 21.0.9 (ve bir JRE 1.8) var. Görev Java/loom
yükseltmesini kapsam dışı bıraktığı için Adım 2'ye geçilmedi — yarım bir sürüm desteği
eklemek hiç eklememekten kötü.

### Beklenmeyen bulgu: Mojang 26.x'te obfuscation'ı bıraktı

Bu, ad göçünün 26.x için gerçekten bittiğinin kanıtı. Version JSON'da `downloads`:

```
1.21.11: [client, client_mappings, server, server_mappings]
26.1:    [client, server]
26.3:    [client, server]
```

`client_mappings` **yok**, çünkü jar zaten deobfuscated. Jar içeriği sayıldı:

| | class sayısı | `net/minecraft/*` | obfuscated top-level |
|---|---|---|---|
| 1.21.11 | 10291 | 33 (%0.3) | 10201 |
| 26.3 | 11383 | 10737 (%94.3) | **0** |

26.3'ün jar'ı `net/minecraft/world/entity/npc/Npc`, `net/minecraft/client/Camera$NearPlane`
gibi **gerçek Mojang adlarını** taşıyor — Candy'nin kaynağının göçten sonra kullandığı
adların aynısı. Yani 26.x için mapping katmanı hiç gerekmiyor; göç boşa gitmedi,
aksine tam isabet etti. Yan etkisi: `loom.officialMojangMappings()` 26.x'te indirecek
bir şey bulamaz, ve loom 1.18+ tam olarak bu durum için var.

### 26.x'i açmak için gereken

İkisi birlikte, biri yetmez:

1. **JDK 25** kurulu olmalı ve Gradle daemon'ı onunla koşmalı.
2. **fabric-loom 1.18+** (`settings.gradle` → `pluginManagement`).

`settings.gradle` bir kez değerlendirildiği için düğüm başına ayrı loom sürümü
**mümkün değil** — 1.18+'a geçmek 1.21.7 ve 1.21.11 düğümlerini de o loom'a taşır,
ikisi yeniden doğrulanmalı. Gradle toolchain kaçış yolu değil: yalnızca `compileJava`'yı
JDK 25'e vermek yetmiyor, daemon'ın kendisi loom 1.18+ yükleyebilmek için JVM 25 olmak
zorunda. Auto-provisioning kurulu değil (foojay resolver yok).

Sıra geldiğinde beklenen iş, ad göçü değil **araç zinciri yükseltmesi + dört düğümün
yeniden doğrulanması**.

### Kalan iş — ölçülmüş envanter

NeoForge düğümünde `compileJava` hata veriyor ama hatalarının **hiçbiri
`net.minecraft` değil**. Mapping göçünün kanıtı bu: Minecraft tarafı tamamen
çözülüyor, kalan her hata `net.fabricmc.*`.

`src/main/java` içinde `net.fabricmc` import eden **101 dosya**, 28 ayrı tip:

| Tip | Dosya | Çağrı biçimi | NeoForge karşılığı |
|---|---|---|---|
| ~~`ServerTickEvents`~~ | ~~65~~ | 68 × `END_SERVER_TICK.register(X::onTick)` | ✅ **TAŞINDI** → `SunucuTick` |
| ~~`PayloadTypeRegistry`~~ | ~~16~~ | 18 × `playS2C().register(ID, CODEC)`, 8 × `playC2S()` | ✅ **TAŞINDI** → `AgKayit` |
| `Environment` / `EnvType` | 14 | `@Environment(EnvType.CLIENT)` | `@OnlyIn(Dist.CLIENT)` — anotasyon, metotla sarılamaz → `//? if fabric` |
| ~~`ServerPlayNetworking`~~ | ~~13~~ | 14 × `send`, 4 × `registerGlobalReceiver` | ✅ **TAŞINDI** → `SunucuAg` |
| `FabricLoader` | 8 | `getConfigDir` ×5, `isModLoaded` ×2, `getGameDir`, `getAllMods`, `getModContainer` | `FMLPaths`, `ModList` |
| ~~`ServerPlayConnectionEvents`~~ | ~~7~~ | join/disconnect | ✅ **TAŞINDI** → `SunucuBaglanti` |
| `ServerLivingEntityEvents` | 5 | — | `LivingDeathEvent` vb. |
| ~~`ClientPlayNetworking`~~ | ~~5~~ | 9 × `registerGlobalReceiver`, 4 × `send`, 4 × `canSend` | ✅ **TAŞINDI** → `IstemciAg` |
| ~~`ClientPlayConnectionEvents`~~ | ~~4~~ | — | ✅ **TAŞINDI** → `IstemciBaglanti` |
| `AttackEntityCallback` | 3 | — | `AttackEntityEvent` |
| diğer 18 tip | 1–2'şer | — | mod bus olayları |
| `ModInitializer` / `ClientModInitializer` | 2 | 4 giriş noktası | `@Mod` sınıfı + `Dist` ayrımı |

**İlerleme:** `ServerTickEvents` taşındı — 65 dosya, 68 çağrı, hepsi tek kalıp
olduğu için betikle. `net.fabricmc` import eden dosya **101 → 57**, `net.fabricmc`
import satırı **178 → 113**. Gövdeler değişmedi: platform sınıfı `SunucuTick`,
çağrı biçimini (`END_SERVER_TICK.register(X::onTick)`) birebir koruduğu için
yalnızca import satırları değişti.

> ### ⚠ Hata sayısı 100 → 268 bir GERİLEME DEĞİL — ölçüm tavanıydı
>
> Bu belgede ve commit mesajlarında önce **100 hata**, sonra **268 hata** geçiyor.
> Arada kod bozulmadı: **javac varsayılan olarak 100 hatada kesiyor**, yani ilk
> ölçüm tavana çarpmış bir sayıydı, gerçek sayı değil. Yalnızca NeoForge
> düğümüne `-Xmaxerrs 2000` eklendikten sonra gerçek sayı görünür oldu: **268**.
>
> Yani **100 bir taban değil, bir tavan**; iki sayı karşılaştırılamaz. Portun
> ilerlemesi **268'e karşı** ölçülüyor:
>
> | Adım | Hata |
> |---|---|
> | ölçüm tavanı (gerçek değil) | ~~100~~ |
> | 1/4 sonrası — gerçek taban | 268 |
> | 2/4 sonrası (ağ katmanı) | **148** |

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

### Ağ katmanı (adım 2/4) — taşındı, ama arayüz bir farkı KABUL ETTİ

Beş tip taşındı, hepsi `com.photonpulse.candy.platform` altında; Fabric/NeoForge
gövdeleri `src/fabric/java` ve `src/neoforge/java` ağaçlarında:

| Fabric tipi | Platform sınıfı | Dosya |
|---|---|---|
| `PayloadTypeRegistry` | `AgKayit` | 16 |
| `ServerPlayNetworking` | `SunucuAg` | 13 |
| `ServerPlayConnectionEvents` | `SunucuBaglanti` | 7 |
| `ClientPlayNetworking` | `IstemciAg` | 5 |
| `ClientPlayConnectionEvents` | `IstemciBaglanti` | 4 |

Toplam 25 dosya değişti.

#### Sessiz uyumsuzluk taraması — `javap`, iki loader

Derleme hatası vermeyen fark, derleme hatasından pahalıdır: jar üretilir, çalışır
gibi görünür, protokol sessizce yanlıştır. Dört nokta tarandı.

| Kontrol | Fabric (`fabric-networking-api-v1-5.1.6`) | NeoForge (`neoforge-21.11.48`) | Sonuç |
|---|---|---|---|
| Payload kimliği | `CustomPacketPayload.Type<T>` (`class_8710$class_9154`) | `CustomPacketPayload.Type<T>` | **birebir** |
| Codec tipi | `StreamCodec<? super RegistryFriendlyByteBuf, T>` (`class_9139<? super class_9129, T>`) | `StreamCodec<? super RegistryFriendlyByteBuf, T>` | **birebir** |
| Alıcı hangi thread'de | oyun thread'i | `PayloadRegistrar` varsayılan olarak ana thread'e sarıyor | **aynı** |
| Bellek bağlantısında serileştirme | tek oyunculuda atlanabiliyor | **her zaman** serileştiriyor | **farksız** — 13 payload'ın hepsi yalnızca `int`/`double`/`boolean`/`String` taşıyan record, paylaşılan mutable nesne yok |

Candy'nin 13 payload'ı `StreamCodec<RegistryFriendlyByteBuf, T>` bildiriyor; iki
loader'ın `? super RegistryFriendlyByteBuf` sınırını da karşılıyor. **Payload
sınıflarına tek satır dokunulmadı** — ilk ölçümün öngörüsü doğru çıktı.

#### Taramada çıkan ÖNGÖRÜLMEYEN sessiz uyumsuzluk: `optional()`

NeoForge kayıtları varsayılan olarak **zorunlu kanal**: kanalı taşımayan karşı
taraf bağlantıdan **düşürülür**. Fabric bunu hiçbir zaman yapmaz. Candy ise
vanilla karşı tarafa tahammül edecek şekilde yazılmış — her `send` try/catch
içinde, istemcide `canSend` kapısı var.

`optional()` çağrılmasa NeoForge jar'ı vanilla istemciyi atardı, Fabric jar'ı
atmazken. Derleyici bunu göremez; test edilmezse kullanıcı bulur. `AgKayit`
içinde `olay.registrar("1").optional()` yazılmasının tek sebebi bu.

Yanındaki küçük fark: `registrar(String)` bir **ağ sürümü** istiyor (NeoForge
sources'tan doğrulandı — mod kimliği değil); Fabric payload tipleri için sürüm
pazarlığı yapmıyor. `"1"` sabitlendi. `optional()` ile birlikte sürümü farklı iki
Candy yine bağlanabiliyor, yalnızca eksik kanal kullanılmıyor.

#### Gizlenebilen fark: kayıt ZAMANLAMASI

Fabric'te kayıt statik registry üzerinden, çağrıldığı anda olur. NeoForge'da
yalnızca `RegisterPayloadHandlersEvent` içinde olabilir; o olay mod
kurucularından **sonra** bir kez ateşlenir. Candy'nin bütün `register()`
çağrıları `ModInitializer`/`ClientModInitializer` karşılığından, yani kurucu
aşamasından geliyor → `AgKayit` NeoForge tarafında kayıtları **kuyruğa** alıyor,
olay geldiğinde boşaltıyor. Bu farkı sarmalayıcı **gerçekten gizliyor**: çağrı
yerleri iki loader'da da aynı satırı yazıyor.

Kuyruk aynı zamanda **tekilleştiriyor**. Fabric aynı tipi ikinci kez kaydedince
atıyor ve Candy bunu her çağrı yerinde try/catch ile yutuyor (tek oyunculuda
sunucu ve istemci aynı JVM'de aynı tipi kaydediyor). NeoForge'da aynı atış olayın
**içinde** olurdu, yani o try/catch'lerin dışında. Kuyruk ID'ye göre
tekilleştirdiği için ikinci kayıt sessizce düşüyor; Candy'deki catch blokları
NeoForge'da hiç ateşlenmiyor.

#### GİZLENEMEYEN fark: C2S'te tip ve alıcı tek çağrıya girmek zorunda

`javap` ile ölçülen asimetri — `PayloadRegistrar`:

```
playToClient(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>)                      <- alıcısız VAR
playToClient(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>, IPayloadHandler<T>)
playToServer(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>, IPayloadHandler<T>)
                                                                                           <- alıcısız YOK
```

S2C tipi tek başına kaydedilebiliyor, **C2S kaydedilemiyor**: tip ve alıcı aynı
çağrıya girmek zorunda. Fabric'te bu ikisi ayrı sınıflarda — tip
`PayloadTypeRegistry`, alıcı `ServerPlayNetworking.registerGlobalReceiver`.

Uydurmak yerine **arayüz farkı kabul etti:** alıcı kaydı iki loader'da da
*yalnızca mod yüklenirken* geçerli. `SunucuAg`/`IstemciAg`'ın
`registerGlobalReceiver`'ı alıcıyı `AgKayit` kuyruğuna veriyor, tip kaydıyla
eşliyor (sıra önemsiz), olay içinde ikisi birlikte uygulanıyor.

- Fabric'te bu sınır **teknik olarak yok**; oraya da yazıldı ki iki loader aynı
  sözü versin, yoksa Fabric'te çalışan kod NeoForge'da sessizce ölürdü.
- İnit'ten sonra çağrılırsa `AgKayit` **hata logluyor** — sessizce yanlış
  çalışmıyor.
- Alıcısı olmayan C2S tipi boş alıcıyla kaydediliyor. Bu doğru davranış: o JVM
  payload'ı hiç almıyor (çok oyunculu istemci kendi C2S tipini yalnızca
  *gönderebilmek* için kaydeder).
- Candy'nin 13 alıcısının hepsi init zamanında, yani sınır pratikte bağlayıcı
  değil. Arayüzün değeri, artık doğruyu söylemesi.

#### Düşürülen parametre: `sender`

Fabric'in JOIN imzası `(handler, sender, server)`; ortadaki `sender` Fabric'e
özgü `PacketSender` ve NeoForge'da **karşılığı yok**. Candy'nin 7 JOIN çağrısının
hiçbiri onu okumuyor. Hep `null` geçen hayalet bir parametre taşımak yerine
arayüzden çıkarıldı — DISCONNECT'in zaten taşıdığı biçim.

İstemci tarafında `handler` da düşürüldü (lambda tek parametreli `client` oldu):
5 çağrının hiçbiri okumuyor **ve** NeoForge'da
`ClientPlayerNetworkEvent.LoggingOut` anında `Minecraft.getConnection()` null
olabiliyor, yani taşımak "bazen null" demekti.

Sunucu tarafında `handler` **korundu ve uydurma değil**: NeoForge olayından gelen
oyuncunun `ServerPlayer.connection` alanı aynı `ServerGamePacketListenerImpl`
nesnesi, yani çağrı yerlerindeki `handler.player` ve `handler.getPlayer()` iki
loader'da da aynı nesneyi okuyor. 1/4'teki kural burada da geçerli: gövdeler
değişmedi, yalnızca import satırı, alıcı adı ve `sender` parametresi.

#### Kalan, ÇALIŞMA ZAMANINDA doğrulanacak nokta

Fabric'in JOIN'i (`Join.onPlayReady`) oyun aşaması hazır olduğunda, NeoForge'un
`PlayerEvent.PlayerLoggedInEvent`'i oyuncu listesine eklendiğinde ateşleniyor —
yakın, ama aynı nokta değil. Candy'nin 7 JOIN dinleyicisinin hiçbiri paket
*göndermiyor* (hepsi kişilik/hafıza okuyup zamanlayıcı kuruyor), o yüzden fark
derleme ya da protokol düzeyinde sorun değil. Yine de bu **ölçülmemiş** bir
nokta: NeoForge jar üretilince ilk bakılacak yerlerden biri.

#### Ölçüm (2/4 sonrası)

| | 1/4 sonrası | 2/4 sonrası |
|---|---|---|
| NeoForge `compileJava` hata | 268 | **148** |
| Hatalı dosya | 57 | **41** |
| `net.fabricmc` import eden dosya | 57 | **41** |
| `net.fabricmc` import satırı | 113 | **72** |
| Kalan ayrı `net.fabricmc` tipi | 23 | **18** |

`src/main/java` içinde `PayloadTypeRegistry`, `ServerPlayNetworking`,
`ClientPlayNetworking`, `ServerPlayConnectionEvents`, `ClientPlayConnectionEvents`
referansı: **sıfır** (beşi de).

`src/fabric/java` ve `src/neoforge/java` ağaçlarından gelen hata: **sıfır** —
yani on platform sınıfının hepsi iki loader'da da derleniyor. Bu, şu NeoForge
API'lerinin çözüldüğünün derleyici kanıtı: `RegisterPayloadHandlersEvent`,
`PayloadRegistrar.optional()`, `playToClient` (iki aşırı yükleme de),
`playToServer`, `IPayloadHandler`, `IPayloadContext`,
`PacketDistributor.sendToPlayer`, `ClientPacketDistributor.sendToServer`,
`ICommonPacketListener.hasChannel`, `PlayerEvent.PlayerLoggedIn/OutEvent`,
`ClientPlayerNetworkEvent.LoggingIn/LoggingOut`.

Fabric: temizlenmiş `build/generated/stonecutter` ağacından kökte
`./gradlew build` **BUILD SUCCESSFUL**, iki jar da üretildi.

> **`@EventBusSubscriber` notu (yeniden kullanılacak):** bu NeoForge sürümünde
> anotasyonun `bus` parametresi **yok** (`javap`: yalnızca `value()` ve
> `modid()`). Olay hangi bus'a gideceğini kendi tipinden söylüyor —
> `IModBusEvent` uygulayanlar mod bus'ına, diğerleri oyun bus'ına. Bu yüzden
> `AgKayit` (mod bus olayı) ve `SunucuBaglanti` (oyun bus olayı) **aynı çıplak**
> `@EventBusSubscriber` ile çalışıyor.

> **`ServerPlayer.getServer()` YOK** (1.21.11, Mojang adları). Oyuncudan sunucuya
> gitmenin yolu `player.level().getServer()` — kaynağın başka yerinde zaten
> kullanılan biçim (`CandyBasarimlar.java`).

### Sıradaki adımlar (sırayla, her adımda Fabric yeşil)

1. **Uzun kuyruk** — `SunucuTick`'in yanına `Platform` (FabricLoader karşılığı) ve
   `@Environment` için `//? if fabric` direktifleri; 60 dosyanın import satırları.
2. ~~**Ağ katmanı**~~ — ✅ **BİTTİ** (2/4): `AgKayit`, `SunucuAg`, `IstemciAg`,
   `SunucuBaglanti`, `IstemciBaglanti`. 25 dosya. Ayrıntı ve `javap` kanıtları
   için yukarıdaki "Ağ katmanı (adım 2/4)" bölümü.
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
