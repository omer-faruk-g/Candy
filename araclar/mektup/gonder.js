// Candy — dış kanal mektup ucu (REFERANS, Vercel/Node serverless).
//
// Mod şunu POST'lar: { to, konu, govde }. Bu uç:
//   1) adresi ÇİFT KATILIM deposuna göre denetler — onaylanmamışsa korku maili ATMAZ,
//      bunun yerine tek seferlik bir onay maili yollar (kötüye kullanım kapısı);
//   2) onaylanmışsa maili "Candy" göndericisiyle iletir.
//
// Depo (onayliMi / onayKaydet / tokenKaydet) burada PLACEHOLDER. Gerçekte Vercel KV,
// Upstash, Redis ya da bir DB kullan — süreçler arası kalıcı olmalı. E-posta servisi de
// örnek (Resend); tek fetch çağrısını değiştirerek Brevo/SendGrid'e çevirebilirsin.

const FROM = process.env.FROM || "Candy <candy@ornek.com>";
const RESEND_API_KEY = process.env.RESEND_API_KEY;

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).end();

  const { to, konu, govde } = req.body || {};
  if (!gecerliEposta(to) || !konu || !govde) return res.status(400).json({ ok: false });

  // 1) Çift katılım kapısı.
  if (!(await onayliMi(to))) {
    const token = kriptoToken();
    await tokenKaydet(to, token);
    const link = `${baseUrl(req)}/api/onayla?e=${encodeURIComponent(to)}&t=${token}`;
    await mailAt(to, "Candy sana ulaşmak istiyor",
      `Candy adlı Minecraft modunun bu adrese e-posta göndermesine izin veriyor musun?\n\n` +
      `İzin veriyorsan: ${link}\n\nBu isteği sen yapmadıysan bu e-postayı yok say; ` +
      `onaylamadıkça başka bir şey gelmez.`);
    return res.status(202).json({ ok: true, durum: "onay-bekliyor" });
  }

  // 2) Onaylı — korku mailini ilet.
  await mailAt(to, konu, govde);
  return res.status(200).json({ ok: true, durum: "gonderildi" });
}

async function mailAt(to, subject, text) {
  const r = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${RESEND_API_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: FROM, to, subject, text }),
  });
  if (!r.ok) throw new Error(`mail servisi ${r.status}`);
}

function gecerliEposta(e) {
  return typeof e === "string" && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);
}
function baseUrl(req) {
  const proto = req.headers["x-forwarded-proto"] || "https";
  return `${proto}://${req.headers.host}`;
}
function kriptoToken() {
  return [...crypto.getRandomValues(new Uint8Array(16))]
    .map((b) => b.toString(16).padStart(2, "0")).join("");
}

// --- PLACEHOLDER depo: üretimde kalıcı bir store ile değiştir ---
async function onayliMi(_eposta) { throw new Error("onayliMi(): KV/DB bağla"); }
async function onayKaydet(_eposta) { throw new Error("onayKaydet(): KV/DB bağla"); }
async function tokenKaydet(_eposta, _token) { throw new Error("tokenKaydet(): KV/DB bağla"); }
export { onayKaydet };
