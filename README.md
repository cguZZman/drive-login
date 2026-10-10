# drive-login

The sign-in service used by the [Google Drive](https://github.com/cguZZman/plugin.googledrive) and [OneDrive](https://github.com/cguZZman/plugin.onedrive) add-ons for Kodi.

A TV can't easily show a Google or Microsoft sign-in page, and the add-ons can't keep an OAuth client secret private. This small web service does both jobs: it lets you sign in from your phone or computer with a short code shown in Kodi, and it adds the client secret when the add-on refreshes its tokens.

The public instance runs at <https://drive-login.herokuapp.com>. You can also [host your own](SETUP.md) and point the add-ons at it.

Its successor, [signin-server](https://github.com/cguZZman/signin-server), runs on Cloudflare Workers at <https://signinserver.com> with the same protocol. This instance keeps serving add-on versions that still point at it.

## How the sign-in works

1. In Kodi, the add-on asks the service for a short code (`POST /pin`) and shows it on screen together with the service address and a QR code.
2. On a phone or computer **on the same network** as Kodi, you open the service and enter the code. The service checks that the request comes from the same public IP address as the Kodi device, then sends you to Google or Microsoft to sign in.
3. The provider sends you back to the service (`/callback`) with an authorization code. The service exchanges it for tokens, using the client secret and PKCE, and keeps them **in memory** until Kodi collects them, at most 3 minutes.
4. Kodi polls the service (`GET /pin/{code}`) with a random secret only it knows, receives the tokens and stores them on the device.
5. About once an hour, the add-on refreshes its access token through the service (`POST /refresh`), which adds the client secret and forwards the request to the provider.

File contents never go through this service: Kodi talks to Google and Microsoft directly. The service keeps no database and doesn't write or log tokens.

## Self-hosting

See **[SETUP.md](SETUP.md)** for creating the Google and Microsoft OAuth apps, configuration, running it locally, deploying it, and pointing the Kodi add-ons at your server.

## Build and test

Requires Java 25. The Maven wrapper downloads everything else.

```bash
./mvnw test
./mvnw package
```

The tests run the whole sign-in over real HTTP for both providers (code, redirect to the provider, callback, token pickup, refresh) against a mock token endpoint, plus the pages, error messages, rate limits, HTTPS redirect and security headers. No real Google or Microsoft credentials are needed.

## License

Copyright (C) 2017-2026 Carlos Guzman (cguZZman).

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).

If you run a modified version of this service for others, the license requires you to offer them its source code.
