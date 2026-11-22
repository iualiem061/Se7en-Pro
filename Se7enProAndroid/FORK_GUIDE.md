<div dir="rtl">

# راهنمای فورک کردن Se7en Pro و انتشار نسخه اندروید

این سند دقیقاً مراحل فورک مخزن اصلی، افزودن شاخه اندروید، بیلد و انتشار APK را توضیح می‌دهد.

</div>

# Forking Se7en Pro & shipping the Android app

## 1) فورک در GitHub (روش وب — ساده‌ترین)

1. برو به `https://github.com/KNG7-P/Se7en-Pro`
2. دکمه **Fork** بالا-راست را بزن → مخزن به اکانت خودت کپی می‌شود (مثلاً `https://github.com/<YOU>/Se7en-Pro`)
3. کلون کن:
```bash
git clone https://github.com/<YOU>/Se7en-Pro.git
cd Se7en-Pro
```

## 2) افزودن شاخه اندروید

سورس اپ اندروید (پوشه `Se7enProAndroid/`) روی شاخه `android` این مخزن قرار داده شده. اگر از قبل آماده کرده‌ای:

```bash
# اگر شاخه android از قبل ساخته شده:
git checkout android

# اگر می‌خواهی خودت بسازی:
git checkout -b android
cp -r /path/to/Se7enProAndroid .   # پوشه پروژه اندروید
git add Se7enProAndroid
git commit -m "Add Se7en Pro Android client"
```

## 3) پوش به فورک خودت

```bash
git remote set-url origin https://github.com/<YOU>/Se7en-Pro.git
git push origin android
```

> نکته: برای پوش به GitHub به یک توکن دسترسی (PAT) نیاز داری — در تنظیمات GitHub → Developer settings → Personal access tokens بساز و به‌جای پسورد استفاده کن.

## 4) بیلد APK

### روش Android Studio (پیشنهادی)
1. پوشه `Se7enProAndroid/` را در Android Studio باز کن (File → Open)
2. یک‌بار در ترمینال استودیو: `bash scripts/fetch-engine.sh` (دانلود باینری موتور ~۸۰MB)
3. منوی Build → Build Bundle(s)/APK(s) → Build APK(s)

### روش خط فرمان
```bash
cd Se7enProAndroid
bash scripts/fetch-engine.sh
./gradlew assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
```

پیش‌نیازها: Android SDK 34 + JDK 17 (Android Studio هر دو را دارد).

## 5) امضا برای انتشار (Release)

```bash
keytool -genkey -v -keystore se7en.keystore -alias se7en -keyalg RSA -keysize 2048 -validity 10000
./gradlew assembleRelease
# سپس با apksigner امضا کن، یا تنظیمات signingConfig را به app/build.gradle.kts اضافه کن
```

## 6) بیلد خودکار با GitHub Actions (اختیاری)

فایل `.github/workflows/android.yml` در شاخه `android`:

```yaml
name: Android CI
on: { push: { branches: [android] } }
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: android-actions/setup-android@v3
      - run: cd Se7enProAndroid && bash scripts/fetch-engine.sh && ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with: { name: se7en-pro-android, path: Se7enProAndroid/app/build/outputs/apk/debug/*.apk }
```

## 7) مجوز

- کد اپ اندروید: **MIT** (همان مجوز مخزن اصلی) — کپی LICENSE را نگه دار
- موتور sing-box: **GPLv3** (باینری جدا مجوز خودش را حفظ می‌کند؛ اپ شما به آن فقط لینک/باندل می‌کند)
- hev-socks5-tunnel: **MIT**

اگر قصد انتشار روی Google Play داری: باندل‌کردن باینری‌های GPL مطابق سیاست‌های Play بررسی شود؛ انتشار در GitHub/GitLab/F-Droid بدون مشکل است.
