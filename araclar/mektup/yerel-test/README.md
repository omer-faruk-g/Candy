# Yerel mektup ucu (test)

Dış kanalı (v10.0.0) **hesap açmadan, gerçek e-posta göndermeden** uçtan uca koşturur.
Mod burada gerçek HTTPS POST'unu atar; bu uç çift katılım kapısını uygular ve
"gönderdiği" mektubu `mektuplar/` altına dosya olarak yazar. Böylece §-temizliği, saat
satırı, alt bilgi ve çift katılım kapısı gözle doğrulanabiliyor.

Üretim ucu burada **değil**: `../gonder.js` + `../onayla.js` + `../depo.js` (Vercel/Resend + KV).

## Niye gerçek TLS

`MektupGonder` yalnızca `https://` uçlara yolluyor — düz `http://` sessizce reddediliyor.
Bu bilinçli bir güvenlik kapısı ve **test için gevşetilmiyor**. O yüzden yerel uç da
gerçek TLS konuşuyor; `kur.sh` bir self-signed sertifika üretip JDK'nin `cacerts`
**kopyasına** ekliyor, koşum da istemci JVM'ini o kopyayla başlatıyor. JDK'nin kendi
deposuna dokunulmuyor ve istemci tüm gerçek CA'ları tanımaya devam ediyor.

## Kullanım

```bash
./kur.sh                 # sertifika + guven deposu (bir kez; 30 gun gecerli)
node sunucu.mjs          # ucu ac  -> https://localhost:8443/api/gonder
node sunucu.mjs --sifirla # durum + mektuplari temizle
```

Sonra başka bir kabukta:

```bash
cd ../../..
./gradlew :1.21.7:runOtoTest    # klavyesiz kosum: dunyaya girer, sirayi kendi kosar
```

`runOtoTest`, `candy-test-cacerts` varsa güven deposunu kendiliğinden bağlar; yoksa
mektup adımı sessizce atlanır (diğer adımlar koşar).

İstemci config'i (`versions/<sürüm>/run/config/candy-mixin.json`):

```json
"mektup": true,
"mektupEposta": "test@ornek.com",
"mektupUc": "https://localhost:8443/api/gonder"
```

## Ne doğrulanabilir

| Soru | Nasıl |
| --- | --- |
| Mod gerçekten POST atıyor mu | `durum.json → post` artar; uç log'u isteği yazar |
| Çift katılım kapısı tutuyor mu | onaysız adres → `202 onay-bekliyor`, `mektuplar/NNN-onay.txt`, korku maili **yok** |
| Onaydan sonra gidiyor mu | `/api/onayla?e=&t=` → sonraki POST `200 gonderildi` + `NNN-korku.txt` |
| §-renk kodları temizleniyor mu | `NNN-korku.txt` gövdesinde `§` **olmamalı** |
| Saat + alt bilgi var mı | gövdede saat satırı ve "bu Candy modu / nasıl kapatılır" satırı |
| Config boşken sıfır istek | `mektupEposta` boş bırak, aynı koşumu yap, `durum.json → istek` **artmamalı** |

## Dosyalar

| Dosya | Ne |
| --- | --- |
| `sunucu.mjs` | ucun kendisi (Node, yalnız standart kütüphane) |
| `kur.sh` | sertifika + güven deposu üretir |
| `durum.json` | istek sayacı + onaylılar + bekleyen tokenlar (üretilir) |
| `mektuplar/` | "gönderilen" mektuplar (üretilir) |

Üretilen hiçbir dosya depoya girmez (`.gitignore`).
