#!/usr/bin/env bash
# Creates the release signing key ONCE and stores it on the "signing-keys" branch.
# Every later build reuses the same key, so new APKs always install over old ones.
set -euo pipefail

BR="signing-keys"
REPO_URL="https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
mkdir -p signing
work="$(mktemp -d)"

if git ls-remote --exit-code --heads "$REPO_URL" "$BR" >/dev/null 2>&1; then
  git clone -q --depth 1 --branch "$BR" "$REPO_URL" "$work"
  if [ -f "$work/uctor.jks.enc" ]; then
    if [ -z "${SIGNING_PASSWORD:-}" ]; then
      echo "::error::کلید امضا رمزگذاری شده ولی Secret به نام SIGNING_PASSWORD پیدا نشد. همان رمز قبلی را دوباره در Secrets بگذارید."
      exit 1
    fi
    openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in "$work/uctor.jks.enc" -out signing/uctor.jks -pass env:SIGNING_PASSWORD \
      || { echo "::error::رمز SIGNING_PASSWORD با رمز قبلی یکی نیست."; exit 1; }
  else
    cp "$work/uctor.jks" signing/uctor.jks
  fi
  cp "$work/signing.properties" signing/signing.properties
  echo "Reusing the existing signing key."
  exit 0
fi

if [ "${IS_PRIVATE:-false}" != "true" ] && [ -z "${SIGNING_PASSWORD:-}" ]; then
  echo "::error::ریپو عمومی (Public) است. برای امنیت کلید امضا، اول در Settings > Secrets and variables > Actions یک Secret به نام SIGNING_PASSWORD با یک رمز طولانی بسازید و دوباره اجرا کنید."
  exit 1
fi

echo "Creating a NEW signing key (this happens only once)."
PASS="$(openssl rand -hex 24)"
keytool -genkeypair -noprompt -keystore signing/uctor.jks -storetype PKCS12 \
  -alias uctor -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=UC Tor, O=UCIRAN, C=IR"
cat > signing/signing.properties <<PROPS
storePassword=$PASS
keyPassword=$PASS
keyAlias=uctor
PROPS

cd "$work"
git init -q
git checkout -q -b "$BR"
cp "$OLDPWD/signing/signing.properties" .
if [ -n "${SIGNING_PASSWORD:-}" ]; then
  openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -in "$OLDPWD/signing/uctor.jks" -out uctor.jks.enc -pass env:SIGNING_PASSWORD
else
  cp "$OLDPWD/signing/uctor.jks" .
fi
cat > README.md <<'TXT'
# UC Tor signing key

این شاخه کلید امضای برنامه را نگه می‌دارد. **هرگز حذفش نکنید.**
اگر این کلید (یا رمز SIGNING_PASSWORD) از بین برود، نسخه‌های جدید روی نسخه‌ی قبلی نصب نمی‌شوند.
یک نسخه‌ی پشتیبان از این شاخه و رمز را جای امنی نگه دارید.
TXT
git add -A
git -c user.name="uctor-bot" -c user.email="uctor-bot@users.noreply.github.com" commit -q -m "Create signing key [skip ci]"
git push -q "$REPO_URL" "$BR"
echo "Signing key created and saved to branch '$BR'."
