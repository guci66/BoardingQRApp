# Cloud build (GitHub Actions)

## Quick steps
1. Create an empty repo on GitHub named `BoardingQRApp` (private is fine).
2. In this folder run:
   ```bash
   git init
   git branch -M main
   git add .
   git commit -m "init with GA workflow"
   git remote add origin https://github.com/<your-username>/BoardingQRApp.git
   git push -u origin main
   ```
3. Open your repo → **Actions** → **Android CI** → wait for green ✔
4. Download **Artifacts**:
   - `app-debug-apk` → contains `app-debug.apk`
5. (Optional) For release signing, add repo Secrets:
   - `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`
   Then re-run the workflow to get signed **APK** and **AAB**.

## Notes
- The workflow uses Temurin JDK 17 automatically. No local JDK required.
- You can also trigger via **Actions → Run workflow**.