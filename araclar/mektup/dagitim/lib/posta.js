// Candy — dış kanalın tek e-posta çağrısı (Resend). Uç DEĞİL, depo.js gibi yardımcı:
// gonder.js korku/onay maillerini, onayla.js de onay anında bekleyen korku mailini
// buradan yollar. Başka servise (Brevo/SendGrid) geçmek için yalnızca bu dosyayı değiştir.

const FROM = process.env.FROM || "Candy <candy@ornek.com>";
const RESEND_API_KEY = process.env.RESEND_API_KEY;

export async function mailAt(to, subject, text) {
  if (!RESEND_API_KEY) throw new Error("RESEND_API_KEY tanımlı değil");
  const r = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${RESEND_API_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: FROM, to, subject, text }),
  });
  if (!r.ok) throw new Error(`mail servisi ${r.status}`);
}
