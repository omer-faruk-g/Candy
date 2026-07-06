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
- **İnternet bağlantısı** (ilk build ~500 MB indirir: Gradle, Yarn, Fabric API, Minecraft assets)

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
candy/build/libs/candy-0.1.0-alpha.jar
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
   - `candy-0.1.0-alpha.jar`
4. Minecraft'ı Fabric profili ile başlat

---

## 🐛 Sorun Giderme

### `java: command not found`
Java 21 kurulu değil veya PATH'te değil. Temurin'i kur, yeniden dene.

### `Could not resolve fabric-loader`
İnternet bağlantısı sorunu veya Fabric Maven'a erişim yok. VPN varsa kapat, tekrar dene.

### `Yarn mapping version not found`
`gradle.properties` içindeki `yarn_mappings` değeri güncel değil.
- https://fabricmc.net/develop/ → 1.21.7 için son build numarasını al
- `gradle.properties`'te güncelle
- Tekrar build et

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
