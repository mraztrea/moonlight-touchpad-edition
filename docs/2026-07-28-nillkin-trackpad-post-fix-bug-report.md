# Báo cáo lỗi (lần 2): Trackpad Nillkin Pocket vẫn không điều khiển được con trỏ Windows sau bản sửa

- **Ngày:** 2026-07-28
- **Trạng thái:** Đã phân tích trên code hiện tại (sau khi plan `docs/superpowers/plans/2026-07-28-nillkin-trackpad-windows-fix.md` đã được thực thi Task 1–5). **Chưa sửa code.**
- **Thiết bị:** Lenovo TB321FU (Android API 36) + Nillkin Cube Pocket Foldable Keyboard `0x21CE:0xB907`
- **Host:** Windows (Sunshine/GFE)
- **Tài liệu liên quan:**
  - `docs/nillkin-trackpad-bug-report-2026-07-28.md` (báo cáo lần 1)
  - `docs/superpowers/plans/2026-07-28-nillkin-trackpad-windows-fix.md` (plan đã thực thi)

---

## 1. Hiện tượng

Sau khi áp dụng bản sửa lần 1, trackpad Nillkin **vẫn không** điều khiển được con trỏ trên host Windows. Bàn phím gõ bình thường; trackpad hoạt động đầy đủ ở tầng Android.

## 2. Những gì bản sửa lần 1 **đã** làm được

Kiểm tra code hiện tại xác nhận các thay đổi đã có mặt:

| Hạng mục | Vị trí | Trạng thái |
|---|---|---|
| Nới `isRelativeMouseEvent()` — chấp nhận `TOOL_TYPE_FINGER`, chỉ loại STYLUS/ERASER | `AndroidNativePointerCaptureProvider.java:112-116` | ✅ |
| `TouchpadMouseDeltaAccumulator` giữ phần dư sub-pixel | `Game.java:1960-1970` | ✅ |
| `hasJoystickAxes()` chặn `ControllerHandler` nuốt event của bàn phím composite | `ControllerHandler.java:318-327`, `Game.java:2582` | ✅ |
| `shouldUseAbsoluteMouseFallback()` | `Game.java:2559-2561` | ✅ (nhưng **thiếu** — xem RC-1) |
| `isTouchpadEvent()` nhận diện theo capability, bỏ fingerprint vendor/product | `Game.java:1875-1915` | ✅ |
| `resetTouchpadGestureState()` ở lifecycle boundary | `Game.java:833, 1263, 2043, 3028` | ✅ |
| `TouchpadDeviceMatcher` / `TouchpadRelativeAdapter` cũ | đã xoá | ✅ |

Nghĩa là: **các nguyên nhân RC-1…RC-7 của báo cáo lần 1 đã được xử lý.** Lỗi còn lại là lỗi **mới**, sinh ra bởi chính cách bản sửa tái cấu trúc chuỗi `if/else-if` trong `handleMotionEvent()`.

---

## 3. Nguyên nhân gốc

### RC-1 (CHẶN, mức nghiêm trọng cao) — Chuỗi `if/else-if` bị tách đôi, làm `updateMousePosition()` chạy **song song** với đường relative

Đây là nguyên nhân chính và là **hồi quy do bản sửa lần 1 tạo ra**.

Moonlight upstream có chuỗi liền mạch, loại trừ lẫn nhau:

```java
if (inputCaptureProvider.eventHasRelativeMouseAxes(event)) {
    // gửi delta tương đối
}
else if ((eventSource & SOURCE_CLASS_POSITION) != 0) { ... }
else if (view != null && trySendPenEvent(view, event)) { return true; }
else if (view != null) { updateMousePosition(view, event); }
```

Code hiện tại (`Game.java:2650-2700`) đã **tách nhánh đầu ra khỏi chuỗi**:

```java
boolean handledRelativeMouseMotion = inputCaptureProvider.eventHasRelativeMouseAxes(event);
if (handledRelativeMouseMotion) {                       // ← if ĐỘC LẬP, không còn else-if
    float deltaX = inputCaptureProvider.getRelativeAxisX(event);
    float deltaY = inputCaptureProvider.getRelativeAxisY(event);
    ...sendTouchpadMouseMove(deltaX, deltaY);
}
if (shouldTryTouchpadRelativeMove(handledRelativeMouseMotion) &&   // ← chuỗi MỚI bắt đầu lại
        trySendTouchpadRelativeMove(event, eventSource)) {
    ...
}
else if (!handledRelativeMouseMotion && (eventSource & SOURCE_CLASS_POSITION) != 0) { ... }
else if (view != null && trySendPenEvent(view, event)) { ... }
else if (view != null && shouldUseAbsoluteMouseFallback(eventSource)) {
    updateMousePosition(view, event);                   // ← VẪN CHẠY dù đã gửi delta
}
```

Truy vết với event của Nillkin khi pointer capture bật và **event source = `SOURCE_TOUCHPAD` (`0x00100008`)**:

| Điều kiện | Giá trị | Kết quả |
|---|---|---|
| `handledRelativeMouseMotion` | `SOURCE_TOUCHPAD && targetView.hasPointerCapture()` → **true** | đã gửi `sendMouseMove(delta)` |
| `shouldTryTouchpadRelativeMove(true)` | `false` | bỏ qua |
| `!handledRelativeMouseMotion && POSITION` | `false` | bỏ qua |
| `view != null && trySendPenEvent(...)` | `false` (tool là FINGER → `convertToolTypeToStylusToolType` trả UNKNOWN) | bỏ qua |
| `view != null && shouldUseAbsoluteMouseFallback(0x100008)` | `0x100008 != 0x20004` → **true** | ⚠️ **`updateMousePosition()` CHẠY** |

`view` khác `null` vì event capture được giao qua `streamView.setOnCapturedPointerListener` (`Game.java:371-376`).

`updateMousePosition()` (`Game.java:2939-2990`) coi `event.getX(0)`/`getY(0)` là toạ độ trong `streamView`. Nhưng dưới pointer capture, event `SOURCE_TOUCHPAD` mang **toạ độ thô của mặt trackpad** (đo được `0..1887` × `0..2015` theo báo cáo lần 1). Hàm clamp về `[0, streamView.getWidth()]` × `[0, streamView.getHeight()]` rồi gọi:

```java
conn.sendMousePosition((short)eventX, (short)eventY,
                       (short)streamView.getWidth(), (short)streamView.getHeight());
```

→ **Mỗi motion event gửi 2 gói mâu thuẫn nhau: một gói relative đúng, rồi ngay sau đó một gói absolute sai.** Gói absolute đến sau nên thắng. Con trỏ Windows bị "ghim" theo toạ độ thô trackpad đã clamp — di chuyển thất thường, không tương ứng với ngón tay, chiều dọc bão hoà ở đáy (2015 > chiều cao view). Triệu chứng người dùng thấy đúng là "không điều khiển được".

**Bằng chứng gián tiếp mạnh:** chuột USB/Bluetooth rời vẫn hoạt động bình thường vì source của chúng là `SOURCE_MOUSE_RELATIVE`, đúng ngưỡng duy nhất mà `shouldUseAbsoluteMouseFallback()` chặn. Guard này được viết ra để chặn đúng triệu chứng này nhưng **chỉ chặn `SOURCE_MOUSE_RELATIVE`, bỏ sót `SOURCE_TOUCHPAD` dưới capture** — mà đó lại chính là source của Nillkin.

### RC-2 (CHẶN, chỉ kích hoạt nếu `AXIS_RELATIVE_X/Y` rỗng) — "đã xử lý" được đặt true kể cả khi delta = 0, làm chết đường dự phòng absolute→delta

`shouldTryTouchpadRelativeMove(handled)` chỉ trả `true` khi `handled == false`. Nhưng `handled` được đặt bằng `eventHasRelativeMouseAxes()`, tức là **"event có khả năng mang trục relative"**, không phải "đã thực sự gửi được chuyển động".

Với `SOURCE_TOUCHPAD` + capture, `getRelativeAxisX/Y()` đọc `AXIS_RELATIVE_X` / `AXIS_RELATIVE_Y` (`AndroidNativePointerCaptureProvider.java:129-148`). Nếu driver/InputMapper của thiết bị này **không populate** hai trục đó (rất có thể với `TouchInputMapper` chạy ở `DeviceMode::UNSCALED` — chế độ mà AOSP chuyển touchpad sang khi `pointerCaptureRequest.enable == true`), thì:

- `deltaX = deltaY = 0`
- `sendTouchpadMouseMove()` trả `false`, không gửi gì
- nhưng `handled` vẫn `= true` → `trySendTouchpadRelativeMove()` **bị bỏ qua hoàn toàn**

`trySendTouchpadRelativeMove()` (`Game.java:2426-2553`) là đường duy nhất còn lại có thể tự tính delta từ toạ độ tuyệt đối (`event.getX(0) - lastTouchpadMouseX`) — và nó **sẽ chạy đúng** nếu được gọi. Bị chặn ở đây nghĩa là mất toàn bộ di chuyển con trỏ **và** mất luôn tap-to-click, tap-drag, double-tap-drag.

Đây chính là hệ quả trực tiếp của quyết định trong plan Task 2 Step 4: *"The finger relative-axes branch is considered handled even for zero deltas, preventing fallthrough into `trySendTouchpadRelativeMove()`."* Quyết định đó đúng cho chuột thật, sai cho touchpad captured không có trục relative.

**Lưu ý:** RC-1 và RC-2 loại trừ nhau theo kịch bản — RC-1 xảy ra khi `AXIS_RELATIVE_X/Y` **có** dữ liệu, RC-2 khi **không có**. Cả hai đều dẫn tới cùng triệu chứng. Giai đoạn 0 (mục 5) sẽ chốt kịch bản nào đang xảy ra. **Cả hai đều cần sửa.**

### RC-3 (CHẶN có điều kiện) — `isTouchpadEvent()` truy vấn motion range theo `SOURCE_MOUSE`, nhưng device sources đổi khi capture bật

`Game.java:1885-1890`:

```java
device.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_MOUSE);
device.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_MOUSE);
```

Chính comment trong `AndroidNativePointerCaptureProvider.java:42-47` ghi nhận hành vi này của Android:

> *"Upon enabling pointer capture, that device will switch to SOURCE_KEYBOARD and SOURCE_TOUCHPAD."*

Tức là sau khi capture bật, thiết bị **có thể mất bit `SOURCE_MOUSE`** và mất luôn motion range đăng ký dưới `SOURCE_MOUSE`. Khi đó:

- `hasMouseXRange = hasMouseYRange = false`
- `(deviceSources & SOURCE_MOUSE) == SOURCE_MOUSE` → `false`
- → `compositeKeyboardTouchpad = false`

Nếu event source là `SOURCE_TOUCHPAD`, guard sớm ở dòng 1876/1898 vẫn cứu được. **Nhưng nếu event source là `SOURCE_MOUSE_RELATIVE`**, `isTouchpadEvent()` trả `false` → toàn bộ cử chỉ trackpad (`trySendTouchpadTwoFingerScroll`, `trySendTouchpadThreeFingerGesture`, `trySendTouchpadRelativeMove`) **tự vô hiệu hoá**, và tệ hơn: giá trị `touchpadEvent` có thể **dao động giữa các event** khi Android đổi qua lại device sources → gesture state bị reset liên tục.

Ngoài ra `nativeTouchpad` kiểm tra `deviceSources & SOURCE_TOUCHPAD` cũng chỉ đúng **sau khi** capture đã đổi sources — trước đó thì không.

### RC-4 (chất lượng) — `shouldApplyTouchpadMouseSpeed()` chỉ dựa vào tool type

`Game.java:2563-2565` áp hệ số `touchpadMouseSpeed/100` (mặc định **40 → 0.4×**) cho mọi `TOOL_TYPE_FINGER`. Nếu Android báo touchpad captured là `TOOL_TYPE_MOUSE` (một số ROM làm vậy cho touchpad ở pointer mode), trackpad sẽ đi nhánh `sendRawMouseMove()` không scale → tốc độ nhảy vọt so với kỳ vọng. Ngược lại, nếu delta relative vốn đã nhỏ, nhân 0.4 làm chuyển động chậm **có thể bị hiểu nhầm là "không di chuyển"**.

Đây không phải nguyên nhân gốc, nhưng nó có thể **che lấp** kết quả kiểm thử của các bản vá phía trên. Cần loại trừ bằng cách tạm đặt `seekbar_touchpad_mouse_speed = 100` khi test.

### RC-5 (chất lượng) — `trySendTouchpadRelativeMove()` chưa xử lý `ACTION_HOVER_EXIT`

`Game.java:2500-2524` reset reference ở `ACTION_DOWN` / `ACTION_HOVER_ENTER`, nhưng `ACTION_HOVER_EXIT` (10) không nằm trong nhánh reset ở dòng 2468-2498 (nhánh đó chỉ bắt CANCEL/UP/BUTTON_RELEASE/POINTER_UP/pointerCount≠1). Với `pointerCount == 1`, `ACTION_HOVER_EXIT` rơi xuống dòng 2513 và trả `false` mà **không reset** `lastTouchpadMouseX/Y`. Lần chạm kế tiếp sinh delta nhảy vọt nếu `touchpadMouseActive` vẫn còn `true`.

### RC-6 (chất lượng) — Không có log chẩn đoán tại điểm quyết định

`describeEvent()` đã bị xoá cùng `TouchpadDeviceMatcher`. Hiện **không có log nào** in ra `eventSource`, `toolType`, `AXIS_RELATIVE_X/Y`, `hasPointerCapture()`. Đây chính là lý do bản sửa lần 1 phải đoán mô hình lỗi và đoán sai. Không nên sửa tiếp mà không có log này.

---

## 4. Tổng kết chuỗi lỗi

```
Stream connect → setInputGrabState(true) → enableCapture() → requestPointerCapture()
  │
  ├─ Android chuyển Nillkin sang SOURCE_TOUCHPAD (DeviceMode::UNSCALED)
  │
  ├─ Nhánh A: AXIS_RELATIVE_X/Y CÓ dữ liệu
  │     handled = true → gửi sendMouseMove(delta)   ✔ đúng
  │     … nhưng chuỗi else-if bị tách → cũng gọi updateMousePosition()
  │     → gửi sendMousePosition(toạ độ thô trackpad)  ✘ SAI, đè lên gói đúng
  │     → CON TRỎ HOST NHẢY LOẠN / KHÔNG ĐIỀU KHIỂN ĐƯỢC          [RC-1]
  │
  └─ Nhánh B: AXIS_RELATIVE_X/Y RỖNG
        handled = true nhưng delta = 0 → không gửi gì
        shouldTryTouchpadRelativeMove(true) = false
        → trySendTouchpadRelativeMove() bị chặn (đường duy nhất còn dùng được)
        → rồi vẫn rơi vào updateMousePosition()                    [RC-2 + RC-1]
        → CON TRỎ HOST KHÔNG DI CHUYỂN / GHIM
```

## 5. Phương án sửa đề xuất

### Giai đoạn 0 — Bổ sung log rồi lấy trace thật (BẮT BUỘC trước khi sửa)

Bài học từ lần 1: 17 unit test xanh mà lỗi vẫn còn, vì fixture sinh ra từ giả định chưa được kiểm chứng. **Không sửa tiếp khi chưa có trace runtime.**

Thêm log tạm (chỉ debug build) ngay đầu `Game.handleMotionEvent()`:

```java
LimeLog.info(String.format(
    "MOTION src=0x%08X devSrc=0x%08X tool=%d action=%d ptrs=%d " +
    "x=%.1f y=%.1f relX=%.2f relY=%.2f capture=%b touchpadEvent=%b",
    event.getSource(),
    event.getDevice() != null ? event.getDevice().getSources() : 0,
    event.getToolType(0), event.getActionMasked(), event.getPointerCount(),
    event.getX(0), event.getY(0),
    event.getAxisValue(MotionEvent.AXIS_RELATIVE_X),
    event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y),
    streamView.hasPointerCapture(),
    isTouchpadEvent(event, event.getSource())));
```

Thu thập **trong lúc stream đang chạy, capture đang bật**, khi di ngón trên trackpad Nillkin:

```
adb logcat -s Moonlight:* | findstr MOTION
```

Trace này chốt 3 câu hỏi quyết định:

1. `src` là `0x00100008` (SOURCE_TOUCHPAD) hay `0x00020004` (SOURCE_MOUSE_RELATIVE)?
2. `relX/relY` có khác 0 không? → chọn Nhánh A hay Nhánh B ở mục 4.
3. `touchpadEvent` có ổn định `true` không, hay dao động? → xác nhận RC-3.

### P1 — Khôi phục tính loại trừ của chuỗi `if/else-if` *(sửa RC-1, ưu tiên #1)*

File: `Game.java:2650-2700`

Ghép nhánh relative trở lại đầu chuỗi để nó loại trừ mọi nhánh sau:

```java
if (inputCaptureProvider.eventHasRelativeMouseAxes(event)) {
    float deltaX = inputCaptureProvider.getRelativeAxisX(event);
    float deltaY = inputCaptureProvider.getRelativeAxisY(event);
    if (shouldApplyTouchpadMouseSpeed(event.getToolType(0))) {
        sendTouchpadMouseMove(deltaX, deltaY);
    } else {
        sendRawMouseMove((short)deltaX, (short)deltaY);
    }
    // KHÔNG rơi xuống bất kỳ nhánh absolute nào
}
else if (trySendTouchpadRelativeMove(event, eventSource)) { ... }
else if ((eventSource & SOURCE_CLASS_POSITION) != 0) { ... }
else if (view != null && trySendPenEvent(view, event)) { return true; }
else if (view != null && shouldUseAbsoluteMouseFallback(eventSource)) {
    updateMousePosition(view, event);
}
```

Đây là fix ít rủi ro nhất và đưa cấu trúc về đúng như upstream.

### P2 — Mở rộng `shouldUseAbsoluteMouseFallback()` *(hàng rào an toàn kép cho RC-1)*

File: `Game.java:2559-2561`

Chặn thêm mọi source touchpad khi đang có pointer capture — trong trạng thái đó `event.getX()` **không bao giờ** là toạ độ view hợp lệ:

```java
static boolean shouldUseAbsoluteMouseFallback(int eventSource, boolean hasPointerCapture) {
    if (eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) {
        return false;
    }
    // SOURCE_TOUCHPAD dưới capture mang toạ độ thô của mặt trackpad, không phải toạ độ view
    if (hasPointerCapture &&
            (eventSource & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
        return false;
    }
    return true;
}
```

Kèm một `LimeLog.warning()` phát một lần khi rơi vào nhánh fallback, để lần sau chẩn đoán nhanh.

### P3 — Tách "có trục relative" khỏi "đã gửi được chuyển động" *(sửa RC-2)*

File: `Game.java:2555-2557` + điểm gọi

`shouldTryTouchpadRelativeMove()` phải dựa trên việc **thực sự đã gửi gói**, không dựa trên khả năng lý thuyết:

```java
boolean sentRelativeMotion = false;
if (inputCaptureProvider.eventHasRelativeMouseAxes(event)) {
    float dx = inputCaptureProvider.getRelativeAxisX(event);
    float dy = inputCaptureProvider.getRelativeAxisY(event);
    if (dx != 0 || dy != 0) {
        sentRelativeMotion = true;
        ... // gửi
    }
}
if (!sentRelativeMotion && trySendTouchpadRelativeMove(event, eventSource)) { ... }
```

Lưu ý phối hợp với P1: điều kiện `dx != 0 || dy != 0` (chứ không phải giá trị trả về của `sendTouchpadMouseMove()`) để chuyển động chậm bị accumulator giữ lại **không** bị hiểu nhầm là "trục relative rỗng" — nếu dùng giá trị trả về, mỗi sample dưới ngưỡng sẽ kích hoạt đường adapter và gây double-count.

### P4 — Sửa `isTouchpadEvent()` truy vấn motion range đúng source *(sửa RC-3)*

File: `Game.java:1875-1915`

- Thử motion range lần lượt trên `SOURCE_MOUSE`, `SOURCE_TOUCHPAD`, rồi `SOURCE_MOUSE_RELATIVE`; chỉ cần một source cho ra range hợp lệ.
- Chấp nhận `SOURCE_MOUSE_RELATIVE` như bằng chứng "đang ở chế độ capture" thay vì đòi bit `SOURCE_MOUSE` trên device sources.
- Cache kết quả phân loại theo `deviceId` (map `deviceId → boolean`), invalidate ở `InputManager.InputDeviceListener` callback. Vừa loại bỏ dao động giữa các event, vừa tránh gọi `getMotionRange()` (khá đắt) trên mọi motion event ở tần số 1000 Hz.

### P5 — Bổ sung reset ở `ACTION_HOVER_EXIT` *(sửa RC-5)*

File: `Game.java:2468-2498` — thêm `ACTION_HOVER_EXIT` vào danh sách action gây reset `touchpadMouseActive` / `lastTouchpadMouseX/Y` / accumulator.

### P6 — Log chẩn đoán thường trực *(sửa RC-6)*

Giữ lại log của Giai đoạn 0 dưới dạng `LimeLog.info()` có cờ bật/tắt, thay vì xoá đi sau khi sửa xong.

## 6. Thứ tự thực hiện đề xuất

| Bước | Nội dung | Điều kiện dừng / chuyển bước |
|---|---|---|
| 1 | Giai đoạn 0 + P6 — lấy trace thật khi đang stream | Có `src`, `relX/relY`, `capture`, `touchpadEvent` |
| 2 | **P1 + P2** | Luôn làm. Đây là fix cho hồi quy nghiêm trọng nhất |
| 3 | Test trên host Windows với `touchpadMouseSpeed = 100` | Nếu con trỏ đã điều khiển được → RC-1 là nguyên nhân duy nhất, dừng ở P4/P5 dọn dẹp |
| 4 | **P3** | Bắt buộc nếu trace cho `relX/relY == 0` |
| 5 | **P4** | Bắt buộc nếu trace cho `touchpadEvent` dao động hoặc `src == SOURCE_MOUSE_RELATIVE` |
| 6 | P5 + P6 | Dọn dẹp |

## 7. Kiểm thử — yêu cầu bắt buộc

Bản sửa lần 1 pass toàn bộ unit test nhưng không sửa được lỗi. Để tránh lặp lại, mỗi bản vá phải kèm test **đỏ trước khi sửa**:

| Vá | Test phải đỏ trước khi sửa |
|---|---|
| P1 | `SOURCE_TOUCHPAD` + `hasPointerCapture=true` + `handledRelative=true` → **không** được gọi `updateMousePosition()` |
| P2 | `shouldUseAbsoluteMouseFallback(SOURCE_TOUCHPAD, true)` → `false` |
| P3 | `relX=0, relY=0` + `eventHasRelativeMouseAxes=true` → đường `trySendTouchpadRelativeMove()` **phải** được gọi |
| P4 | device sources = `KEYBOARD \| TOUCHPAD` (không có `MOUSE`), event source `SOURCE_MOUSE_RELATIVE` → `isTouchpadEvent()` **phải** trả `true` |
| P5 | `ACTION_HOVER_EXIT` với `pointerCount=1` → reference phải được reset |

Nếu một test nào trong bảng trên **không đỏ** ở code hiện tại, nghĩa là giả thiết tương ứng trong báo cáo này chưa được chứng minh — phải quay lại Giai đoạn 0 trước khi viết code.

## 8. Tiêu chí nghiệm thu

Bắt buộc xác minh trên **host Windows thật** với phần cứng Nillkin (không thể chứng minh bằng JVM test):

- [ ] Con trỏ Windows di chuyển đúng hướng, tỉ lệ hợp lý, không nhảy/không ghim
- [ ] Không có gói `sendMousePosition` nào được gửi khi capture đang bật (kiểm tra qua log)
- [ ] Di chuyển chậm vẫn tạo chuyển động (accumulator hoạt động)
- [ ] Nhấc tay rồi chạm lại không gây nhảy con trỏ
- [ ] Tap-to-click, tap-drag, double-tap-drag hoạt động
- [ ] Cuộn 2 ngón, pinch-zoom, cử chỉ 3 ngón hoạt động
- [ ] Chuột Bluetooth/USB rời: không hồi quy
- [ ] Trackpad ảo trên màn hình cảm ứng: không hồi quy
- [ ] Gamepad, stylus, bàn phím Xiaomi: không hồi quy
- [ ] Mất/lấy lại focus, disconnect/reconnect: con trỏ vẫn điều khiển được
- [ ] Stream 30 phút: không drift, không crash
