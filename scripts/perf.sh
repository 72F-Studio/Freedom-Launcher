#!/usr/bin/env bash
# Measures Eigen the way enthusiasts will: cold start and home's frame times, on a real device.
# Usage: ANDROID_SERIAL=<device> ./scripts/perf.sh [runs]   (installs the R8 qa build; clears nothing)
# Writes build/perf/report.md with the device, the numbers and how they were taken, ready for a post or the listing.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the device to measure (adb devices lists them)}"
RUNS="${1:-10}"
PKG=com.studio72f.eigen
ACT=$PKG/.MainActivity
OUT=build/perf; mkdir -p "$OUT"

./gradlew -q :app:assembleQa
adb install -r app/build/outputs/apk/qa/app-qa.apk >/dev/null
# Let ART apply the baseline profile the way Play would.
adb shell cmd package compile -f -m speed-profile "$PKG" >/dev/null

model="$(adb shell getprop ro.product.manufacturer | tr -d '\r') $(adb shell getprop ro.product.model | tr -d '\r')"
android="$(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"

starts=()
for i in $(seq "$RUNS"); do
  adb shell am force-stop "$PKG"; sleep 1
  t=$(adb shell am start -W -n "$ACT" | tr -d '\r' | awk -F': ' '/TotalTime/ {print $2}')
  starts+=("$t")
done
median=$(printf '%s\n' "${starts[@]}" | sort -n | awk '{a[NR]=$1} END {print (NR%2)?a[(NR+1)/2]:int((a[NR/2]+a[NR/2+1])/2)}')

# Frames: open the app drawer, both side pages and every drawer, a few times over.
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
read -r W H < <(adb shell wm size | tr -d '\r' | awk -F'[ x]' '/Physical/ {print $3, $4}')
for i in 1 2 3; do
  adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 250; sleep 0.6   # app drawer
  adb shell input keyevent KEYCODE_BACK; sleep 0.5
  adb shell input swipe $((W/2)) $((H/3)) $((W/2)) $((H*2/3)) 250; sleep 0.8   # open every drawer
  adb shell input swipe $((W/2)) $((H/3)) $((W/2)) $((H*2/3)) 250; sleep 0.8   # and close them
  adb shell input swipe $((W*4/5)) $((H/2)) $((W/5)) $((H/2)) 200; sleep 0.6   # widgets page
  adb shell input swipe $((W/5)) $((H/2)) $((W*4/5)) $((H/2)) 200; sleep 0.6   # home
  adb shell input swipe $((W/5)) $((H/2)) $((W*4/5)) $((H/2)) 200; sleep 0.6   # conversations
  adb shell input swipe $((W*4/5)) $((H/2)) $((W/5)) $((H/2)) 200; sleep 0.6   # home
done
gfx="$(adb shell dumpsys gfxinfo "$PKG" | tr -d '\r')"
frames=$(awk -F': ' '/Total frames rendered/ {print $2; exit}' <<<"$gfx")
janky=$(awk -F': ' '/Janky frames:/ {print $2; exit}' <<<"$gfx")
p50=$(awk -F': ' '/50th percentile/ {print $2; exit}' <<<"$gfx")
p90=$(awk -F': ' '/90th percentile/ {print $2; exit}' <<<"$gfx")
p99=$(awk -F': ' '/99th percentile/ {print $2; exit}' <<<"$gfx")

cat > "$OUT/report.md" <<MD
# Eigen performance · $(date +%F)

Device: $model, Android $android. Build: R8 qa (release code, debuggable), baseline profile applied.

| Measure | Result |
| --- | --- |
| Cold start, median of $RUNS | ${median} ms |
| Frames rendered | ${frames} |
| Janky frames | ${janky} |
| Frame time p50 / p90 / p99 | ${p50} / ${p90} / ${p99} |

How: \`am start -W\` after \`force-stop\` for cold start; \`dumpsys gfxinfo\` over three passes of the app drawer, open and
close all drawers, and both side pages, driven by \`input swipe\`. Reproduce with \`ANDROID_SERIAL=<device> ./scripts/perf.sh\`.
MD
cat "$OUT/report.md"
