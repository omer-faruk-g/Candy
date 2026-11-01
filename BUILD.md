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

Proje artık tek sürümlü değil. Kaynak `src/` altında paylaşılıyor; her Minecraft
sürümü `versions/<sürüm>/` altında bir Gradle düğümü.

| Dosya | İş |
|---|---|
| `settings.gradle` | Stonecutter eklentisi + düğüm listesi (`versions("1.21.7", "1.21.11")`) |
| `stonecutter.gradle` | Aktif düğüm (`stonecutter.active`) |
| `build.gradle` | MERKEZ betik — her düğüm için bir kez çalışır |
| `versions/<sürüm>/gradle.properties` | O sürümün mc/loader/fapi değerleri (`yarn_mappings` ölü) |
| `gradle.properties` | Sürümden bağımsız: mod_version, maven_group |

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
- **26.x — hâlâ yok, ama engel kalktı.** 26.x Mojang resmî mapping istiyordu; kaynak
  artık Mojang adlarını taşıyor, yani düğüm eklemek **sıradan sürüm işi** oldu
  (yeni `versions/<sürüm>/`, API farklarını kapat). Bu, ad göçü değil.

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

Şu an yalnızca **Fabric** — 1.21.7 ve 1.21.11.

| Loader | 1.21.7 | 1.21.11 |
|---|---|---|
| Fabric | ✅ jar üretiyor | ✅ jar üretiyor |
| NeoForge | ❌ yok | ❌ yok |
| Forge | ❌ yok | ❌ yok |

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
