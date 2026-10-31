#!/usr/bin/env bash
#
# One-shot build script for the Android Code Studio AI fork.
#
# What it does:
#   1. Installs the Android command-line tools + required SDK components
#      (platform 34, build-tools 35.0.0, platform-tools) if not present.
#   2. Generates a local debug keystore (signing/local-debug-key.jks) so the
#      build does not need the upstream private signing password.
#   3. Runs the Gradle debug build and prints where the APK lands.
#
# All downloads use domestic mirrors (Aliyun) for speed in China.
#
# Usage:
#   ./build.sh            # full build
#   ./build.sh sdk        # only set up the Android SDK
#

set -euo pipefail
cd "$(dirname "$0")"

export DEBIAN_FRONTEND=noninteractive

# --- 1. Android SDK -------------------------------------------------------
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

setup_sdk() {
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  local tools_zip="/tmp/android-commandlinetools.zip"
  local tools_root="$ANDROID_HOME/cmdline-tools/latest"

  if [ ! -x "$tools_root/bin/sdkmanager" ]; then
    echo "==> Downloading Android command-line tools..."
    curl -fsSL -o "$tools_zip" \
      "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

    rm -rf "$tools_root"
    # the archive contains a 'cmdline-tools' folder
    unzip -q -o "$tools_zip" -d /tmp/android-tools-tmp
    rm -rf "$tools_root"
    if [ -d /tmp/android-tools-tmp/cmdline-tools ]; then
      mv /tmp/android-tools-tmp/cmdline-tools "$tools_root"
    fi
    chmod +x "$tools_root/bin/sdkmanager" 2>/dev/null || true
  fi

  echo "==> Installing SDK components (platform 34, build-tools 35.0.0, platform-tools)..."
  yes | "$tools_root/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
      "platforms;android-34" "build-tools;35.0.0" "platform-tools" || true

  echo "sdk.dir=$ANDROID_HOME" > local.properties
}

# --- 2. Local debug keystore ---------------------------------------------
setup_keystore() {
  local key="$PWD/signing/local-debug-key.jks"
  if [ ! -f "$key" ]; then
    echo "==> Generating local debug keystore at signing/local-debug-key.jks..."
    keytool -genkeypair -v \
      -keystore "$key" \
      -storepass androidcs -keypass androidcs \
      -alias androidcs \
      -dname "CN=Android Code Studio AI, OU=dev, O=dev, L=NA, S=NA, C=US" \
      -keyalg RSA -keysize 2048 -validity 10000
  fi
}

# --- 2.5 JDK 21 -----------------------------------------------------------
# fuzzysearch.jar and some toolchain artifacts are built for Java 21 (class
# version 65). The kapt/javac step cannot read them on JDK 17, so pin JDK 21.
setup_jdk21() {
  export JDK21_HOME="${JDK21_HOME:-/opt/jdk21}"
  if [ -x "$JDK21_HOME/bin/java" ]; then
    echo "==> Using JDK 21 at $JDK21_HOME"
    return 0
  fi
  echo "==> JDK 21 missing, downloading Temurin 21..."
  mkdir -p /tmp/jdk21-dl "$JDK21_HOME"
  curl -fsSL --retry 3 -o /tmp/jdk21-dl/jdk21.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
  tar -xzf /tmp/jdk21-dl/jdk21.tar.gz -C "$JDK21_HOME" --strip-components=1
  "$JDK21_HOME/bin/java" -version
}

# --- 3. Build -------------------------------------------------------------
do_build() {
  setup_keystore
  setup_jdk21
  export JAVA_HOME="$JDK21_HOME"
  export PATH="$JDK21_HOME/bin:$PATH"

  export ACS_KEYSTORE="$PWD/signing/local-debug-key.jks"
  export SIGNING_STORE_PASSWORD="androidcs"
  export SIGNING_KEY_PASSWORD="androidcs"
  export ACS_KEY_ALIAS="androidcs"

  echo "==> Building debug APK..."
  ./gradlew :core:app:assembleDebug --no-daemon "$@"

  echo
  echo "==> Build finished. APK output location:"
  ls -lh core/app/build/outputs/apk/ 2>/dev/null || true
  find core/app/build/outputs/apk -name "*.apk" 2>/dev/null || true
}

case "${1:-all}" in
  sdk)  setup_sdk; echo "SDK setup complete.";;
  all)  setup_sdk; do_build;;
  build) setup_keystore; do_build;;
  *) echo "usage: $0 [all|sdk|build]"; exit 1;;
esac
