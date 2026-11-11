# Eigen 1.1 launch plan

What 1.1 adds is in the README's Power features section. This is how to put it in front of the people who pay for launchers.

## Before publishing (yours to do)

1. **Play Console: accessibility declaration.** Eigen now ships an AccessibilityService. Fill in App content → Accessibility API with the text in `docs/store/PLAY_LISTING.md` (Accessibility service section) and record the in-app disclosure (Settings → Gestures → Turn on) for the review video. Expect review to take longer than usual.
2. **Price.** Plus is ₹349 today (fallback in `EigenState.plusPrice`). With private apps and automation in it, a launch at the equivalent of US$4.99–5.99 (₹449–499) fits the Nova Prime / Smart Launcher Pro range. Change it in Play Console; the app shows Play's price. Consider a launch week at today's price.
3. **Real phones.** Run `./scripts/perf.sh` on a Pixel and a Samsung and keep `build/perf/report.md`. Test the gesture service, the biometric prompt (Android 9 PIN fallback included), Tasker's run-task permission, and contacts search on hardware: these were built against the APIs and only partly exercised on the emulator.
4. **Version.** Done: 1.1.0 (versionCode 7).

## Where enthusiasts are

| Place | Post | Notes |
| --- | --- | --- |
| r/androidapps | "I made a launcher with no internet permission: gestures, Tasker contexts, on-device AI" | Lead with the privacy model and a 30 s screen recording of a context switching when headphones connect. |
| r/androidthemes, r/androidthemes Discord | A setup screenshot with its `.eigen` file attached | Setups are the growth loop: every shared setup is an ad. |
| XDA forums, Apps & Games | A thread with the changelog, perf report and a beta link | Answer every reply for the first week. |
| r/tasker | The automation section of the README, with a sample profile | Tasker users pay for integrations. |
| Android Police / 9to5Google tips inbox | A two-paragraph pitch plus the press kit | "No internet permission" plus "Gemini Nano on device" is the hook. |

## Reddit post draft

> **Eigen Launcher 1.1: a calm launcher with power under the hood, and no internet permission**
>
> I build Eigen alone. It's a drawer-based home screen (Ratio fans will feel at home) that never goes online: no ads, no analytics, no account, no INTERNET permission in the manifest.
>
> New in 1.1: settable gestures (double-tap to lock and more), search for shortcuts, contacts, settings and unit conversions, icon shapes and themed icons, private apps behind your fingerprint, widget stacks, shareable setups, and contexts that react to battery, Wi-Fi, meetings or your car's Bluetooth and can run Tasker tasks. The assistant runs on Gemini Nano on your phone and can set timers or catch you up on messages.
>
> Free is a complete launcher. Plus is a one-time purchase, no subscription. Cold start on my Pixel: [perf.sh number] ms; frame report in the comments.

## Measuring it

Watch Play Console's conversion to Plus, refunds and 1-star reviews for the first two weeks. If reviews mention the accessibility prompt, move the Turn on step later (it's only asked for when a system gesture is first used).
