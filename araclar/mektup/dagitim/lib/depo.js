// Candy — dış kanal ucunun kalıcı deposu (Vercel KV / Upstash Redis, REST).
//
// NİYE AYRI DOSYA: çift katılım kaydını ve onay bekleyen korku mailini hem `gonder.js`
// (yazar) hem `onayla.js` (okur) kullanıyor. Vercel'de her uç ayrı bir fonksiyon, yani bellekteki bir Map iki uç
// arasında paylaşılmaz ve soğuk başlatmada kaybolur — onay kaydı süreç dışında,
// kalıcı bir yerde durmak ZORUNDA. Yoksa her istek "onaysız" görünür ve kapı bir
// işe yaramaz.
//
// Ortam değişkenleri (Vercel KV bağlarsan ikisi de kendiliğinden gelir):
//   KV_REST_API_URL    örn. https://eu1-xxx.upstash.io
//   KV_REST_API_TOKEN  o veritabanının REST token'ı
//
// Başka bir depo kullanacaksan (Postgres, Mongo, Firestore) yalnızca bu dosyanın dışa
// açık fonksiyonlarını değiştir; uçların kodu aynı kalır.

const KV_URL = process.env.KV_REST_API_URL;
const KV_TOKEN = process.env.KV_REST_API_TOKEN;

/** Onay kaydı kalıcı — süresi yok. Token 24 saat sonra kendiliğinden ölür. */
const TOKEN_OMRU_SN = 86_400;

const anahtarOnay = (e) => `candy:onay:${e.trim().toLowerCase()}`;
const anahtarToken = (e) => `candy:token:${e.trim().toLowerCase()}`;
const anahtarBekleyen = (e) => `candy:bekleyen:${e.trim().toLowerCase()}`;

async function kv(...parcalar) {
  if (!KV_URL || !KV_TOKEN) {
    throw new Error(
      "KV_REST_API_URL / KV_REST_API_TOKEN tanımlı değil — çift katılım deposu bağlanmadan " +
        "uç açılmamalı (bkz. araclar/mektup/README.md)."
    );
  }
  const yol = parcalar.map((p) => encodeURIComponent(p)).join("/");
  const r = await fetch(`${KV_URL}/${yol}`, {
    headers: { Authorization: `Bearer ${KV_TOKEN}` },
  });
  if (!r.ok) throw new Error(`KV ${r.status}`);
  return (await r.json()).result;
}

/** Bu adres sahibi tarafından onaylandı mı? */
export async function onayliMi(eposta) {
  return (await kv("get", anahtarOnay(eposta))) === "1";
}

/** Onay linkine tıklandı — adresi kalıcı onaylılar listesine yaz. */
export async function onayKaydet(eposta) {
  await kv("set", anahtarOnay(eposta), "1");
}

/** Bekleyen onay token'ı (varsa). Aynı adrese ikinci onay maili atmamak için de okunur. */
export async function tokenOku(eposta) {
  return await kv("get", anahtarToken(eposta));
}

export async function tokenKaydet(eposta, token) {
  await kv("set", anahtarToken(eposta), token, "EX", String(TOKEN_OMRU_SN));
}

export async function tokenGecerli(eposta, token) {
  const kayitli = await tokenOku(eposta);
  if (!kayitli || !token || kayitli.length !== token.length) return false;
  // Sabit süreli karşılaştırma: token uzunluğu sabit (32 hex) olduğu için güvenli.
  let fark = 0;
  for (let i = 0; i < kayitli.length; i++) fark |= kayitli.charCodeAt(i) ^ token.charCodeAt(i);
  return fark === 0;
}

export async function tokenSil(eposta) {
  await kv("del", anahtarToken(eposta));
}

/** Onay beklerken gelen son korku maili ({konu, govde}); token'la aynı ömür. */
export async function bekleyenKaydet(eposta, mektup) {
  await kv("set", anahtarBekleyen(eposta), JSON.stringify(mektup), "EX", String(TOKEN_OMRU_SN));
}

/**
 * Bekleyen maili okur VE siler — ikinci tıklama aynı maili bir daha yollamasın.
 * Tek komut (GETDEL): ayrı get + del arasında gelen ikinci istek de aynı maili okuyup
 * ikinci kez yolluyordu.
 */
export async function bekleyenAl(eposta) {
  const ham = await kv("getdel", anahtarBekleyen(eposta));
  if (!ham) return null;
  try {
    return JSON.parse(ham);
  } catch {
    return null;
  }
}

const anahtarSiklik = (e) => `candy:siklik:${e.trim().toLowerCase()}`;

/**
 * Sunucu tarafı sıklık freni: bu adrese son `sn` saniyede mail gittiyse false.
 * İstemcinin 20 dk soğuması uca doğrudan POST atana karşı hiçbir şey değil; asıl fren
 * bu. SET NX EX tek komut: iki eşzamanlı istekten yalnızca biri geçer.
 */
export async function siklikIzni(eposta, sn) {
  return (await kv("set", anahtarSiklik(eposta), "1", "NX", "EX", String(sn))) === "OK";
}
