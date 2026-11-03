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

- **Java 21+** — https://adoptium.net/temurin/releases/ (Temurin 21)
- **İnternet bağlantısı** (ilk build ~500 MB indirir: Gradle, Mojang mapping'leri, Fabric API,
  Minecraft assets)

### Java'yı kontrol et

```bash
java --version
```

Çıktı `21.x.x` görmelisin. Değilse Temurin 21'i kur.

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
candy/versions/1.21.7/build/libs/candy-8.4.0+1.21.7.jar
(sürüm numarası gradle.properties -> mod_version ile değişir)
```

---

## 🎮 Kurulum

1. **Fabric Loader** kur: https://fabricmc.net/use/installer/
   - Minecraft sürümü: **1.21.7**
   - Loader sürümü: en son
2. **Fabric API** indir: https://modrinth.com/mod/fabric-api
   - 1.21.7 uyumlu son sürüm
3. `%APPDATA%\.minecraft\mods\` (Windows) veya `~/.minecraft/mods/` (Linux/Mac) klasörüne:
   - `fabric-api-XXX.jar`
   - `candy-8.3.0.jar`
4. Minecraft'ı Fabric profili ile başlat

---

## 🐛 Sorun Giderme

### `java: command not found`
Java 21 kurulu değil veya PATH'te değil. Temurin'i kur, yeniden dene.

### `Could not resolve fabric-loader`
İnternet bağlantısı sorunu veya Fabric Maven'a erişim yok. VPN varsa kapat, tekrar dene.

### `Yarn mapping version not found`
**Artık çıkmaz.** Build Yarn kullanmıyor; Mojang'ın resmî mapping'lerine geçti
(`loom.officialMojangMappings()`). Bu hata görünüyorsa elinde göçten önceki bir
checkout var. `versions/<sürüm>/gradle.properties` içindeki `yarn_mappings` anahtarı
ölü config — doğrulama/karşılaştırma için duruyor, build okumuyor.

### `Unsupported class file major version`
Java 21+ olmalı. Java 17 veya altını kullanıyorsan güncelle.

### Windows'ta `'gradlew' is not recognized`
`.\gradlew.bat build` kullan (backslash + .bat).

### Build başarılı ama JAR oyunda yüklenmez
- Fabric API yüklü mü kontrol et (mods/ içinde)
- Minecraft sürümü 1.21.7 mi
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
| `src/main/java` | **Paylaşılan** 208 dosya — dört düğümün hepsi bunu derler |
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

**Gradle 9.8.2 + Java 21 gerekiyor.** Stonecutter 0.9 Gradle 9 istiyor; loom bu yüzden
1.17.2'de sabit — loom 1.18+ JVM 25 istiyor, bu makinede Java 21 var.

### Sürüm durumu

- **1.21.7** — yeşil, jar üretiyor.
- **1.21.11** — yeşil, jar üretiyor.
- **26.x — düğüm YOK. Mapping engeli kalktı, yerine JDK 25 engeli çıktı.** Ölçüm
  aşağıda; kısası: Fabric tarafında eksik yok, engel tamamen araç zincirinde.

#### 26.x — ölçülmüş durum (2026-11-02)

Mojang beş 26.x sürümü yayınladı. Fabric Loader ve Fabric API **hepsi için hazır**:

| Sürüm | Çıkış | Fabric Loader | Fabric API | Java | MC class major | Derlenebilir mi |
|---|---|---|---|---|---|---|
| 26.1   | 2026-03-24 | 0.20.1 | 0.145.1+26.1   | 25 | 69 | ❌ |
| 26.1.1 | 2026-04-01 | 0.20.1 | 0.145.4+26.1.1 | 25 | 69 | ❌ |
| 26.1.2 | 2026-04-09 | 0.20.1 | 0.155.3+26.1.2 | 25 | 69 | ❌ |
| 26.2   | 2026-06-16 | 0.20.1 | 0.161.0+26.2   | 25 | 69 | ❌ |
| 26.3   | 2026-09-15 | 0.20.1 | 0.165.0+26.3   | 25 | 69 | ❌ |

Karşılaştırma: 1.21.7 ve 1.21.11 → Java 21, class major 65.

**Engel: 26.x hattının tamamı Java 25 istiyor.** Bu makinede yalnızca JDK 21 var, ve
görev gereği Java/loom yükseltmesi yapılmadı. Üç bağımsız ölçüm:

**1. Loom zaten yapılandırma aşamasında reddediyor.** Geçici bir `versions/26.3`
düğümü eklenip denendi (commit edilmedi):

```
> Failed to setup Minecraft, java.lang.IllegalStateException:
  Minecraft 26.3 requires Java 25 but Gradle is using 21
```

Kaynağa hiç dokunmadan, `compileJava`'ya gelmeden duruyor.

**2. javac 21, MC 26.x bayt kodunu okuyamıyor.** Gerçek 26.3 class dosyaları
`client.jar`'dan çekilip bu makinenin javac'ına verildi:

```
bad class file: .../net/minecraft/world/entity/npc/Npc.class
  class file has wrong version 69.0, should be 65.0
```

`--release 21` ile ve onsuz aynı. Bu duvar mapping veya API farkı değil, JDK'nın
kendisi.

**3. 26.x'i destekleyen loom hattı JVM 25 bayt kodu.** Loom jar'larının class major
sürümü: 1.17.2 → 65, 1.17.21 → 65, **1.18.1 / 1.18.4 / 1.19.3 → 69**. Yani loom 1.18+
JVM 21'de koşan bir Gradle daemon'ına **yüklenemez bile**.

#### Beklenmeyen bulgu: 26.x hiç mapping yayınlamıyor

Mojang version JSON'unda `downloads` anahtarları:

```
1.21.11: [client, client_mappings, server, server_mappings]
26.3:    [client, server]
```

Sebebi: **Mojang 26.x hattında obfuscation'ı bıraktı.** Jar içeriği sayıldı:

| | class sayısı | `net/minecraft/*` | obfuscated top-level |
|---|---|---|---|
| 1.21.11 | 10291 | 33 (%0.3) | 10201 |
| 26.3 | 11383 | 10737 (%94.3) | **0** |

İki sonucu var:

- `loom.officialMojangMappings()` 26.x'te indirecek bir şey bulamaz — `client_mappings`
  yok. loom 1.18+ tam olarak bu durum için var.
- **Ad göçü 26.x için gerçekten bitmiş.** 26.3'ün jar'ı `net/minecraft/world/entity/npc/Npc`
  gibi gerçek Mojang adlarını taşıyor; Candy'nin kaynağı göçten sonra zaten tam olarak
  bu adları kullanıyor. 26.x için mapping katmanı **hiç gerekmiyor**.

#### Açmak için gereken

İkisi birlikte, biri yetmez:

1. **JDK 25** — Gradle daemon'ının kendisi onunla koşmalı.
2. **fabric-loom 1.18+** — `settings.gradle` içindeki `pluginManagement`.

`settings.gradle` bir kez değerlendirildiği için **düğüm başına ayrı loom sürümü
mümkün değil**: 1.18+'a geçmek 1.21.7 ve 1.21.11 düğümlerini de o loom'a taşır, ikisinin
yeniden doğrulanması gerekir. Gradle toolchain ile kaçış yolu yok — yalnızca
`compileJava`'yı JDK 25'e vermek yetmiyor, daemon'ın kendisi loom 1.18+ yükleyebilmek
için JVM 25 olmak zorunda. Toolchain auto-provisioning da kurulu değil (foojay
resolver yok).

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

`GameProfile` (authlib 7: record → `name()`, `properties()`) ve diğer tüm kurallar
`>=1.21.9` eşiğine bağlı, ama **yalnızca 1.21.11'de doğrulandı**. 1.21.8/1.21.9/1.21.10
düğümü eklenirse bu eşiklerin her birini o sürümün jar'ına karşı yeniden kontrol et.

## Loader desteği

Fabric ve **NeoForge**, iki MC sürümünde de jar üretiyor ve istemci açılışı
ölçüldü.

| Loader | 1.21.7 | 1.21.11 |
|---|---|---|
| Fabric | ✅ jar + açılış | ✅ jar + açılış |
| NeoForge | ✅ jar + açılış | ✅ jar + açılış |
| Forge | ❌ yok | ❌ yok |

NeoForge düğümleri artık varsayılan açık (`candy.neoforge=true`). Kapatmak için
`gradle.properties` içinde `false` yap ya da `-Pcandy.neoforge=false` geç.

Jar adları çakışmasın diye NeoForge jar'ı `-neoforge` eki taşıyor; Fabric adı
değişmedi:

```
candy-9.0.0+1.21.11.jar            <- Fabric
candy-9.0.0+1.21.11-neoforge.jar   <- NeoForge
```

Her jar yalnızca kendi loader'ının metadata'sını taşır (`fabric.mod.json` /
`META-INF/neoforge.mods.toml`), ikisi birden değil.

### Ölçülen açılış sonuçları

Dört düğümün hepsi `runClient` ile ana menüye kadar açıldı, mixin uygulaması
`-Dmixin.debug.verbose=true` ile sayıldı:

| Düğüm | Sonuç | Mixin | Hata |
|---|---|---|---|
| fabric 1.21.7 | ana menü | 10/11 | 0 |
| fabric 1.21.11 | ana menü | 10/11 | 0 |
| neoforge 1.21.7 | ana menü | 11/11 | 0 |
| neoforge 1.21.11 | ana menü | 11/11 | 0 |

Fabric'teki 10/11 eksiklik değil: `CommandManagerMixin`'in hedefi
`net.minecraft.commands.Commands` ana menüde hiç yüklenmiyor, o yüzden bu test
onu kapsamıyor. Doğrulamak için dünyaya girmek gerekir.

**Açılış testi oynanabilirlik testi değil.** 1.21.11 `SurumKapisi` tarafından
hâlâ "test edilmemiş" sayılıyor. Ayrıntı ve bilinen kusurlar:
[MULTILOADER.md](MULTILOADER.md).

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
