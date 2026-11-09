// Candy — dış kanal mektup ucu (Vercel/Node serverless).
//
// Mod şunu POST'lar: { to, konu, govde }. Bu uç:
//   1) adresi ÇİFT KATILIM deposuna göre denetler — onaylanmamışsa korku maili ATMAZ,
//      bunun yerine TEK bir onay maili yollar (kötüye kullanım kapısı). Korku maili
//      "bekleyen" olarak saklanır; onay linkine tıklandığı an onayla.js onu yollar;
//   2) onaylanmışsa maili "Candy" göndericisiyle iletir.
//
// Uç herkese açık: modu olmayan biri de curl ile POST atabilir. O yüzden içerik ve
// sıklık BURADA sınırlanıyor, istemciye güvenilmiyor:
//   - konu/gövde metin olmalı, uzunluğu sınırlı, link ya da alan adı yok (Candy'nin
//     satırları hiç link taşımıyor; link taşıyan bir "Candy" maili oltalamadır);
//   - onaylı adrese SIKLIK_SN içinde en fazla bir mail.
//
// Depo: ./depo.js (Vercel KV / Upstash REST). E-posta: ./posta.js (Resend).
//
// Yerelde, hesap açmadan denemek için: ./yerel-test/ (gerçek TLS, maili diske yazar).

import { onayliMi, tokenOku, tokenKaydet, tokenSil, bekleyenKaydet, siklikIzni } from "./depo.js";
import { mailAt } from "./posta.js";

const KONU_MAKS = 100;
const GOVDE_MAKS = 1200;
/** İstemcinin kendi soğuması 20 dk; sunucu biraz daha gevşek ama asıl fren o değil, bu. */
const SIKLIK_SN = 15 * 60;
const LINK =
  /(:\/\/|www\.|\b[a-z0-9-]+\.(com|net|org|io|app|dev|xyz|ru|tk|co|me|ly|gl|link|site|top|info|biz|tr)\b)/i;

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).end();

  const { to, konu, govde } = req.body || {};
  if (!gecerliEposta(to) || typeof konu !== "string" || typeof govde !== "string") {
    return res.status(400).json({ ok: false });
  }
  if (!konu.trim() || !govde.trim() || konu.length > KONU_MAKS || govde.length > GOVDE_MAKS) {
    return res.status(400).json({ ok: false, durum: "icerik-siniri" });
  }
  if (LINK.test(konu) || LINK.test(govde)) {
    return res.status(400).json({ ok: false, durum: "link-yasak" });
  }

  try {
    // 1) Çift katılım kapısı.
    if (!(await onayliMi(to))) {
      // Onay anında hemen gitsin diye sakla; son gelen kazanır, token'la aynı ömür.
      await bekleyenKaydet(to, { konu, govde });
      // Bekleyen bir onay zaten varsa İKİNCİ onay maili atılmaz. Yoksa bu uç, aynı
      // adrese tekrar tekrar POST atan birinin elinde gelen kutusu şişiren bir alete
      // dönüşürdü — kapının amacı tam tersi.
      if (!(await tokenOku(to))) {
        const token = kriptoToken();
        await tokenKaydet(to, token);
        const link = `${baseUrl(req)}/api/onayla?e=${encodeURIComponent(to)}&t=${token}`;
        try {
          await mailAt(
            to,
            "Candy sana ulaşmak istiyor",
            `Candy adlı Minecraft modunun bu adrese e-posta göndermesine izin veriyor musun?\n\n` +
              `İzin veriyorsan: ${link}\n\nOnaylarsan Candy'nin ilk mektubu hemen gelir.\n\n` +
              `Bu isteği sen yapmadıysan bu e-postayı yok say; ` +
              `onaylamadıkça başka bir şey gelmez.`
          );
        } catch (e) {
          // Onay maili gitmediyse token'ı bırakma: yoksa adres 24 saat boyunca "onay
          // maili zaten gitti" sanılır ve hiç onay maili almadan kilitli kalırdı.
          await tokenSil(to).catch(() => {});
          throw e;
        }
      }
      return res.status(202).json({ ok: true, durum: "onay-bekliyor" });
    }

    // 2) Onaylı — korku mailini ilet (sıklık freninden geçerse).
    if (!(await siklikIzni(to, SIKLIK_SN))) {
      return res.status(429).json({ ok: false, durum: "cok-sik" });
    }
    await mailAt(to, konu, govde);
    return res.status(200).json({ ok: true, durum: "gonderildi" });
  } catch {
    // Depo ya da Resend hatası: yakalanmasa fonksiyon çöker, istemci 500 + yığın görür.
    return res.status(502).json({ ok: false, durum: "gonderilemedi" });
  }
}

function gecerliEposta(e) {
  return typeof e === "string" && e.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);
}
/**
 * Onay linkinin kökü. BASE_URL tanımlıysa o (önerilir): Host ve X-Forwarded-Proto
 * istemciden gelen başlıklar; onlara güvenmek onay mailine yabancı bir alan adı ya da
 * http:// koydurabilir.
 */
function baseUrl(req) {
  if (process.env.BASE_URL) return process.env.BASE_URL.replace(/\/+$/, "");
  return `https://${req.headers.host}`;
}
function kriptoToken() {
  return [...crypto.getRandomValues(new Uint8Array(16))]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}
