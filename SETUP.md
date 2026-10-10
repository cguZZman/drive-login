# Hosting your own sign-in service

This guide sets up your own instance of drive-login, so your Kodi add-ons sign in through a server you control, with your own Google and Microsoft OAuth apps.

You need:

- Java 25 (the Maven wrapper in this repository downloads everything else).
- An address reachable over **HTTPS** by your phone or computer and by your Kodi devices, for example a Heroku app or your own server behind a reverse proxy.
- A Google Cloud project for Google Drive, a Microsoft Entra app registration for OneDrive, or both.

In the steps below, `https://login.example.com` stands for your server's address.

## 1. Create the OAuth apps

The service only needs the providers you want to use.

### Google Drive

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project and enable the **Google Drive API**.
2. Under **Google Auth Platform**:
   - **Branding:** set the app name, a support email, and your server's home page and privacy policy URLs (`https://login.example.com/` and `https://login.example.com/privacypolicy`).
   - **Audience:** choose **External**.
   - **Data Access:** add the scope `https://www.googleapis.com/auth/drive.readonly`. It is the only scope the service requests.
   - **Clients:** create a client of type **Web application**, with the authorized redirect URI `https://login.example.com/callback`. Keep the client ID and client secret.
3. Decide the publishing status:
   - **Testing:** only the Google accounts you add as test users can sign in, and their sign-ins expire after 7 days (Google limits refresh tokens of apps in testing).
   - **In production without verification:** sign-ins don't expire, but users see a "Google hasn't verified this app" warning and the app is limited to 100 users. This is usually the right choice for personal or family use.
   - **Verified:** needed for public use. `drive.readonly` is a restricted scope, so Google's review is extensive.

### OneDrive

1. In the [Microsoft Entra admin center](https://entra.microsoft.com/), go to **App registrations → New registration**.
   - **Supported account types:** "Accounts in any organizational directory and personal Microsoft accounts" (needed for personal OneDrive).
   - **Redirect URI:** platform **Web**, `https://login.example.com/callback`.
2. **Certificates & secrets → New client secret.** Copy the value (not the ID). Client secrets expire (at most after 24 months), so note when to renew it.
3. **API permissions → Add a permission → Microsoft Graph → Delegated permissions:** `Files.Read.All`, `Sites.Read.All`, `User.Read` and `offline_access`.

## 2. Configure the service

All settings are environment variables.

### Required

| Variable | Value |
|---|---|
| `CALLBACK_URL` | `https://login.example.com/callback`. Must match the redirect URI registered with each provider exactly. |

For **Google Drive**:

| Variable | Value |
|---|---|
| `PROVIDER_GOOGLEDRIVE_CLIENT_ID` | Your Google client ID |
| `PROVIDER_GOOGLEDRIVE_CLIENT_SECRET` | Your Google client secret |
| `PROVIDER_GOOGLEDRIVE_URL_AUTHORIZE` | `https://accounts.google.com/o/oauth2/v2/auth` |
| `PROVIDER_GOOGLEDRIVE_URL_TOKEN` | `https://oauth2.googleapis.com/token` |

For **OneDrive**:

| Variable | Value |
|---|---|
| `PROVIDER_ONEDRIVE_CLIENT_ID` | Your Entra application (client) ID |
| `PROVIDER_ONEDRIVE_CLIENT_SECRET` | Your Entra client secret value |
| `PROVIDER_ONEDRIVE_URL_AUTHORIZE` | `https://login.microsoftonline.com/common/oauth2/v2.0/authorize` |
| `PROVIDER_ONEDRIVE_URL_TOKEN` | `https://login.microsoftonline.com/common/oauth2/v2.0/token` |

### Optional

| Variable | Default | Effect |
|---|---|---|
| `REQUIRE_HTTPS` | `true` | Redirects plain HTTP to HTTPS (301 for GET/HEAD, 308 for other methods). Set to `false` only when running locally without TLS. |
| `RATE_LIMIT_PIN` | `10` | `POST /pin` requests per minute per IP. |
| `RATE_LIMIT_SIGNIN` | `20` | `/authorize` and `/signin/*` requests per minute per IP. |
| `RATE_LIMIT_REFRESH_IP` | `60` | `POST /refresh` requests per minute per IP. |
| `RATE_LIMIT_REFRESH_TOKEN` | `10` | `POST /refresh` requests per minute per refresh token (counted by hash). |

Set a rate limit to `0` to disable it.

Keep the client secrets out of the repository: set them in your host's configuration or in an environment file only you can read.

## 3. Run it locally

Useful to try your OAuth apps before deploying. Register `http://localhost:8080/callback` as an additional redirect URI with each provider (both allow plain HTTP for `localhost`).

```bash
./mvnw package
```

Put the variables in a file only you can read, for example `~/.config/drive-login/local.env`:

```bash
CALLBACK_URL=http://localhost:8080/callback
REQUIRE_HTTPS=false
PROVIDER_GOOGLEDRIVE_CLIENT_ID=...
PROVIDER_GOOGLEDRIVE_CLIENT_SECRET=...
PROVIDER_GOOGLEDRIVE_URL_AUTHORIZE=https://accounts.google.com/o/oauth2/v2/auth
PROVIDER_GOOGLEDRIVE_URL_TOKEN=https://oauth2.googleapis.com/token
```

Then start the server with it:

```bash
(set -a; . ~/.config/drive-login/local.env; set +a; java -jar target/drive-login-1.0.0.jar --server.port=8080)
```

Open <http://localhost:8080>. Kodi on the same machine can use `http://localhost:8080` as its sign-in server (see step 5).

## 4. Deploy it

The service is a single Spring Boot application (`target/drive-login-1.0.0.jar`) that listens on the port given by `--server.port` (8080 by default; on Heroku the `Procfile` passes `$PORT`).

Whatever the host:

- **Run exactly one instance.** Sign-in codes and rate-limit counters live in memory, so a second instance wouldn't know the codes created by the first.
- **Serve it over HTTPS.** Browsers and providers require it for the sign-in, and the add-ons send tokens to it.
- **Client IP behind a proxy.** The service pairs the browser with the Kodi device by public IP address, and uses it for rate limits. It reads the client IP from `X-Forwarded-For` and the scheme from `X-Forwarded-Proto`, but only when the request comes from a proxy on a private network (for example Heroku's router, or nginx or Caddy on the same machine). If your proxy connects from a public address, tell the service to trust it with `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (a regular expression matching the proxy's IP addresses). Otherwise every user appears to come from the proxy.

### Heroku

The repository includes a `Procfile` and `system.properties` (Java 25).

```bash
heroku create my-drive-login
heroku stack:set heroku-26 -a my-drive-login
heroku config:set -a my-drive-login CALLBACK_URL=https://my-drive-login.herokuapp.com/callback PROVIDER_GOOGLEDRIVE_CLIENT_ID=... PROVIDER_GOOGLEDRIVE_CLIENT_SECRET=... PROVIDER_GOOGLEDRIVE_URL_AUTHORIZE=https://accounts.google.com/o/oauth2/v2/auth PROVIDER_GOOGLEDRIVE_URL_TOKEN=https://oauth2.googleapis.com/token
git push https://git.heroku.com/my-drive-login.git master:main
```

A single Eco or Basic dyno is enough.

### Your own server

Run the jar as a service (for example with systemd) behind a reverse proxy that terminates TLS and forwards `X-Forwarded-For` and `X-Forwarded-Proto`. A minimal Caddy configuration:

```
login.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

## 5. Point the Kodi add-ons at your server

In Kodi, open the settings of the Google Drive or OneDrive add-on, go to **Advanced → Sign-in Server**, and enter your server's address (for example `https://login.example.com`). Then add your account again from the add-on.

The phone or computer you sign in with must be on the same network as the Kodi device, because the service pairs them by public IP address.

## 6. Adapt the pages to your instance

The pages describe the public instance. Before others use yours, review:

- `src/main/resources/templates/privacypolicy.html`: who runs the service, where it is hosted, and how to contact you. Google and Microsoft review this page if you publish your apps.
- `src/main/resources/messages.properties`: the service name and page texts.
- `src/main/resources/templates/fragments/layout.html`: the footer links.

## Endpoints

| Endpoint | Used by | Purpose |
|---|---|---|
| `POST /pin` | Kodi | Create a sign-in code |
| `GET /pin/{code}` | Kodi | Collect the tokens once the sign-in is done |
| `POST /refresh` | Kodi | Refresh an access token |
| `GET /ip` | Kodi | Show the public IP the service sees (troubleshooting) |
| `GET /`, `POST /authorize`, `GET /signin/{code}` | Browser | Enter the code (form or QR link) and go to the provider |
| `GET` or `POST /callback` | Provider | Return from the sign-in |
| `GET /privacypolicy` | Browser | Privacy policy |
