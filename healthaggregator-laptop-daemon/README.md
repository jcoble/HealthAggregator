# healthaggregator-laptop-daemon

Small HTTP daemon that serves the laptop-side SQLite for phone↔laptop sync.

## One-time setup

```bash
# 1. Bootstrap the laptop DB from the phone (requires USB + adb).
mkdir -p ~/HealthAggregatorData
adb exec-out run-as com.healthaggregator cat databases/healthaggregator.db \
  > ~/HealthAggregatorData/healthaggregator.db
cp ~/HealthAggregatorData/healthaggregator.db \
   ~/HealthAggregatorData/healthaggregator.db.bootstrap-backup

# 2. Install Python deps and boot the daemon.
cd healthaggregator-laptop-daemon
make install   # creates .venv and installs fastapi/uvicorn/pytest
make run       # runs on port 8719

# 3. Read the auth token (generated on first daemon start; saved to sync.token):
cat ~/HealthAggregatorData/sync.token
```

## Auto-start on login (macOS)

```bash
make install-launchd   # registers launchd plist, daemon persists across reboots
make uninstall-launchd # removes it
```

Logs go to `~/HealthAggregatorData/daemon.log` (stdout) and `~/HealthAggregatorData/daemon.err.log` (stderr).

## Pair the phone

Open the HealthAggregator Android app → Settings → Laptop Sync → Pair with laptop. Enter:
- **Hostname:** your laptop's Tailscale hostname (e.g. `jesse-laptop.tail-abc.ts.net`). Run `tailscale status` on the laptop to find it.
- **Token:** the contents of `~/HealthAggregatorData/sync.token`.

Tap Test and save. On success you can press "Sync now" from Settings anytime.

## Troubleshooting

- **"Can't reach laptop":** is Tailscale up on both devices? Is the daemon running (`ps aux | grep uvicorn`)? Is the token correct?
- **"Daemon is on schema vN, phone is on vM":** laptop is ahead. This shouldn't happen during normal use. Either install a newer APK, or re-bootstrap by copying a fresh `healthaggregator.db` from the phone over adb.
- **"Sync failed" with HTTP 500:** check `~/HealthAggregatorData/daemon.err.log` for the stack trace.

## Data location

- Active DB: `~/HealthAggregatorData/healthaggregator.db`
- Auth token: `~/HealthAggregatorData/sync.token` (chmod 600)
- Bootstrap backup: `~/HealthAggregatorData/healthaggregator.db.bootstrap-backup`
- Logs: `~/HealthAggregatorData/daemon.log`, `~/HealthAggregatorData/daemon.err.log`

Deliberately outside the git repo so it can't be accidentally committed.

## Architecture

See `Docs/specs/2026-04-18-laptop-sync-design.md` in the parent repo for the full design.

Quick summary: phone reads its Room DB, serializes every row as a column→value JSON dict, POSTs to `/sync/push`. Daemon INSERT-OR-IGNOREs rows by natural keys (LWW for `chat_conversations`). `GET /sync/pull` returns everything on the laptop; phone merges the same way. Schema migrations flow phone-authoritatively over `/sync/migrate` when the daemon is behind.
