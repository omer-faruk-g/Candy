// Candy — onay akisi testi. GERCEK MAIL YOK, AG YOK: sahte Resend + sahte KV.
//
//   node onay-testi.mjs
//
// Iki surumu de ayni senaryoyla dener:
//   A) gercek-sunucu.mjs  (yerel uc; sahte Resend'e RESEND_URL ile baglanir,
//                          gecici bir depo dosyasi kullanir — gercek-depo.json'a dokunmaz)
//   B) ../gonder.js + ../onayla.js  (Vercel surumu; fetch sahtelenir: KV + Resend)
//
// Senaryo: onaysiz adrese korku maili -> yalnizca onay maili gider, korku saklanir;
// ikinci POST ikinci onay maili ATMAZ; yanlis token reddedilir; dogru linke tiklaninca
// SAKLANAN korku maili HEMEN gider; ayni linke ikinci tiklama hicbir sey yollamaz;
// onayli adrese POST dogrudan gider.

import http from "node:http";
import https from "node:https";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath, pathToFileURL } from "node:url";

const BURADA = path.dirname(fileURLToPath(import.meta.url));
const TO = "oyuncu@ornek.com";
const K1 = { to: TO, konu: "Alt+F4 seni kurtaramaz", govde: "Seni izliyorum.\n\n- Candy" };
const K2 = { to: TO, konu: "Arkana bakma", govde: "Hala buradayim.\n\n- Candy" };

let hata = 0;
function bak(kosul, ad) {
  console.log(`  ${kosul ? "GECTI" : "KALDI"}  ${ad}`);
  if (!kosul) hata++;
}

// ─── A) yerel uc ────────────────────────────────────────────────────────────
async function yerelUc() {
  console.log("A) gercek-sunucu.mjs (sahte Resend)");
  const giden = [];
  const sahteResend = http.createServer((req, res) => {
    let b = "";
    req.on("data", (c) => (b += c));
    req.on("end", () => {
      giden.push(JSON.parse(b));
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ id: `sahte-${giden.length}` }));
    });
  });
  await new Promise((r) => sahteResend.listen(0, "127.0.0.1", r));

  const gecici = fs.mkdtempSync(path.join(os.tmpdir(), "candy-onay-"));
  const depo = path.join(gecici, "depo.json");
  // Eski bicim ("bekleyen" alani yok): geriye uyum da denenmis olsun.
  fs.writeFileSync(depo, JSON.stringify({ onay: {}, token: {} }));
  const PORT = 18443, ONAY_PORT = 18080;

  const srv = spawn(process.execPath, [path.join(BURADA, "gercek-sunucu.mjs")], {
    env: {
      ...process.env,
      RESEND_API_KEY: "sahte-anahtar",
      RESEND_URL: `http://127.0.0.1:${sahteResend.address().port}/emails`,
      DEPO_DOSYA: depo,
      PORT: String(PORT),
      ONAY_PORT: String(ONAY_PORT),
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let log = "";
  srv.stdout.on("data", (c) => (log += c));
  srv.stderr.on("data", (c) => (log += c));
  for (let i = 0; i < 100 && !(log.includes("HAZIR") && log.includes("onay linkleri")); i++)
    await new Promise((r) => setTimeout(r, 50));

  const post = (govde) =>
    new Promise((ok, red) => {
      const q = https.request(
        { host: "127.0.0.1", port: PORT, path: "/api/gonder", method: "POST",
          rejectUnauthorized: false, headers: { "Content-Type": "application/json" } },
        (r) => { let b = ""; r.on("data", (c) => (b += c)); r.on("end", () => ok({ kod: r.statusCode, b })); }
      );
      q.on("error", red);
      q.end(JSON.stringify(govde));
    });
  const al = (url) =>
    new Promise((ok, red) => {
      http.get(url, (r) => { let b = ""; r.on("data", (c) => (b += c)); r.on("end", () => ok({ kod: r.statusCode, b })); }).on("error", red);
    });

  try {
    let r = await post(K1);
    bak(r.kod === 202 && JSON.parse(r.b).durum === "onay-bekliyor", "onaysiz POST -> 202 onay-bekliyor");
    bak(giden.length === 1 && giden[0].subject === "Candy sana ulasmak istiyor", "yalnizca ONAY maili gitti (korku gitmedi)");
    const link = (giden[0]?.text || "").match(/http:\/\/localhost:18080\/api\/onayla\?e=[^\s]+&t=[0-9a-f]{32}/)?.[0];
    bak(!!link, "onay linki duz http://localhost (sertifika uyarisi yok)");

    r = await post(K2);
    bak(r.kod === 202 && giden.length === 1, "ikinci onaysiz POST -> ikinci onay maili YOK");

    r = await al(`http://localhost:${ONAY_PORT}/api/onayla?e=${encodeURIComponent(TO)}&t=${"0".repeat(32)}`);
    bak(r.kod === 400 && giden.length === 1, "yanlis token -> 400, mail yok");

    r = await al(link);
    bak(r.kod === 200 && r.b.includes("ilk mektubu su an yola cikti"), "dogru linke tiklama -> 200 'ilk mektubu su an yola cikti'");
    bak(giden.length === 2 && giden[1].subject === K2.konu && giden[1].to === TO, `saklanan korku maili HEMEN gitti ("${giden[1]?.subject}")`);

    r = await al(link);
    bak(r.kod === 400 && giden.length === 2, "ayni linke ikinci tiklama -> 400, ikinci kez mail yok");

    r = await post(K1);
    bak(r.kod === 200 && giden.length === 3 && giden[2].subject === K1.konu, "onayli adrese POST -> dogrudan gitti");

    const d = JSON.parse(fs.readFileSync(depo, "utf8"));
    bak(d.onay[TO] === true && !d.token[TO] && !d.bekleyen[TO], "depo: onay=true, token ve bekleyen temizlendi");
  } finally {
    srv.kill();
    sahteResend.close();
    fs.rmSync(gecici, { recursive: true, force: true });
  }
}

// ─── B) Vercel surumu ───────────────────────────────────────────────────────
async function vercelUc() {
  console.log("B) ../gonder.js + ../onayla.js (sahte KV + sahte Resend)");
  const KV = new Map();
  const giden = [];
  process.env.KV_REST_API_URL = "https://sahte-kv.test";
  process.env.KV_REST_API_TOKEN = "sahte";
  process.env.RESEND_API_KEY = "sahte-anahtar";
  const gercekFetch = globalThis.fetch;
  globalThis.fetch = async (url, secenek = {}) => {
    url = String(url);
    if (url.startsWith("https://sahte-kv.test/")) {
      const [komut, ...arg] = url.slice(22).split("/").map(decodeURIComponent);
      let result;
      if (komut === "get") result = KV.has(arg[0]) ? KV.get(arg[0]) : null;
      else if (komut === "getdel") { result = KV.has(arg[0]) ? KV.get(arg[0]) : null; KV.delete(arg[0]); }
      else if (komut === "set") {
        // Redis gibi: NX varsa ve anahtar zaten varsa yazmaz, null doner (EX burada yok sayilir).
        if (arg.includes("NX") && KV.has(arg[0])) result = null;
        else { KV.set(arg[0], arg[1]); result = "OK"; }
      }
      else if (komut === "del") result = KV.delete(arg[0]) ? 1 : 0;
      else throw new Error(`sahte KV bu komutu bilmiyor: ${komut}`);
      return new Response(JSON.stringify({ result }), { status: 200 });
    }
    if (url === "https://api.resend.com/emails") {
      giden.push(JSON.parse(secenek.body));
      return new Response(JSON.stringify({ id: `sahte-${giden.length}` }), { status: 200 });
    }
    throw new Error(`beklenmeyen ag istegi: ${url}`);
  };

  try {
    const ust = path.join(BURADA, "..");
    const gonder = (await import(pathToFileURL(path.join(ust, "gonder.js")).href)).default;
    const onayla = (await import(pathToFileURL(path.join(ust, "onayla.js")).href)).default;
    const yanit = () => {
      const r = { kod: 0, govde: null, baslik: {} };
      r.setHeader = (k, v) => ((r.baslik[k.toLowerCase()] = v), r);
      r.status = (k) => ((r.kod = k), r);
      r.json = (j) => ((r.govde = j), r);
      r.send = (s) => ((r.govde = s), r);
      r.end = () => r;
      return r;
    };
    const istek = (body) => ({ method: "POST", body, headers: { host: "candy.example", "x-forwarded-proto": "https" } });

    let r = yanit();
    await gonder(istek(K1), r);
    bak(r.kod === 202 && r.govde.durum === "onay-bekliyor", "onaysiz POST -> 202 onay-bekliyor");
    bak(giden.length === 1 && giden[0].subject === "Candy sana ulaşmak istiyor", "yalnizca ONAY maili gitti (korku gitmedi)");
    const m = (giden[0]?.text || "").match(/https:\/\/candy\.example\/api\/onayla\?e=([^&\s]+)&t=([0-9a-f]{32})/);
    bak(!!m, "onay linki ucun kendi adresine gidiyor");
    const sorgu = { e: decodeURIComponent(m?.[1] || ""), t: m?.[2] };

    r = yanit();
    await gonder(istek(K2), r);
    bak(r.kod === 202 && giden.length === 1, "ikinci onaysiz POST -> ikinci onay maili YOK");

    r = yanit();
    await onayla({ method: "GET", query: { e: TO, t: "0".repeat(32) } }, r);
    bak(r.kod === 400 && giden.length === 1, "yanlis token -> 400, mail yok");

    // Iki adim: link tarayicilarinin GET'i onaylamamali, yalnizca POST formu gostermeli.
    r = yanit();
    await onayla({ method: "GET", query: sorgu }, r);
    bak(r.kod === 200 && /<form method="POST"/.test(String(r.govde)) && giden.length === 1 && !KV.has(`candy:onay:${TO}`),
      "GET linki -> yalnizca 'Onayla' formu; onay yok, mail yok");
    bak(r.baslik["cache-control"] === "no-store", "onay sayfasi onbelleklenmiyor");

    r = yanit();
    await onayla({ method: "POST", query: sorgu }, r);
    bak(r.kod === 200 && String(r.govde).includes("ilk mektubu şu an yola çıktı"), "Onayla dugmesi (POST) -> 200 'ilk mektubu su an yola cikti'");
    bak(giden.length === 2 && giden[1].subject === K2.konu && giden[1].to === TO, `saklanan korku maili HEMEN gitti ("${giden[1]?.subject}")`);

    r = yanit();
    await onayla({ method: "POST", query: sorgu }, r);
    bak(r.kod === 400 && giden.length === 2, "ayni linke ikinci tiklama -> 400, ikinci kez mail yok");

    r = yanit();
    await gonder(istek(K1), r);
    bak(r.kod === 200 && r.govde.durum === "gonderildi" && giden.length === 3, "onayli adrese POST -> dogrudan gitti");

    r = yanit();
    await gonder(istek(K2), r);
    bak(r.kod === 429 && r.govde.durum === "cok-sik" && giden.length === 3, "15 dk icinde ikinci POST -> 429, mail yok");
    bak(KV.get(`candy:onay:${TO}`) === "1" && !KV.has(`candy:token:${TO}`) && !KV.has(`candy:bekleyen:${TO}`),
      "KV: onay=1, token ve bekleyen temizlendi");
  } finally {
    globalThis.fetch = gercekFetch;
  }
}

await yerelUc();
await vercelUc();
console.log(hata === 0 ? "SONUC: HEPSI GECTI" : `SONUC: ${hata} KONTROL KALDI`);
process.exit(hata === 0 ? 0 : 1);
