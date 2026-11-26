# RF-REAPR - Guidelines for AI Agents & Developers

## AGENT NAVIGATION & MANIFEST RULES

### Mandatory Manifest Consultation
> **Before attempting any code modification, you must consult `.agent_manifest.json` to identify the architectural pattern and the specific files responsible for data flow. Do not guess file paths.**

### End-of-Session Manifest Updates
At the end of each development session, if any new features, files, repositories, database models/DAOs/entities, or architectural components were added, modified, or removed, AI agents **MUST** update `.agent_manifest.json` with the new/updated paths so the code map remains accurate for future sessions.

## CRITICAL ARCHITECTURAL CONSTRAINTS

### 1. STRICT 100% FOSS POLICY (F-DROID COMPLIANCE)
* **Zero Proprietary SDKs:** RF-REAPR is strictly Free and Open Source Software (FOSS), targeted for distribution on F-Droid and other open-source Android repositories.
* **FORBIDDEN DEPENDENCIES:**
  * **NO** Google Play Services (`com.google.android.gms:*`, including `play-services-location`)
  * **NO** Google Mobile Ads / AdMob (`com.google.android.gms:play-services-ads`)
  * **NO** Google Play Billing (`com.android.billingclient:*`)
  * **NO** ML Kit or closed-source vision/speech SDKs (`com.google.mlkit:*`)
  * **NO** Firebase SDKs that pull in non-free binaries or Play Services requirements
  * **NO** Analytics, crash reporting, tracking, or proprietary telemetry libraries
  * **NO** prebuilt binaries (`.so`, `.jar`, `.aar`, `.apk`) committed to the repo. The only exception is `gradle/wrapper/gradle-wrapper.jar`, which F-Droid verifies against official checksums.
* **ALLOWED DEPENDENCIES:**
  * Android Jetpack libraries (`androidx.*`)
  * Kotlin standard/coroutines/serialization libraries
  * Open-source third-party dependencies with permissive/copyleft FOSS licenses (Apache 2.0, MIT, BSD, GPL, MPL), resolved only from `google()` or `mavenCentral()`
  * Standard Android framework APIs (e.g., `LocationManager` via `android.location.*`)
* **NO PRODUCT FLAVORS FOR PROPRIETARY PLUGINS:** Do not add product flavor dimensions or build variants that rely on non-free or Google Play-specific dependencies.
* **Verify before release:** `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` must not contain any `gms`, `firebase`, `mlkit`, `billingclient`, or analytics artifacts.

### 2. PRIVACY & OFFLINE FIRST
* The application must remain fully functional offline.
* All user data (security project records, evidence photo captures, PCAP network captures, audit checklists, scan logs) must stay local on device or be exportable via user-initiated standard formats.
* Third-party online services (Shodan, Have I Been Pwned, AbuseIPDB, AlienVault OTX, etc.) must only be contacted on explicit user action. These are declared via the `NonFreeNet` anti-feature in the F-Droid recipe; if a **new** non-free service is added, update the `AntiFeatures` text in the fdroiddata recipe (`metadata/com.fearmikey.rf_reapr.yml`, see Release workflow; ask the user before touching fdroiddata) and the "Optional online integrations" paragraph in `fastlane/metadata/android/en-US/full_description.txt`.

### 3. NATIVE CODE & REPRODUCIBLE BUILDS
* The native iperf3 library is built from the **Git submodule** at `app/src/main/cpp/iperf3` (declared in `.gitmodules`, pointing to `https://github.com/esnet/iperf.git`). Never commit prebuilt `.so` files.
* **The submodule must stay pinned to an UNMODIFIED commit that exists upstream.** Never commit inside the submodule; F-Droid clones it fresh and cannot fetch local-only commits (this broke the v1.5.6 build).
* RF-REAPR's JNI patches to iperf3 live in the main repo as patched copies in `app/src/main/cpp/iperf_android/` (`cjson.c`, `iperf_error.c` with `longjmp` instead of `exit()`, `iperf_pthread.c`), and `CMakeLists.txt` compiles those instead of the upstream files. To change iperf3 code, copy the file there and update `CMakeLists.txt`. When bumping the submodule, re-apply the patches to the new upstream versions of these files.
* `iperf_config.h` and `version.h` are checked in under `app/src/main/cpp/iperf_android/`. **Do NOT run `./configure` inside the submodule**; it generates `src/iperf_config.h` / `src/version.h`, which shadow the checked-in headers and make local builds differ from F-Droid's. If the submodule is bumped, update `IPERF_VERSION`/`PACKAGE_VERSION` in those headers.
* `ndkVersion` in `app/build.gradle.kts` and `ndk:` in the F-Droid recipe **must match** exactly (currently `27.0.12077973`).
* Keep `-ffile-prefix-map` and `LINKER:--build-id=none` in `app/src/main/cpp/CMakeLists.txt`.
* Keep `dependenciesInfo { includeInApk = false; includeInBundle = false }` in `app/build.gradle.kts`.
* Keep the `tasks.whenTaskAdded { if (name.contains("ArtProfile")) enabled = false }` block in `app/build.gradle.kts`. R8's baseline-profile rewriting (AGP 8.7.3) depends on the build machine's CPU count, which made `assets/dexopt/baseline.prof` differ from F-Droid's build (v1.6.1). Only re-enable profiles after proving that a 2-CPU build (`taskset -c 0-1`) and a full-core build of a fresh clone are byte-identical.

### 4. SECRETS
* Never commit keystores (`*.jks`, `*.keystore`), `local.properties`, API keys, or tokens.
* Release signing is done only by `scripts/build-release.sh`, which reads `~/.config/rf_reapr-release/release.env` (chmod 600, outside the repo). Agents must **never read, print or grep that file, and never ask for passwords in chat**. (The optional `local.properties` signing block in `app/build.gradle.kts` is for local convenience only and is not used for releases.)

---

## RELEASE WORKFLOW (GitHub Releases + F-Droid reproducible builds)

### F-Droid state (re-check at the start of EVERY release)
* **State B: inclusion MR open** (checked 2026-11-25): <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/59100>, branch `com.fearmikey.rf_reapr` on the `fork` remote (`git@gitlab.com:fearmikey/fdroiddata.git`). Not on fdroiddata `origin/master`, not published on f-droid.org.
* The recipe lives **only** in fdroiddata (`metadata/com.fearmikey.rf_reapr.yml`); there is no copy in this repo. It uses reproducible builds: `Binaries: https://github.com/fearmikey/RF_REAPR/releases/download/v%v/RF_REAPR-%v.apk` and `AllowedAPKSigningKeys: 9caaad2cf535abd6f64159aaa7c63e3471e3f38fbbd88ff536fc894afde23ab2`. F-Droid builds the tag itself, compares it with the GitHub asset and ships the APK with the user's signature.
* The state changes when the MR is merged, so **re-run these checks first** (read-only) and update this section if the state changed:
  1. `cd ~/fdroid/fdroiddata && git fetch origin master && git show origin/master:metadata/com.fearmikey.rf_reapr.yml` (fetch only; never switch that checkout's branch, other sessions use it). If the file exists, the app is merged.
  2. `curl -s "https://gitlab.com/api/v4/projects/fdroid%2Ffdroiddata/merge_requests?state=opened&search=com.fearmikey.rf_reapr&in=title,description"`; read new MR notes with `glab api "projects/fdroid%2Ffdroiddata/merge_requests/59100/notes?sort=desc"` (needs auth) and respect reviewer requests.
  3. `curl -s https://f-droid.org/api/v1/packages/com.fearmikey.rf_reapr` (404 = not published). If published, download `https://f-droid.org/repo/com.fearmikey.rf_reapr_<versionCode>.apk` and check its signer with `apksigner verify --print-certs`.
* Rules by state:
  * **B (MR open):** after the GitHub release, update the MR recipe (replace the build entry, don't add one), keeping the MR at **one commit** (amend + `git push --force-with-lease fork com.fearmikey.rf_reapr`). Only rebase if GitLab reports a conflict. **Ask the user before every push to the fork**, and push only after the GitHub release has its APK.
  * **Merged with `AllowedAPKSigningKeys` and published signer = release cert (state D):** do nothing on fdroiddata. The checkupdates bot builds new tags. Never open version-bump MRs, never push to the old inclusion branch, and never change the asset name (`RF_REAPR-%v.apk`), signing key or signing flags without first merging a minimal fdroiddata MR (ask the user; branch from fresh `origin/master` in a new worktree; run `fdroid rewritemeta` + `fdroid lint`).
  * **Merged but F-Droid-signed (state C):** GitHub-only releases plus the tag. **Never** add `Binaries`/`AllowedAPKSigningKeys`; switching keys would stop updates for existing users.
  * If a bot update is pending or another open MR touches this app, stop and tell the user before touching F-Droid.

### Trigger
When the user says **"do a release"** / **"new release build"**, that counts as approval to commit, tag, push `master` and the tag, run the release script and create the GitHub release. It is **not** approval to push anything to fdroiddata/GitLab.

### Steps
1. **Re-check the F-Droid state** (above). Stop and report if anything changed unexpectedly.
2. **Bump the version** in `app/build.gradle.kts`: `versionCode` +1 (single universal APK, no per-ABI codes), `versionName` X.Y.Z.
3. **CHANGELOG.md:** add a new `## [X.Y.Z] - YYYY-MM-DD` section at the top. Never add to an already released section.
4. **Fastlane:** create `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (max 500 characters).
5. **README** version badge (and anything else that names the version), plus `.agent_manifest.json` if files/components changed.
6. **Commit** `Release X.Y.Z: <summary>` (no AI attribution), then create an **annotated** tag `git tag -a vX.Y.Z -m "RF-REAPR X.Y.Z"`. The tag format must match the recipe's `UpdateCheckMode: Tags ^v[0-9.]+$`.
7. **Push:** `git push origin master && git push origin vX.Y.Z`.
8. **Build:** run `scripts/build-release.sh vX.Y.Z` as a **background** shell session (about 5 minutes). It clones the tag fresh, builds with JDK 17 like F-Droid, and signs with `apksigner --alignment-preserved true`, v2+v3 only. Output: `dist/vX.Y.Z/RF_REAPR-X.Y.Z.apk` (+ `.sha256`). If `~/.config/rf_reapr-release/release.env` is missing, give the user the setup commands from the script header; never handle passwords yourself.
9. **Check** that the printed `Cert SHA-256` equals `AllowedAPKSigningKeys` (`9caaad2c…3ab2`); the script also aborts on a mismatch. Stop if anything differs.
10. **GitHub release:** `gh release create vX.Y.Z dist/vX.Y.Z/RF_REAPR-X.Y.Z.apk --title "RF-REAPR X.Y.Z" --notes "<the CHANGELOG section>"`. If `gh` is unavailable, ask the user to upload in the browser.
11. **F-Droid follow-up:**
    * **B:** in the MR branch, set the build entry's `versionName`/`versionCode`/`commit` (the **full 40-character hash** of the tagged commit, `git rev-parse vX.Y.Z^{commit}`) and `CurrentVersion`/`CurrentVersionCode`; keep `subdir: app`, `submodules: true`, `gradle: [yes]`, the `sudo` JDK 17 block and `ndk:` equal to `ndkVersion`. Run `fdroid rewritemeta com.fearmikey.rf_reapr` and `fdroid lint com.fearmikey.rf_reapr` (from `~/fdroid/venv`, `JAVA_HOME` set), optionally `fdroid build -v com.fearmikey.rf_reapr:<versionCode>`, amend, then **ask** before pushing.
    * **C/D:** nothing. The bot picks up the tag.

### Rules
* Never build release APKs from the working tree or Android Studio. Incremental builds produce a different `classes.dex` (this is why v1.6.0's asset failed F-Droid verification).
* Never move or re-create a pushed tag; fix forward with a new version.
* Never change the signing key, the apksigner flags or the asset name (`RF_REAPR-%v.apk`) on your own.
* **NEVER** sign with the old `rf_reapr.jks` (alias `rf_reapr_alias`, SHA-256 `c802d4a6…081d`). It was publicly exposed in git history and is considered compromised. Release key: `~/Documents/Keystores/RF_REAPR-Release.jks`, alias `key0`.
* Never add path-, time- or machine-dependent build inputs (timestamps, absolute paths, git metadata, hostnames). Keep `vcsInfo { include = false }` and the section 3 items intact.
* If a shell command times out, check whether it actually ran (`git reflog`, `git tag -l`, `pgrep`, output files) before retrying; never blindly repeat `commit --amend`, `tag` or pushes.
