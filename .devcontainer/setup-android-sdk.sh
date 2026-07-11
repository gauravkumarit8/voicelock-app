#!/usr/bin/env bash
# Runs once when the Codespace is created. Downloads Android cmdline-tools
# and the SDK packages this project needs (API 35 platform + build-tools).
# Codespaces has full internet access, unlike some sandboxed dev environments,
# so this pulls directly from Google's hosted SDK manager.
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
mkdir -p "$SDK_ROOT/cmdline-tools"
cd "$SDK_ROOT/cmdline-tools"

if [ ! -d "latest" ]; then
  echo "Downloading Android cmdline-tools..."
  curl -sSL -o cmdline-tools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q cmdline-tools.zip
  rm cmdline-tools.zip
  mv cmdline-tools latest
fi

export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses > /dev/null || true
sdkmanager --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

# Persist env vars for future shells in this Codespace
{
  echo "export ANDROID_HOME=$SDK_ROOT"
  echo "export ANDROID_SDK_ROOT=$SDK_ROOT"
  echo "export PATH=\$PATH:$SDK_ROOT/platform-tools:$SDK_ROOT/cmdline-tools/latest/bin"
} >> ~/.bashrc

# Generate the Gradle wrapper (kept out of source control until first boot
# so the repo doesn't need to vendor the wrapper jar)
if [ ! -f "$(dirname "$0")/../gradlew" ]; then
  cd "$(dirname "$0")/.."
  gradle wrapper --gradle-version 8.9
fi

echo "Android SDK ready at $SDK_ROOT"
