// Candy — çift katılım onay ucu (REFERANS). Onay e-postasındaki linke tıklayınca açılır:
//   GET /api/onayla?e=<eposta>&t=<token>
// Token eşleşirse adresi onaylılar deposuna yazar; bundan sonra korku mailleri gider.

import { onayKaydet } from "./gonder.js";

export default async function handler(req, res) {
  const { e, t } = req.query || {};
  if (!e || !t || !(await tokenGecerli(e, t))) {
    return res.status(400).send("Geçersiz ya da süresi dolmuş onay bağlantısı.");
  }
  await onayKaydet(e);
  await tokenSil(e);
  res.status(200).send("Onaylandı. Candy artık bu adrese ulaşabilir. Bir daha kapatmak için modun config dosyasına bak.");
}

// --- PLACEHOLDER depo: gonder.js'teki store ile aynı kalıcı kaynağı kullan ---
async function tokenGecerli(_e, _t) { throw new Error("tokenGecerli(): KV/DB bağla"); }
async function tokenSil(_e) { throw new Error("tokenSil(): KV/DB bağla"); }
