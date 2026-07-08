# Moonlight Touchpad Edition Release Notes

## v0.1.1-touchpad

Moonlight Touchpad Edition is an unofficial fork of Moonlight Android focused on Android tablet keyboard and touchpad support.

This build is not affiliated with the official Moonlight project.

## Highlights

- Xiaomi Pad 6 Max 14 magnetic keyboard and Bluetooth keyboard touchpad handling
- Relative touchpad cursor movement with configurable speed
- One-finger tap and physical touchpad press as left click
- Two-finger tap as right click
- Two-finger vertical and horizontal scrolling
- Two-finger pinch zoom using Ctrl + mouse wheel
- Double tap with second tap held as left-button hold/drag
- Three-finger gestures for middle click, task switching, task view, and desktop
- Default tablet-friendly resolution and low-latency frame pacing
- Sponsor page with bundled WeChat Pay and Alipay QR codes
- Optional sponsor note for device model or requested optimization

## Download

APK:

`moonlight-touchpad-edition-v0.1.1-release.apk`

## Changes Since v0.1.0

- Fixed startup crash in landscape tablet layout caused by the sponsor button only being present in the portrait layout.

Package name:

`com.limelight.touchpadedition`

Because this package name is independent, it can be installed alongside official Moonlight and debug builds.

## License

Moonlight Android is licensed under GPLv3. If you distribute this APK, distribute the corresponding source code too and keep `LICENSE.txt`.

## Remote Sponsor QR Updates

The app includes bundled sponsor QR codes as a fallback.

For remote updates, upload the `sponsor` folder to the repository and set `sponsor_config_url` in `app/src/main/res/values/strings.xml` to the raw GitHub URL for:

`sponsor/sponsor.json`

Example:

`https://raw.githubusercontent.com/YOUR_NAME/moonlight-touchpad-edition/main/sponsor/sponsor.json`

The JSON can use relative image paths:

```json
{
  "wechat_qr_url": "wechat.png",
  "alipay_qr_url": "alipay.jpg"
}
```

After this is configured in the APK, replacing `sponsor/wechat.png` or `sponsor/alipay.jpg` in GitHub will update the QR codes shown in the app the next time the sponsor page is opened.

## Suggested Social Post

Moonlight Touchpad Edition: an unofficial Moonlight Android fork optimized for Android tablet keyboards and touchpads. Tested primarily on Xiaomi Pad 6 Max 14 with the official magnetic keyboard. Supports touchpad movement, taps, two-finger scrolling, pinch zoom, three-finger gestures, and low-latency streaming defaults.

GitHub:

APK:

Source:
