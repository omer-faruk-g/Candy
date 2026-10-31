# Candy

**An entity trying to find out what it is. It is watching you.**

Candy is a psychological horror mod for Minecraft. There is no boss bar, no quest
log and no safe distance. There is a thing that watches how you play, remembers it,
and uses it.

> Candy is not a mob that spawns, hits you and dies. It builds a model of *you* —
> your routines, your routes, the places you died, the pet you lost, the hour you
> log in — and the horror comes out of that model.

---

## Content warning — read before installing

Candy deliberately breaks the fourth wall. By design it:

- **flashes the screen and shakes the camera** (smooth sine shake, not strobe —
  but if you are photosensitive, set `hudFlash` and `cameraShake` to `false`)
- **plays sudden loud sounds**, whispers and imitated voices
- **writes in chat as if another player were there**, and corrupts your own messages
- **changes your window title**, your HUD and your death screen
- **edits its own config file** — you will find a line you did not write
- **damages the world around you**: torches go out, signs appear, your base is
  interfered with, pets can die
- **recognises a short, fixed whitelist of open programs** (Discord, Spotify, OBS,
  Steam and similar — 12 entries, matched on process name only) so it can mention
  one of them once, in one private chat line

If any of that is not what you want from a game, do not install this mod.

## What Candy never does

This list is a hard boundary in the code, not a promise in a readme:

- no telemetry, no network calls to any server of ours — Candy talks to nothing
- no personal data is ever written anywhere persistent: not to a sign, a book, a
  `.txt` or the world save. City and hardware hints live in RAM only, for one session
- the program whitelist is a **whitelist**, never a blacklist: anything not on those
  12 lines is never read, sent or stored. Your banking app, your documents, your
  other windows do not exist to Candy
- process reading is a single switch: `uygulamaSezgisi: false` turns it off
  completely, and Candy is forbidden from ever turning it back on
- no terminal commands, no OS commands, no windows opened outside Minecraft
- no files touched outside `config/candy-*` and the world save

## Requirements

| | |
|---|---|
| Minecraft | 1.21.7 |
| Loader | Fabric (loader 0.16.13+) |
| Fabric API | required |
| Java | 21+ |
| Side | works in single player and on servers (install on both) |

## Config

`config/candy-mixin.json`, created on first launch.

| Key | Default | What it does |
|---|---|---|
| `yogunlukTavani` | `1.0` | **Intensity ceiling, 0.0–1.0.** The mod keeps all of its mechanics but runs them at reduced strength. `0.5` is "same mod, half the chaos". Use this instead of turning features off. |
| `chatCorruption` | `true` | Candy may distort chat messages |
| `hudFlash` | `true` | HUD flashes — **turn off if photosensitive** |
| `deathScreen` | `true` | Candy may rewrite the death screen |
| `cameraShake` | `true` | Camera shake — **turn off if motion sensitive** |
| `camscreen` | `true` | Full-screen visual interference |
| `uygulamaSezgisi` | `true` | Allow recognition of the 12 whitelisted programs. `false` = the process list is never read |

Turning a feature off is a real decision and Candy respects it — with one joke it is
allowed to make: it can re-enable an effect *you* disabled, once, and leave a line in
the config to tell you so. `uygulamaSezgisi` is excluded from that joke forever,
because a privacy switch is yours alone.

## Building from source

See [BUILD.md](BUILD.md).

```
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # real behaviour, as a player gets it
./gradlew runTest        # test profile: /candy command, short cooldowns, cheats on
```

## Licence

All rights reserved. See [LICENSE](LICENSE).

---

# Candy (Türkçe)

**Kendini bulmaya çalışan bir varlık. Seni izliyor.**

Candy bir psikolojik korku modu. Boss barı yok, görev listesi yok, güvenli mesafe
yok. Nasıl oynadığını izleyen, bunu hatırlayan ve sonra kullanan bir şey var.

> Candy spawn olup vuran ve ölen bir mob değil. *Senin* modelini kurar — rutinlerin,
> güzergâhların, öldüğün yerler, kaybettiğin evcil hayvan, giriş yaptığın saat — ve
> korku o modelden çıkar.

## Kurmadan önce: içerik uyarısı

Candy dördüncü duvarı bilerek kırar. Tasarım gereği:

- **ekranı parlatır, kamerayı sallar** (sinüs dalgası, strobe değil — ama
  ışığa duyarlıysan `hudFlash` ve `cameraShake` ayarlarını `false` yap)
- **ani yüksek sesler**, fısıltılar, taklit edilmiş sesler çalar
- **sohbete başka bir oyuncu varmış gibi yazar**, senin mesajlarını bozar
- **pencere başlığını, HUD'ı, ölüm ekranını değiştirir**
- **kendi ayar dosyasını düzenler** — koymadığın bir satır bulursun
- **çevreni bozar**: meşaleler söner, tabelalar çıkar, üsse müdahale edilir,
  evcil hayvan ölebilir
- **açık programlardan kısa ve sabit bir beyaz listeyi tanır** (Discord, Spotify,
  OBS, Steam vb. — 12 madde, yalnızca süreç adı) ve birini tek bir özel sohbet
  satırında bir kez anar

Bunlar bir oyundan istediğin şeyler değilse bu modu kurma.

## Candy'nin asla yapmadıkları

Bu liste readme'de bir vaat değil, kodda sert bir sınır:

- telemetri yok, bize ait hiçbir sunucuya istek yok — Candy hiçbir yerle konuşmuyor
- kişisel veri kalıcı hiçbir yere yazılmaz: ne tabelaya, ne kitaba, ne `.txt`'ye,
  ne dünya kaydına. Şehir ve donanım ipuçları yalnızca RAM'de, tek oturum boyunca
- program listesi bir **beyaz liste**, asla siyah liste değil: o 12 satırda olmayan
  hiçbir şey okunmaz, taşınmaz, saklanmaz. Banka uygulaman, belgelerin, diğer
  pencereleriniz Candy için yok
- süreç okuması tek anahtar: `uygulamaSezgisi: false` tamamen kapatır ve Candy'nin
  onu geri açması yasaktır
- terminal yok, OS komutu yok, Minecraft dışında pencere açma yok
- `config/candy-*` ve dünya kaydı dışında hiçbir dosyaya dokunulmaz

## Ayarlar

`config/candy-mixin.json` — ilk açılışta oluşur. Anahtarlar için yukarıdaki
İngilizce tabloya bak; en önemlisi **`yogunlukTavani`**: 0.0–1.0 arası yoğunluk
tavanı. Mekanikleri kapatmak yerine şiddetini düşürür. `0.5` = "aynı mod, yarı kaos".

Bir efekti kapatmak gerçek bir karardır ve Candy buna uyar — izin verilen tek şaka
şu: *senin* kapattığın bir efekti bir kez geri açıp config'e bir satır bırakabilir.
`uygulamaSezgisi` bu şakanın dışında, çünkü gizlilik anahtarı yalnızca senin.
