# Báo cáo lỗi: Trackpad Nillkin Pocket không điều khiển được con trỏ trên Windows host

- **Ngày:** 2026-07-28
- **Trạng thái:** Đã phân tích nguyên nhân, **chưa sửa code**
- **Thiết bị:** Lenovo TB321FU (Android API 36) + Nillkin Cube Pocket Foldable Keyboard `0x21CE:0xB907`
- **Host:** Windows (Sunshine/GFE)
- **Phạm vi file liên quan:**
  - `app/src/main/java/com/limelight/Game.java`
  - `app/src/main/java/com/limelight/binding/input/touchpad/TouchpadDeviceMatcher.java`
  - `app/src/main/java/com/limelight/binding/input/touchpad/TouchpadRelativeAdapter.java`
  - `app/src/main/java/com/limelight/binding/input/capture/AndroidNativePointerCaptureProvider.java`
  - `app/src/main/java/com/limelight/binding/input/ControllerHandler.java`

---

## 1. Hiện tượng

Trackpad trên bàn phím Nillkin hoạt động bình thường ở tầng Android (di chuyển con trỏ hệ thống, click, cuộn). Nhưng khi vào màn hình stream của Moonlight và kết nối tới host Windows, con trỏ trên host **không di chuyển theo trackpad**. Bàn phím vẫn gõ được, các nút bấm có thể có phản hồi rời rạc.

## 2. Dữ liệu đầu vào đã có

Theo `docs/nillkin-touchpad-validation.md`:

| Thuộc tính | Giá trị đo được |
|---|---|
| Vendor/Product | `0x21CE:0xB907` |
| `InputDevice.getSources()` | `KEYBOARD \| DPAD \| MOUSE \| JOYSTICK` (≈ `0x01002313`) |
| Trục | ABS X `0..1887`, ABS Y `0..2015` |
| REL X/Y ở tầng evdev | **Không có** |
| Xác minh con trỏ trên host | **Chưa từng pass** (dòng 29 của validation doc ghi rõ "remains pending") |

Điểm mấu chốt: Android **không** báo `SOURCE_TOUCHPAD` cho thiết bị này. Node touchpad được `TouchInputMapper` chạy ở chế độ pointer và phát event dưới dạng `SOURCE_MOUSE`. Nghĩa là mọi giả định "Nillkin = touchpad tuyệt đối" trong matcher hiện tại đều lệch so với thực tế runtime.

## 3. Luồng code hiện tại

`Game.handleMotionEvent()` (dòng 1780–1906):

```
resetIfDeviceChanged(deviceId)
  → pointerSource        = POINTER | POSITION | MOUSE_RELATIVE ?
  → hasRelativeMouseAxes = inputCaptureProvider.eventHasRelativeMouseAxes(event)
  → touchpadCandidate    = pointerSource && touchpadDeviceMatcher.isCandidate(...)

  if  shouldHandleAsController(...)                 → ControllerHandler.handleMotionEvent()
  elif !touchpadCandidate && deviceHasJoystick
       && ControllerHandler.tryHandleTouchpadEvent() → nuốt event
  elif pointerSource:
       if  hasRelativeMouseAxes  → sendMouseMove(delta)         ← đường đúng
       elif touchpadCandidate    → TouchpadRelativeAdapter      ← đường fork thêm vào
       elif SOURCE_CLASS_POSITION→ sendMousePosition(abs)
       elif pen                  → sendPenEvent
       else                      → updateMousePosition(view,event)  ← fallback nguy hiểm
```

Khi stream kết nối, `Game.java:2438` gọi `setInputGrabState(true)` → `enableCapture()` → `hideCursor()` → `View.requestPointerCapture()`. **Pointer capture LUÔN bật trong lúc stream.**

## 4. Nguyên nhân gốc

### RC-1 (chính) — Dưới pointer capture, `eventSource` đổi thành `SOURCE_MOUSE_RELATIVE`, làm matcher trượt trên **hai** điều kiện độc lập

Khi capture bật, Android chuyển event của thiết bị `SOURCE_MOUSE` thành `SOURCE_MOUSE_RELATIVE` (`0x00020004`).

**Trượt lần 1** — `TouchpadDeviceMatcher.hasValidAbsoluteAxes()` (dòng 73–77) truy vấn motion range bằng **event source**, không phải device source:

```java
device.getMotionRange(MotionEvent.AXIS_X, source);   // source = SOURCE_MOUSE_RELATIVE
```

Motion range của thiết bị chỉ đăng ký dưới `SOURCE_MOUSE`, nên hàm này trả `null` → `hasValidAbsoluteAxes = false` → `isCandidate()` return `false` ngay tại guard đầu tiên (dòng 32).

**Trượt lần 2** — nhánh fingerprint Nillkin (dòng 38–41) yêu cầu event source phải chứa bit `SOURCE_MOUSE` (`0x2002`) hoặc `SOURCE_TOUCHPAD` (`0x100008`):

```java
return hasSource(eventSource, InputDevice.SOURCE_MOUSE) ||
       hasSource(eventSource, InputDevice.SOURCE_TOUCHPAD);
```

`0x20004 & 0x2002 != 0x2002` → `false`. Không có nhánh nào chấp nhận `SOURCE_MOUSE_RELATIVE`.

→ `touchpadCandidate` **luôn = false** trong lúc stream. Toàn bộ `TouchpadRelativeAdapter` là code chết trong kịch bản thực tế.

### RC-2 (chính) — `eventHasRelativeMouseAxes()` đòi `TOOL_TYPE_MOUSE`, nhưng trackpad báo `TOOL_TYPE_FINGER`

`AndroidNativePointerCaptureProvider.java:118`:

```java
return (eventSource == InputDevice.SOURCE_MOUSE_RELATIVE
        && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) ||
       (eventSource == InputDevice.SOURCE_TOUCHPAD && targetView.hasPointerCapture());
```

Ngón tay trên trackpad được `TouchInputMapper` map thành `TOOL_TYPE_FINGER`, không phải `TOOL_TYPE_MOUSE`. Nillkin cũng không báo `SOURCE_TOUCHPAD`, nên vế thứ hai cũng trượt.

→ `hasRelativeMouseAxes = false`, dù Android **đang** cung cấp delta hợp lệ trong `AXIS_X/AXIS_Y`.

### RC-3 (hệ quả của RC-1 + RC-2) — Event rơi vào fallback `updateMousePosition()` và ghim con trỏ vào góc trên-trái

Sau khi RC-1 và RC-2 cùng trượt, chuỗi `if/else-if` tại `Game.java:1851–1906` đi xuống:

- `hasRelativeMouseAxes` → false
- `touchpadCandidate` → false
- `(eventSource & SOURCE_CLASS_POSITION) != 0` → `0x20004 & 0x8 == 0` → false
- `trySendPenEvent()` → false (không phải stylus)
- `view != null` → **chạy `updateMousePosition(view, event)`**

`updateMousePosition()` (dòng 2151–2201) coi `event.getX(0)`/`getY(0)` là **toạ độ tuyệt đối trong view**. Nhưng dưới pointer capture đây là **delta tương đối** (giá trị nhỏ, thường ±1..±10, âm khi di chuyển trái/lên). Dòng 2198–2199 clamp giá trị âm về 0, rồi gửi:

```java
conn.sendMousePosition((short)eventX, (short)eventY, w, h);
```

→ Host nhận lệnh đặt con trỏ tuyệt đối quanh toạ độ (0..10, 0..10). Kết quả quan sát được đúng như báo cáo: **con trỏ trên Windows dính ở góc trên-trái và chỉ rung nhẹ, không điều khiển được.**

### RC-4 (chặn đường dự phòng) — `TouchpadRelativeAdapter` bỏ qua `ACTION_HOVER_MOVE`

Ngay cả khi vá RC-1 để matcher nhận diện được thiết bị, adapter vẫn không chạy. `TouchpadRelativeAdapter.onMotionEvent()` (dòng 41–45):

```java
if (action != ACTION_MOVE) {   // ACTION_MOVE = 2
    deltaX = 0; deltaY = 0;
    return false;
}
```

Di chuyển con trỏ **không giữ nút** trên một thiết bị nguồn mouse sinh ra `ACTION_HOVER_MOVE` (**= 7**), không phải `ACTION_MOVE` (= 2). `ACTION_MOVE` chỉ xuất hiện khi đang kéo với nút trái được giữ.

→ Adapter chỉ hoạt động lúc drag, im lặng hoàn toàn khi di chuyển bình thường. Đây là kịch bản sử dụng chính.

### RC-5 (chặn đường dự phòng) — `ControllerHandler` nuốt event khi `touchpadCandidate = false`

`Game.java:1802–1806`:

```java
else if (!touchpadCandidate &&
        (deviceSources & InputDevice.SOURCE_CLASS_JOYSTICK) != 0 &&
        controllerHandler.tryHandleTouchpadEvent(event)) {
    return true;
}
```

Nillkin có bit `SOURCE_JOYSTICK` trong `getSources()`, nên điều kiện thứ hai luôn đúng. Nếu event tới với source đúng bằng `SOURCE_MOUSE` (trường hợp capture chưa kịp bật, hoặc sau khi mất/lấy lại focus), `ControllerHandler.tryHandleTouchpadEvent()` (dòng 1697–1714) trả:

```java
return !prefConfig.gamepadTouchpadAsMouse;   // mặc định gamepadTouchpadAsMouse = false
```

→ trả `true` → **event bị nuốt, không bao giờ tới nhánh pointer.**

Rủi ro liên quan: `ControllerHandler.getContextForEvent()` (dòng 1038) **tự tạo context cho bất kỳ device nào**, và `ControllerHandler.handleMotionEvent()` (dòng 1834) **luôn `return true`**. Nghĩa là nếu event Nillkin lọt vào nhánh controller đầu tiên thì cũng bị nuốt vô điều kiện.

### RC-6 (chất lượng, không chặn) — Adapter mất chuyển động chậm do làm tròn

`TouchpadRelativeAdapter.java:47–50`:

```java
deltaX = clampToShort((int)(x - referenceX));
referenceX = x;                       // reference vẫn nhảy tới vị trí mới
```

Ép kiểu `(int)` cắt phần thập phân nhưng reference vẫn được cập nhật đầy đủ → phần dư bị **vứt bỏ vĩnh viễn**. Di chuyển chậm (delta < 1 đơn vị/sample) cho ra delta = 0 mãi mãi. Cần giữ lại phần dư (accumulator) thay vì truncate.

### RC-7 (chất lượng, không chặn) — Reset chéo thiết bị

`Game.java:1786` gọi `touchpadRelativeAdapter.resetIfDeviceChanged(event.getDeviceId())` cho **mọi** motion event, kể cả từ màn hình cảm ứng của tablet. Chạm màn hình sẽ reset reference của trackpad, gây mất một sample mỗi lần chuyển qua lại.

---

## 5. Tổng kết chuỗi lỗi

```
Stream connect
  → requestPointerCapture()
      → eventSource = SOURCE_MOUSE_RELATIVE, toolType = TOOL_TYPE_FINGER
          → RC-2: eventHasRelativeMouseAxes() = false  (đòi TOOL_TYPE_MOUSE)
          → RC-1: isCandidate() = false                (đòi ABS range theo event source
                                                        + đòi bit SOURCE_MOUSE/TOUCHPAD)
              → RC-3: rơi vào updateMousePosition()
                  → gửi delta như toạ độ tuyệt đối
                      → CON TRỎ HOST GHIM Ở GÓC TRÊN-TRÁI
```

Ba lớp phòng vệ còn lại (adapter, controller-bypass) cũng hỏng độc lập: RC-4, RC-5.

## 6. Vì sao bản sửa trước (`8028aa7c` + fingerprint allowlist) không giải quyết được

Thiết kế trong `docs/superpowers/specs/2026-07-27-nillkin-composite-touchpad-fix-design.md` giả định event Nillkin tới dưới dạng `SOURCE_MOUSE`/`SOURCE_TOUCHPAD` **với toạ độ tuyệt đối**, và toàn bộ test là unit test thuần Java dùng fixture do chính giả định đó sinh ra. Không có test nào mô phỏng trạng thái pointer capture. Validation doc cũng tự ghi nhận "Windows-host cursor validation remains pending". Vì vậy bản sửa pass 17 unit test nhưng không chạm được vào đường dẫn thật.

---

## 7. Phương án sửa đề xuất

### Giai đoạn 0 — Xác nhận bằng trace thật (bắt buộc trước khi sửa)

Chưa từng có trace event **trong lúc stream**. Cần:

```
adb shell setprop log.tag.MoonlightTouchpad DEBUG
adb logcat -s MoonlightTouchpad
```

Ghi lại, ở trạng thái đã kết nối host và capture đang bật:

- `eventSource` (hex), `toolType`, `actionMasked`
- `AXIS_X/AXIS_Y` và `AXIS_RELATIVE_X/AXIS_RELATIVE_Y`
- `targetView.hasPointerCapture()`
- `device.getMotionRange(AXIS_X, SOURCE_MOUSE)` và với `SOURCE_MOUSE_RELATIVE`

Hiện log trong `TouchpadDeviceMatcher.describeEvent()` **không in `AXIS_RELATIVE_X/Y` và không in `hasPointerCapture()`** — cần bổ sung hai trường này trước, vì đó chính là hai biến quyết định.

Trace này sẽ chốt: fix nằm ở **P1** (Android đã cung cấp relative axes → chỉ cần nới điều kiện) hay ở **P2** (Android chỉ cung cấp absolute → cần adapter).

### P1 — Sửa `eventHasRelativeMouseAxes()` (ưu tiên cao nhất, ít rủi ro nhất)

File: `AndroidNativePointerCaptureProvider.java:113–142`

- Bỏ ràng buộc `toolType == TOOL_TYPE_MOUSE` cho `SOURCE_MOUSE_RELATIVE`. Nguồn `SOURCE_MOUSE_RELATIVE` **chỉ tồn tại** khi capture đang bật, nên bản thân nó đã đủ tin cậy; ràng buộc tool type là thừa và chính là thứ loại trackpad ra.
  - Nếu cần giữ an toàn cho SPen (issue #1030), thay bằng blacklist `TOOL_TYPE_STYLUS`/`TOOL_TYPE_ERASER` thay vì whitelist `TOOL_TYPE_MOUSE`.
- Thêm điều kiện chấp nhận: bất kỳ event nào có `hasPointerCapture() == true` và giá trị `AXIS_RELATIVE_X/Y` khác 0.
- Giữ nguyên `getRelativeAxisX/Y()` (đã cộng dồn historical samples đúng cách).

Nếu trace ở Giai đoạn 0 cho thấy relative axes có dữ liệu, **P1 một mình là đủ** và là fix sạch nhất: event đi thẳng vào `conn.sendMouseMove(delta)` chuẩn của Moonlight, không cần adapter, không cần fingerprint.

### P2 — Sửa `TouchpadDeviceMatcher` (chỉ làm nếu P1 không đủ)

File: `TouchpadDeviceMatcher.java`

1. `hasValidAbsoluteAxes()`: truy vấn motion range theo **device sources** thực tế (thử lần lượt `SOURCE_MOUSE`, `SOURCE_TOUCHPAD`), không dùng event source. Sửa chữ ký để nhận cả `deviceSources`.
2. Nhánh fingerprint Nillkin: chấp nhận thêm `eventSource == SOURCE_MOUSE_RELATIVE`.
3. Cân nhắc bỏ dần fingerprint cứng `0x21CE:0xB907` sau khi có tiêu chí capability đủ chặt — hiện allowlist làm fix không áp dụng được cho các bàn phím composite khác.

### P3 — Sửa `TouchpadRelativeAdapter` (bắt buộc nếu đi đường P2)

File: `TouchpadRelativeAdapter.java`

1. Chấp nhận `ACTION_HOVER_MOVE` (7) ngang hàng `ACTION_MOVE` (2). Thêm hằng số `ACTION_HOVER_MOVE`.
2. Xử lý `ACTION_HOVER_EXIT` (10) như một điểm reset reference (nhấc tay khỏi trackpad).
3. Giữ phần dư thập phân: tích luỹ `accumX += (x - referenceX)`, phát phần nguyên, giữ lại phần lẻ — thay cho `(int)` truncate.
4. Cộng dồn historical samples (`getHistoricalAxisValue`) để không mất mẫu khi event bị gộp.

### P4 — Chặn `ControllerHandler` nuốt event

File: `Game.java:1802` và `ControllerHandler.tryHandleTouchpadEvent()`

- Bổ sung guard: không route vào `tryHandleTouchpadEvent()` khi thiết bị **không có trục joystick thật** (`hasJoystickAxes == false`). Nillkin báo `SOURCE_JOYSTICK` nhưng không phải gamepad; `ControllerHandler.hasJoystickAxes()` (dòng 318) đã có sẵn logic kiểm tra này, chỉ chưa được dùng ở điểm route.
- Đây là fix bền hơn allowlist theo vendor/product và cũng sửa cho các bàn phím composite khác.

### P5 — Hàng rào an toàn cho fallback

File: `Game.java:1899–1906`

- Không gọi `updateMousePosition()` khi `inputCaptureProvider` đang capture và `eventSource == SOURCE_MOUSE_RELATIVE`. Trong trạng thái đó `event.getX()` **không bao giờ** là toạ độ tuyệt đối hợp lệ. Thà không gửi gì còn hơn ghim con trỏ vào góc màn hình.
- Nên log warning một lần khi rơi vào nhánh này để lần sau chẩn đoán nhanh hơn.

### P6 — Vệ sinh

- `Game.java:1786`: chỉ gọi `resetIfDeviceChanged()` cho event thuộc pointer source, không cho mọi motion event.
- Bổ sung `AXIS_RELATIVE_X/Y` và `hasPointerCapture()` vào `describeEvent()`.

---

## 8. Thứ tự thực hiện đề xuất

| Bước | Nội dung | Điều kiện dừng |
|---|---|---|
| 1 | Giai đoạn 0 + P6 (log) | Có trace runtime trong lúc stream |
| 2 | P1 | Nếu con trỏ host di chuyển đúng → **dừng, không cần P2/P3** |
| 3 | P5 | Luôn làm — chặn triệu chứng ghim góc |
| 4 | P4 | Luôn làm — chặn nuốt event |
| 5 | P2 + P3 | Chỉ khi P1 không đủ |

## 9. Tiêu chí nghiệm thu

Không đóng lỗi cho đến khi **xác minh trên host Windows thật** (validation doc hiện đang để "Pending" cho toàn bộ hàng này):

- [ ] Con trỏ Windows di chuyển đúng hướng, tỉ lệ hợp lý
- [ ] Di chuyển chậm vẫn tạo chuyển động (kiểm tra RC-6)
- [ ] Nhấc tay rồi chạm lại không gây nhảy con trỏ
- [ ] Click trái / phải / cuộn hoạt động
- [ ] Chuột Bluetooth ngoài: không hồi quy
- [ ] Trackpad ảo trên màn hình cảm ứng: không hồi quy
- [ ] Gamepad, stylus, bàn phím: không hồi quy
- [ ] Mất/lấy lại focus, disconnect/reconnect: con trỏ vẫn điều khiển được
- [ ] Stream 30 phút: không drift, không crash

## 10. Ghi chú về kiểm thử

Unit test hiện tại (`TouchpadDeviceMatcherTest`, `TouchpadRelativeAdapterTest`, `GameInputRoutingTest`) dùng fixture do chính giả định sai sinh ra, nên pass mà không phản ánh thực tế. Cần bổ sung ít nhất:

- Test matcher với `eventSource = SOURCE_MOUSE_RELATIVE` — hiện tại **phải fail**.
- Test adapter với `ACTION_HOVER_MOVE` — hiện tại **phải fail**.
- Test `eventHasRelativeMouseAxes` với `TOOL_TYPE_FINGER` + `SOURCE_MOUSE_RELATIVE` — hiện tại **phải fail**.

Ba test này là điều kiện cần: nếu chúng không đỏ trước khi sửa, nghĩa là mô hình lỗi trong báo cáo này chưa được chứng minh và cần quay lại Giai đoạn 0.
