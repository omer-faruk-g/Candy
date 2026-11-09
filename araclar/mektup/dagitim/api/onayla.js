// Candy — çift katılım onay ucu. Onay e-postasındaki link iki adımlı:
//   GET  /api/onayla?e=<eposta>&t=<token>  → yalnızca "Onayla" düğmeli bir sayfa
//   POST /api/onayla?e=<eposta>&t=<token>  → düğmeye basıldı: onay + bekleyen mail
//
// NEDEN İKİ ADIM: Outlook Safe Links, kurumsal posta geçitleri ve bazı antivirüsler
// maildeki linkleri insan tıklamadan ÖNCE GET ile açıyor. Tek adımlı GET onayı, adres
// sahibi hiç görmeden adresi onaylı yapıyordu: çift katılım kapısını boşa çıkaran hata.
// Link tarayıcıları form POST etmez.
//
// Onay anında, onay beklerken gelen korku maili HEMEN yollanır — tıklayan "eeee, ne
// oldu?" diye kalmasın.

import { onayKaydet, tokenGecerli, tokenSil, bekleyenAl } from "../lib/depo.js";
import { mailAt } from "../lib/posta.js";

export default async function handler(req, res) {
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  res.setHeader("Cache-Control", "no-store");
  if (req.method !== "GET" && req.method !== "POST") return res.status(405).end();

  // ?e=a&e=b dizi olarak gelir; tek değer değilse geçersiz say (yoksa .trim() patlar).
  const e = tek(req.query?.e);
  const t = tek(req.query?.t);
  try {
    if (!e || !t || !(await tokenGecerli(e, t))) {
      return res.status(400).send(sayfa("Geçersiz ya da süresi dolmuş onay bağlantısı."));
    }

    if (req.method === "GET") {
      const eylem = `/api/onayla?e=${encodeURIComponent(e)}&t=${encodeURIComponent(t)}`;
      return res.status(200).send(
        sayfa(
          `<b>${kacis(e)}</b> adresine Candy'nin (bir Minecraft modu) e-posta göndermesine izin veriyor musun?</p>` +
            `<form method="POST" action="${kacis(eylem)}"><button type="submit">Onayla</button></form>` +
            `<p>İstemiyorsan bu sayfayı kapat; onaylamadıkça hiçbir şey gelmez.`
        )
      );
    }

    await onayKaydet(e);
    await tokenSil(e);

    let sonra = "Candy bir sonraki fırsatta yazacak.";
    const m = await bekleyenAl(e);
    if (m) {
      try {
        await mailAt(e, m.konu, m.govde);
        sonra = "Candy'nin ilk mektubu şu an yola çıktı. Gelen kutuna bak.";
      } catch {
        sonra = "Bekleyen mektup şu an gönderilemedi; Candy bir sonraki fırsatta yazacak.";
      }
    }
    return res
      .status(200)
      .send(sayfa(`Onaylandı. ${sonra} Bir daha kapatmak için modun config dosyasına bak.`));
  } catch {
    return res.status(502).send(sayfa("Şu an onaylanamadı; biraz sonra linke yeniden tıkla."));
  }
}

function tek(v) {
  return typeof v === "string" && v.length <= 254 ? v : undefined;
}
function kacis(s) {
  return String(s).replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}
function sayfa(icerik) {
  return `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width">` +
    `<title>Candy</title><p>${icerik}</p>`;
}
