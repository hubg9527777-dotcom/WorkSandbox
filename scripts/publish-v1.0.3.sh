#!/usr/bin/env bash
# Safe interactive publisher for WorkSandbox v1.0.3.
set -Eeuo pipefail

VERSION="1.0.3"
TAG="v${VERSION}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"; }
restore_terminal() { stty echo 2>/dev/null || true; }
trap restore_terminal EXIT INT TERM
read_secret() {
  printf '%s' "$1" >&2
  stty -echo
  IFS= read -r REPLY
  stty echo
  printf '\n' >&2
}
ANDROID_STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -x "$ANDROID_STUDIO_JBR/bin/java" ] && [ -x "$ANDROID_STUDIO_JBR/bin/keytool" ]; then
  export JAVA_HOME="$ANDROID_STUDIO_JBR"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

need git; need gh; need keytool; need base64; need shasum
[ -x ./gradlew ] || fail "Missing executable ./gradlew"
[ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ] || fail "Android SDK Build Tools not found"
grep -q "versionName \"${VERSION}\"" app/build.gradle || fail "Expected versionName ${VERSION}"
grep -q "versionCode 2" app/build.gradle || fail "Expected versionCode 2"
test -f .github/workflows/release.yml || fail "Missing release workflow"
test -f release.properties.example || fail "Missing release.properties.example"
git config user.name >/dev/null || fail "Run: git config --global user.name 'Your Name'"
git config user.email >/dev/null || fail "Run: git config --global user.email 'you@example.com'"

printf '\n== 1/7: Check GitHub authentication ==\n'
gh auth status || fail "Run first: gh auth login -h github.com -s repo,workflow"
REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
printf 'Target repository: %s\n' "$REPO"
if [ "$(gh repo view --json isPrivate --jq .isPrivate)" = "true" ]; then
  printf 'WARNING: private repositories need GitHub Enterprise Cloud for artifact attestations.\n' >&2
fi

printf '\n== 2/7: Create or reuse Android signing key ==\n'
KEYSTORE_DIR="$HOME/.android/worksandbox"
KEYSTORE="$KEYSTORE_DIR/worksandbox-release.jks"
KEY_ALIAS="worksandbox"
mkdir -p "$KEYSTORE_DIR" && chmod 700 "$KEYSTORE_DIR"
if [ ! -f "$KEYSTORE" ]; then
  printf 'No old key found. Creating: %s\n' "$KEYSTORE"
  read_secret 'New keystore password: '; STORE_PASSWORD="$REPLY"
  read_secret 'Repeat keystore password: '; STORE_CONFIRM="$REPLY"
  [ "$STORE_PASSWORD" = "$STORE_CONFIRM" ] || fail "Keystore passwords do not match"
  read_secret 'New key password: '; KEY_PASSWORD="$REPLY"
  read_secret 'Repeat key password: '; KEY_CONFIRM="$REPLY"
  [ "$KEY_PASSWORD" = "$KEY_CONFIRM" ] || fail "Key passwords do not match"
  umask 077
  ANDROID_KEYSTORE_PASSWORD="$STORE_PASSWORD" ANDROID_KEY_PASSWORD="$KEY_PASSWORD" \
    keytool -genkeypair -keystore "$KEYSTORE" -storetype JKS \
    -storepass:env ANDROID_KEYSTORE_PASSWORD -keypass:env ANDROID_KEY_PASSWORD -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=WorkSandbox, OU=Personal, O=WorkSandbox, C=CN" -noprompt
  chmod 600 "$KEYSTORE"
  printf 'Back up this key securely: %s\n' "$KEYSTORE"
else
  printf 'Using existing key: %s\n' "$KEYSTORE"
  read_secret 'Existing keystore password: '; STORE_PASSWORD="$REPLY"
  read_secret 'Existing key password: '; KEY_PASSWORD="$REPLY"
fi
unset STORE_CONFIRM KEY_CONFIRM
export ANDROID_KEYSTORE_PATH="$KEYSTORE"
export ANDROID_KEYSTORE_PASSWORD="$STORE_PASSWORD"
export ANDROID_KEY_ALIAS="$KEY_ALIAS"
export ANDROID_KEY_PASSWORD="$KEY_PASSWORD"

printf '\n== 3/7: Build and verify signed Release APK ==\n'
./gradlew --no-daemon clean lintRelease assembleRelease
APK="app/build/outputs/apk/release/app-release.apk"
test -f "$APK" || fail "Release APK was not generated"
BUILD_TOOLS_DIR="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
test -x "$BUILD_TOOLS_DIR/apksigner" || fail "apksigner not found"
test -x "$BUILD_TOOLS_DIR/aapt" || fail "aapt not found"
"$BUILD_TOOLS_DIR/apksigner" verify --verbose --print-certs "$APK"
if "$BUILD_TOOLS_DIR/aapt" dump badging "$APK" | grep -q 'application-debuggable'; then
  fail "Release APK is debuggable"
fi
"$BUILD_TOOLS_DIR/aapt" dump badging "$APK" | grep -E 'package:|sdkVersion|targetSdkVersion'
shasum -a 256 "$APK"

printf '\n== 4/7: Configure GitHub Actions Secrets ==\n'
KEYSTORE_BASE64="$(base64 < "$KEYSTORE" | tr -d '\n')"
printf '%s' "$KEYSTORE_BASE64" | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$STORE_PASSWORD" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$KEY_ALIAS" | gh secret set ANDROID_KEY_ALIAS --repo "$REPO"
printf '%s' "$KEY_PASSWORD" | gh secret set ANDROID_KEY_PASSWORD --repo "$REPO"
unset KEYSTORE_BASE64 STORE_PASSWORD KEY_PASSWORD
unset ANDROID_KEYSTORE_PATH ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD
gh secret list --repo "$REPO" | grep -E 'ANDROID_KEYSTORE_BASE64|ANDROID_KEYSTORE_PASSWORD|ANDROID_KEY_ALIAS|ANDROID_KEY_PASSWORD' || fail "Secret check failed"

printf '\n== 5/7: Prepare commit and tag ==\n'
git diff --check
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then fail "Local tag $TAG already exists"; fi
if git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then fail "Remote tag $TAG already exists"; fi

git add .github/workflows/release.yml .gitignore app/build.gradle \
  app/src/main/AndroidManifest.xml \
  app/src/main/java/com/mysandbox/work/AdminReceiver.java \
  app/src/main/java/com/mysandbox/work/MainActivity.java \
  app/src/main/res/values/strings.xml \
  app/src/main/res/xml/backup_rules.xml \
  app/src/main/res/xml/data_extraction_rules.xml \
  app/src/main/res/xml/device_admin.xml \
  release.properties.example scripts/publish-v1.0.3.sh

git diff --cached --check
printf 'Files to commit:\n'
git diff --cached --name-only
printf 'Commit, push main, and publish %s. Type RELEASE-%s to continue: ' "$TAG" "$TAG"
IFS= read -r CONFIRM
[ "$CONFIRM" = "RELEASE-$TAG" ] || fail "Canceled before any commit, push, or release"

printf '\n== 6/7: Commit and push %s ==\n' "$TAG"
git commit -m "fix: harden work profile provisioning and release security"
git push origin main
git tag -a "$TAG" -m "WorkSandbox ${VERSION} security hardening"
git push origin "$TAG"
COMMIT_SHA="$(git rev-parse HEAD)"

printf '\n== 7/7: Wait for release and verify provenance ==\n'
RUN_ID=""
for _ in $(seq 1 20); do
  RUN_ID="$(gh run list --repo "$REPO" --workflow release.yml --commit "$COMMIT_SHA" --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)"
  [ -n "$RUN_ID" ] && break
  sleep 15
done
[ -n "$RUN_ID" ] || fail "No release workflow appeared within 5 minutes; inspect GitHub Actions"
printf 'Release workflow run: %s\n' "$RUN_ID"
gh run watch "$RUN_ID" --repo "$REPO" --exit-status

VERIFY_DIR="$(mktemp -d)"
trap 'rm -rf "$VERIFY_DIR"; restore_terminal' EXIT
gh release download "$TAG" --repo "$REPO" --pattern "WorkSandbox-${TAG}.apk" --dir "$VERIFY_DIR"
ASSET="$VERIFY_DIR/WorkSandbox-${TAG}.apk"
test -f "$ASSET" || fail "Release APK download failed"
shasum -a 256 "$ASSET"
gh attestation verify "$ASSET" --repo "$REPO"
gh release view "$TAG" --repo "$REPO" --web
printf 'Success: %s has been published and provenance verification passed.\n' "$TAG"
