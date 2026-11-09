#!/usr/bin/env bash
# Candy — yerel mektup ucu icin sertifika + guven deposu uretir.
#
# NIYE TLS: MektupGonder yalnizca "https://" uclara yolluyor (duz http = sus). Bu
# bilincli bir guvenlik kapisi, test icin gevsetilmiyor. O yuzden yerel uc de gercek
# TLS konusuyor ve istemci JVM'i bu sertifikayi taniyan bir guven deposuyla baslatiliyor.
#
# Uretilenler (hicbiri commit edilmez, bkz .gitignore):
#   localhost-key.pem / localhost-cert.pem  — ucun TLS anahtari + sertifikasi
#   candy-test-cacerts                      — JDK cacerts KOPYASI + bu sertifika
set -euo pipefail
cd "$(dirname "$0")"

GUN=${GUN:-30}
PORT=${PORT:-8443}

echo "[kur] self-signed sertifika (CN=localhost, SAN=localhost/127.0.0.1, ${GUN} gun)"
# MSYS2_ARG_CONV_EXCL: Git Bash "-subj /CN=localhost" argumanini Windows yoluna
# cevirip openssl'i bozuyor; bu degisken o cevirimi kapatiyor.
MSYS2_ARG_CONV_EXCL='*' openssl req -x509 -newkey rsa:2048 -nodes -days "$GUN" \
  -keyout localhost-key.pem -out localhost-cert.pem \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

JH=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/.*java\.home = *//p')
SRC="$JH/lib/security/cacerts"
[ -f "$SRC" ] || { echo "[kur] HATA: cacerts bulunamadi: $SRC"; exit 1; }

echo "[kur] guven deposu: JDK cacerts kopyasi + yerel sertifika"
# KOPYA kullaniliyor: JDK'nin kendi deposuna dokunulmuyor, ve istemci hala tum gercek
# CA'lari taniyor (Mojang oturum sunucusu dahil) — yalnizca bir sertifika EKLENIYOR.
rm -f candy-test-cacerts
cp "$SRC" candy-test-cacerts
keytool -importcert -noprompt -trustcacerts \
  -alias candy-yerel-test -file localhost-cert.pem \
  -keystore candy-test-cacerts -storepass changeit >/dev/null

echo "[kur] TAMAM"
echo "      guven deposu : $(pwd)/candy-test-cacerts  (sifre: changeit)"
echo "      uc           : https://localhost:${PORT}/api/gonder"
