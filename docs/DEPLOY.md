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

## 9. Fill in the README placeholders

Replace `<DEPLOYED_URL>`, `<DEMO_API_TOKEN>`, `<SANDBOX_BUYER_EMAIL>` and `<SANDBOX_BUYER_PASSWORD>` in
`README.md` with the real values before submitting. The demo token is published to judges by design, so
it must be the server token and not one you use anywhere else.

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
