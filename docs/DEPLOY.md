# Deploying Pylos to a VPS

Written for an OVHcloud VPS-1 running Ubuntu, but nothing here is OVH specific.

You need a domain name pointing at the VPS. The extension refuses to send its bearer token to a plain
`http` host, so TLS is required, and TLS needs a hostname.

## 1. Point DNS at the VPS

Create an `A` record for the host you will use, for example `pylos.yourdomain.com`, pointing at the
VPS public IPv4 address. Wait until it resolves:

```bash
dig +short pylos.yourdomain.com
```

Caddy cannot issue a certificate until this answers with the VPS IP.

## 2. Open the firewall

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Port 8080 stays closed. The app binds to `127.0.0.1:8080` and is only reachable through Caddy.

## 3. Install Docker

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

Log out and back in so the group membership applies.

## 4. Get the code

```bash
git clone https://github.com/DreadVoice/Pylos.git
cd Pylos
```

## 5. Write the environment file

```bash
cp .env.example .env
nano .env
```

Fill in every value. Notes on the ones that matter:

| Variable | Notes |
|---|---|
| `PYLOS_DOMAIN` | The hostname from step 1. Caddy requests a certificate for exactly this name |
| `PYLOS_API_TOKEN` | Generate a **new** one for the server. Do not reuse your local token |
| `PYLOS_ALLOWED_ORIGINS` | `chrome-extension://*` so the extension can call the API |
| `POSTGRES_PASSWORD` | Any strong value. Only the app container ever connects |
| `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` | Your sandbox REST app credentials |
| `GEMINI_API_KEY` | Your AI Studio key |
| `SANDBOX_BUYER_EMAIL` | The sandbox personal account the demo invoices go to |

Generate a server token with:

```bash
openssl rand -base64 32 | tr -d '/+=' | cut -c1-32
```

Keep `.env` out of git. It already is: the repository ignores `.env` and `*.env`.

## 6. Start it

```bash
docker compose up -d --build
```

The first build downloads the Maven dependency tree and takes a few minutes. Caddy will request a
certificate as soon as it starts, so DNS must already be correct.

## 7. Check it came up

```bash
docker compose ps
docker compose logs app | grep -E 'Prod configuration check|Successfully applied|Started PylosApplication'
curl -fsS https://pylos.yourdomain.com/demo > /dev/null && echo "demo page ok"
curl -s -o /dev/null -w '%{http_code}\n' https://pylos.yourdomain.com/api/actions
```

The last command must print `401`. If it prints `200`, the API is unauthenticated — stop and fix
`PYLOS_API_TOKEN` before going further.

The app refuses to start on the `prod` profile unless `POSTGRES_URL` is a `jdbc:postgresql://` URL and
`PYLOS_API_TOKEN` is set, so a misconfigured deploy fails loudly at boot rather than silently exposing
money-moving endpoints.

## 8. Point the extension at it

On your machine, edit `extension/public/manifest.json` and add the deployed host to both lists:

```json
"host_permissions": [
  "http://localhost:8080/*",
  "http://127.0.0.1:8080/*",
  "https://pylos.yourdomain.com/*"
],
"content_scripts": [
  {
    "matches": [
      "http://localhost:8080/demo/*",
      "http://127.0.0.1:8080/demo/*",
      "https://pylos.yourdomain.com/demo/*"
    ],
    "js": ["content.js"],
    "run_at": "document_idle"
  }
]
```

Then rebuild and zip for the submission:

```bash
cd extension && npm run verify
cd dist && zip -r ../pylos-extension.zip .
```

`npm run verify` enforces that every host pattern is a specific `https` host or localhost. A wildcard
or a plain `http` remote host fails the build.

## 9. Register the PayPal webhook

Webhooks are what make the side panel light up when a buyer pays or a refund settles. PayPal has to be
able to reach the deployment over HTTPS, which it now can.

In the [developer dashboard](https://developer.paypal.com), open your sandbox REST app, scroll to
**Webhooks**, and add one:

| Field | Value |
|---|---|
| Webhook URL | `https://pylos.yourdomain.com/webhooks/paypal` |
| Event types | `INVOICING.INVOICE.PAID`, `INVOICING.INVOICE.CANCELLED`, `PAYMENT.CAPTURE.COMPLETED`, `PAYMENT.CAPTURE.REFUNDED` |

Copy the webhook id PayPal shows into `PAYPAL_WEBHOOK_ID` in `.env` and restart:

```bash
docker compose up -d
```

The same thing over the API, if you prefer:

```bash
TOKEN=$(curl -s -u "$PAYPAL_CLIENT_ID:$PAYPAL_CLIENT_SECRET"   -d grant_type=client_credentials   https://api-m.sandbox.paypal.com/v1/oauth2/token | python -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')

curl -s -X POST https://api-m.sandbox.paypal.com/v1/notifications/webhooks   -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json'   -d '{"url":"https://pylos.yourdomain.com/webhooks/paypal",
       "event_types":[{"name":"INVOICING.INVOICE.PAID"},
                      {"name":"INVOICING.INVOICE.CANCELLED"},
                      {"name":"PAYMENT.CAPTURE.COMPLETED"},
                      {"name":"PAYMENT.CAPTURE.REFUNDED"}]}'
```

`PAYPAL_WEBHOOK_ID` is what the signature check is made against. **Until it is set, every webhook is
rejected with a 401** — the verifier fails closed rather than trusting an unverified event. That is the
safe default, but it does mean an unset id looks like a working deployment with notifications that never
arrive. Check for this in the logs:

```bash
docker compose logs app | grep 'webhook-id is not configured'
```

The webhook endpoint is the one path outside `/api` that needs no bearer token, because PayPal cannot
send one. It is protected instead by asking PayPal to verify the signature of every event, and the
certificate url is checked to be a `paypal.com` host before anything is sent.

## 10. Fill in the README placeholders

Replace `<DEPLOYED_URL>`, `<DEMO_API_TOKEN>`, `<SANDBOX_BUYER_EMAIL>` and `<SANDBOX_BUYER_PASSWORD>` in
`README.md` with the real values before submitting. The demo token is published to judges by design, so
it must be the server token and not one you use anywhere else.

Read the token off the server rather than inventing one:

```bash
ssh ubuntu@your.server 'grep "^PYLOS_API_TOKEN=" ~/Pylos/.env | cut -d= -f2-'
```

The sandbox buyer password is the one you set on the personal account under **Testing Tools → Sandbox
accounts** at [developer.paypal.com](https://developer.paypal.com).

### What publishing the token means, and undoing it

A reader of the repository can call every `/api` endpoint on the deployment. That is the point — judges
need it — but be clear about the exposure:

- The deployment is sandbox only (`PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com`), so no real money
  can move. Keep it that way for as long as the token is published.
- A caller can create and send sandbox invoices, propose actions, and refund **within** what PayPal says
  was captured. The refund cap is enforced against PayPal, not against anything a caller controls.
- A caller can spend your Gemini quota through `/api/assist`, but not without limit: five requests per
  client per minute and two hundred across the whole deployment per day, after which it answers `429`
  with a `Retry-After`. The limits are in-memory and per instance, which is fine for one container.

Once judging is over, rotate the token and take it out of `README.md`:

```bash
ROTATE_SECRETS=1 deploy/remote-deploy.sh <host> <ssh-user> <domain>
```

That regenerates the API token while keeping the database password, so the app still reaches its
database. Update the extension options afterwards.

## Doing all of that with the script

`deploy/remote-deploy.sh` does steps 2 to 7 over SSH. Run it from the repository root, with a filled in
`backend/.env`:

```bash
deploy/remote-deploy.sh 203.0.113.10 ubuntu pylos.yourdomain.com
```

It is safe to run again. On the first run it generates a `PYLOS_API_TOKEN` and a `POSTGRES_PASSWORD`;
on every run after that it reads the ones already on the server and keeps them, so the token you
published to judges stays valid and the app keeps the password its database was created with. It prints
which it did:

```
api token        : reused from the server
database password: reused from the server
```

`PAYPAL_WEBHOOK_ID` is taken from `backend/.env` if set there, and otherwise kept from the server, so
registering the webhook once is enough.

`ROTATE_SECRETS=1` forces a new API token, which is harmless — update `README.md` and the extension
options afterwards. The database password is left alone, because rotating it is not harmless: Postgres
only reads `POSTGRES_PASSWORD` when it first creates its data directory, so a new one takes effect only
if the `pylos-db` volume is recreated, which discards every stored action and notification.
`ROTATE_DB_PASSWORD=1` does it anyway, with that warning printed.

On Windows, `SSH_BIN` points the script at a specific ssh binary, which matters when Git Bash's ssh
cannot see the Windows ssh-agent:

```bash
SSH_BIN=/c/Windows/System32/OpenSSH/ssh.exe deploy/remote-deploy.sh 203.0.113.10 ubuntu pylos.yourdomain.com
```

## Updating a running deployment

```bash
git pull
docker compose up -d --build
```

Flyway applies any new migrations on boot and Hibernate validates the entities against the result. If a
migration is missing, the app refuses to start rather than running against a schema it does not match.

## Backups

The database lives in the `pylos-db` volume.

```bash
docker compose exec -T db pg_dump -U pylos pylos > pylos-$(date +%F).sql
```

Only proposals and their outcomes live there. Invoices themselves live in PayPal.
