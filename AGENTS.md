# RF-REAPR - Guidelines for AI Agents & Developers

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
* Third-party online services (Shodan, Have I Been Pwned, AbuseIPDB, AlienVault OTX, etc.) must only be contacted on explicit user action. These are declared via the `NonFreeNet` anti-feature in the F-Droid recipe; if a **new** non-free service is added, update the `AntiFeatures` text in `fdroid/com.fearmikey.rf_reapr.yml` and the "Optional online integrations" paragraph in `fastlane/metadata/android/en-US/full_description.txt`.

### 3. NATIVE CODE & REPRODUCIBLE BUILDS
* The native iperf3 library is built from the **Git submodule** at `app/src/main/cpp/iperf3` (declared in `.gitmodules`, pointing to `https://github.com/esnet/iperf.git`). Never commit prebuilt `.so` files.
* **The submodule must stay pinned to an UNMODIFIED commit that exists upstream.** Never commit inside the submodule; F-Droid clones it fresh and cannot fetch local-only commits (this broke the v1.5.6 build).
* RF-REAPR's JNI patches to iperf3 live in the main repo as patched copies in `app/src/main/cpp/iperf_android/` (`cjson.c`, `iperf_error.c` with `longjmp` instead of `exit()`, `iperf_pthread.c`), and `CMakeLists.txt` compiles those instead of the upstream files. To change iperf3 code, copy the file there and update `CMakeLists.txt`. When bumping the submodule, re-apply the patches to the new upstream versions of these files.
* `iperf_config.h` and `version.h` are checked in under `app/src/main/cpp/iperf_android/`. **Do NOT run `./configure` inside the submodule**; it generates `src/iperf_config.h` / `src/version.h`, which shadow the checked-in headers and make local builds differ from F-Droid's. If the submodule is bumped, update `IPERF_VERSION`/`PACKAGE_VERSION` in those headers.
* `ndkVersion` in `app/build.gradle.kts` and `ndk:` in the F-Droid recipe **must match** exactly (currently `27.0.12077973`).
* Keep `-ffile-prefix-map` and `LINKER:--build-id=none` in `app/src/main/cpp/CMakeLists.txt`.
* Keep `dependenciesInfo { includeInApk = false; includeInBundle = false }` in `app/build.gradle.kts`.

### 4. SECRETS
* Never commit keystores (`*.jks`, `*.keystore`), `local.properties`, API keys, or tokens. Signing credentials are read from `local.properties` (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

---

## RELEASE & PUBLISHING WORKFLOW (F-DROID)

### AUTOMATED F-DROID UPDATES
The app is published on F-Droid with Reproducible Builds enabled and auto-update tracking via Git tags. When helping the user create a new release, agents **MUST** follow this exact workflow to ensure F-Droid successfully picks up the update:

1. **Bump Versions:** Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. **Reproducible Build Configuration:** Ensure `vcsInfo { include = false }` is present in `app/build.gradle.kts` under `buildTypes.release`. This prevents AGP from embedding local Git commit metadata in `META-INF/version-control-info.textproto`, which breaks F-Droid reproducible build verification. Also confirm the items in section 3 above are intact.
3. **Fastlane Changelog:** Create a new text file at `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` containing concise release notes (max 500 characters).
4. **Standard Changelog & Readme:** Update the main `CHANGELOG.md` with detailed bullet points under `## [X.Y.Z] - YYYY-MM-DD` and update the version badge in `README.md`.
5. **Commit the Release:** Commit all source/version changes. This commit is the one F-Droid builds.
6. **F-Droid Metadata Recipe:** Update `fdroid/com.fearmikey.rf_reapr.yml`:
   * Update `versionName` and `versionCode` in the `Builds` entry, plus `CurrentVersion` and `CurrentVersionCode`.
   * Update `commit` with the **full 40-character Git commit hash** of the release commit from step 5 (`git rev-parse HEAD`). Never use a tag name or a short hash.
   * Ensure `gradle:` is set to `- yes` (do NOT use `- foss`; there are no product flavors).
   * Ensure `submodules: true` and `ndk:` matching `ndkVersion` are present.
   * Commit the recipe change separately (it must not alter the release commit).
7. **Tag & Push:** Create a git tag strictly following the `vX.Y.Z` format (e.g., `v1.5.7`) **on the release commit from step 5** (`git tag vX.Y.Z <hash>`), then push both the commits and the tag to GitHub (`git push origin master --tags`).
8. **Reproducible Build Binary:** F-Droid requires the compiled APK to verify reproducible builds. Instruct the user to:
   * Check out the tagged commit with submodules (`git submodule update --init --recursive`) and build the signed release APK (`app-release.apk`) using their secure `rf_reapr.jks` keystore (kept outside the repository).
   * Go to GitHub Releases, draft a new release for the newly pushed `vX.Y.Z` tag.
   * Upload the `app-release.apk` file. (F-Droid is hardcoded to look for this exact filename at `https://github.com/fearmikey/RF_REAPR/releases/download/v%v/app-release.apk`.)
   * The APK must be signed with the key whose SHA-256 certificate digest matches `AllowedAPKSigningKeys` in the recipe.
