# Phương án sửa: con trỏ host đứng yên khi con trỏ Android chạm mép màn hình

- **Ngày:** 2026-07-28
- **Trạng thái:** **Đã triển khai Hướng B** (B.1 + B.2 + B.3, có điều chỉnh: feature-detect tại ranh giới gesture thay vì cache SparseBooleanArray; học thiết bị không khai báo motion range qua `touchpadRelativeAxisDevices`). Unit test 11/11 xanh. Chưa test trên phần cứng thật (mục 5).
- **Thiết bị:** Lenovo TB321FU (Android API 36) + Nillkin Cube Pocket `0x21CE:0xB907`
- **Host:** Windows (Sunshine/GFE)
- **File liên quan:**
  - `app/src/main/java/com/limelight/Game.java` (`trySendTouchpadRelativeMove`, dòng 2426–2550)
  - `app/src/main/java/com/limelight/binding/input/capture/AndroidNativePointerCaptureProvider.java`

---

## 1. Nguyên nhân gốc (đã xác định, độ tin cậy cao)

Chuỗi bằng chứng khép kín trong chính code hiện tại:

**(a) Pointer capture đang bị TẮT cho đúng thiết bị này.**

`AndroidNativePointerCaptureProvider.java:31-33` + `:48-51`:

```java
static boolean isPointerCaptureBlockedDevice(int vendorId, int productId) {
    return vendorId == 0x21CE && productId == 0xB907;   // ← Nillkin
}
...
// ZUI disables this touchpad's InputReader mapper while pointer capture is active.
if (isPointerCaptureBlockedDevice(device.getVendorId(), device.getProductId())) {
    return false;   // → hasCaptureCompatibleInputDevice() = false → KHÔNG requestPointerCapture()
}
```

**(b) Không có capture ⇒ `eventHasRelativeMouseAxes()` luôn trả `false`.**

`AndroidNativePointerCaptureProvider.java:135-136` yêu cầu `SOURCE_MOUSE_RELATIVE` hoặc `SOURCE_TOUCHPAD && targetView.hasPointerCapture()`. Cả hai đều không thoả → nhánh relative ở `Game.java:2689-2700` **không bao giờ chạy**. Đường duy nhất đang hoạt động là `trySendTouchpadRelativeMove()`.

**(c) `trySendTouchpadRelativeMove()` tính delta từ toạ độ con trỏ Android.**

`Game.java:2542-2547`:

```java
float deltaX = event.getX(0) - lastTouchpadMouseX;
float deltaY = event.getY(0) - lastTouchpadMouseY;
lastTouchpadMouseX = event.getX(0);
lastTouchpadMouseY = event.getY(0);
sendTouchpadMouseMove(deltaX, deltaY);
```

**(d) Thiết bị chạy ở `DeviceMode::POINTER` của AOSP `TouchInputMapper`.**

Báo cáo lần 1 đo được: `ABS X 0..1887`, `ABS Y 0..2015`, **REL X/Y ở tầng evdev: không có**. Không có trục relative vật lý ⇒ Android bắt buộc chạy touchpad qua `PointerController` để tổng hợp con trỏ.

**Kết luận:** `event.getX(0)/getY(0)` trong nhánh 1 ngón chính là **vị trí con trỏ hệ thống của Android** (đã bị `PointerController::setPosition()` clamp vào biên display), **không phải** vị trí ngón tay trên mặt trackpad.

```
Ngón tay đi tiếp sang trái
  → PointerController.move(-8, 0)
  → position.x = max(0, position.x - 8) = 0   ← CLAMP
  → event.getX(0) = 0 (không đổi so với lần trước)
  → deltaX = 0 - 0 = 0
  → sendTouchpadMouseMove(0, 0) → không gửi gói nào
  → CON TRỎ HOST ĐỨNG YÊN
```

Con trỏ Android vô hình (`AndroidPointerIconCaptureProvider` đặt `PointerIcon.TYPE_NULL`) nên người dùng không nhìn thấy nó bị kẹt ở mép — chỉ thấy "chuột host không di chuyển tiếp được".

**Đây là lỗi kiến trúc, không phải lỗi logic:** hệ thống đang lấy một tín hiệu *bị chặn biên* (vị trí con trỏ) để tái tạo một tín hiệu *không biên* (chuyển động tương đối). Không có cách chỉnh tham số nào cứu được; phải đổi nguồn tín hiệu.

### Vì sao scroll 2 ngón / cử chỉ 3 ngón KHÔNG bị lỗi này

Ở chế độ nhiều ngón, AOSP chuyển sang `PointerGesture::Mode::SWIPE/FREEFORM`. Toạ độ các pointer khi đó được tổng hợp quanh tâm cử chỉ chứ không phải vị trí `PointerController` bị clamp, nên `getPointerAverageX/Y()` vẫn biến thiên bình thường. Vì vậy **mọi thay đổi chỉ được phép chạm vào nhánh 1 ngón** — đó cũng là điều kiện để không phá scroll và right-click 2 ngón.

---

## 2. Ba hướng sửa, xếp theo thứ tự nên thử

### Hướng A — Bật lại pointer capture (lý tưởng nhất, thử trước, chi phí ~15 phút)

Nếu capture chạy được, toàn bộ lớp lỗi này biến mất vĩnh viễn: không còn con trỏ Android, không còn biên, delta là relative thuần.

Ràng buộc "ZUI disables this touchpad's InputReader mapper while pointer capture is active" được ghi từ chẩn đoán cũ (2 báo cáo trước đều đoán sai mô hình lỗi ít nhất một lần). **Cần kiểm chứng lại trên API 36 trước khi loại bỏ hướng này.**

**Cách thử (tạm, không commit):**

1. Tạm cho `isPointerCaptureBlockedDevice()` trả `false`.
2. Build debug, chạy stream, di ngón trên trackpad.
3. Đọc log `MOTION` đã có sẵn ở `Game.java:2583-2598`:
   ```
   adb logcat -s Moonlight:* | findstr MOTION
   ```

| Quan sát | Kết luận |
|---|---|
| **Không có** dòng `MOTION` nào khi di ngón | ZUI đúng là tắt mapper → **loại Hướng A**, đi Hướng B |
| Có `MOTION` với `src=0x00100008` (`SOURCE_TOUCHPAD`), `capture=true`, `relX/relY ≠ 0` | ✅ **Chọn Hướng A** — chỉ cần xoá `isPointerCaptureBlockedDevice()` |
| Có `MOTION` nhưng `relX=relY=0.00` liên tục | Capture sống nhưng không có trục relative → giữ block, đi Hướng B |

Nếu Hướng A thắng: xoá `isPointerCaptureBlockedDevice()` + test tương ứng trong `AndroidNativePointerCaptureProviderTest.java:13-18`, và **phải** giữ nguyên `shouldUseAbsoluteMouseFallback()` chặn `SOURCE_MOUSE_RELATIVE` (đã có) đồng thời bổ sung chặn `SOURCE_TOUCHPAD` khi `hasPointerCapture()` — nếu không sẽ tái hiện đúng lỗi "click bị nhảy vị trí con trỏ" mà bạn yêu cầu tránh (xem mục 4, RR-1).

---

### Hướng B — Dùng `AXIS_RELATIVE_X/Y` ngay cả khi KHÔNG có capture (khuyến nghị chính)

Từ Android 12, `TouchInputMapper` điền `AXIS_RELATIVE_X/Y` vào MotionEvent của touchpad ở chế độ POINTER. Giá trị này được tính **trước** bước clamp của `PointerController`, nên nó **không bão hoà ở biên**. Thiết bị chạy API 36 → khả năng có rất cao.

Giá trị này cũng đã đi qua `PointerVelocityControl` (pointer acceleration) — tức là **bằng đúng** delta vị trí khi con trỏ chưa chạm biên. Hệ quả rất quan trọng: **đổi sang nguồn này không làm thay đổi tốc độ/cảm giác chuột ở vùng giữa màn hình**, chỉ khác ở mép. Rủi ro hồi quy gần như bằng 0.

**Kiểm chứng trước khi code:** log `MOTION` hiện tại đã in `relX/relY`. Di ngón chậm ở giữa màn hình, xem `relX/relY` có khác 0 không. Nếu có → Hướng B khả thi.

#### B.1 — Sửa duy nhất 6 dòng trong `trySendTouchpadRelativeMove()`

`Game.java:2542-2547`, thay bằng:

```java
float deltaX;
float deltaY;
if (useRelativeAxesForTouchpad(event)) {
    deltaX = getAccumulatedAxis(event, MotionEvent.AXIS_RELATIVE_X);
    deltaY = getAccumulatedAxis(event, MotionEvent.AXIS_RELATIVE_Y);
}
else {
    deltaX = event.getX(0) - lastTouchpadMouseX;
    deltaY = event.getY(0) - lastTouchpadMouseY;
}

// LUÔN cập nhật, bất kể dùng nguồn nào — các ngưỡng tap/drag phía trên
// vẫn đọc toạ độ tuyệt đối, và việc chuyển qua lại giữa 2 nguồn phải an toàn.
lastTouchpadMouseX = event.getX(0);
lastTouchpadMouseY = event.getY(0);

sendTouchpadMouseMove(deltaX, deltaY);
```

`getAccumulatedAxis()` phải cộng cả historical samples, giống hệt `getRelativeAxisX/Y()` ở `AndroidNativePointerCaptureProvider.java:140-159` — nếu bỏ historical sẽ mất chuyển động khi batching ở 1000 Hz:

```java
private static float getAccumulatedAxis(MotionEvent event, int axis) {
    float value = event.getAxisValue(axis);
    for (int i = 0; i < event.getHistorySize(); i++) {
        value += event.getHistoricalAxisValue(axis, i);
    }
    return value;
}
```

#### B.2 — Feature-detection có cache theo `deviceId`

Không dựa vào version check, dựa vào thiết bị thật:

```java
// SparseBooleanArray touchpadRelativeAxisSupport, key = deviceId
private boolean useRelativeAxesForTouchpad(MotionEvent event) {
    int deviceId = event.getDeviceId();
    int cached = touchpadRelativeAxisSupport.get(deviceId, UNKNOWN);
    if (cached != UNKNOWN) {
        return cached == SUPPORTED;
    }

    InputDevice device = event.getDevice();
    boolean supported = device != null &&
            device.getMotionRange(MotionEvent.AXIS_RELATIVE_X, event.getSource()) != null &&
            device.getMotionRange(MotionEvent.AXIS_RELATIVE_Y, event.getSource()) != null;
    touchpadRelativeAxisSupport.put(deviceId, supported ? SUPPORTED : UNSUPPORTED);
    return supported;
}
```

- Cache là bắt buộc: `getMotionRange()` khá đắt, không được gọi mỗi motion event.
- Invalidate cache trong `InputManager.InputDeviceListener.onInputDeviceChanged/Removed` (`AndroidNativePointerCaptureProvider` đã đăng ký listener sẵn, có thể mở rộng hoặc thêm listener riêng ở `Game`).
- Quyết định phải **ổn định trong suốt một gesture** — nếu dao động giữa các event sẽ gây double-count. Cache theo deviceId đảm bảo điều đó.

**Chốt an toàn (nếu `getMotionRange` không khai báo trục dù event vẫn mang giá trị):** cho phép "học" — nếu thấy `AXIS_RELATIVE_X/Y != 0` từ deviceId này ít nhất một lần thì chuyển `SUPPORTED` vĩnh viễn cho device đó. Nhưng **chỉ được chuyển ở ranh giới gesture** (`ACTION_DOWN` / `ACTION_HOVER_ENTER`), không giữa chừng.

#### B.3 — Sửa ngưỡng tap/drag để không bị "mù" ở mép màn hình

`Game.java:2523-2540` đo quãng đường bằng toạ độ tuyệt đối:

```java
float distance = sqrt(pow(event.getX(0) - touchpadOneFingerTapStartX, 2) + ...);
```

Khi con trỏ bị ghim ở mép, `distance` luôn ≈ 0 dù ngón tay vuốt mạnh → `touchpadOneFingerTapActive` **không bị huỷ** → khi nhấc tay sẽ **sinh ra một cú click trái giả**. Đây chính là kịch bản "click bị nhảy / click ngoài ý muốn" cần tránh.

Sửa: cộng dồn quãng đường từ chính delta đang dùng, thay vì từ toạ độ:

```java
touchpadOneFingerTravel += Math.hypot(deltaX, deltaY);   // reset ở ACTION_DOWN/HOVER_ENTER
touchpadOneFingerTapMaxDistance = Math.max(touchpadOneFingerTapMaxDistance,
                                           touchpadOneFingerTravel);
```

Áp dụng tương tự cho `touchpadTapDragCandidateActive` (`:2532-2540`) và cho ngưỡng double-tap-drag ở `:2452-2454`.

> Lưu ý: quãng đường cộng dồn luôn ≥ khoảng cách thẳng, nên ngưỡng hiện tại có thể nhạy hơn một chút. Nếu tap bị trượt nhiều, nới `TOUCHPAD_ONE_FINGER_TAP_DISTANCE_THRESHOLD` chứ **không** quay lại dùng toạ độ.

#### B.4 — Không đụng vào bất kỳ thứ gì khác

Không sửa `handleMotionEvent()`, không sửa `eventHasRelativeMouseAxes()`, không sửa `trySendTouchpadTwoFingerScroll()`, `trySendTouchpadThreeFingerGesture()`, `isTouchpadSecondaryButtonAction()`, `shouldUseAbsoluteMouseFallback()`. Đây là điều kiện để 3 hồi quy bạn nêu không xảy ra.

---

### Hướng C — Dự phòng nếu cả A và B đều không có tín hiệu relative

Chỉ dùng khi log xác nhận `relX = relY = 0.00` **và** capture không sống. Khi đó không tồn tại nguồn dữ liệu chuyển động nào ngoài vị trí con trỏ đã clamp, nên chỉ có thể **giảm nhẹ**, không sửa dứt điểm.

**C.1 — Phát hiện bão hoà + trôi theo vận tốc cuối (edge-drift)**

```java
boolean atLeftEdge  = event.getX(0) <= EDGE_EPS;
boolean atRightEdge = event.getX(0) >= streamView.getWidth() - 1 - EDGE_EPS;
```

- Duy trì `lastVelocityX/Y` (px/ms) đo được ở các sample **chưa** bão hoà, làm mượt bằng EMA.
- Khi một trục bão hoà mà vẫn còn nhận `ACTION_MOVE`/`ACTION_HOVER_MOVE`: gửi `lastVelocityX * dt` theo hướng bão hoà thay cho delta đo được.
- Dừng trôi khi: trục hết bão hoà / `ACTION_UP` / `ACTION_CANCEL` / `ACTION_POINTER_DOWN` / quá `EDGE_DRIFT_MAX_MS` (đề xuất 1500 ms).
- Trục vuông góc vẫn dùng delta thật.

**Rủi ro đã biết:** nếu AOSP bỏ dispatch event khi toạ độ không đổi (`moveNeeded == false` trong `dispatchPointerSimple`), sẽ không có event nào để bám → phải chuyển sang `Handler.postDelayed` phát nhịp ~8 ms. Biến thể theo timer nguy hiểm hơn (con trỏ tiếp tục trôi khi người dùng để yên ngón ở mép), nên bắt buộc phải có `EDGE_DRIFT_MAX_MS` và huỷ ngay ở `ACTION_UP`.

**C.2 — Chỉ dùng khi C.1 không đạt:** đưa `EDGE_DRIFT` ra thành setting tắt-mặc-định, tránh áp hành vi heuristic lên các thiết bị khác.

---

## 3. Thứ tự thực hiện

| Bước | Việc | Điều kiện chuyển bước |
|---|---|---|
| 0 | Chạy stream, thu log `MOTION` (đã có sẵn ở `Game.java:2583`), di ngón chậm ở giữa + vuốt tới mép | Có giá trị thực của `src`, `relX/relY`, `capture` |
| 1 | Thử Hướng A (tạm bỏ block capture) | Có `MOTION` + `relX/relY ≠ 0` → dừng ở A |
| 2 | Nếu A hỏng: **B.1 + B.2 + B.3** | Đây là phương án chính |
| 3 | Test chấp nhận (mục 5) với `seekbar_touchpad_mouse_speed = 100` | |
| 4 | Chỉ khi log cho `relX/relY = 0`: Hướng C | |
| 5 | Đưa log `MOTION` về sau cờ bật/tắt thay vì `BuildConfig.DEBUG` cứng | Dọn dẹp |

---

## 4. Đối chiếu trực tiếp với 3 hồi quy bạn yêu cầu tránh

| Hồi quy cần tránh | Cơ chế bảo vệ trong phương án |
|---|---|
| **Click / right-click 2 ngón làm nhảy vị trí con trỏ trên host** | Nguyên nhân duy nhất gây nhảy là `updateMousePosition()` gửi `sendMousePosition()` với toạ độ sai. Hướng B **không chạm** vào `handleMotionEvent()` nên nhánh đó vẫn không đổi. Ngoài ra B.1 giữ nguyên toàn bộ các `return` sớm ở `ACTION_DOWN` / `ACTION_BUTTON_PRESS` (`:2436-2446`, `:2497-2508`) — event nút **không bao giờ** đi tới đoạn tính delta, nên không thể sinh delta nhảy. **RR-1:** riêng Hướng A bắt buộc phải mở rộng `shouldUseAbsoluteMouseFallback()` để chặn `SOURCE_TOUCHPAD` khi đang capture, nếu không lỗi này sẽ tái xuất hiện. |
| **Mất scroll 2 ngón** | `trySendTouchpadTwoFingerScroll()` (`:2166-2323`) chạy **trước** (`:2681`) và `return true` sớm; nó dùng `getPointerAverageX/Y()` ở chế độ gesture đa ngón, không bị clamp. Phương án không sửa hàm này, cũng không sửa thứ tự gọi. |
| **Mất right-click 2 ngón** | Do `touchpadTwoFingerTapActive` trong cùng hàm trên (`:2202-2206`) và `isTouchpadSecondaryButtonAction()` (`:2658-2668`). Cả hai không bị chạm. Ngưỡng `TOUCHPAD_TWO_FINGER_TAP_DISTANCE_THRESHOLD` vẫn dùng toạ độ trung bình 2 ngón — **không** đổi sang travel-distance (thay đổi ở B.3 chỉ áp cho nhánh 1 ngón). |

Rủi ro thêm cần canh:

- **RR-2 — double-count:** nếu `useRelativeAxesForTouchpad()` đổi giá trị giữa gesture, một sample có thể bị tính hai lần. Chặn bằng cache theo `deviceId` và chỉ đổi trạng thái ở ranh giới gesture (B.2).
- **RR-3 — tap giả ở mép:** đã xử lý ở B.3.
- **RR-4 — chuột USB/Bluetooth rời:** source là `SOURCE_MOUSE_RELATIVE`, `isTouchpadEvent()` trả `false` → `trySendTouchpadRelativeMove()` thoát ngay ở `:2427`. Không bị ảnh hưởng.
- **RR-5 — trackpad ảo trên màn cảm ứng / stylus / gamepad:** không đi qua nhánh 1 ngón của hàm này.

---

## 5. Test

### Unit test (JVM) — phải ĐỎ trước khi sửa

| Vá | Test |
|---|---|
| B.1 | Event `ACTION_MOVE`, `x` không đổi (bão hoà), `AXIS_RELATIVE_X = -8` → phải gửi `sendMouseMove` với dx ≠ 0 |
| B.1 | Event có 3 historical samples relative → tổng delta phải cộng đủ cả 4 |
| B.2 | Device không khai báo `AXIS_RELATIVE_X` → phải rơi về delta toạ độ, giá trị bằng đúng hành vi hiện tại |
| B.2 | Gọi 100 event cùng `deviceId` → `getMotionRange()` chỉ được gọi 1 lần (cache) |
| B.3 | `x` bão hoà + relative delta lớn → `touchpadOneFingerTapActive` phải bị huỷ (không sinh click khi nhấc tay) |
| Hồi quy | 2 ngón `ACTION_POINTER_DOWN` → `ACTION_MOVE` → `ACTION_POINTER_UP` nhanh → phải sinh right-click |
| Hồi quy | 2 ngón vuốt dọc → phải sinh `sendMouseHighResScroll` |

Bài học từ 2 lần trước: **17 unit test xanh mà lỗi vẫn còn**, vì fixture sinh từ giả định chưa kiểm chứng. Vì vậy **bước 0 (thu log thật) là bắt buộc**, không được bỏ qua.

### Chấp nhận trên phần cứng thật (Nillkin + host Windows)

- [ ] Vuốt liên tục sang trái/phải/lên/xuống nhiều lần: con trỏ host đi tiếp không giới hạn, **không dừng ở bất kỳ điểm nào**
- [ ] Đẩy con trỏ host sát 4 cạnh và 4 góc màn hình host rồi kéo ngược lại: phản hồi tức thì, không có "vùng chết"
- [ ] Di chậm 1–2 px: vẫn có chuyển động (accumulator hoạt động)
- [ ] Nhấc tay ở mép rồi chạm lại giữa trackpad: con trỏ host **không nhảy**
- [ ] Tap 1 ngón → click trái; tap 1 ngón **ở sát mép trackpad khi con trỏ Android đang ghim** → vẫn click đúng, không click giả sau khi vuốt
- [ ] Tap 2 ngón → click phải (thử cả ở giữa và khi con trỏ đang ghim ở mép)
- [ ] Vuốt 2 ngón dọc/ngang → scroll đúng chiều, không nhảy con trỏ
- [ ] Pinch 2 ngón → zoom; vuốt 3 ngón → Alt-Tab / Win+Tab / Win+D
- [ ] Tap-drag và double-tap-drag: kéo được qua toàn màn hình host, kể cả khi con trỏ Android chạm mép giữa chừng
- [ ] Chuột Bluetooth/USB rời: không hồi quy
- [ ] Mất/lấy lại focus, disconnect/reconnect: điều khiển vẫn đúng
- [ ] Stream 30 phút: không drift, không lệch, không crash
