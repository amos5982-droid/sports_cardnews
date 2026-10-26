#!/bin/bash
# 카드뉴스 제작기를 안드로이드 앱(APK)으로 만든다.
# 사용법: ./build.sh sports      (스포츠 카드뉴스, 이 저장소 주소를 연다)
#         ./build.sh economy     (경제 카드뉴스)
# 안드로이드 스튜디오와 SDK 없이, 자바(JDK 11 이상)와 파이썬 3만 있으면 돼요.
# 필요한 도구(aapt2, dx, apksig, android.jar)는 처음 한 번 Maven Central에서 받아 .tools 폴더에 둬요.
set -euo pipefail
cd "$(dirname "$0")"
APP="${1:-sports}"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}"

case "$APP" in
  sports)
    PKG=kr.cardnews.sports; LABEL="스포츠 카드뉴스"
    URL=https://amos5982-droid.github.io/sports_cardnews/card-news-maker.html
    ICON="${ICON_SRC:-../icon-maskable-512.png}"; OUT=sports-cardnews.apk ;;
  economy)
    PKG=kr.cardnews.economy; LABEL="경제 카드뉴스"
    URL=https://amos5982-droid.github.io/-/economy/card-news-maker.html
    ICON="${ICON_SRC:?경제 앱 아이콘 경로를 ICON_SRC로 알려 주세요(economy/icon-maskable-512.png)}"; OUT=economy-cardnews.apk ;;
  *) echo "사용법: $0 sports|economy"; exit 1 ;;
esac

T=.tools
mkdir -p "$T"
C=https://repo.maven.apache.org/maven2
get() { # 경로 -> .tools/파일, 429(요청 제한)면 기다렸다 다시
  local f="$T/$(basename "$1")"; [ -s "$f" ] && return 0
  for i in 1 2 3 4 5 6; do curl -sSf -m 300 -o "$f" "$C/$1" && return 0; rm -f "$f"; echo "다시 받는 중($i): $1"; sleep $((i*8)); done
  return 1
}
get com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar
get com/google/android/tools/dx/1.7/dx-1.7.jar
get org/apktool/apktool-cli/3.0.3/apktool-cli-3.0.3.jar
get org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
ANDROID_JAR="$T/android-all-14-robolectric-10818077.jar"
if [ ! -x "$T/aapt2" ]; then (cd "$T" && unzip -o -q apktool-cli-3.0.3.jar prebuilt/linux/aapt2 && mv prebuilt/linux/aapt2 aapt2 && rm -rf prebuilt && chmod +x aapt2); fi

B="build/$APP"; rm -rf "$B"; mkdir -p "$B/res/mipmap-anydpi-v26" "$B/res/drawable-nodpi" "$B/res/values" "$B/classes"

# 아이콘: 둥근 마스크용(maskable) 그림을 앞면, 그림 모서리 색을 배경으로 쓴다
cp "$ICON" "$B/res/drawable-nodpi/ic_fg.png"
BG=$(python3 -c "
try:
    from PIL import Image
    p = Image.open('$ICON').convert('RGB').getpixel((2, 2)); print('#%02x%02x%02x' % p)
except Exception:
    print('#ffffff')")
echo "<?xml version=\"1.0\" encoding=\"utf-8\"?><resources><color name=\"ic_bg\">$BG</color></resources>" > "$B/res/values/colors.xml"
cat > "$B/res/mipmap-anydpi-v26/ic_launcher.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
  <background android:drawable="@color/ic_bg"/>
  <foreground android:drawable="@drawable/ic_fg"/>
</adaptive-icon>
XML

cat > "$B/AndroidManifest.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$PKG">
  <uses-permission android:name="android.permission.INTERNET"/>
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
  <application android:label="$LABEL" android:icon="@mipmap/ic_launcher" android:allowBackup="true"
      android:usesCleartextTraffic="false" android:theme="@android:style/Theme.Material.Light.NoActionBar">
    <activity android:name="kr.cardnews.app.MainActivity" android:exported="true"
        android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout|uiMode"
        android:windowSoftInputMode="adjustResize">
      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
      <meta-data android:name="start_url" android:value="$URL"/>
    </activity>
  </application>
</manifest>
XML

# 자바 -> dex
javac -nowarn --release 8 -cp "$ANDROID_JAR" -d "$B/classes" app/MainActivity.java 2>&1 | grep -v "^Note:\|warning: \[options\]" || true
python3 tools/patch_classes.py "$B/classes"
java -cp "$T/dx-1.7.jar" com.android.dx.command.Main --dex --output="$B/classes.dex" "$B/classes"

# 리소스와 매니페스트 묶기
"$T/aapt2" compile --dir "$B/res" -o "$B/res.zip"
"$T/aapt2" link -o "$B/base.apk" -I "$ANDROID_JAR" --manifest "$B/AndroidManifest.xml" -R "$B/res.zip" \
  --min-sdk-version 29 --target-sdk-version 34 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" --auto-add-overlay
python3 tools/align.py "$B/base.apk" "$B/aligned.apk" "$B/classes.dex"

# 서명(키는 이 컴퓨터에만 두고 저장소에는 올리지 않아요. 앱을 업데이트하려면 같은 키로 서명해야 해요)
KS="${KEYSTORE:-$HOME/.cardnews-release.p12}"; PW="${KEYSTORE_PASS:-cardnews-release}"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass "$PW" -alias cardnews -keyalg RSA -keysize 2048 \
    -validity 36500 -dname "CN=Card News,O=Card News Maker" >/dev/null 2>&1
  echo "새 서명 키를 만들었어요: $KS (잃어버리지 않게 따로 보관하세요)"
fi
mkdir -p "$B/sign"
javac -nowarn -cp "$T/apksig-2.3.0.jar" -d "$B/sign" tools/Sign.java
java -Dstdout.encoding=UTF-8 --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED --add-exports java.base/sun.security.util=ALL-UNNAMED \
  -cp "$B/sign:$T/apksig-2.3.0.jar" Sign "$KS" "$PW" cardnews "$B/aligned.apk" "$OUT"
echo "완성: android/$OUT ($(du -h "$OUT" | cut -f1))"
"$T/aapt2" dump badging "$OUT" | grep -E "^package|sdkVersion|targetSdk|application-label:|launchable"
