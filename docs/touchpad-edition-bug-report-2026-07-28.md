# Báo cáo lỗi: Moonlight Touchpad Edition — cùng nhóm nguyên nhân với lỗi Nillkin

- **Ngày:** 2026-07-28
- **Repo:** `D:\Projects\Android\moonlight-touchpad-edition` (v0.1.4-touchpad, package `com.limelight.touchpadedition`)
- **Trạng thái:** Đã phân tích nguyên nhân, **chưa sửa code**
- **Đối chiếu:** `docs/nillkin-trackpad-bug-report-2026-07-28.md` (repo `Moonlight-android-trackpack`)

---

## 0. Kết luận ngắn

Fork này **có cùng bốn lỗi cấu trúc** với repo `Moonlight-android-trackpack`, chỉ khác cách biểu hiện:

| Mã | Lỗi | Repo trackpack | Repo touchpad-edition |
|---|---|---|---|
| A | `eventHasRelativeMouseAxes()` đòi `TOOL_TYPE_MOUSE` | Có | **Có — file giống hệt từng dòng** |
| B | Bộ nhận diện touchpad không chấp nhận `SOURCE_MOUSE_RELATIVE` | `TouchpadDeviceMatcher` | **`isTouchpadEvent()`** |
| C | Fallback `updateMousePosition()` coi delta là toạ độ tuyệt đối | Có | **Có — `Game.java:2613`** |
| D | `ControllerHandler.tryHandleTouchpadEvent()` nuốt event của thiết bị composite | Có | **Có — `Game.java:2494`** |

Thêm hai lỗi **chỉ có ở fork này**:

| Mã | Lỗi |
|---|---|
| E | Cài đặt "tốc độ con trỏ" (`touchpadMouseSpeed`) **không có tác dụng** trên đường dẫn chính |
| F | Nhận diện touchpad dựa trên **chuỗi tên thiết bị**, không phải capability |
| G | Không reset state cử chỉ khi mất focus |

Kết quả thực tế: fork này chạy được với bàn phím Xiaomi Pad 6 Max 14 (thiết bị **có** báo `SOURCE_TOUCHPAD`), nhưng sẽ **hỏng đúng như Nillkin** trên mọi bàn phím composite báo `SOURCE_MOUSE` thay vì `SOURCE_TOUCHPAD`.

---

## 1. Lỗi A — `eventHasRelativeMouseAxes()` giống hệt repo kia

`app/src/main/java/com/limelight/binding/input/capture/AndroidNativePointerCaptureProvider.java:118`

```java
return (eventSource == InputDevice.SOURCE_MOUSE_RELATIVE
        && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) ||
       (eventSource == InputDevice.SOURCE_TOUCHPAD && targetView.hasPointerCapture());
```

File này **không được fork sửa** — giống 100% bản gốc và giống repo `Moonlight-android-trackpack`.

Ngón tay trên trackpad được `TouchInputMapper` map thành `TOOL_TYPE_FINGER`, không phải `TOOL_TYPE_MOUSE`. Vì vậy:

- Thiết bị báo `SOURCE_TOUCHPAD` (Xiaomi) → **vế thứ hai cứu được** → chạy đúng.
- Thiết bị báo `SOURCE_MOUSE` (Nillkin và phần lớn bàn phím Bluetooth composite) → dưới pointer capture đổi thành `SOURCE_MOUSE_RELATIVE` với `TOOL_TYPE_FINGER` → **cả hai vế đều trượt** → `false`.

Đây chính là lý do fork "hoạt động với bàn phím Xiaomi" nhưng không phải là fix tổng quát.

## 2. Lỗi B — `isTouchpadEvent()` không xử lý `SOURCE_MOUSE_RELATIVE`

`Game.java:1869–1888`

```java
private static boolean isTouchpadEvent(MotionEvent event, int eventSource) {
    if (eventSource == InputDevice.SOURCE_TOUCHPAD) {
        return true;
    }
    ...
    return (device.supportsSource(InputDevice.SOURCE_TOUCHPAD) || looksLikeTabletKeyboardTouchpad) &&
            ((eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 ||
             (eventSource & InputDevice.SOURCE_CLASS_POINTER)  != 0 ||
             (eventSource & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD);
}
```

Phân tích bit:

| Hằng số | Giá trị | Class |
|---|---|---|
| `SOURCE_CLASS_POINTER` | `0x02` | — |
| `SOURCE_CLASS_TRACKBALL` | `0x04` | — |
| `SOURCE_CLASS_POSITION` | `0x08` | — |
| `SOURCE_MOUSE` | `0x00002002` | POINTER |
| `SOURCE_TOUCHPAD` | `0x00100008` | POSITION |
| **`SOURCE_MOUSE_RELATIVE`** | **`0x00020004`** | **TRACKBALL** |

`SOURCE_MOUSE_RELATIVE` thuộc class **TRACKBALL**, không nằm trong cả ba điều kiện được kiểm tra:

- `0x20004 & 0x08` = 0 → POSITION trượt
- `0x20004 & 0x02` = 0 → POINTER trượt
- `0x20004 & 0x100008` ≠ `0x100008` → TOUCHPAD trượt

→ **Trong lúc pointer capture đang bật, `isTouchpadEvent()` luôn trả `false` cho mọi thiết bị nguồn mouse.**

Đây là bản sao chức năng của RC-1 trong repo `Moonlight-android-trackpack` (ở đó `TouchpadDeviceMatcher` trượt vì lý do tương đương).

## 3. Lỗi C — Fallback ghim con trỏ vào góc trên-trái

Khi A và B cùng trượt, chuỗi `if/else-if` tại `Game.java:2579–2616` đi xuống:

```java
if (!handledRelativeMouseMotion && trySendTouchpadRelativeMove(event, eventSource)) { ... }
    // → isTouchpadEvent() = false → reset state → return false

else if (!handledRelativeMouseMotion && (eventSource & SOURCE_CLASS_POSITION) != 0) { ... }
    // → 0x20004 & 0x08 == 0 → false

else if (view != null && trySendPenEvent(view, event)) { ... }
    // → không phải stylus → false

else if (view != null) {
    updateMousePosition(view, event);          // ← Game.java:2615
}
```

`updateMousePosition()` coi `event.getX(0)/getY(0)` là **toạ độ tuyệt đối trong view**. Dưới pointer capture đây là **delta tương đối** (±1..±10, âm khi di chuyển trái/lên), bị clamp về `[0, width]` rồi gửi `conn.sendMousePosition(...)`.

→ **Con trỏ trên host bị ghim ở góc trên-trái và chỉ rung nhẹ.** Triệu chứng giống hệt repo kia.

Lưu ý: `view != null` luôn đúng ở đây vì event tới qua `OnCapturedPointerListener` đăng ký tại `Game.java:370`.

## 4. Lỗi D — `ControllerHandler` nuốt event thiết bị composite

`Game.java:2489–2496`

```java
if (!touchpadEvent && (eventSource & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
    if (controllerHandler.handleMotionEvent(event)) return true;
}
else if (!touchpadEvent && (deviceSources & InputDevice.SOURCE_CLASS_JOYSTICK) != 0
        && controllerHandler.tryHandleTouchpadEvent(event)) {
    return true;
}
```

Bàn phím composite (Nillkin báo `KEYBOARD | DPAD | MOUSE | JOYSTICK`) có bit `SOURCE_JOYSTICK` trong `getSources()`. Khi `touchpadEvent = false` (luôn xảy ra theo lỗi B), điều kiện thứ hai đúng, và `ControllerHandler.tryHandleTouchpadEvent()` (`ControllerHandler.java:1697–1714`) trả:

```java
return !prefConfig.gamepadTouchpadAsMouse;   // mặc định false → trả true
```

→ **Event bị nuốt trước khi tới bất kỳ logic touchpad nào.**

Rủi ro kèm theo: `ControllerHandler.getContextForEvent()` **tự tạo context cho bất kỳ device nào**, và `ControllerHandler.handleMotionEvent()` **luôn `return true`**. Nghĩa là nhánh thứ nhất cũng nuốt vô điều kiện nếu event lọt vào.

## 5. Lỗi E — Cài đặt tốc độ con trỏ không có tác dụng trên đường dẫn chính

`prefConfig.touchpadMouseSpeed` chỉ được dùng **đúng một chỗ**, tại `Game.java:2449`, bên trong `trySendTouchpadRelativeMove()`:

```java
float speedFactor = prefConfig.touchpadMouseSpeed / 100.0f;
touchpadMouseRemainderX += (event.getX(0) - lastTouchpadMouseX) * speedFactor;
```

Nhưng nhánh relative-axes ở `Game.java:2562–2578` — đường dẫn **chính** cho bàn phím Xiaomi khi capture đang bật — **không áp dụng speedFactor**:

```java
short deltaX = (short)inputCaptureProvider.getRelativeAxisX(event);
short deltaY = (short)inputCaptureProvider.getRelativeAxisY(event);
...
conn.sendMouseMove(deltaX, deltaY);      // không nhân speedFactor
```

Và nhánh này chạy **trước**, đồng thời set `handledRelativeMouseMotion = true` để chặn `trySendTouchpadRelativeMove()`.

→ Trên chính thiết bị mà fork nhắm tới (Xiaomi Pad 6 Max 14, báo `SOURCE_TOUCHPAD`, capture bật), **thanh trượt "tốc độ con trỏ" trong Settings không thay đổi gì**. Đây là tính năng được quảng cáo ở dòng 12 của `TOUCHPAD_EDITION_RELEASE.md`.

Kèm theo: `(short)` ép kiểu cắt phần thập phân của relative axis. Di chuyển chậm (delta < 1.0) cho ra 0 và bị vứt bỏ — không có accumulator giữ phần dư như nhánh `trySendTouchpadRelativeMove()` đã làm đúng ở dòng 2455–2463.

## 6. Lỗi F — Nhận diện bằng chuỗi tên thiết bị

`Game.java:1879–1882`

```java
String deviceName = device.getName() != null ? device.getName().toLowerCase(Locale.US) : "";
boolean looksLikeTabletKeyboardTouchpad =
        deviceName.contains("touchpad") ||
        (deviceName.contains("xiaomi") && deviceName.contains("touch"));
```

Vấn đề:

- Phụ thuộc tên thương mại, không phải capability. Nillkin Cube Pocket không chứa chuỗi `"touchpad"` trong tên composite device.
- `deviceName.contains("touchpad")` quá rộng theo hướng ngược lại: mọi thiết bị có chữ "touchpad" trong tên (kể cả touchpad của gamepad DualShock) đều lọt.
- Không có allowlist vendor/product, không có kiểm tra motion range.

So sánh: repo `Moonlight-android-trackpack` dùng fingerprint `0x21CE:0xB907` — cứng nhắc nhưng ít nhất còn xác định. Cả hai cách đều là triệu chứng của cùng một thiếu sót: **chưa có tiêu chí capability đủ chặt để phân biệt touchpad với chuột.**

`isXiaomiPadKeyboardDevice()` (`Game.java:1890–1899`) mắc cùng vấn đề, và nó ảnh hưởng tới cả bàn phím: `translateKeyEventForHost()` truyền `-1` làm deviceId cho thiết bị khớp tên — nghĩa là bàn phím của hãng khác sẽ không được hưởng workaround này.

## 7. Lỗi G — Không reset state cử chỉ khi mất focus

`Game.java:827–838`:

```java
public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    this.modifierFlags = 0;
    inputCaptureProvider.onWindowFocusChanged(hasFocus);
}
```

Không có gì reset `touchpadMouseActive`, `touchpadPrimaryButtonDown`, `touchpadScrollActive`, `touchpadTapDragCandidateActive`, `lastTouchpadMouseX/Y`, `touchpadMouseRemainderX/Y`.

Hậu quả có thể quan sát:

- Nếu mất focus giữa lúc `touchpadPrimaryButtonDown = true` → nút trái **kẹt ở trạng thái nhấn** trên host, vì `sendMouseButtonUp()` chỉ được gọi từ nhánh `ACTION_UP` mà nhánh đó không bao giờ tới.
- `lastTouchpadMouseX/Y` giữ giá trị cũ → sample đầu tiên sau khi lấy lại focus tạo **cú nhảy con trỏ** lớn.
- `scheduleTouchpadTapDrag()` có thể còn treo callback.

Repo `Moonlight-android-trackpack` đã xử lý điểm này (`touchpadRelativeAdapter.reset()` trong `onWindowFocusChanged`); fork này thì chưa.

## 8. Lỗi H (phụ) — Rò rỉ giữa hai đường dẫn delta

`Game.java:2567` chỉ set `handledRelativeMouseMotion = true` khi `deltaX != 0 || deltaY != 0`. Nếu relative delta bằng 0 (rất phổ biến: sample đầu, di chuyển dưới ngưỡng), luồng rơi tiếp vào `trySendTouchpadRelativeMove()`, hàm này lại tính delta **từ `event.getX(0)` tuyệt đối**.

→ Hai bộ đếm delta cùng chạy trên cùng một chuỗi event, `lastTouchpadMouseX/Y` được cập nhật rời rạc → khoảng cách dùng cho nhận diện tap/drag (`touchpadOneFingerTapMaxDistance`, `touchpadTapDragStartX`) tính sai → tap bị nhận nhầm thành drag hoặc ngược lại.

Sửa: quyết định **một** đường dẫn cho mỗi thiết bị ngay từ đầu event, không rơi chéo dựa trên việc delta có bằng 0 hay không.

---

## 9. Vì sao fork chạy được trên Xiaomi mà hỏng trên Nillkin

```
Xiaomi Pad 6 Max 14 keyboard
  → Android báo SOURCE_TOUCHPAD
      → isTouchpadEvent(): trúng dòng đầu tiên `eventSource == SOURCE_TOUCHPAD`  ✔
      → eventHasRelativeMouseAxes(): trúng vế thứ hai (TOUCHPAD && hasPointerCapture)  ✔
          → hoạt động (nhưng bỏ qua touchpadMouseSpeed — lỗi E)

Nillkin Cube Pocket / bàn phím composite khác
  → Android báo SOURCE_MOUSE (TouchInputMapper chạy chế độ pointer)
  → pointer capture bật → eventSource = SOURCE_MOUSE_RELATIVE, toolType = TOOL_TYPE_FINGER
      → eventHasRelativeMouseAxes(): cả hai vế trượt   ✘ (lỗi A)
      → isTouchpadEvent(): class TRACKBALL không khớp  ✘ (lỗi B)
      → tryHandleTouchpadEvent() nuốt event            ✘ (lỗi D)
      → hoặc rơi vào updateMousePosition()             ✘ (lỗi C)
          → CON TRỎ HOST GHIM Ở GÓC TRÊN-TRÁI
```

Cả hai repo đều xây logic trên giả định "touchpad = `SOURCE_TOUCHPAD` + toạ độ tuyệt đối". Giả định đó **sai với thiết bị composite dưới pointer capture**, và đó là nguyên nhân gốc chung.

---

## 10. Phương án sửa đề xuất

### Bước 0 — Trace runtime (bắt buộc trước khi sửa)

Fork này **không có log chẩn đoán nào** cho input path (repo kia ít nhất có `TouchpadDeviceMatcher.describeEvent()`). Cần thêm log tạm, bật bằng `log.tag.*`, ghi trong lúc **đã stream và capture đang bật**:

- `eventSource` (hex), `device.getSources()` (hex), `device.getName()`
- `toolType`, `actionMasked`, `pointerCount`
- `AXIS_X/Y` và `AXIS_RELATIVE_X/Y`
- `targetView.hasPointerCapture()`

Chạy với cả bàn phím Xiaomi lẫn một bàn phím composite (Nillkin) để chốt hai profile.

### P1 — Sửa `eventHasRelativeMouseAxes()` (ưu tiên cao nhất, ít rủi ro nhất)

`AndroidNativePointerCaptureProvider.java:113–120`

- Bỏ whitelist `toolType == TOOL_TYPE_MOUSE`. Nguồn `SOURCE_MOUSE_RELATIVE` **chỉ tồn tại khi capture bật**, nên bản thân nó đã đủ tin cậy.
- Nếu cần giữ an toàn cho SPen (moonlight-android issue #1030), đổi sang **blacklist** `TOOL_TYPE_STYLUS`/`TOOL_TYPE_ERASER`.
- Đây là fix chung cho **cả hai repo** — cùng một file, cùng một dòng.

### P2 — Sửa `isTouchpadEvent()`

`Game.java:1869–1888`

- Thêm `SOURCE_MOUSE_RELATIVE` vào tập nguồn được chấp nhận, hoặc kiểm tra `SOURCE_CLASS_TRACKBALL`.
- Thay `looksLikeTabletKeyboardTouchpad` (so khớp chuỗi tên) bằng kiểm tra capability: thiết bị external + có `AXIS_X/AXIS_Y` motion range hợp lệ tra theo **device sources**, không phải event source.
- Nếu vẫn cần allowlist, dùng vendor/product thay vì tên thương mại.

### P3 — Hàng rào an toàn cho fallback

`Game.java:2613–2616`

- Không gọi `updateMousePosition()` khi capture đang bật và `eventSource == SOURCE_MOUSE_RELATIVE`. Trong trạng thái đó `event.getX()` **không bao giờ** là toạ độ tuyệt đối hợp lệ.
- Thà không gửi gì còn hơn ghim con trỏ vào góc màn hình. Log warning một lần để lần sau chẩn đoán nhanh.

### P4 — Chặn `ControllerHandler` nuốt event

`Game.java:2489–2496`

- Thêm guard: không route vào `tryHandleTouchpadEvent()` khi thiết bị **không có trục joystick thật**. `ControllerHandler.hasJoystickAxes()` (dòng 318) đã có sẵn logic này, chỉ chưa được dùng ở điểm route.
- Bền hơn allowlist, và sửa cho mọi bàn phím composite chứ không riêng một model.

### P5 — Thống nhất tốc độ con trỏ và giữ phần dư

`Game.java:2562–2578`

- Áp `prefConfig.touchpadMouseSpeed / 100.0f` cho **cả** nhánh relative-axes, không chỉ `trySendTouchpadRelativeMove()`.
- Dùng chung accumulator (`touchpadMouseRemainderX/Y`) cho cả hai nhánh thay vì ép `(short)` trực tiếp, để không mất chuyển động chậm.
- Hợp nhất hai đường dẫn delta thành một hàm duy nhất — đồng thời sửa luôn lỗi H.

### P6 — Reset state khi mất focus / ngắt kết nối

`Game.java:827–838` và điểm dừng stream

- Thêm hàm `resetTouchpadGestureState()` reset toàn bộ cờ và bộ đếm.
- Gọi trong `onWindowFocusChanged(false)`, khi tắt capture, và khi `stopConnection()`.
- Bắt buộc gửi `sendMouseButtonUp()` nếu `touchpadPrimaryButtonDown` còn `true`, tránh kẹt nút trái trên host.
- Huỷ callback `scheduleTouchpadTapDrag()` đang treo.

---

## 11. Thứ tự thực hiện đề xuất

| Bước | Nội dung | Ghi chú |
|---|---|---|
| 1 | Bước 0 (log) | Chốt profile event cho Xiaomi và composite |
| 2 | P1 | Fix chung cho cả hai repo; nếu con trỏ chạy đúng thì P2 có thể thu hẹp |
| 3 | P3 + P4 | Luôn làm — chặn hai triệu chứng nặng nhất |
| 4 | P6 | Sửa kẹt nút trái, rủi ro cao khi alt-tab |
| 5 | P2 | Thay name-matching bằng capability |
| 6 | P5 | Trả lại tác dụng cho cài đặt tốc độ con trỏ |

## 12. Tiêu chí nghiệm thu

Xác minh trên host Windows thật, với **ít nhất hai bàn phím** (một báo `SOURCE_TOUCHPAD`, một báo `SOURCE_MOUSE`):

- [ ] Con trỏ Windows di chuyển đúng hướng trên cả hai loại bàn phím
- [ ] Thanh trượt tốc độ con trỏ thay đổi được tốc độ thật
- [ ] Di chuyển chậm vẫn tạo chuyển động
- [ ] 12 cử chỉ liệt kê trong `TOUCHPAD_EDITION_RELEASE.md` hoạt động trên cả hai loại
- [ ] Alt-tab ra ngoài rồi quay lại: không kẹt nút trái, không nhảy con trỏ
- [ ] Chuột Bluetooth ngoài, gamepad, stylus, màn hình cảm ứng: không hồi quy
- [ ] Stream 30 phút: không drift, không crash

## 13. Ghi chú

Repo này **không có unit test nào** cho input path (`app/src/test` trống, khác với repo `Moonlight-android-trackpack` có 3 test class). Trước khi sửa nên thêm ít nhất ba test thuần Java, và cả ba **phải đỏ trước khi sửa**:

- `isTouchpadEvent()` với `eventSource = SOURCE_MOUSE_RELATIVE`
- `eventHasRelativeMouseAxes()` với `SOURCE_MOUSE_RELATIVE` + `TOOL_TYPE_FINGER`
- Đường dẫn delta có áp `touchpadMouseSpeed` trong nhánh relative-axes

Nếu chúng không đỏ, mô hình lỗi trong báo cáo này chưa được chứng minh và cần quay lại Bước 0.
