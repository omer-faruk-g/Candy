# Candy

**An entity trying to find out what it is. It is watching you.**



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
| Minecraft | **1.21.7** or **1.21.11** — one jar per version, pick the matching one |
| Loader | Fabric (0.16.14+ on 1.21.7, 0.20.1+ on 1.21.11) |
| Fabric API | required |
| Java | 21+ |
| Side | works in single player and on servers (install on both) |

Jars are named `candy-<mod version>+<minecraft version>.jar`, e.g.
`candy-8.4.0+1.21.11.jar`. Installing the wrong one will not load.

One known difference between the two: the Mirror dimension's *face on the blocks*
effect runs on 1.21.7 only. Minecraft rewrote the texture atlas upload path in
1.21.9 and the effect depended on the old one, so it is switched off on 1.21.11
rather than approximated. Everything else is identical.

## Config

`config/candy-mixin.json`, created on first launch. The keys the content warning
above refers to are `hudFlash` (HUD flashes) and `cameraShake` (camera shake) —
set either to `false` if you are photosensitive or motion sensitive, and

`yogunlukTavani` (0.0–1.0) is an intensity ceiling: `0.5` is "same mod, half the
chaos", and it is the setting to reach for instead of turning features off.

## Building from source

See [BUILD.md](BUILD.md).

The build is multi-version ([Stonecutter](https://github.com/kikugie/stonecutter)):
one shared source tree, one node per Minecraft version under `versions/`.

```
./gradlew build                            # active node -> versions/<ver>/build/libs/
./gradlew "Set active project to 1.21.11"  # switch which version you build and run
./gradlew runClient                        # real behaviour, as a player gets it
./gradlew runTest                          # test profile: /candy, short cooldowns, cheats on
```

Requires Gradle 9.8.2 and Java 21. Build details, the version-split rules and the
per-version API notes are in [BUILD.md](BUILD.md).

## Licence

[CC BY-NC-SA 4.0](LICENSE).

**Modpacks: go ahead, no permission needed.** Credit "Candy by PhotonPulse" with a
link, keep the pack free, and if you ship a *modified* Candy it carries the same
licence. Streaming and monetised video are fine — "NonCommercial" is about selling
the mod, not about your channel.

Up to and including v8.4.0 this repo carried AGPL-3.0 text while the mod metadata
declared `ARR`. Those contradicted each other and gave no clear answer to "can I
put this in a pack?". CC BY-NC-SA 4.0 replaces both from the next release on, and
an AGPL copy you already hold keeps its grant — see [LICENSE](LICENSE).

---

# Candy (Türkçe)


## Gereksinimler

| | |
|---|---|
| Minecraft | **1.21.7** veya **1.21.11** — her sürüm için ayrı jar, eşleşeni indir |
| Loader | Fabric (1.21.7'de 0.16.14+, 1.21.11'de 0.20.1+) |
| Fabric API | zorunlu |
| Java | 21+ |
| Taraf | tek oyuncu ve sunucuda çalışır (ikisine de kur) |

Jar adı `candy-<mod sürümü>+<minecraft sürümü>.jar` biçiminde, örn.
`candy-8.4.0+1.21.11.jar`. Yanlışını kurarsan yüklenmez.

İki sürüm arasındaki tek fark: Ayna boyutundaki *blokların yüze dönmesi* efekti
yalnızca 1.21.7'de çalışıyor. Minecraft 1.21.9'da doku atlası yükleme yolunu
yeniden yazdı, efekt eskisine dayanıyordu; tahminle yaklaştırmak yerine 1.21.11'de
kapatıldı. Geri kalan her şey aynı.

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

- terminal yok, OS komutu yok, Minecraft dışında pencere açma yok
- `config/candy-*` ve dünya kaydı dışında hiçbir dosyaya dokunulmaz

## Ayarlar

`config/candy-mixin.json` — ilk açılışta oluşur. Yukarıdaki içerik uyarısının
bahsettiği anahtarlar `hudFlash` (HUD parlaması) ve `cameraShake` (kamera
sallanması): ışığa veya harekete duyarlıysan birini ya da ikisini `false` yap.
`uygulamaSezgisi: false` program tanımayı tamamen kapatır. `yogunlukTavani`
(0.0–1.0) yoğunluk tavanı: `0.5` = "aynı mod, yarı kaos" — efektleri kapatmak
yerine önce buna bak.

## Lisans

[CC BY-NC-SA 4.0](LICENSE).

**Modpack: serbest, izin istemeye gerek yok.** "Candy by PhotonPulse" künyesini
bağlantıyla ver, paketi ücretli yapma, *değiştirdiğin* bir Candy'yi dağıtıyorsan
aynı lisansla dağıt. Yayın ve gelir getiren video serbest — "NonCommercial" modun
satılmasını kısıtlar, kanalını değil.

v8.4.0'a kadar depoda AGPL-3.0 metni duruyordu, mod verisi ise `ARR` diyordu; ikisi
çelişiyor ve "paketime koyabilir miyim?" sorusuna cevap vermiyordu. CC BY-NC-SA 4.0
sonraki sürümden itibaren ikisinin yerine geçiyor; elinde AGPL'li bir kopya varsa o
kopyanın hakkı duruyor — [LICENSE](LICENSE).
