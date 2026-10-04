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

## Tests

```bash
./mvnw test
```

`SignInFlowTest` runs the whole sign-in over real HTTP for every provider (pin, redirect to the provider, callback, token pickup, refresh) against a mock token endpoint, plus the pages, error messages and security headers. No real Google or Microsoft credentials are needed.
