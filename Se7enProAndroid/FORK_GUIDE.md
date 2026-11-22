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

پروژه از قبل کانفیگ امضای داخلی دارد: اگر فایل `Se7enProAndroid/app/keystore/se7en-release.keystore` و `keystore.properties` موجود باشند، بیلد release **خودکار با آن امضا می‌شود**؛ در غیر این صورت با کلید debug امضا می‌شود (برای CI امن است).

### ساخت keystore جدید برای خودت
```bash
mkdir -p Se7enProAndroid/app/keystore
keytool -genkeypair -v \
  -keystore Se7enProAndroid/app/keystore/se7en-release.keystore \
  -alias se7en -keyalg RSA -keysize 2048 -validity 10950
```

### فایل پسوردها (هرگز commit نشود — در .gitignore هست)
`Se7enProAndroid/app/keystore/keystore.properties`:
```properties
storePassword=<رمز keystore>
keyAlias=se7en
keyPassword=<رمز کلید>
```

سپس:
```bash
./gradlew assembleRelease
# خروجی: app/build/outputs/apk/release/app-release.apk  (امضاشده با کلید خودت)
```

> ⚠️ فایل keystore را در جایی امن نگه دار — هر نسخه جدید باید با همان کلید امضا شود تا آپدیت روی نسخه‌های قبلی نصب شود. هرگز keystore را در ریپو یا Release آپلود نکن.
> keystore فعلی نسخه v1.0.0 از سازنده اولیه تهیه شده؛ برای برند خودت حتماً یک keystore جدید بساز.

## 6) بیلد خودکار با GitHub Actions (فعال است ✅)

Workflow آماده: `.github/workflows/android-build.yml` — روی هر push به شاخه `android` (در مسیر `Se7enProAndroid/**`) به‌صورت خودکار:
1. JDK 17 را نصب می‌کند
2. با `scripts/fetch-engine.sh` باینری موتور را دانلود می‌کند
3. `assembleRelease` می‌زند (بدون keystore → امضای debug)
4. APK را به آرتیفکت‌های Actions آپلود می‌کند
5. اگر تگ `v*` پوش شود، APK مستقیماً در GitHub Releases منتشر می‌شود

استفاده دستی: تب **Actions** در ریپو → Android Build → Run workflow.

> برای انتشار خودکار با کلید خودت در CI: محتوای keystore را به‌صورت Base64 در Secret `SE7EN_KEYSTORE` و پسوردها در `SE7EN_KEYSTORE_PASS` بگذار و یک step decode قبل از بیلد اضافه کن.

## 7) مجوز

- کد اپ اندروید: **MIT** (همان مجوز مخزن اصلی) — کپی LICENSE را نگه دار
- موتور sing-box: **GPLv3** (باینری جدا مجوز خودش را حفظ می‌کند؛ اپ شما به آن فقط لینک/باندل می‌کند)
- hev-socks5-tunnel: **MIT**

اگر قصد انتشار روی Google Play داری: باندل‌کردن باینری‌های GPL مطابق سیاست‌های Play بررسی شود؛ انتشار در GitHub/GitLab/F-Droid بدون مشکل است.
