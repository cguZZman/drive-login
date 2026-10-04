# kodi-login

## Funding card

The auth-success page can show a card asking users to help fund the server. Configure it with environment variables:

| Variable | Default | Effect |
|---|---|---|
| `FUNDING_URL` | empty | Donation page link. The card is hidden when empty. |
| `FUNDING_GOAL` | `0` | Yearly goal in USD. The progress bar is hidden when `0`. |
| `FUNDING_RAISED` | `0` | Amount raised so far this year, updated by hand. |

```bash
heroku config:set FUNDING_URL=https://github.com/sponsors/<user> FUNDING_GOAL=1500 FUNDING_RAISED=0
```

## Security settings

| Variable | Default | Effect |
|---|---|---|
| `CLIENT_IP_FORWARDED_HEADER` | `X-Forwarded-For` | Header holding the client IP; its last entry is used (Heroku's router appends the real address there). Set it to empty when the app is not behind a proxy that sets it, or clients could forge their address. |
| `RATE_LIMIT_PIN` | `10` | `POST /pin` requests per minute per IP. |
| `RATE_LIMIT_SIGNIN` | `20` | `/authorize` and `/signin/*` requests per minute per IP. |
| `RATE_LIMIT_REFRESH_IP` | `60` | `POST /refresh` requests per minute per IP. |
| `RATE_LIMIT_REFRESH_TOKEN` | `10` | `POST /refresh` requests per minute per refresh token (counted by hash). |

Set a limit to `0` to disable it. The sign-in is bound to the browser that started it with a short-lived `__Host-signin` cookie, and uses PKCE (S256) with the provider.

## Tests

```bash
./mvnw test
```

`SignInFlowTest` runs the whole sign-in over real HTTP for every provider (pin, redirect to the provider, callback, token pickup, refresh) against a mock token endpoint, plus the pages, error messages and security headers. No real Google or Microsoft credentials are needed.
