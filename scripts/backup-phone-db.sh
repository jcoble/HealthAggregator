#!/usr/bin/env bash
# Pull the HealthAggregator Room DB off the phone into a timestamped backup dir.
# Safe to run while the app is open — force-stops it first so the WAL is quiesced,
# then pulls .db, .db-wal, .db-shm. Does NOT restart the app.
#
# Usage: ./scripts/backup-phone-db.sh [PACKAGE_NAME]
#   default PACKAGE_NAME = com.healthaggregator
#
# Output:
#   ~/HealthAggregatorData/backups/<UTC timestamp>/healthaggregator.db{,-wal,-shm}
#   ~/HealthAggregatorData/backups/latest  → symlink to the newest backup
#
# Exits non-zero if no device is connected, if run-as fails, or if nothing was pulled.

set -euo pipefail

PKG="${1:-com.healthaggregator}"
DB_NAME="healthaggregator.db"
BACKUP_ROOT="${HOME}/HealthAggregatorData/backups"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="${BACKUP_ROOT}/${STAMP}"

# Precondition: exactly one device connected
if ! command -v adb >/dev/null; then
	echo "ERROR: adb not found on PATH" >&2
	exit 1
fi
DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
if [ "$DEVICE_COUNT" -eq 0 ]; then
	echo "ERROR: no Android device connected (see 'adb devices')" >&2
	exit 2
fi
if [ "$DEVICE_COUNT" -gt 1 ]; then
	echo "ERROR: multiple devices connected — ambiguous" >&2
	exit 3
fi

# Precondition: package installed
if ! adb shell pm list packages | grep -q "package:${PKG}$"; then
	echo "ERROR: package ${PKG} not installed on device — nothing to back up" >&2
	exit 4
fi

mkdir -p "$DEST"

# Quiesce the WAL by force-stopping the app (safe — no active writer)
adb shell am force-stop "$PKG"

# Pull the three SQLite files. Only the .db is required; WAL/SHM are optional.
adb exec-out run-as "$PKG" cat "databases/${DB_NAME}"     > "${DEST}/${DB_NAME}"
adb exec-out run-as "$PKG" cat "databases/${DB_NAME}-wal" > "${DEST}/${DB_NAME}-wal" 2>/dev/null || rm -f "${DEST}/${DB_NAME}-wal"
adb exec-out run-as "$PKG" cat "databases/${DB_NAME}-shm" > "${DEST}/${DB_NAME}-shm" 2>/dev/null || rm -f "${DEST}/${DB_NAME}-shm"

# Verify we got something — if the .db is tiny AND there's no WAL, something went wrong
MAIN_SIZE="$(stat -f %z "${DEST}/${DB_NAME}" 2>/dev/null || stat -c %s "${DEST}/${DB_NAME}")"
if [ "$MAIN_SIZE" -lt 1024 ] && [ ! -f "${DEST}/${DB_NAME}-wal" ]; then
	echo "ERROR: pulled .db is suspiciously small (${MAIN_SIZE} bytes) and no WAL exists" >&2
	echo "       check that '${PKG}' is debuggable and run-as works:" >&2
	echo "         adb shell run-as ${PKG} ls -l databases/" >&2
	exit 5
fi

# Checkpoint the WAL into the main file so the backup is self-contained
sqlite3 "${DEST}/${DB_NAME}" "PRAGMA wal_checkpoint(TRUNCATE);" >/dev/null 2>&1 || true

# Update 'latest' symlink
ln -sfn "$STAMP" "${BACKUP_ROOT}/latest"

# Print summary + row counts so we can eyeball the backup
echo "✓ Phone DB backed up to ${DEST}"
sqlite3 "${DEST}/${DB_NAME}" <<'SQL'
SELECT 'user_version=' || user_version FROM pragma_user_version();
SELECT 'patients=' || COUNT(*) FROM patients;
SELECT 'lab_observations=' || COUNT(*) FROM lab_observations;
SELECT 'vitals_observations=' || COUNT(*) FROM vitals_observations;
SELECT 'conditions=' || COUNT(*) FROM conditions;
SELECT 'medications=' || COUNT(*) FROM medications;
SELECT 'allergies=' || COUNT(*) FROM allergies;
SELECT 'encounters=' || COUNT(*) FROM encounters;
SELECT 'documents=' || COUNT(*) FROM documents;
SELECT 'diagnostic_reports=' || COUNT(*) FROM diagnostic_reports;
SELECT 'source_records=' || COUNT(*) FROM source_records;
SELECT 'chat_conversations=' || COUNT(*) FROM chat_conversations;
SELECT 'chat_messages=' || COUNT(*) FROM chat_messages;
SQL
echo "Latest backup: ${BACKUP_ROOT}/latest → ${STAMP}"
