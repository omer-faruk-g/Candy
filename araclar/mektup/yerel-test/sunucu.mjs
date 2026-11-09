// Candy — YEREL mektup ucu (test). Resend/Vercel/hesap GEREKTİRMEZ.
//
// Modun gerçek dış kanal zincirini uçtan uca koşturmak için: mod HTTPS POST atar,
// bu uç çift katılım kapısını uygular ve "gönderdiği" maili diske yazar. Böylece
// §-temizliği, saat satırı, alt bilgi ve çift katılım kapısı GÖZLE doğrulanabilir —
// gerçek bir e-posta servisine hiç dokunmadan.
//
// Üretim ucu burada DEĞİL: ../gonder.js + ../onayla.js (Vercel/Resend + KV).
//
// Koşum:  node sunucu.mjs            (önce ./kur.sh ile sertifika üret)
// Durum:  https://localhost:8443/durum
// Sıfırla: node sunucu.mjs --sifirla

import https from "node:https";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const BURADA = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 8443);
const DURUM_DOSYA = path.join(BURADA, "durum.json");
const MEKTUP_DIZIN = path.join(BURADA, "mektuplar");

if (process.argv.includes("--sifirla")) {
  fs.rmSync(DURUM_DOSYA, { force: true });
  fs.rmSync(MEKTUP_DIZIN, { recursive: true, force: true });
  console.log("[yerel-uc] durum ve mektuplar silindi");
  process.exit(0);
}

// ─── durum: istek sayacı + onaylılar + bekleyen tokenlar ───
function durumOku() {
  try {
    return JSON.parse(fs.readFileSync(DURUM_DOSYA, "utf8"));
  } catch {
    return { istek: 0, post: 0, onayli: [], token: {}, mektup: 0 };
  }
}
function durumYaz(d) {
  fs.writeFileSync(DURUM_DOSYA, JSON.stringify(d, null, 2), "utf8");
}

function mektupYaz(tip, to, konu, govde) {
  fs.mkdirSync(MEKTUP_DIZIN, { recursive: true });
  const d = durumOku();
  d.mektup += 1;
  durumYaz(d);
  const ad = `${String(d.mektup).padStart(3, "0")}-${tip}.txt`;
  const metin =
    `--- YEREL TEST MEKTUBU (gercekten gonderilmedi) ---\n` +
    `zaman : ${new Date().toISOString()}\n` +
    `tip   : ${tip}\n` +
    `to    : ${to}\n` +
    `konu  : ${konu}\n` +
    `--- govde ---\n${govde}\n--- son ---\n`;
  fs.writeFileSync(path.join(MEKTUP_DIZIN, ad), metin, "utf8");
  console.log(`[yerel-uc] mektup yazildi: mektuplar/${ad}`);
  return ad;
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

const sunucu = https.createServer(
  {
    key: fs.readFileSync(path.join(BURADA, "localhost-key.pem")),
    cert: fs.readFileSync(path.join(BURADA, "localhost-cert.pem")),
  },
  async (req, res) => {
    const u = new URL(req.url, `https://localhost:${PORT}`);

    // HER istek sayılır — "mektupEposta bos ise sifir istek" testinin olcusu bu.
    const d0 = durumOku();
    d0.istek += 1;
    if (req.method === "POST") d0.post += 1;
    durumYaz(d0);
    console.log(
      `[yerel-uc] #${d0.istek} ${req.method} ${u.pathname} ` +
        `ua="${req.headers["user-agent"] || ""}"`
    );

    // ── durum ──
    if (u.pathname === "/durum") {
      res.writeHead(200, { "Content-Type": "application/json" });
      return res.end(JSON.stringify(durumOku(), null, 2));
    }

    // ── onay linki (cift katilim 2. adim) ──
    if (u.pathname === "/api/onayla") {
      const e = u.searchParams.get("e"),
        t = u.searchParams.get("t");
      const d = durumOku();
      if (!e || !t || d.token[e] !== t) {
        res.writeHead(400, { "Content-Type": "text/plain; charset=utf-8" });
        return res.end("Gecersiz ya da suresi dolmus onay baglantisi.");
      }
      if (!d.onayli.includes(e)) d.onayli.push(e);
      delete d.token[e];
      durumYaz(d);
      res.writeHead(200, { "Content-Type": "text/plain; charset=utf-8" });
      return res.end("Onaylandi. Candy artik bu adrese ulasabilir.");
    }

    // ── modun POST'u ──
    if (u.pathname === "/api/gonder") {
      if (req.method !== "POST") {
        res.writeHead(405);
        return res.end();
      }
      const body = await govdeOku(req);
      const { to, konu, govde } = body || {};
      if (!gecerliEposta(to) || !konu || !govde) {
        console.log("[yerel-uc] 400 — eksik/gecersiz alan");
        res.writeHead(400, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ ok: false }));
      }

      const d = durumOku();
      if (!d.onayli.includes(to)) {
        // ZORUNLU KAPI: korku maili ATILMAZ, tek seferlik onay maili gider.
        const token = crypto.randomBytes(16).toString("hex");
        d.token[to] = token;
        durumYaz(d);
        const link = `https://localhost:${PORT}/api/onayla?e=${encodeURIComponent(to)}&t=${token}`;
        mektupYaz(
          "onay",
          to,
          "Candy sana ulasmak istiyor",
          `Candy adli Minecraft modunun bu adrese e-posta gondermesine izin veriyor musun?\n\n` +
            `Izin veriyorsan: ${link}\n\nBu istegi sen yapmadiysan bu e-postayi yok say.`
        );
        res.writeHead(202, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ ok: true, durum: "onay-bekliyor" }));
      }

      mektupYaz("korku", to, konu, govde);
      res.writeHead(200, { "Content-Type": "application/json" });
      return res.end(JSON.stringify({ ok: true, durum: "gonderildi" }));
    }

    res.writeHead(404);
    res.end();
  }
);

// "localhost" JVM'e gore degisiyor: NeoForge (ModDevGradle) -Djava.net.preferIPv6Addresses=system
// veriyor, istemci ::1'e gidiyor; Fabric 127.0.0.1'e. Ikisini de dinle. Sertifika SAN'i
// DNS:localhost oldugu icin iki adreste de dogrulanir. Biri doluysa digeri yine acilir.
const sunucu6 = https.createServer(
  {
    key: fs.readFileSync(path.join(BURADA, "localhost-key.pem")),
    cert: fs.readFileSync(path.join(BURADA, "localhost-cert.pem")),
  },
  sunucu.listeners("request")[0]
);
for (const [s, adres] of [[sunucu, "127.0.0.1"], [sunucu6, "::1"]]) {
  s.on("error", (e) => console.log(`[yerel-uc] ${adres}:${PORT} dinlenemedi (${e.code})`));
  s.listen(PORT, adres, () =>
    console.log(`[yerel-uc] HAZIR https://localhost:${PORT}/api/gonder (${adres})`));
}
