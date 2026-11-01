#!/usr/bin/env bash
set -euo pipefail

HOST="${1:-}"
SSH_USER="${2:-}"
DOMAIN="${3:-}"
REPO="${REPO:-https://github.com/DreadVoice/Pylos.git}"
LOCAL_ENV="${LOCAL_ENV:-backend/.env}"

if [ -z "$HOST" ] || [ -z "$SSH_USER" ] || [ -z "$DOMAIN" ]; then
  echo "usage: deploy/remote-deploy.sh <host> <ssh-user> <domain>" >&2
  echo "example: deploy/remote-deploy.sh 15.235.248.3 ubuntu pylos.example.com" >&2
  exit 2
fi

if [ ! -f "$LOCAL_ENV" ]; then
  echo "cannot find $LOCAL_ENV; run this from the repository root" >&2
  exit 2
fi

read_local() {
  grep "^$1=" "$LOCAL_ENV" | head -1 | cut -d= -f2- | tr -d '\r'
}

PAYPAL_CLIENT_ID=$(read_local PAYPAL_CLIENT_ID)
PAYPAL_CLIENT_SECRET=$(read_local PAYPAL_CLIENT_SECRET)
PAYPAL_BASE_URL=$(read_local PAYPAL_BASE_URL)
GEMINI_API_KEY=$(read_local GEMINI_API_KEY)
GEMINI_MODEL=$(read_local GEMINI_MODEL)
GEMINI_FALLBACK_MODEL=$(read_local GEMINI_FALLBACK_MODEL)
SANDBOX_BUYER_EMAIL=$(read_local SANDBOX_BUYER_EMAIL)

for required in PAYPAL_CLIENT_ID PAYPAL_CLIENT_SECRET GEMINI_API_KEY; do
  if [ -z "${!required}" ]; then
    echo "$required is empty in $LOCAL_ENV" >&2
    exit 2
  fi
done

SERVER_TOKEN=$(head -c 32 /dev/urandom | base64 | tr -d '/+=' | cut -c1-32)
DB_PASSWORD=$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | cut -c1-24)

SSH_BIN="${SSH_BIN:-ssh}"
SSH="$SSH_BIN -o StrictHostKeyChecking=accept-new ${SSH_USER}@${HOST}"

echo "==> checking dns for $DOMAIN"
RESOLVED=$(getent hosts "$DOMAIN" 2>/dev/null | awk '{print $1}' | head -1 || nslookup "$DOMAIN" 2>/dev/null | awk '/^Address: /{print $2}' | tail -1 || true)
if [ -z "$RESOLVED" ]; then
  echo "warning: $DOMAIN does not resolve yet; Caddy will not get a certificate" >&2
elif [ "$RESOLVED" != "$HOST" ]; then
  echo "warning: $DOMAIN resolves to $RESOLVED, not $HOST" >&2
fi

echo "==> installing docker on the server"
$SSH 'set -e
  if ! command -v docker >/dev/null; then
    sudo apt-get update -qq
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq docker.io docker-compose-v2 git
    sudo systemctl enable --now docker
    sudo usermod -aG docker "$USER"
  fi
  docker --version'

echo "==> fetching the code"
$SSH "set -e
  if [ -d ~/Pylos/.git ]; then
    cd ~/Pylos && git fetch --quiet origin && git reset --hard --quiet origin/main
  else
    git clone --quiet '$REPO' ~/Pylos
  fi
  cd ~/Pylos && git log --oneline -1"

echo "==> writing the server environment file"
$SSH "cat > ~/Pylos/.env" <<EOF
PYLOS_DOMAIN=${DOMAIN}
POSTGRES_USERNAME=pylos
POSTGRES_PASSWORD=${DB_PASSWORD}
PYLOS_API_TOKEN=${SERVER_TOKEN}
PYLOS_ALLOWED_ORIGINS=chrome-extension://*
PAYPAL_CLIENT_ID=${PAYPAL_CLIENT_ID}
PAYPAL_CLIENT_SECRET=${PAYPAL_CLIENT_SECRET}
PAYPAL_BASE_URL=${PAYPAL_BASE_URL:-https://api-m.sandbox.paypal.com}
PAYPAL_WEBHOOK_ID=
GEMINI_API_KEY=${GEMINI_API_KEY}
GEMINI_MODEL=${GEMINI_MODEL:-gemini-3.5-flash}
GEMINI_FALLBACK_MODEL=${GEMINI_FALLBACK_MODEL:-gemini-3.5-flash-lite}
SANDBOX_BUYER_EMAIL=${SANDBOX_BUYER_EMAIL}
EOF
$SSH 'chmod 600 ~/Pylos/.env && echo "env written"'

echo "==> opening the firewall"
$SSH 'set -e
  if command -v ufw >/dev/null; then
    sudo ufw allow 22/tcp >/dev/null
    sudo ufw allow 80/tcp >/dev/null
    sudo ufw allow 443/tcp >/dev/null
    sudo ufw --force enable >/dev/null
    sudo ufw status | head -6
  else
    echo "ufw not installed, skipping"
  fi'

echo "==> building and starting (this takes a few minutes on first run)"
$SSH 'cd ~/Pylos && sudo docker compose up -d --build 2>&1 | tail -12'

echo "==> waiting for the app"
$SSH 'cd ~/Pylos
  for i in $(seq 1 60); do
    code=$(sudo docker compose exec -T app curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/demo 2>/dev/null || true)
    if [ "$code" = "200" ]; then echo "app ready after ${i}s"; break; fi
    sleep 3
  done
  sudo docker compose logs app 2>/dev/null | grep -E "Prod configuration check|Successfully applied|Started PylosApplication|Refusing to start" | tail -5'

echo
echo "==> verifying from the public internet"
sleep 5
echo -n "GET https://${DOMAIN}/demo        -> "
curl -s -o /dev/null -w '%{http_code}\n' --max-time 20 "https://${DOMAIN}/demo" || echo "unreachable"
echo -n "GET https://${DOMAIN}/api/actions -> "
curl -s -o /dev/null -w '%{http_code}\n' --max-time 20 "https://${DOMAIN}/api/actions" || echo "unreachable"
echo -n "with the token                    -> "
curl -s -o /dev/null -w '%{http_code}\n' --max-time 20 -H "Authorization: Bearer ${SERVER_TOKEN}" "https://${DOMAIN}/api/actions" || echo "unreachable"

echo
echo "================ deployment summary ================"
echo "demo page   : https://${DOMAIN}/demo/chat"
echo "backend url : https://${DOMAIN}"
echo "api token   : ${SERVER_TOKEN}"
echo
echo "The /api/actions check above must say 401 without the token and 200 with it."
echo "Put the backend url and api token in README.md and in the extension options."
echo "Add https://${DOMAIN}/* to host_permissions and content_scripts in"
echo "extension/public/manifest.json, then run npm run verify and rebuild."
