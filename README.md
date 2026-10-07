# Evaclear Android app

The Evaclear app for Android. It opens **https://www.evacleartradingenterprise.com** inside a fast, full-screen app with the Evaclear icon, so customers get the same shop, cart, MoMo/card/bank/credit checkout and My Account they have on the website.

Because the app shows the live website, **every change you make to the website appears in the app straight away**. You only rebuild the app to change its name, icon or version.

## What the app does

| | |
|---|---|
| Opens | `https://www.evacleartradingenterprise.com/` (set in `app/build.gradle.kts` → `websiteUrl`) |
| Stays in the app | Evaclear website pages and Paystack card checkout |
| Opens outside the app | WhatsApp, phone calls, email, maps, Facebook/Instagram/TikTok and other sites |
| Extras | Splash screen, pull-down to refresh, loading bar, "You're offline" screen with Try again / Call us, Android back gesture, file upload (e.g. a payment slip) |
| Shared links | Evaclear website links open straight in the app (after step 4 below) |
| Package name | `com.evacleartradingenterprise.app` (can never change after publishing) |
| Android versions | Android 7.0 and newer; built for Android 16 (API 36) as Google Play requires |

---

## 1. Get the app file (APK) — no software needed (GitHub)

1. Create a free account at **github.com** → **New repository** → name it `evaclear-android` → **Private** → Create.
2. Click **uploading an existing file** and drag in **everything inside** the `evaclear-android` folder (unzip it first).
   - The `.github` folder is hidden on Mac/Windows. If it didn't upload, click **Add file → Create new file**, type the name `.github/workflows/android.yml`, paste the contents of that file from the zip, and **Commit**.
3. Open the **Actions** tab. The "Build Android app" job starts by itself (about 5 minutes). If it doesn't, click it → **Run workflow**.
4. When it shows a green tick, open it and download **evaclear-debug-apk** at the bottom. Unzip it to get `app-debug.apk`.
5. Send the APK to an Android phone (WhatsApp/Drive/USB), open it and allow "Install unknown apps". This test version is called "Evaclear" and can sit next to the Play Store version.

## 2. Make the Play Store version (signed AAB)

Google Play needs an **.aab** file signed with your own *upload key*. You make the key once and keep it safe.

**a. Create the key** (on any computer with Java, or in Android Studio's Terminal):

```bash
keytool -genkeypair -v -keystore evaclear-upload.jks -alias evaclear -keyalg RSA -keysize 2048 -validity 10000
```

Choose a strong password and answer the questions (name: Evaclear Trading Enterprise, city: Kumasi, country code: GH). **Back up `evaclear-upload.jks` and the password** somewhere safe (not in GitHub).

**b. Turn the key into text:**

- Mac/Linux: `base64 -i evaclear-upload.jks | pbcopy` (or `base64 -w0 evaclear-upload.jks` on Linux)
- Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("evaclear-upload.jks")) | Set-Clipboard`

**c. Add 4 secrets** in GitHub → your repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | the text you just copied |
| `KEYSTORE_PASSWORD` | your keystore password |
| `KEY_ALIAS` | `evaclear` |
| `KEY_PASSWORD` | your key password (same as above if you pressed Enter) |

**d.** Go to **Actions → Build Android app → Run workflow**. When it finishes, download **evaclear-release-for-play-store**. Inside is `app-release.aab` (for Google Play) and a signed `app-release.apk`.

### Or build on your computer with Android Studio

1. Install **Android Studio** (developer.android.com/studio) and open the `evaclear-android` folder. Let it download what it asks for.
2. Test: plug in a phone (USB debugging on) and press ▶ Run.
3. Release: **Build → Generate Signed App Bundle or APK → Android App Bundle**, choose/create `evaclear-upload.jks`, pick **release**. The file appears in `app/release/`.

## 3. Publish on Google Play

1. Sign up at **play.google.com/console** (one-time US$25). Choose an **Organization** account for Evaclear Trading Enterprise if you can (needs a free D-U-N-S number); *personal* accounts must run a **closed test with at least 12 testers for 14 days** before going public.
2. **Create app** → name "Evaclear" → App → Free.
3. Fill in the store listing and *App content* using `play-store/store-listing.md`, and upload the pictures in `play-store/` (icon, feature graphic, screenshots).
4. **Testing → Internal testing → Create release** → upload `app-release.aab`. Accept **Play App Signing** when asked. Add your own email as a tester and install it from the link.
5. When happy, promote the release to **Closed testing** (personal accounts) or **Production**, and send for review (usually a few days).

## 4. Link the app and the website both ways

**Website links open in the app (App Links):**

1. In Play Console → **Test and release → App integrity → App signing**, copy the **SHA-256 certificate fingerprint** of the *App signing key*.
2. Open `play-store/assetlinks.json`, replace `PASTE:THE:SHA-256…` with that fingerprint (keep the quotes). Optionally add a second line with your upload key's fingerprint so GitHub/Android Studio test builds also work.
3. In the **website** project, save it as `public/.well-known/assetlinks.json`, rebuild and redeploy. Check it opens at `https://www.evacleartradingenterprise.com/.well-known/assetlinks.json`.

**"Get the app" link on the website:** once the app is live, put its Play link in the website's `src/data/site.json` → `"app": { "playStoreUrl": "https://play.google.com/store/apps/details?id=com.evacleartradingenterprise.app" }` and redeploy. A "Get the Evaclear Android app" button then appears in the footer (hidden inside the app itself).

## 5. Updating the app later

- **Products, prices, pages, payments** → change the website only. The app picks it up instantly.
- **App name, icon, colours or web address** → edit the app, then in `app/build.gradle.kts` raise `appVersionCode` by 1 (and `appVersionName`, e.g. `1.0.1`), build a new AAB and upload it as a new release.

## Where things are

```
app/build.gradle.kts                 website address, version, signing
app/src/main/AndroidManifest.xml     permissions, app links
app/src/main/java/.../MainActivity.java  the app's behaviour
app/src/main/res/values/strings.xml  app name and offline texts
app/src/main/res/values/colors.xml   brand colours
app/src/main/res/drawable/           icon and splash artwork (vector)
app/src/main/res/mipmap-*/           icon for older Android versions
.github/workflows/android.yml        automatic build on GitHub
play-store/                          Play listing text, icon, feature graphic, screenshots, assetlinks.json
```

## Alternative: PWABuilder (no code)

The website is also an installable web app (it has a manifest and works offline). Customers on Android Chrome can tap **⋮ → Add to Home screen / Install app**. You can also go to **pwabuilder.com**, enter the website address and download a ready-made Play Store package (Trusted Web Activity). That route needs the same `assetlinks.json` step. This project is recommended instead, because it keeps WhatsApp, calls and Paystack working smoothly and gives you full control.

## Technical notes

- Android Gradle Plugin 8.13, Gradle 8.14.3, Java 17, compileSdk/targetSdk 36, minSdk 24, no Kotlin.
- Only HTTPS is allowed; file and content access from web pages is off; there is no JavaScript bridge into the app.
- Third-party cookies are on for the Paystack checkout iframe. Login sessions are excluded from cloud backups.
- The website is told it is inside the app through `EvaclearApp/<version>` in the user agent.
