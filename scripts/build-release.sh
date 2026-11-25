#!/usr/bin/env bash
# Build the signed, F-Droid-reproducible release APK for RF-REAPR from a git tag.
#
#   scripts/build-release.sh v1.6.1
#
# Mirrors F-Droid's build of the fdroiddata recipe: fresh clone at the tag (with
# submodules), clean tree, the recipe's prebuild steps, `gradlew assembleRelease`
# with JDK 17, then apksigner adds ONLY the APK Signing Block
# (--alignment-preserved) so F-Droid can copy the signature onto its own build.
#
# Never build release APKs from the working tree or from Android Studio.
#
# Secrets: ~/.config/rf_reapr-release/release.env (chmod 600) with
#   KEYSTORE=/path/to/RF_REAPR-Release.jks
#   KS_PASS=...            (keystore password)
#   KEY_PASS=...           (optional, defaults to KS_PASS)
#   KEY_ALIAS=key0         (optional)
# Missing passwords are prompted for (no echo). They are only ever passed to
# apksigner via environment variables, never on the command line.
#
# One-time setup (type the password at the prompt; it is never echoed):
#   mkdir -p ~/.config/rf_reapr-release && chmod 700 ~/.config/rf_reapr-release
#   read -rsp 'Keystore password: ' p; echo
#   ( umask 077; printf 'KEYSTORE=%q\nKS_PASS=%q\nKEY_ALIAS=key0\n' \
#       "$HOME/Documents/Keystores/RF_REAPR-Release.jks" "$p" \
#       > ~/.config/rf_reapr-release/release.env ); unset p
# (If the key password differs, append a KEY_PASS line the same way.)
#
# Environment overrides (mainly for testing):
#   OUT_DIR          output dir            (default: <repo>/dist/<tag>)
#   RELEASE_ENV      secrets file          (default: ~/.config/rf_reapr-release/release.env)
#   SOURCE_REPO      repo to clone from    (default: this repository)
#   WORK_BASE        where the temp clone goes (default: ~/.cache; /tmp is tmpfs)
#   BUILD_JAVA_HOME  JDK 17 for Gradle     (default: auto-detected in ~/.gradle/jdks)
#   SIGN_JAVA_HOME   JDK for apksigner     (default: ~/android-studio/jbr)
#   EXPECTED_CERT    required signer SHA-256, or "skip" (default: AllowedAPKSigningKeys)
#   KEEP_WORK=1      keep the temp clone
set -euo pipefail

APP=RF_REAPR
# Must equal AllowedAPKSigningKeys in the fdroiddata recipe.
RELEASE_CERT=9caaad2cf535abd6f64159aaa7c63e3471e3f38fbbd88ff536fc894afde23ab2

die() { echo "ERROR: $*" >&2; exit 1; }
log() { echo "==> $*" >&2; }

TAG=${1:-}
[[ $TAG =~ ^v[0-9]+(\.[0-9]+)+$ ]] || die "usage: $0 vX.Y.Z  (tag must match the recipe's UpdateCheckMode ^v[0-9.]+\$)"
VERSION=${TAG#v}

REPO_DIR=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)
SOURCE_REPO=${SOURCE_REPO:-$REPO_DIR}
OUT_DIR=${OUT_DIR:-$REPO_DIR/dist/$TAG}
RELEASE_ENV=${RELEASE_ENV:-$HOME/.config/rf_reapr-release/release.env}
WORK_BASE=${WORK_BASE:-$HOME/.cache}
SIGN_JAVA_HOME=${SIGN_JAVA_HOME:-$HOME/android-studio/jbr}
EXPECTED_CERT=${EXPECTED_CERT:-$RELEASE_CERT}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME

APK_NAME="$APP-$VERSION.apk"   # recipe: Binaries: .../download/v%v/RF_REAPR-%v.apk
SIGNED_APK="$OUT_DIR/$APK_NAME"
[[ -e $SIGNED_APK ]] && die "$SIGNED_APK already exists; refusing to overwrite"

# --- toolchain -------------------------------------------------------------
if [[ -z ${BUILD_JAVA_HOME:-} ]]; then
  for j in "$HOME"/.gradle/jdks/*17*/ /usr/lib/jvm/*17*/; do
    if [[ -x $j/bin/java ]] && "$j/bin/java" -version 2>&1 | grep -q 'version "17\.'; then
      BUILD_JAVA_HOME=${j%/}; break
    fi
  done
fi
[[ -n ${BUILD_JAVA_HOME:-} && -x $BUILD_JAVA_HOME/bin/java ]] \
  || die "no JDK 17 found; set BUILD_JAVA_HOME (F-Droid builds with OpenJDK 17)"
[[ -x $SIGN_JAVA_HOME/bin/java ]] || die "SIGN_JAVA_HOME=$SIGN_JAVA_HOME has no bin/java"
BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/*/ 2>/dev/null | sort -V | tail -1)
APKSIGNER=${BUILD_TOOLS%/}/apksigner
[[ -x $APKSIGNER ]] || die "apksigner not found under $ANDROID_HOME/build-tools"
apksigner() { PATH="$SIGN_JAVA_HOME/bin:$PATH" JAVA_HOME="$SIGN_JAVA_HOME" "$APKSIGNER" "$@" 2> >(grep -v -e '^WARNING: .*restricted method' -e 'NativeLibraryUtil' -e 'enable-native-access' -e 'Restricted methods will be blocked' >&2); }

# --- secrets (load before the long build so prompts come first) ------------
if [[ -f $RELEASE_ENV ]]; then
  perms=$(stat -c %a "$RELEASE_ENV")
  (( (8#$perms & 8#077) == 0 )) || echo "WARNING: $RELEASE_ENV is readable by others (mode $perms); run: chmod 600 '$RELEASE_ENV'" >&2
  set -a
  # shellcheck disable=SC1090
  . "$RELEASE_ENV"
  set +a
else
  echo "WARNING: $RELEASE_ENV not found; prompting instead" >&2
fi
if [[ -z ${KEYSTORE:-} ]]; then read -rp "Keystore path: " KEYSTORE; fi
KEYSTORE=${KEYSTORE/#\~/$HOME}
[[ -f $KEYSTORE ]] || die "keystore not found: $KEYSTORE"
if [[ -z ${KS_PASS:-} ]]; then read -rsp "Keystore password: " KS_PASS; echo >&2; fi
KEY_PASS=${KEY_PASS:-$KS_PASS}
export KS_PASS KEY_PASS
ALIAS_ARGS=()
[[ -n ${KEY_ALIAS:-} ]] && ALIAS_ARGS=(--ks-key-alias "$KEY_ALIAS")

# --- fresh clone at the tag -------------------------------------------------
mkdir -p "$WORK_BASE"
WORK=$(mktemp -d "$WORK_BASE/rf_reapr-release.XXXXXX")
cleanup() { if [[ ${KEEP_WORK:-0} == 1 ]]; then log "kept $WORK"; else rm -rf "$WORK"; fi; }
trap cleanup EXIT

log "cloning $SOURCE_REPO at $TAG into $WORK/src"
git clone -q --no-local "$SOURCE_REPO" "$WORK/src"
cd "$WORK/src"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || die "tag $TAG not found in $SOURCE_REPO"
COMMIT=$(git rev-parse "refs/tags/$TAG^{commit}")
if remote=$(git -C "$REPO_DIR" ls-remote origin "refs/tags/$TAG^{}" "refs/tags/$TAG" 2>/dev/null) && [[ -n $remote ]]; then
  remote_commit=$(awk '/\^\{\}$/ {print $1}' <<<"$remote"); remote_commit=${remote_commit:-$(awk '{print $1; exit}' <<<"$remote")}
  [[ $remote_commit == "$COMMIT" ]] || die "tag $TAG is $COMMIT locally but $remote_commit on origin"
else
  echo "WARNING: tag $TAG is not on origin (fine for a test, not for a release)" >&2
fi
git -c advice.detachedHead=false checkout -q "$COMMIT"
git submodule update --init --recursive -q

grep -q "versionName = \"$VERSION\"" app/build.gradle.kts \
  || die "app/build.gradle.kts versionName does not match $VERSION"
VERSION_CODE=$(sed -n 's/^ *versionCode = \([0-9]\+\).*/\1/p' app/build.gradle.kts)

# --- build (one universal APK; no ABI splits) -------------------------------
git reset -q --hard
git clean -dffxq
git submodule foreach -q --recursive 'git reset -q --hard && git clean -dffxq'
# Recipe prebuild. Keep identical to the fdroiddata recipe's `prebuild:` lines.
# (Tags up to v1.6.0 needed: sed -i -e '/foojay-resolver/d' settings.gradle.kts)
if grep -q foojay-resolver settings.gradle.kts; then
  sed -i -e '/foojay-resolver/d' settings.gradle.kts
fi

log "building $TAG ($COMMIT, versionCode $VERSION_CODE) with $BUILD_JAVA_HOME"
JAVA_HOME=$BUILD_JAVA_HOME PATH="$BUILD_JAVA_HOME/bin:$PATH" \
  ./gradlew --no-daemon --no-build-cache assembleRelease
UNSIGNED=app/build/outputs/apk/release/app-release-unsigned.apk
[[ -f $UNSIGNED ]] || die "expected $UNSIGNED"

# --- sign: add the APK Signing Block only ------------------------------------
mkdir -p "$OUT_DIR"
log "signing -> $SIGNED_APK"
apksigner sign \
  --ks "$KEYSTORE" --ks-pass env:KS_PASS --key-pass env:KEY_PASS "${ALIAS_ARGS[@]}" \
  --alignment-preserved true \
  --v1-signing-enabled false --v2-signing-enabled true \
  --v3-signing-enabled true --v4-signing-enabled false \
  --out "$SIGNED_APK" "$UNSIGNED"
unset KS_PASS KEY_PASS
rm -f "$SIGNED_APK.idsig"

VERIFY=$(apksigner verify -v --print-certs "$SIGNED_APK")
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"$VERIFY" || die "v2 signature missing"
grep -q 'Verified using v3 scheme (APK Signature Scheme v3): true' <<<"$VERIFY" || die "v3 signature missing"
CERT=$(grep -E '^(V[0-9.]+ )?Signer( #1)?:? certificate SHA-256 digest: ' <<<"$VERIFY" | awk '{print $NF}' | sort -u)
[[ -n $CERT && $CERT != *$'\n'* ]] || die "expected exactly one signer certificate, got: ${CERT:-none}"
(cd "$OUT_DIR" && sha256sum "$APK_NAME" > "$APK_NAME.sha256")

echo
echo "Tag:          $TAG ($COMMIT)"
echo "versionCode:  $VERSION_CODE"
echo "APK:          $SIGNED_APK"
echo "APK SHA-256:  $(cut -d' ' -f1 "$SIGNED_APK.sha256")"
echo "Cert SHA-256: $CERT"
if [[ $EXPECTED_CERT != skip && $CERT != "$EXPECTED_CERT" ]]; then
  rm -f "$SIGNED_APK" "$SIGNED_APK.sha256"
  die "signer $CERT != expected $EXPECTED_CERT (AllowedAPKSigningKeys); APK removed"
fi
