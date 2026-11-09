# Candy — dış kanal (mektup) sunucusu

Candy v9.2.0'daki "dış kanal", oyuncuyu oyun penceresinin **dışında** — gerçek gelen
kutusunda — korkutur. Mod oyuncunun cihazından **doğrudan e-posta göndermez** (o yol
SMTP portları / kimlik doğrulama / antivirüs yüzünden hem kırılgan hem yanlış); bunun
yerine istemci, oyuncunun config'ine yazdığı `https://` adrese küçük bir JSON POST atar.
E-postayı **senin kuracağın bu uç** gönderir.

Bu klasördeki uç **çalışır hâlde**: çift katılım deposu da dahil (`depo.js`, Vercel KV / Upstash REST). Hesap açmadan denemek için `yerel-test/` var — gerçek TLS konuşur, "gönderdiği" mektubu diske yazar.

## Modun yolladığı istek

`POST <mektupUc>` · `Content-Type: application/json`

```json
{ "to": "oyuncu@ornek.com", "konu": "Alt+F4 seni kurtaramaz", "govde": "…\n\n— Candy" }
```

- `to` — oyuncunun **kendi** config'ine (`config/candy-mixin.json → mektupEposta`) yazdığı adres.
- `konu`, `govde` — metin oyuncunun diline göre istemcide zaten kuruldu; sunucu düz iletir.
- Mod başka hiçbir şey yollamaz: ekran görüntüsü yok, konum yok, donanım yok, oyuncu adı yok.

Başarıda `2xx` dön; başka her şeyde mod sessizce vazgeçer (oyun etkilenmez).

## ZORUNLU GÜVENLİK KAPISI — çift katılım (double opt-in)

Mod yalnızca config'teki tek adrese yollar, ama birisi **başkasının** adresini yazıp onu
korkutmayı deneyebilir. Bunu durduran yer **burası**, mod değil:

1. Bir adres ilk kez geldiğinde korku e-postasını **gönderme**. Önce o adrese tek bir
   onay e-postası at: "Candy adlı Minecraft modunun bu adrese ulaşmasına izin veriyor
   musun? Onaylamak için: …". Onaylananları kalıcı bir yerde (KV/DB) tut.
2. Yalnızca **onaylanmış** adreslere korku e-postası gönder. Onay beklerken gelen korku
   e-postası gönderilmez, saklanır (token'la aynı ömür, son gelen kazanır) ve onay linkine
   tıklandığı **an** yollanır — onaylayan kişi "tıkladım, ne oldu?" diye kalmaz.

Böylece yalnızca gelen kutusunun sahibi "evet" derse Candy oraya ulaşır.

## Gönderici

`From:` görünen ad **Candy** olsun (örn. `Candy <candy@alanadin.com>`). Alan adı senin
doğruladığın bir alan olmalı (SPF/DKIM), yoksa mektup spam'e düşer. Gerçek bir kişiyi ya
da kurumu taklit eden bir adres **kullanma** — bu Candy adlı kurgusal karakter, o kadar.

## Kurulum (örnek: Vercel + Resend)

- `gonder.js`, `onayla.js`, `depo.js` ve `posta.js` dosyalarını projenin `api/` altına koy
  (`depo.js` ve `posta.js` uç değil: ikisinin paylaştığı depo ve e-posta katı).
- Projeye bir **KV** bağla (Vercel KV ya da Upstash Redis). Bağlayınca
  `KV_REST_API_URL` ve `KV_REST_API_TOKEN` kendiliğinden gelir; `depo.js` başka
  bir şey istemiyor. Depo bağlanmadan uç açılmamalı: o zaman her istek "onaysız"
  görünür ve çift katılım kapısı bir işe yaramaz (`depo.js` bu durumda hata fırlatır).
- Diğer ortam değişkenleri: `RESEND_API_KEY`, `FROM="Candy <candy@alanadin.com>"`.
- `mektupUc` = `https://<projen>.vercel.app/api/gonder`.
- Başka bir depo kullanacaksan (Postgres, Mongo, Firestore) yalnızca `depo.js`'in dışa
  açık fonksiyonlarını değiştir; uçların kodu aynı kalır.
- Yayınlamadan önce yerelde denemek için: `yerel-test/README.md`.
- Ücretsiz katman korku modu için fazlasıyla yeter; servis (Resend/Brevo/SendGrid) fark etmez,
  `posta.js` içindeki tek `fetch` çağrısını değiştir.

## Oyuncu tarafı (hatırlatma)

Taze kurulumda kanal **kapalı**: `mektupEposta` ve `mektupUc` boş → sıfır ağ isteği.
Oyuncu adresini yazmak rızadır; `mektup: false` ya da adresi silmek kapatır. Giden her
mektubun altında bunun Candy modu olduğu ve nasıl kapatılacağı yazar.
