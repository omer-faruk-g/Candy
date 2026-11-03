# 🔨 Candy'yi Kendi Bilgisayarında Derleme

İki yöntem: **GitHub Actions** (kolay, JAR otomatik gelir) veya **elle build** (10 dk).

---

## 🚀 Yöntem 1 — GitHub Actions (Önerilen)

Kod sende, build GitHub'da. Sonuç: hazır JAR.

### Adımlar

1. **Yeni bir repo aç**: github.com/new — mesela `candy-mod`
2. **Kodları push et**:
   ```bash
   cd candy
   git init
   git add .
   git commit -m "Candy v0.2"
   git branch -M main
   git remote add origin https://github.com/KULLANICI_ADIN/candy-mod.git
   git push -u origin main
   ```
3. **Actions çalışır** — 3-5 dakika içinde build biter
4. **JAR'ı indir**:
   - Repo → **Actions** sekmesi
   - En üstteki yeşil ✓ olan build'e tıkla
   - En altta **Artifacts** → `candy-jar` (indir)

### Release ile daha temiz

1. Repo → **Releases** → **Create a new release**
2. Tag: `v0.2`, Title: `Candy v0.2`
3. **Publish release**
4. Actions otomatik build eder ve JAR'ı release'e attach eder
5. Sonra release sayfasından direkt indirebilirsin

---

## 🖥️ Yöntem 2 — Elle Build (Windows / Linux / Mac)

### Ön gereksinimler

- **JDK 25** — https://adoptium.net/temurin/releases/ (Temurin 25). **Java 21 yetmiyor:**
  26.x düğümleri için gereken fabric-loom 1.19.3 JVM 25 bayt kodu taşıyor, yani Gradle
  daemon'ı JDK 25 ile koşmak zorunda — yalnızca 1.21.7 derleyecek olsan bile.
- **İnternet bağlantısı** (ilk build ~500 MB indirir: Gradle, Mojang mapping'leri, Fabric API,
  Minecraft assets)

> **Oyuncunun Java'sı değişmiyor.** Derleme JDK 25 istiyor, ama 1.21.x jar'ları hâlâ
> `release 21` bayt kodu üretiyor ve `"java": ">=21"` ilan ediyor. Yalnızca 26.x
> jar'ları Java 25 istiyor — zaten Minecraft 26.x'in kendisi de istiyor.

### Java'yı kontrol et

```bash
java --version
```

Çıktı `25.x.x` görmelisin. Değilse Temurin 25'i kur ve `JAVA_HOME`'u ona çevir:

```bash
export JAVA_HOME="/yol/jdk-25"
```

### Build komutları

**Linux / Mac:**
```bash
cd candy
chmod +x gradlew
./gradlew build
```

**Windows (PowerShell veya CMD):**
```powershell
cd candy
.\gradlew.bat build
```

### İlk build zaman alır

- **İlk build**: ~5-10 dakika (indirmeler)
- **Sonraki build**'ler: ~30 saniye (cache'lenir)

### Sonuç

JAR şurada olur:
```
candy/versions/<sürüm>/build/libs/candy-8.4.1+<sürüm>.jar
```

Kökteki `./gradlew build` beş düğümün hepsini derliyor, yani beş jar birden çıkıyor:

```
versions/1.21.7/build/libs/candy-8.4.1+1.21.7.jar
versions/1.21.11/build/libs/candy-8.4.1+1.21.11.jar
versions/26.1/build/libs/candy-8.4.1+26.1.jar
versions/26.2/build/libs/candy-8.4.1+26.2.jar
versions/26.3/build/libs/candy-8.4.1+26.3.jar
```

(mod sürüm numarası kökteki `gradle.properties` → `mod_version` ile değişir)

---

## 🎮 Kurulum

1. **Fabric Loader** kur: https://fabricmc.net/use/installer/
   - Minecraft sürümü: **1.21.7**, **1.21.11**, **26.1**, **26.2** veya **26.3**
   - Loader sürümü: en son (1.21.7 için en az 0.16.14, diğer hepsi için en az 0.20.1)
2. **Fabric API** indir: https://modrinth.com/mod/fabric-api
   - kurduğun Minecraft sürümüyle eşleşen son sürüm
3. `%APPDATA%\.minecraft\mods\` (Windows) veya `~/.minecraft/mods/` (Linux/Mac) klasörüne:
   - `fabric-api-XXX.jar`
   - `candy-8.4.1+<sürüm>.jar` — **sürümüne eşleşen jar**, yanlışı yüklenmez
4. Minecraft'ı Fabric profili ile başlat

---

## 🐛 Sorun Giderme

### `java: command not found`
JDK 25 kurulu değil veya PATH'te değil. Temurin 25'i kur, yeniden dene.

### `Could not resolve fabric-loader`
İnternet bağlantısı sorunu veya Fabric Maven'a erişim yok. VPN varsa kapat, tekrar dene.

### `Yarn mapping version not found`
**Artık çıkmaz.** Build Yarn kullanmıyor; Mojang'ın resmî mapping'lerine geçti
(`loom.officialMojangMappings()`). Bu hata görünüyorsa elinde göçten önceki bir
checkout var. `versions/<sürüm>/gradle.properties` içindeki `yarn_mappings` anahtarı
ölü config — doğrulama/karşılaştırma için duruyor, build okumuyor.

### `Unsupported class file major version`
veya `Dependency requires at least JVM runtime version 25. This build uses a Java 21 JVM.`

Gradle JDK 25 ile koşmuyor. `java --version` 25 göstermiyorsa `JAVA_HOME`'u Temurin
25'e çevir. Bu hata 26.x düğümleri eklendikten sonra **yalnızca 1.21.7 derlerken de**
çıkar, çünkü loom eklentisinin kendisi JVM 25 bayt kodu.

### Windows'ta `'gradlew' is not recognized`
`.\gradlew.bat build` kullan (backslash + .bat).

### Build başarılı ama JAR oyunda yüklenmez
- Fabric API yüklü mü kontrol et (mods/ içinde)
- jar'ın sürümü kurduğun Minecraft sürümüyle eşleşiyor mu (`candy-8.4.1+26.3.jar`
  yalnızca 26.3 hattında yüklenir)
- Fabric Loader sürümü yeterli mi — 26.x ve 1.21.11 **en az 0.20.1** istiyor; daha
  eski bir loader'da jar yüklenir ama mixin'ler sessizce reddedilir
- `.minecraft/logs/latest.log` içine bak (crash detayı burada)

---

## 🧪 Geliştirme Modu (opsiyonel)

Modu geliştirirken oyunun içinde test etmek için:

```bash
./gradlew runClient   # Minecraft client'ı başlatır, mod yüklü
./gradlew runServer   # Sunucu başlatır
```

İlk çalıştırmada EULA kabul etmen gerekebilir — `eula.txt` içine `eula=true` yaz.

---

## 📦 JAR Dağıtımı

Kendine iş görsün diye JAR'ı arkadaşlarınla paylaşabilirsin. GitHub Release en temiz yol:
1. Actions build ederse zaten release'e attach oluyor
2. Elle build ediyorsan JAR'ı manuel Release'e yükle

Modrinth'e yüklemek istersen: https://docs.modrinth.com/docs/tutorials/publishing/


---

## Çok sürümlü yapı (Stonecutter 0.9.8)

Proje artık tek sürümlü değil. Kaynak `src/` altında paylaşılıyor; her
Minecraft+loader bileşimi `versions/<düğüm>/` altında bir Gradle düğümü. **Dal
(branch) yok, yalnızca düğüm** — dal başına ayrı `src/` ağacı üretilirdi.

| Dosya | İş |
|---|---|
| `settings.gradle` | Stonecutter eklentisi + düğüm listesi (4 düğüm: 2 Fabric, 2 NeoForge) |
| `stonecutter.gradle` | Aktif düğüm (`stonecutter.active`) |
| `build.gradle` | MERKEZ betik — her düğüm için bir kez çalışır, iki loader'ı da çevirir |
| `versions/<düğüm>/gradle.properties` | O düğümün mc/loader/fapi veya neoforge değerleri (`yarn_mappings` ölü) |
| `gradle.properties` | Sürümden bağımsız: mod_version, maven_group |
| `src/main/java` | **Paylaşılan** 206 dosya — dört düğümün hepsi bunu derler |
| `src/fabric/java`, `src/neoforge/java` | Loader'a özel ince platform katmanı; düğüm yalnızca kendi loader'ının ağacını derler |

Düğümün loader'ı `versions/<düğüm>/gradle.properties` içindeki `loader` anahtarıyla
seçiliyor. `build.gradle` eklentileri `apply false` ile classpath'e alıp o anahtara
göre uyguluyor (`fabric-loom` ya da `net.neoforged.moddev`). İkinci bir build betiği
**bilinçli olarak yok**: `replacements` listesi tek yerde kalmak zorunda, iki kopya
arasındaki herhangi bir fark aktif düğüm değiştiğinde paylaşılan `src/` ağacını
yarım çevrilmiş bırakırdı.

`stonecutter.constants.match(loader, 'fabric', 'neoforge')` sayesinde kaynakta
`//? if fabric {` / `//? if neoforge {` de kullanılabilir — sürüm direktifleriyle
aynı kurallara tabi (javadoc koymayın).

Running Stonecutter 0.9.8
Warning: Stonecutter is used with Groovy buildscripts
------------------------------------------------------------------------------------
Stonecutter requires a more complex Gradle setup than typical Java projects.
While Groovy DSL works, Kotlin DSL offers significant advantages:
- The type-safe model prevents silent failures and Groovy's dynamic typing
  shenanigans that are hell to debug.
- Full IDE support with code completion, documentation and source navigation.
- Kotlin-exclusive operator functions for simpler configurations.
------------------------------------------------------------------------------------
For more information see:
- https://stonecutter.kikugie.dev/wiki/v2/faq#groovy-support
- https://docs.gradle.org/current/userguide/migrating_from_groovy_to_kotlin_dsl.html

To acknowledge and disable this warning add to the `gradle.properties`:
```
dev.kikugie.stonecutter.hard_mode=true
```
------------------------------------------------------------------------------------

> Configure project :1.21.11
Fabric Loom: 1.17.2

> Configure project :1.21.7
Fabric Loom: 1.17.2

> Task :1.21.7:compileJava UP-TO-DATE
> Task :1.21.7:processResources UP-TO-DATE
> Task :1.21.7:classes UP-TO-DATE
> Task :1.21.7:jar UP-TO-DATE
> Task :1.21.7:processIncludeJars UP-TO-DATE
> Task :1.21.7:remapJar UP-TO-DATE
> Task :1.21.7:sourcesJar UP-TO-DATE
> Task :1.21.7:remapSourcesJar UP-TO-DATE
> Task :1.21.7:assemble UP-TO-DATE
> Task :1.21.7:compileTestJava NO-SOURCE
> Task :1.21.7:processTestResources NO-SOURCE
> Task :1.21.7:testClasses UP-TO-DATE
> Task :1.21.7:test NO-SOURCE
> Task :1.21.7:validateAccessWidener NO-SOURCE
> Task :1.21.7:check UP-TO-DATE
> Task :1.21.7:build UP-TO-DATE

BUILD SUCCESSFUL in 7s
7 actionable tasks: 7 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.8.2/userguide/configuration_cache_enabling.html
Running Stonecutter 0.9.8
Warning: Stonecutter is used with Groovy buildscripts
------------------------------------------------------------------------------------
Stonecutter requires a more complex Gradle setup than typical Java projects.
While Groovy DSL works, Kotlin DSL offers significant advantages:
- The type-safe model prevents silent failures and Groovy's dynamic typing
  shenanigans that are hell to debug.
- Full IDE support with code completion, documentation and source navigation.
- Kotlin-exclusive operator functions for simpler configurations.
------------------------------------------------------------------------------------
For more information see:
- https://stonecutter.kikugie.dev/wiki/v2/faq#groovy-support
- https://docs.gradle.org/current/userguide/migrating_from_groovy_to_kotlin_dsl.html

To acknowledge and disable this warning add to the `gradle.properties`:
```
dev.kikugie.stonecutter.hard_mode=true
```
------------------------------------------------------------------------------------

> Configure project :1.21.11
Fabric Loom: 1.17.2

> Configure project :1.21.7
Fabric Loom: 1.17.2

> Task :1.21.7:compileJava UP-TO-DATE
> Task :1.21.11:stonecutterPrepare
> Task :1.21.7:processResources UP-TO-DATE
> Task :1.21.7:classes UP-TO-DATE
> Task :1.21.7:jar UP-TO-DATE
> Task :1.21.7:processIncludeJars UP-TO-DATE
> Task :1.21.7:remapJar UP-TO-DATE
> Task :1.21.7:sourcesJar UP-TO-DATE
> Task :1.21.7:remapSourcesJar UP-TO-DATE
> Task :1.21.7:assemble UP-TO-DATE
> Task :1.21.7:compileTestJava NO-SOURCE
> Task :1.21.7:processTestResources NO-SOURCE
> Task :1.21.7:testClasses UP-TO-DATE
> Task :1.21.7:test NO-SOURCE
> Task :1.21.7:validateAccessWidener NO-SOURCE
> Task :1.21.7:check UP-TO-DATE
> Task :1.21.7:build UP-TO-DATE
> Task :1.21.11:stonecutterGenerate

> Task :1.21.11:compileJava

> Task :1.21.11:compileJava FAILED

[Incubating] Problems report is available at: file:///C:/Users/HP/Downloads/candy-v0.2-buildable/candy/build/reports/problems/problems-report.html
10 actionable tasks: 3 executed, 7 up-to-date
Running Stonecutter 0.9.8
Warning: Stonecutter is used with Groovy buildscripts
------------------------------------------------------------------------------------
Stonecutter requires a more complex Gradle setup than typical Java projects.
While Groovy DSL works, Kotlin DSL offers significant advantages:
- The type-safe model prevents silent failures and Groovy's dynamic typing
  shenanigans that are hell to debug.
- Full IDE support with code completion, documentation and source navigation.
- Kotlin-exclusive operator functions for simpler configurations.
------------------------------------------------------------------------------------
For more information see:
- https://stonecutter.kikugie.dev/wiki/v2/faq#groovy-support
- https://docs.gradle.org/current/userguide/migrating_from_groovy_to_kotlin_dsl.html

To acknowledge and disable this warning add to the `gradle.properties`:
```
dev.kikugie.stonecutter.hard_mode=true
```
------------------------------------------------------------------------------------

> Configure project :1.21.11
Fabric Loom: 1.17.2

> Configure project :1.21.7
Fabric Loom: 1.17.2

> Task :1.21.11:stonecutterPrepare UP-TO-DATE
> Task :1.21.11:stonecutterMerge
> Task :1.21.11:stonecutterPrepareTest
> Task :1.21.11:stonecutterMergeTest NO-SOURCE
> Task :stonecutterSwitchTo1.21.11
> Task :Set active project to 1.21.11

BUILD SUCCESSFUL in 8s
4 actionable tasks: 3 executed, 1 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.8.2/userguide/configuration_cache_enabling.html

**Gradle 9.8.2 + JDK 25 gerekiyor.** Stonecutter 0.9 Gradle 9 istiyor; 26.x için
gereken fabric-loom 1.19.3 ise JVM 25 bayt kodu taşıyor (class major 69), yani Gradle
daemon'ı JDK 25 ile koşmak **zorunda** — 1.21.x düğümleri için bile. Dağıtılan 1.21.x
jar'ları hâlâ `release 21` üretiyor, oyuncunun Java'sı değişmiyor.

### Sürüm durumu

Beş Fabric düğümü, hepsi yeşil, hepsi jar üretiyor:

| Düğüm | MC dep | Loader alt sınırı | Fabric API | jar bayt kodu | Mixin compat |
|---|---|---|---|---|---|
| 1.21.7  | `~1.21.7`  | `>=0.16.14` | 0.129.0+1.21.7  | 21 | `JAVA_21` |
| 1.21.11 | `~1.21.11` | `>=0.20.1`  | 0.141.6+1.21.11 | 21 | `JAVA_21` |
| 26.1    | `~26.1`    | `>=0.20.1`  | 0.145.1+26.1    | 25 | `JAVA_25` |
| 26.2    | `~26.2`    | `>=0.20.1`  | 0.161.0+26.2    | 25 | `JAVA_25` |
| 26.3    | `~26.3`    | `>=0.20.1`  | 0.165.0+26.3    | 25 | `JAVA_25` |

Ayrıca iki `-neoforge` düğümü var, `candy.neoforge=false` ile **kapalı** (port bitmedi).

> **Ne çalıştırıldı — ve ne çalıştırılmadı.** Bu bölümdeki şema ölçümleri statik, ama
> doğrulama statik kalmadı:
>
> | ne | sonuç |
> |---|---|
> | `./gradlew clean build tumunuTopla` | EXIT=0, beş jar, her biri kendi şemasını taşıyor |
> | `:26.3:runTest` (istemci) | 12 doku atlası kuruldu, 88 Candy satırı, sıfır `InvalidInjectionException` |
> | `:26.3:runServer` | `SurumKapisi: 26.3 — test edilmiş sürüm`, 85 Candy satırı, mixin hatası yok |
> | `:1.21.11:runServer` | aynısı 1.21.11 için, 85 Candy satırı |
> | `:1.21.7:runClient` | gerileme kontrolü: 87 Candy satırı, 13 atlas, sıfır mixin hatası |
> | `DimensionType.DIRECT_CODEC` koşumu | beş boyut dosyası 26.3'te **ve** 1.21.11'de gerçek registry'ye karşı çözümlendi; eski ağaç 1.21.7'de çözümlendi |
> | 5 düğüm × 15 mixin hedefi = 75 | hepsi kendi jar'ında `javap` ile bulundu — ve bu koşum 1.21.11'de iki **sessiz** kopuk ortaya çıkardı |
>
> Çalıştırılmayan tek şey: **bir dünyaya girilmedi.** Sunucu Mojang EULA'sında duruyor;
> `eula=true` yazmak kullanıcının kararı, derlemenin kararı değil — bu yüzden yazılmadı.
> Yani mixin'lerin *uygulandığı* ve boyut dosyalarının *çözümlendiği* ölçüldü, ama
> efektlerin canlı bir dünyada nasıl göründüğü görülmedi.

#### 26.x sürüm manzarası (2026-11-03'te ölçüldü)

Mojang manifest'inden: 26.x hattında **beş release** var, 26.4 yalnızca snapshot.

| Sürüm | Çıkış | Java | MC class major | Düğüm |
|---|---|---|---|---|
| 26.1   | 2026-03-24 | 25 | 69 | `versions/26.1` |
| 26.1.1 | 2026-04-01 | 25 | 69 | `~26.1` kapsıyor |
| 26.1.2 | 2026-04-09 | 25 | 69 | `~26.1` kapsıyor |
| 26.2   | 2026-06-16 | 25 | 69 | `versions/26.2` |
| 26.3   | 2026-09-15 | 25 | 69 | `versions/26.3` |

Karşılaştırma: 1.21.7 ve 1.21.11 → Java 21, class major 65.

**26.1 hattı için ayrı düğüm gerekmiyor — ölçüldü.** Candy'nin dokunduğu 11 sınıfın
(8 mixin hedefi + `GuiGraphicsExtractor` + `EntityType` + `Blocks`) `javap -p` çıkışı
26.1, 26.1.1 ve 26.1.2'de **birebir aynı**, 2609 satırın tamamı. Yani `~26.1` dep'i üç
sürümü birlikte dürüstçe karşılıyor.

#### Mapping duvarı: loom'un iki eklenti kimliği

**Mojang 26.x hattında obfuscation'ı bıraktı.** Version JSON'undaki `downloads`
anahtarları:

```
1.21.11: [client, client_mappings, server, server_mappings]
26.3:    [client, server]
```

Jar içeriği sayıldı:

| | class sayısı | `net/minecraft/*` | obfuscated top-level |
|---|---|---|---|
| 1.21.11 | 10291 | 33 (%0.3) | 10201 |
| 26.3 | 11383 | 10737 (%94.3) | **0** |

Yani 26.x'te eşlenecek bir şey yok, ama `loom.officialMojangMappings()` yine de
`client_mappings` arıyor ve bulamayınca duruyordu:

```
> Failed to find official mojang mappings for 26.3
```

Çözüm **loom'un iki ayrı eklenti kimliği olduğu**: `fabric-loom` → `LoomGradlePlugin`
(remap yapar), `net.fabricmc.fabric-loom` → `LoomNoRemapGradlePlugin` (remap yapmaz).
İkincisi tam olarak deobfuscated jar durumu için var. `settings.gradle` ikisini de
`pluginManagement`'a alıyor, `build.gradle` düğüme göre birini uyguluyor. Fabric
intermediary de 1.21.11'de duruyor, yani ara katman da yok — 26.x düğümleri
`yarn_mappings` taşımıyor.

#### 26.x TEK BİR API DEĞİL — üç eşik gerekiyor

Beklenti tek bir `>=26` eşiğiydi. **Ölçüm bunu çürüttü**: tek eşikle 26.1 ve 26.2
düğümleri kırmızı kalıyor.

| Düğüm | Tek `>=26` eşiğiyle | Sebep (javap ile ölçüldü) |
|---|---|---|
| 26.1 | **19 hata** | `EntityType` sabitleri hâlâ sınıfın kendisinde (`EntityTypes` tutucusu yok), bedler renk renk ayrı blok (`Blocks.BLUE_BED` duruyor), `net.minecraft.client.gui.Hud` sınıfı henüz yok |
| 26.2 | **20 hata** | `setInvulnerable` tek, `ConstantValue` hâlâ `providers.number`'da ve `setRolls(NumberProvider)`, `swing` iki parametreli, `net.minecraft.util.Prediction` sınıfı yok, `SignText` hâlâ değiştirilebilir |

Son durum: `>=26` (12 kural), `>=26.2` (11 kural), `>=26.3` (6 kural + `TabelaUyum`).

Üstüne, HUD çizim noktasının **iki eşiği birbirinden bağımsız**: metot adı 26'da,
sınıf 26.2'de değişti.

| | sınıf + metot |
|---|---|
| 1.21.x | `Gui.render(GuiGraphics, DeltaTracker)` |
| 26.1 | `Gui.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` |
| 26.2+ | `Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` |

Parametreler üç kademede de aynı, bu yüzden 190 satırlık gövde **tek kopya** kalıyor;
`InGameHudMixin` içinde yalnızca iki anotasyon satırı ayrılıyor. `Gui` → `Hud` bir
metin kuralı **olamaz**, çünkü `Gui` aynı zamanda `GuiGraphics`'in alt dizgisi.

#### Derleyicinin GÖRMEDİĞİ hatalar: mixin hedefleri

`candy.mixins.json` içinde `"injectors": {"defaultRequire": 0}` var ve `method =` bir
**dizgi**. Yani yanlış bir hedef derleme hatası vermez, **sessizce hiç uygulanmaz**.
Bu yüzden 11 mixin'in hepsi beş düğümde tek tek `javap` ile ölçüldü. 26.x'e geçişte
dört hedef bozuktu ve hiçbiri derlemeyi kırmıyordu:

| Mixin | 1.21.x | 26.x |
|---|---|---|
| `InGameHudMixin` | `Gui.render` | `Gui`/`Hud.extractRenderState` |
| `CameraMixin` | `Camera.setup` (5 par.) | `Camera.update(DeltaTracker)` |
| `ChatHudMixin` | `addMessage(Component)` | `addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag)` |
| `LightmapTextureManagerMixin` | `LightTexture` | `LightmapRenderStateExtractor` |

İki hedef ise 26.x'te **değişmedi**, bu da ölçülmesi gereken bir şeydi:
`FogRenderer.setupFog` 26.x'te de bu adı taşıyor ve `DeathScreen.init()` parametresiz.

**Aynı ölçüm 1.21.11'de önceden duran iki sessiz kopuk ortaya çıkardı.** Bu 26.x
portunun getirdiği bir gerileme değil: `>=1.21.9 && <26` aralığında iki yanlış
`replacements` kuralı duruyordu.

| kural | 1.21.11 jar'ında gerçek (javap) |
|---|---|
| `setupFog` → `applyFog` | `applyFog` **yok**. Ad hiç değişmemiş, 1.21.11'de de `setupFog`. Değişen şey imza: `boolean thickFog` 1.21.9'da düştü. |
| `getGameInformation` → `getLeftText` | `getLeftText` **yok**, `getGameInformation` da yok (1.21.9'da kalktı). Kalan tek şekil: `renderLines(GuiGraphics, List<String>, boolean)`. |

`defaultRequire: 0` olduğu için ikisi de sessizce ölmüştü: 1.21.11 jar'ı derleniyor,
yükleniyor, log tertemiz — ama **ayna sisi ve F3 spoof hiç çalışmıyordu**. İki kural
kaldırıldı; iki mixin'e ölçülen gerçek hedeflerle birer `//? if >=1.21.9 && <26` dalı
eklendi. Bu, "derleniyor" ile "çalışıyor"un aynı şey olmadığının en keskin örneği —
ve tek geçerli kanıtın o sürümün jar'ındaki bayt kod olduğunun.

`DebugScreenOverlay.extractLines` ise **parametre sayısı değiştiriyor**: 26.2'de 3,
26.3'te 4 (`int genislik` eklendi). Mixin geri çağrı imzasının hedefle birebir aynı
olmasını istiyor, uymazsa **uygulama anında** `InvalidInjectionException` — derleyici
bunu da görmüyor. Bu yüzden üç kademeli kabuk var, spoof gövdesi tek yerde
(`candy$f3Duzelt`). Ayrıca `extractLines` yalnızca `extractRenderState`'in doldurduğu
listeyi **çiziyor**, o yüzden `@At("HEAD")` + yerinde değiştirme kullanılıyor;
`@At("TAIL")` çizimden sonraya düşerdi (disassembly ile ölçüldü).

#### Stonecutter'ın dokümante edilmemiş üç yapısal kuralı

Üçü de deneyle ölçüldü, hiçbiri belgelerde yok.

1. **`replacements` İKİ YÖNLÜ çalışıyor.** `replacements.string(kosul)` içindeki bir
   kural, koşulun doğru olduğu düğümde ileri, **yanlış olduğu düğümde TERS** koşuyor.
   `drawString` → `text` kuralı eklendiğinde 1.21.11'in üretilen ağacında
   `texture` → `drawStringure` ve `getContext` → `getCondrawString` oluştu, 21 hata.
   Sonuç: her kuralın **sonuç** metni kaynak ağaçta ya hiç bulunmamalı ya da yalnızca
   pasif dalın blok yorumu içinde bulunmalı. Kurallar alıcıya çapalanıyor
   (`ctx.drawString(` → `ctx.text(`), çünkü düz metin değiştirme alıcı tipini bilmiyor.
2. **`replacements` `main/resources`'a UĞRAMIYOR.** `candy.mixins.json` üretilen ağaca
   kopyalanıyor ama kurallar üzerinde koşmuyor — `compatibilityLevel` 26.3 düğümünde
   `JAVA_21` kalmıştı. Sürüme bağlı kaynak değerleri Gradle `processResources` +
   `expand` ile veriliyor (`fabric.mod.json` zaten o yoldan besleniyordu).
3. **Directive bloğunun İÇİNDE ama `/* */` DIŞINDA kalan yorumların `//` öneki
   siliniyor.** Geriye çıplak metin kalıyor ve geçersiz Java üretiyor — 26.2 ile
   26.3'te `unclosed character literal` (bir kesme işareti char literali sanıldı).
   Böyle yorumlar `//? if` satırının **üstüne** yazılmalı; `/* */` içindekiler sağlam
   kalıyor.

Buna bir de şu ekleniyor: **aktif düğüm `src/`'yi yerinde derliyor ve replacements
orada koşmuyor**, yani `src/` her zaman aktif sürümün adlarını taşıyor (aktif düğüm
1.21.7 → `<26` dalları yorumsuz, `>=26` dalları `/* */` içinde). Javadoc **asla**
`//? if` bloğunun içine girmez: içinde `*/` var ve pasif dalın blok yorumunu erken
kapatır.

#### Derleyicinin görmediği ikinci katman: veri (JSON)

Burada **iki** ayrı sorun vardı ve ikisini de derleme görmez; JSON derlenmiyor.
Kaynak belge değil: her sürümün **kendi jar'ındaki** vanilla
`data/minecraft/dimension_type/*.json` dosyaları okundu.

**1. 26.x bir anahtarı zorunlu kıldı.** 26.3'te `has_ender_dragon_fight` `fieldOf`
(zorunlu), 1.21.11 kodekinde ise hiç yok — ve Candy'nin beş dosyasının hiçbirinde
yoktu. Yani 26.x'te çözümleme hata verir, on bir özel boyut **hiç yüklenmezdi**.

**2. Şema 1.21.9'da kırılmış ve Candy bunu fark etmemiş.** Sekiz anahtar (`ultrawarm`,
`natural`, `piglin_safe`, `bed_works`, `respawn_anchor_works`, `has_raids`, `effects`,
`fixed_time`) 1.21.11 kodekinde de **yok**. Yerine `attributes`
(`EnvironmentAttributeMap`), `skybox`, `cardinal_light`, `has_fixed_time` +
`timelines` geldi; `monster_spawn_light_level` ve `monster_spawn_block_light_limit`
kaldı (ikisi de hâlâ **zorunlu**). DFU bilinmeyen anahtarı sessizce atıyor, yani
dosyalar **hata vermeden** yükleniyor ve sekiz anahtarın tamamı gözardı ediliyordu.
Ölçülen default'lar bunun ne demek olduğunu söylüyor: `visual/sky_color` **0x000000**,
`visual/fog_color` **0x000000**, `gameplay/sky_light_level` **15.0**. Yani 1.21.11'de
on bir özel boyut **siyah gökyüzü, siyah sis ve sürekli gündüz** ile oynanıyordu.
Bu bir gerileme değil, 1.21.11'den beri öyleydi — ve burada düzeltildi.

Çeviri tahminle yapılmadı: vanilla'nın **kendi** `the_nether` / `the_end` /
`overworld` dosyaları iki sürümde yan yana okundu, yani aynı eski değerlerin resmî
karşılığı doğrudan ölçüldü.

| eski anahtar | yeni karşılığı (ölçüldü) |
|---|---|
| `effects: minecraft:the_end` | `skybox: "end"` + `visual/sky_color`, `visual/fog_color`, `visual/sky_light_factor` |
| `effects: minecraft:overworld` | `timelines: "#minecraft:in_overworld"` — `day` zaman çizgisi bütün gökyüzü attribute'larını zaten sürüyor |
| `fixed_time: 18000` | `has_fixed_time: true` + `day` çizgisinin 13670–22330 arası keyframe değerleri (donuk gece) |
| `bed_works: false` | `gameplay/bed_rule` → `can_sleep`/`can_set_spawn` **never** |
| `bed_works: true` | ölçülen default (`BedRule.CAN_SLEEP_WHEN_DARK`) — yazılmıyor |
| `has_raids: false` | `gameplay/can_start_raid: false` |
| `respawn_anchor_works` | `gameplay/respawn_anchor_works` |
| `natural: true` | `gameplay/nether_portal_spawns_piglin: true` |
| `piglin_safe: false`, `ultrawarm: false` | ölçülen default — yazılmıyor |
| `monster_spawn_*` | aynı kaldı, hâlâ zorunlu |

İki ayrıntı özellikle önemli:

- **`bed_rule`'un alan adı 26.x'te değişti**: 1.21.11 `explodes`, 26.3
  `destroy_on_use` + `destroy_on_leave` (`javap` ile iki jar'da ayrı ayrı ölçüldü,
  ikisi de opsiyonel). Dosyalar **ikisini birden** yazıyor; her sürüm tanımadığını
  atar.
- **`attributes` anahtarı registry'den çözülüyor**, yani orada bilinmeyen bir id
  sessizce atılmaz — çözümleme hatası verir. Kullanılan bütün attribute id'leri
  26.3'te **ve** 1.21.11'de var (1.21.11'in 45 id'si 26.3'ün 51'inin alt kümesi,
  `EnvironmentAttributes` bayt kodundan çıkarıldı). Yalnızca 26.3'te olan
  `visual/ambient_light_color` **kullanılmadı**.

Sonuç: tek dosya iki şemayı birden karşılayamaz, bu yüzden `dimension_type` artık
**sürüme göre ayrı ağaçtan** geliyor — `src/boyut-eski/resources` (<1.21.9) ve
`src/boyut-yeni/resources` (>=1.21.9). `replacements` `resources`'a uğramıyor ve
`processResources`/`expand` tek tek değer için; burada bütün dosya değişiyor, o yüzden
`src/fabric-kayit-*` ile aynı yol kullanıldı: `srcDir` seçimi.

**Ayna'nın gökyüzü senkronu kaybolmadı, yeri değişti.** 26.x'te `Level.setDayTime`
yok ve zaman seviye başına değil **saat başına** tutuluyor
(`ServerClockManager.setTotalTicks(Holder<WorldClock>, long)`, ölçüldü), yani Ayna'ya
tek tek zaman yazılamıyor. Karşılığı `dimension_type`'ın `default_clock` alanı:
`ayna` ve `kor` artık `minecraft:overworld` saatini paylaşıyor. Anahtar olmasa
`getDefaultClockTime()` **0** dönerdi (ölçüldü: `getClockTimeTicks` =
`opt.map(..).orElse(0L)`) ve gökyüzü şafakta donar kalırdı. Sürekli izleme, eski iki
anlık `setDayTime` senkronundan daha iyi.

#### 26.x'te kapanan tek özellik

`DonanimGonder` **GPU ve CPU** dizgilerini toplamıyor:

- `GpuDevice` `com.mojang.renderpearl.api.device`'a taşındı ve renderer adı veren bir
  metodu yok — `getRenderer()` kaldırıldı (ölçüldü: 0 eşleşme).
- `com.mojang.blaze3d.platform.GLX` **jar'dan tamamen kalktı** (Mojang SDL'e geçti),
  `_getCpuInfo()`'nun yerine geçen bir API yok.

Kaybolan davranış: 26.x'te "Ben PC'de %s var." diyaloğu yalnızca işletim sistemini ve
JVM'e ayrılan belleği sayıyor, ekran kartı ile işlemci adını **saymıyor**. Uydurma bir
kaynak kullanılmadı. (Veri yine hiçbir yere gitmiyor — client kendi sunucusuna
yolluyor, dışarıya tek bir istek yok.)

`MeYuz` (bloklardaki yüz) 26.x'te de kapalı — 1.21.9'da zaten kapanmıştı, sebebi
aşağıdaki "1.21.9+ için bilinen davranış farkı".

### Mapping: Mojang resmî adları (Yarn değil)

Build **her düğümde** `loom.officialMojangMappings()` kullanıyor. Yarn hiçbir yerde
kullanılmıyor. Gerekçe ve göçün nasıl yapıldığı: [MULTILOADER.md](MULTILOADER.md).

Pratikte bilmen gerekenler:

- Kaynaktaki MC adları Mojang adlarıdır: `Level`, `ServerPlayer`, `Gui`,
  `BuiltInRegistries`, `Entity.level()`, `Entity.position()`.
- Jar hâlâ **intermediary**'ye remap ediliyor (Fabric'in çalışma zamanı bunu ister);
  `remapJar` mixin anotasyonlarını da çeviriyor, refmap dosyası yok ve gerekmiyor.
- Göç `./gradlew :<düğüm>:migrateMappings --mappings "net.minecraft:mappings:<sürüm>"`
  ile yapıldı — Loom'un kendi task'ı, Mercury (Eclipse JDT) tabanlı, tip duyarlı.
  Tek bilinen sınırı: **kütüphane metodunu ezen bildirimleri** yeniden adlandırmıyor
  (gövdedeki `super.x()` çağrısını çeviriyor ama `public void x(` imzasını bırakıyor).
  Göçte bu 10 bildirimi elle kapattı; `@Override` taşıdıkları için derleyici hepsini
  yakaladı.

### Sürüm ayrımı iki mekanizmayla yapılıyor

**1. `build.gradle` → `replacements`** — düz ad değişiklikleri için.

Bu düz metin değişimidir, **alıcı tipini bilmez**. Bir ad iki tarafta da geçerliyse
çıplak desen yanlış sınıfları da vurur. Bu yüzden belirsiz adlar alıcıya çapalanır
(`key.location()`, `.dimension().location()`), çıplak bırakılmaz; `ResourceLocation`
gibi Candy'de tek anlamı olan adlar çıplak kalır.

Kural listesi Mojang adlarına göre yeniden yazıldı ve **ölçümle** çıkarıldı: iki
düğümün `migrateMappings` ile göçürülmüş ağaçları birebir karşılaştırıldı, listeye
yalnızca gerçekten farklılaşan semboller girdi. Yarn'dan gelen `Entity.getWorld` /
`getPos` kuralları **silindi** — onlar Yarn'ın kendi yeniden adlandırmasıydı, Mojang
iki sürümde de `level()` / `position()` diyor. Karşılığında Yarn'ın sakladığı iki
gerçek yeniden adlandırma eklendi: `ResourceLocation` → `Identifier` ve
`PlayerRenderState` → `AvatarRenderState`.

Yönü ters çevirmek çözüm **değil**: aktif düğüm `src/`'yi yerinde derler, replacement
orada hiç koşmaz. Yani `src/` her zaman **aktif sürümün** adlarını taşımak zorunda.

Yeni bir entity değişkeni eklenirse kuralı da eklemek gerekir; eksikse 1.21.11
derlemesi o satırda yüksek sesle patlar, sessiz kalmaz.

**2. `//? if` directive** — imza/mimari değiştiğinde.

```java
//? if >=1.21.9 {
/*yeniKod();
*/
//?}
//? if <1.21.9 {
eskiKod();
//?}
```

Pasif dal `/* */` içine alınır, Stonecutter aktif sürüme göre yorumları yer değiştirir.
`src/` aktif düğümün (şu an 1.21.7) dalı açık olacak şekilde yazılır.
**Dikkat:** pasif dalın içine javadoc (`*/` içeren) koymayın — blok yorumu erken kapanır.

Directive kullanan yerler: `mixin/CommandManagerMixin.java` (izin modeli),
`client/CandyDoppelRenderer.java`, `client/TrueRealCandyRenderer.java` (render imzası
+ zırh katmanları + skin), `client/MeYuz.java` (atlas GPU yolu).

### 1.21.9+ için bilinen davranış farkı

**ME yüz boyama (`client/MeYuz.java`) 1.21.9+ sürümlerinde KAPALI.** Özellik blok
atlasındaki sprite'ın mipmap'lerini değiştirip atlas içindeki x/y ofsetine geri
yüklüyordu. 1.21.9'da bu yol yeniden yazıldı: `SpriteContents.upload` artık sprite'ı
hedef dokunun (0,0) köşesine yazıyor, ofset uygulamıyor — `Sprite.upload` da ofsetsiz
delege ediyor (bytecode'dan doğrulandı). Doğrulanmamış bir blit paylaşılan blok
atlasını bozacağı için özellik devre dışı bırakıldı, tahminle port edilmedi.
1.21.7'de aynen çalışmaya devam ediyor.

### Doğrulanmamış varsayım

`GameProfile` (authlib 7: record → `name()`, `properties()`) ve diğer tüm `>=1.21.9`
kuralları **yalnızca 1.21.11'de doğrulanıldı**. 1.21.8/1.21.9/1.21.10 düğümü eklenirse
bu eşiklerin her birini o sürümün jar'ına karşı yeniden kontrol et.

26.x eşikleri (`>=26`, `>=26.2`, `>=26.3`) böyle bir varsayım **taşımıyor**: üçü de
26.1, 26.2 ve 26.3 jar'larına karşı ayrı ayrı ölçüldü — tek bir `>=26` eşiği
denendiğinde iki düğüm kırmızı kaldığı için ayrıldılar.

Mixin'lerin uygulanması ve boyut dosyalarının çözümlenmesi de artık varsayım değil:
ikisi de çalıştırılarak ölçüldü (yukarıdaki "Ne çalıştırıldı" tablosu). `>=1.21.9`
eşiğinin mixin **adlarına** dair taşıdığı iki varsayım ise yanlış çıktı ve kaldırıldı
— bkz. "Derleyicinin GÖRMEDİĞİ hatalar: mixin hedefleri".

## Loader desteği

Şu an jar üreten **yalnızca Fabric** — beş sürümde.

| Sürüm | Fabric | NeoForge | Forge |
|---|---|---|---|
| 1.21.7  | ✅ jar üretiyor | 🚧 düğüm var, jar yok | ❌ yok |
| 1.21.11 | ✅ jar üretiyor | 🚧 düğüm var, jar yok | ❌ yok |
| 26.1    | ✅ jar üretiyor ¹ | ❌ düğüm yok | ❌ yok |
| 26.2    | ✅ jar üretiyor ¹ | ❌ düğüm yok | ❌ yok |
| 26.3    | ✅ jar üretiyor ¹ | ❌ düğüm yok | ❌ yok |

¹ 26.x jar'ları derleniyor, 26.3 istemcisi ve sunucusu açıldı, on bir mixin'in tamamı
uygulandı ve beş boyut dosyası gerçek kodekle çözümlendi. Girilmemiş olan tek şey canlı
bir dünya — bkz. yukarıdaki "Sürüm durumu".

NeoForge düğümleri (`versions/1.21.7-neoforge`, `versions/1.21.11-neoforge`)
**varsayılan olarak kapalı** — `gradle.properties` içindeki `candy.neoforge=false`.
Kökteki `./gradlew build` bütün düğümleri derlediği için, derlenmeyen bir düğüm açık
bırakılsa build ve CI kırmızı olurdu. Üzerinde çalışmak için:

```bash
./gradlew -Pcandy.neoforge=true :1.21.7-neoforge:compileJava
```

Açıldığında Minecraft+NeoForge classpath'i çözülüyor, ama kaynak **derlenmiyor**:
`src/main/java` içindeki 101 dosya hâlâ `net.fabricmc.*` import ediyor. Platform
katmanı (`src/fabric/java`, `src/neoforge/java`) kuruldu ama henüz yalnızca sunucu
tick kancasını kapsıyor. Envanter ve sıradaki adımlar: [MULTILOADER.md](MULTILOADER.md).

NeoForge portu bir kez denendi ve engele çarptı; **o engelin yarısı artık kalktı:**

- **ModDevGradle/NeoGradle** — NeoForge Mojang resmî adlarına derleniyor, Candy'nin
  kaynağı o zaman Yarn adlarını taşıyordu (143 dosya, 156 ayrı sınıf adı). **Bu neden
  ortadan kalktı:** kaynak Mojang adlarına göçtü. Yol açık ama **henüz denenmedi** —
  tabloya ✅ yazmak için NeoForge'un gerçekten jar üretmesi gerekiyor.
- **Architectury Loom** (Yarn adlarını koruyan tek yoldu) — kurulum çalıştı, sonra
  Minecraft'ı Yarn'a remap ederken upstream çakışmasında durdu
  (`InGameHud.renderHotbar`, 1.21.7 ve 1.21.11'de aynı). **Artık ilgisiz:** Yarn'ı
  korumaya ihtiyaç yok.

Ayrıntı, doğrulama yöntemi ve sıradaki adımlar: [MULTILOADER.md](MULTILOADER.md).
