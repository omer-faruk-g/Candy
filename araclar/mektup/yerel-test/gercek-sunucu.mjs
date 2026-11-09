// Candy — GERCEK mektup ucu, yerelde.
//
// sunucu.mjs maili diske yazar (sahte). BU dosya gonder.js'in AYNI mantigini
// kosturur ama maili GERCEKTEN Resend uzerinden yollar. Vercel ve KV gerekmez:
// cift katilim deposu depo.js'in yerine yerel bir JSON dosyasi.
//
// Kosum:
//   export RESEND_API_KEY=re_xxx        # kendi anahtarin, bu dosyaya YAZMA
//   node gercek-sunucu.mjs
//
// Resend ucretsiz katmani: alan adi dogrulamadiysan FROM 'onboarding@resend.dev'
// olmak ZORUNDA ve yalnizca HESAP SAHIBININ adresine mail gider. Kendine test
// ederken tam olarak bu durum gecerli.
//
// ONAY AKISI: onaysiz adrese gelen korku maili DUSURULMUYOR, onay token'iyla birlikte
// "bekleyen" olarak saklaniyor. Onay linkine tiklandigi an o mail gidiyor — "tikladim,
// eeee ne oldu?" boslugu yok. Link duz http://localhost (ONAY_PORT): mod POST'u TLS
// ister ama link tarayicida acilir; localhost'ta http sertifika uyarisi vermez ve
// token makineden hic cikmaz. Telefonda acilmaz — bunun icin uc herkese acik bir
// adreste (Vercel) olmali; o surum ../gonder.js + ../onayla.js.
//
// Yalnizca test icin: RESEND_URL (sahte Resend), DEPO_DOSYA (gercek onay deposuna
// dokunmamak icin), PORT / ONAY_PORT.

import http from "node:http";
import https from "node:https";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const BURADA = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 8443);
const ONAY_PORT = Number(process.env.ONAY_PORT || 8080);
const DEPO_DOSYA = process.env.DEPO_DOSYA || path.join(BURADA, "gercek-depo.json");
const RESEND_URL = process.env.RESEND_URL || "https://api.resend.com/emails";
const FROM = process.env.FROM || "Candy <onboarding@resend.dev>";
// Anahtar: ortam degiskeni ya da --anahtar-dosya <yol> / RESEND_API_KEY_FILE.
// Dosya YOLU gizli degil; anahtarin KENDISI komut satirina/gecmise hic dusmez.
function anahtarOku() {
  if (process.env.RESEND_API_KEY) return process.env.RESEND_API_KEY.trim();
  const i = process.argv.indexOf("--anahtar-dosya");
  const yol = i >= 0 ? process.argv[i + 1] : process.env.RESEND_API_KEY_FILE;
  if (!yol) return null;
  try {
    return fs.readFileSync(yol, "utf8").trim() || null;
  } catch (t) {
    console.error("[gercek-uc] anahtar dosyasi okunamadi: " + yol + " (" + t.message + ")");
    return null;
  }
}
const RESEND_API_KEY = anahtarOku();
const TOKEN_OMRU_MS = 86_400_000;

if (!RESEND_API_KEY) {
  console.error(
    "[gercek-uc] Resend anahtari yok. Ornek:\n" +
      "  node gercek-sunucu.mjs --anahtar-dosya <yol>\n" +
      "Anahtari resend.com/api-keys adresinden kendin al; bu dosyaya YAZMA."
  );
  process.exit(1);
}

// ─── depo: depo.js'in bes fonksiyonunun yerel karsiligi ─────────────────────
const nrm = (e) => e.trim().toLowerCase();
function depoOku() {
  let d;
  try {
    d = JSON.parse(fs.readFileSync(DEPO_DOSYA, "utf8"));
  } catch {
    d = {};
  }
  // Eski depo dosyalarinda "bekleyen" yok.
  return { onay: d.onay || {}, token: d.token || {}, bekleyen: d.bekleyen || {} };
}
function depoYaz(d) {
  fs.writeFileSync(DEPO_DOSYA, JSON.stringify(d, null, 2), "utf8");
}
const onayliMi = (e) => depoOku().onay[nrm(e)] === true;
function onayKaydet(e) {
  const d = depoOku();
  d.onay[nrm(e)] = true;
  delete d.token[nrm(e)];
  depoYaz(d);
}
function tokenOku(e) {
  const k = depoOku().token[nrm(e)];
  if (!k) return null;
  if (Date.now() > k.biter) return null;
  return k.deger;
}
function tokenKaydet(e, token) {
  const d = depoOku();
  d.token[nrm(e)] = { deger: token, biter: Date.now() + TOKEN_OMRU_MS };
  depoYaz(d);
}
function tokenGecerli(e, token) {
  const kayitli = tokenOku(e);
  if (!kayitli || !token || kayitli.length !== token.length) return false;
  return crypto.timingSafeEqual(Buffer.from(kayitli), Buffer.from(token));
}
/** Onay beklerken gelen son korku maili; token'la ayni omur. Son gelen kazanir. */
function bekleyenKaydet(e, konu, govde) {
  const d = depoOku();
  d.bekleyen[nrm(e)] = { konu, govde, biter: Date.now() + TOKEN_OMRU_MS };
  depoYaz(d);
}
/** Bekleyen maili okur VE siler — iki tiklama ayni maili iki kez yollamasin. */
function bekleyenAl(e) {
  const d = depoOku();
  const m = d.bekleyen[nrm(e)];
  if (!m) return null;
  delete d.bekleyen[nrm(e)];
  depoYaz(d);
  return Date.now() > m.biter ? null : m;
}

// ─── gercek mail ────────────────────────────────────────────────────────────
async function mailAt(to, subject, text) {
  const r = await fetch(RESEND_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ from: FROM, to, subject, text }),
  });
  const govde = await r.text();
  if (!r.ok) {
    console.error(`[gercek-uc] RESEND ${r.status}: ${govde}`);
    throw new Error(`mail servisi ${r.status}`);
  }
  console.log(`[gercek-uc] MAIL GITTI -> ${to} | konu: ${subject} | ${govde}`);
}

const gecerliEposta = (e) =>
  typeof e === "string" && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);

function govdeOku(req) {
  return new Promise((res) => {
    let b = "";
    req.on("data", (c) => (b += c));
    req.on("end", () => {
      try {
        res(JSON.parse(b || "{}"));
      } catch {
        res(null);
      }
    });
  });
}

// onay linki — gercek mailden tiklanir. Hem http (ONAY_PORT, yeni linkler) hem https
// (PORT, daha once yollanmis linkler) ayni isleyiciyi kullanir.
async function onayIsle(u, res) {
  const e = u.searchParams.get("e") || "";
  const t = u.searchParams.get("t") || "";
  if (!gecerliEposta(e) || !tokenGecerli(e, t)) {
    console.log(`[gercek-uc] onay REDDEDILDI: ${e}`);
    res.writeHead(400, { "Content-Type": "text/plain; charset=utf-8" });
    return res.end("Gecersiz ya da suresi dolmus onay linki.");
  }
  onayKaydet(e);
  console.log(`[gercek-uc] ONAYLANDI: ${e}`);
  const m = bekleyenAl(e);
  let sonra = "Candy bir sonraki firsatta yazacak.";
  if (m) {
    try {
      await mailAt(e, m.konu, m.govde);
      sonra = "Candy'nin ilk mektubu su an yola cikti. Gelen kutuna bak.";
    } catch (t2) {
      console.error(`[gercek-uc] bekleyen mail GIDEMEDI: ${t2.message}`);
      sonra = "Bekleyen mektup su an gonderilemedi; Candy bir sonraki firsatta yazacak.";
    }
  }
  res.writeHead(200, { "Content-Type": "text/plain; charset=utf-8" });
  return res.end(`Onaylandi: ${e}\n${sonra}`);
}

const sunucu = https.createServer(
  {
    key: fs.readFileSync(path.join(BURADA, "localhost-key.pem")),
    cert: fs.readFileSync(path.join(BURADA, "localhost-cert.pem")),
  },
  async (req, res) => {
    const u = new URL(req.url, `https://localhost:${PORT}`);

    if (u.pathname === "/durum") {
      res.writeHead(200, { "Content-Type": "application/json" });
      return res.end(JSON.stringify(depoOku(), null, 2));
    }

    if (u.pathname === "/api/onayla") return onayIsle(u, res);

    if (req.method === "POST" && u.pathname === "/api/gonder") {
      const body = await govdeOku(req);
      const { to, konu, govde } = body || {};
      if (!gecerliEposta(to) || !konu || !govde) {
        console.log("[gercek-uc] 400 — eksik/gecersiz alan");
        res.writeHead(400, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ ok: false }));
      }

      // 1) CIFT KATILIM KAPISI — gonder.js ile ayni.
      if (!onayliMi(to)) {
        bekleyenKaydet(to, konu, govde);
        if (!tokenOku(to)) {
          const token = crypto.randomBytes(16).toString("hex");
          tokenKaydet(to, token);
          const link = `http://localhost:${ONAY_PORT}/api/onayla?e=${encodeURIComponent(
            to
          )}&t=${token}`;
          await mailAt(
            to,
            "Candy sana ulasmak istiyor",
            `Candy adli Minecraft modunun bu adrese e-posta gondermesine izin veriyor musun?\n\n` +
              `Izin veriyorsan: ${link}\n` +
              `(Yerel test sunucusu: baglantiyi oyunun calistigi bilgisayarda ac.)\n\n` +
              `Onaylarsan Candy'nin ilk mektubu hemen gelir.\n\n` +
              `Bu istegi sen yapmadiysan bu e-postayi yok say; ` +
              `onaylamadikca baska bir sey gelmez.`
          );
        } else {
          console.log(`[gercek-uc] onay zaten bekliyor, ikinci mail ATILMADI: ${to}`);
        }
        res.writeHead(202, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ ok: true, durum: "onay-bekliyor" }));
      }

      // 2) Onayli — korku mailini GERCEKTEN ilet.
      await mailAt(to, konu, govde);
      res.writeHead(200, { "Content-Type": "application/json" });
      return res.end(JSON.stringify({ ok: true, durum: "gonderildi" }));
    }

    res.writeHead(404);
    res.end();
  }
);

const onaySunucu = http.createServer((req, res) => {
  const u = new URL(req.url, `http://localhost:${ONAY_PORT}`);
  if (req.method === "GET" && u.pathname === "/api/onayla") return onayIsle(u, res);
  res.writeHead(404);
  res.end();
});

sunucu.listen(PORT, "127.0.0.1", () => {
  console.log(`[gercek-uc] HAZIR (GERCEK MAIL) https://localhost:${PORT}/api/gonder`);
  console.log(`[gercek-uc] FROM: ${FROM}`);
});
onaySunucu.listen(ONAY_PORT, "127.0.0.1", () => {
  console.log(`[gercek-uc] onay linkleri: http://localhost:${ONAY_PORT}/api/onayla`);
});
