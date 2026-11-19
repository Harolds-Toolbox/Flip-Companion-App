# Development diagnostics backend

`worker.js` accepts only allowlisted, structured call events from opt-in development APKs. Its D1 binding must be named `DB`. Run `schema.sql` against a new D1 database before enabling uploads. For an existing database, run `migrations/0002_language_tag.sql` once. Older Dev APKs remain compatible and record language as `und`. Configure a daily Cron Trigger for the Worker so records older than 30 days are removed.

Deploy from this directory with `npm ci` and `npm run deploy` after authenticating `cf` as `harry@pabiautomations.co.uk`. `cloudflare.config.ts` retains the production D1 binding and daily retention trigger.

The endpoint has no read API. Review events in the Cloudflare D1 dashboard. It never asks the client for caller names, message text, notification text, audio, contacts, or a phone number, and it does not store the caller's IP address in D1. The anonymous installation ID is generated on the phone; the development build shows it so a tester can identify their own records. The app has a button to stop sharing and delete all records for that installation ID.

The Worker rejects unknown JSON fields, limits requests to 32 KiB and 20 events, and limits each installation ID to 300 events per hour. As an unauthenticated public endpoint, an attacker could still submit fabricated events using new IDs; do not use these records as a security or billing source of truth.
