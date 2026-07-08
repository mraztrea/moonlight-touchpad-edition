# Moonlight Touchpad Edition Release Notes

## v0.1.4-touchpad

Moonlight Touchpad Edition is an unofficial fork of Moonlight Android focused on Android tablet keyboard and touchpad support.

Moonlight Touchpad Edition 是 Moonlight Android 的非官方修改版，主要优化安卓平板键盘和触摸板体验。

This build is not affiliated with the official Moonlight project.

本项目与官方 Moonlight 项目无隶属关系。

## Highlights / 主要特性

- Xiaomi Pad 6 Max 14 magnetic keyboard and Bluetooth keyboard touchpad handling
- 支持小米 Pad 6 Max 14 原厂磁吸键盘和蓝牙键盘触摸板输入
- Relative touchpad cursor movement with configurable speed
- 支持触摸板相对移动鼠标指针，并可调节指针速度
- Default touchpad pointer speed is 40%, adjustable up to 100%
- 触摸板指针速度默认 40%，最高可调到 100%
- Default tablet-friendly resolution and low-latency frame pacing
- 默认使用更适合平板的分辨率和低延迟帧同步策略
- Independent package name: `com.limelight.touchpadedition`
- 使用独立包名 `com.limelight.touchpadedition`，可与官方 Moonlight 共存安装

## Added Gestures / 新增手势

- One-finger movement: move the host mouse pointer
- 单指移动：移动串流电脑上的鼠标指针
- One-finger tap: left click
- 单指轻点：鼠标左键单击
- Physical touchpad press: left click / left-button hold
- 按下触摸板：鼠标左键单击或左键按住
- Double tap with second tap held: left-button hold and drag
- 双击且第二下按住：鼠标左键按住并拖拽
- Two-finger tap: right click
- 双指轻点：鼠标右键
- Two-finger vertical scroll: mouse wheel vertical scrolling
- 双指上下滑动：鼠标滚轮纵向滚动
- Two-finger horizontal scroll: mouse wheel horizontal scrolling
- 双指左右滑动：鼠标横向滚动
- Two-finger pinch: zoom using Ctrl + mouse wheel
- 双指捏合：通过 Ctrl + 鼠标滚轮模拟缩放
- Three-finger tap: middle click
- 三指轻点：鼠标中键
- Three-finger left/right swipe: Alt + Tab task switching
- 三指左右滑动：Alt + Tab 任务切换
- Three-finger up swipe: task view
- 三指上滑：任务视图
- Three-finger down swipe: show desktop
- 三指下滑：显示桌面

## Download / 下载

APK:

`moonlight-touchpad-edition-v0.1.4-release.apk`

Source / 源码:

`moonlight-touchpad-edition-v0.1.4-source.zip`

## Package Name / 包名

`com.limelight.touchpadedition`

Because this package name is independent, it can be installed alongside official Moonlight and debug builds.

由于使用独立包名，它可以和官方 Moonlight、调试版同时安装。

## License / 许可证

Moonlight Android is licensed under GPLv3. If you distribute this APK, distribute the corresponding source code too and keep `LICENSE.txt`.

Moonlight Android 使用 GPLv3 许可证。如果分发 APK，也应同时分发对应源码并保留 `LICENSE.txt`。

## Remote Sponsor QR Updates / 远程赞助二维码更新

The app includes bundled sponsor QR codes as a fallback.

应用内置赞助二维码作为兜底。

For remote updates, replace these files in the GitHub repository:

如需远程更新二维码，替换 GitHub 仓库中的这些文件：

- `sponsor/wechat.png`
- `sponsor/alipay.jpg`

The app reads:

应用读取：

`https://raw.githubusercontent.com/wyc0820/moonlight-touchpad-edition/main/sponsor/sponsor.json`

## Suggested Social Post / 社交平台文案

Moonlight Touchpad Edition is an unofficial Moonlight Android fork optimized for Android tablet keyboards and touchpads. It supports one-finger pointer movement, left/right/middle click gestures, two-finger vertical and horizontal scrolling, pinch zoom, double-tap-hold dragging, and three-finger task gestures. Tested primarily on Xiaomi Pad 6 Max 14 with the official magnetic keyboard.

Moonlight Touchpad Edition 是一个针对安卓平板键盘和触摸板体验优化的非官方 Moonlight Android 修改版。支持单指移动指针、左键/右键/中键手势、双指纵向和横向滚动、双指捏合缩放、双击按住拖拽，以及三指任务切换等手势。主要基于小米 Pad 6 Max 14 原厂磁吸键盘测试。
