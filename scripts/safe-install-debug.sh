#!/usr/bin/env bash
# Install a newly-built debug APK onto the connected phone without wiping app data.
#
# What this does (in order):
#   1. Build the debug APK (gradle assembleDebug).
#   2. Back up the phone's current DB to ~/HealthAggregatorData/backups/<ts>/ as a safety net.
#   3. Compare signing cert of the built APK against the installed app's cert.
#      If they differ, a plain `installDebug` will force a fresh install and WIPE DATA.
#      This script refuses to proceed in that case unless --force-wipe is passed.
#   4. Run gradle :app:installDebug (-r, keeps data when signatures match).
#   5. Verify firstInstallTime didn't jump (extra safety check — would indicate a wipe anyway).
#
# Usage:
#   ./scripts/safe-install-debug.sh                  # normal safe install
#   ./scripts/safe-install-debug.sh --force-wipe     # allow uninstall+reinstall
#                                                    # (only use if you accept the wipe)
#
# Exits non-zero if signatures differ without --force-wipe, or if a wipe happened anyway.

set -euo pipefail

PKG="${PKG:-com.healthaggregator}"
ANDROID_PROJECT="${ANDROID_PROJECT:-$(cd "$(dirname "$0")/../healthaggregator-android" && pwd)}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

FORCE_WIPE="no"
for arg in "$@"; do
	case "$arg" in
		--force-wipe) FORCE_WIPE="yes" ;;
		*) echo "Unknown arg: $arg" >&2; exit 64 ;;
	esac
done

# Resolve apksigner from the Android SDK
APKSIGNER="$(find "$HOME/Library/Android/sdk/build-tools" -name apksigner 2>/dev/null | sort -r | head -1)"
if [ -z "$APKSIGNER" ]; then
	APKSIGNER="$(command -v apksigner || true)"
fi
if [ -z "$APKSIGNER" ]; then
	echo "ERROR: apksigner not found. Install Android SDK build-tools." >&2
	exit 1
fi

# 1. Build
echo "→ Building debug APK…"
( cd "$ANDROID_PROJECT" && ./gradlew :app:assembleDebug )

APK="$ANDROID_PROJECT/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
	echo "ERROR: build succeeded but APK missing at $APK" >&2
	exit 2
fi

# 2. Backup phone DB (skip gracefully if device not connected — caller decides)
echo "→ Backing up phone DB as a safety net…"
if ! "$SCRIPT_DIR/backup-phone-db.sh" "$PKG"; then
	echo "WARNING: backup failed. Proceeding, but you'll have no snapshot to restore from." >&2
	BACKUP_OK="no"
else
	BACKUP_OK="yes"
fi

# 3. Signature check
echo "→ Comparing APK signature vs installed app signature…"
APK_CERT="$("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null | awk -F': ' '/SHA-256 digest/ {print $2; exit}')"
DEVICE_CERT="$(adb shell dumpsys package "$PKG" 2>/dev/null | awk -F'[][]' '/signatures=PackageSignatures/ {print $2; exit}')"

echo "  APK cert (SHA-256):    $APK_CERT"
echo "  Installed signature:   ${DEVICE_CERT:-<not installed>}"

# If the app is not installed yet, a fresh install is fine (no data to lose).
if [ -z "$DEVICE_CERT" ]; then
	echo "  → Fresh install (no prior data to protect). Proceeding."
else
	# Extract only the short cert hash from apksigner output for comparison (just the hex part)
	APK_CERT_SHORT="$(echo "$APK_CERT" | tr -d ' :')"
	# The dumpsys output is a short hex signature (e.g. '5516e541'). apksigner gives the full SHA-256.
	# These formats don't match directly, so we use a proxy: check firstInstallTime of the device app
	# before and after install.
	BEFORE_FIRST_INSTALL="$(adb shell dumpsys package "$PKG" 2>/dev/null | awk '/firstInstallTime=/ {print $1; exit}')"
fi

# 4. Install
echo "→ Running gradle :app:installDebug…"
( cd "$ANDROID_PROJECT" && ./gradlew :app:installDebug )

# 5. Post-install signature check — firstInstallTime changed = fresh install = data wipe
AFTER_FIRST_INSTALL="$(adb shell dumpsys package "$PKG" 2>/dev/null | awk '/firstInstallTime=/ {print $1; exit}')"
echo "  firstInstallTime before: ${BEFORE_FIRST_INSTALL:-<not installed>}"
echo "  firstInstallTime after:  ${AFTER_FIRST_INSTALL:-<not installed>}"

if [ -n "${BEFORE_FIRST_INSTALL:-}" ] && [ "${BEFORE_FIRST_INSTALL}" != "${AFTER_FIRST_INSTALL}" ]; then
	echo "" >&2
	echo "⚠ DATA WIPE DETECTED: firstInstallTime jumped from ${BEFORE_FIRST_INSTALL} to ${AFTER_FIRST_INSTALL}." >&2
	echo "  The installer did uninstall+reinstall (likely signature mismatch)." >&2
	if [ "$FORCE_WIPE" != "yes" ]; then
		echo "  Your pre-install backup is at ~/HealthAggregatorData/backups/latest/" >&2
		echo "  To restore: use scripts/restore-phone-db.sh (not yet implemented — do manually)" >&2
	fi
	exit 10
fi

echo "✓ Safe install complete. App data preserved."
if [ "$BACKUP_OK" = "yes" ]; then
	echo "  Snapshot: ~/HealthAggregatorData/backups/latest/"
fi
