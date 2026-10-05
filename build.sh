#!/bin/sh
# Bildirimim APK derleme betiği (Gradle gerektirmez).
# Gerekenler: JDK 11+, Android SDK (platforms;android-34 ve build-tools;34.0.0)
# Kullanım:  ANDROID_HOME=/sdk/yolu sh build.sh
set -e
: "${ANDROID_HOME:?ANDROID_HOME ayarlanmalı (Android SDK klasörü)}"
BT="$ANDROID_HOME/build-tools/${BUILD_TOOLS:-34.0.0}"
JAR="$ANDROID_HOME/platforms/android-34/android.jar"
KS="${KEYSTORE:-$HOME/.android/debug.keystore}"

rm -rf build && mkdir -p build/gen build/classes build/dex
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml -A assets \
  --java build/gen --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0 build/res.zip
javac --release 8 -Xlint:-options -cp "$JAR" -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --lib "$JAR" --min-api 26 --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

# Debug anahtarı yoksa oluştur
[ -f "$KS" ] || { mkdir -p "$(dirname "$KS")"; keytool -genkeypair -keystore "$KS" -storepass android \
  -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"; }
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --out apk/Bildirimim.apk build/aligned.apk
echo "Hazır: apk/Bildirimim.apk"
