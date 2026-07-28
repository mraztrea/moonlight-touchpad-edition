# Hướng dẫn build bản Release

## Chọn đúng biến thể

Với các thiết bị Android hiện đại, sử dụng biến thể `nonRootRelease`.

- `nonRootRelease`: bản production thông thường, application ID
  `com.limelight.touchpadedition`.
- `rootRelease`: chỉ dành cho thiết bị root và hỗ trợ tối đa Android 7.1
  (API 25).

## Yêu cầu

- Android SDK và NDK đã được cấu hình trong `local.properties`.
- Các Git submodule đã được tải đầy đủ.
- Gradle chạy bằng JDK 21, không sử dụng Java 8.
- Trên Windows phải đặt biến môi trường `HOST_OS=windows` khi build native code.

## Cách khuyến nghị: tạo APK Release đã ký

Trong Android Studio:

1. Chọn **Build → Generate Signed App Bundle or APK**.
2. Chọn **APK**.
3. Chọn module `app`.
4. Chọn file keystore `.jks` hiện có hoặc tạo keystore mới.
5. Chọn:
   - Flavor: `nonRoot`
   - Build type: `release`
6. Bật cả hai loại chữ ký **V1** và **V2**.
7. Chọn **Create** để bắt đầu build.

> [!IMPORTANT]
> Hãy sao lưu keystore và mật khẩu ở nơi an toàn. Nếu mất keystore, bạn sẽ
> không thể phát hành bản cập nhật cho cùng application ID.

Không lưu keystore, mật khẩu hoặc thông tin ký ứng dụng vào Git.

## Build APK Release bằng PowerShell

Mở PowerShell và chạy:

```powershell
cd D:\Projects\Android\moonlight-touchpad-edition

$env:JAVA_HOME = "C:\Users\ducthanh276\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:HOST_OS = "windows"

git submodule update --init --recursive
.\gradlew.bat :app:assembleNonRootRelease
```

APK chưa ký được tạo tại:

```text
app\build\outputs\apk\nonRoot\release\app-nonRoot-release-unsigned.apk
```

Repo hiện chưa cấu hình release signing trong `app/build.gradle`, vì vậy APK
tạo trực tiếp bằng Gradle là APK chưa ký. Muốn cài đặt hoặc phát hành, hãy tạo
bản đã ký bằng Android Studio theo hướng dẫn phía trên.

## Build AAB để phát hành lên Google Play

Chạy:

```powershell
.\gradlew.bat :app:bundleNonRootRelease
```

File AAB được tạo tại:

```text
app\build\outputs\bundle\nonRootRelease\app-nonRoot-release.aab
```

Để tạo AAB đã ký, trong Android Studio chọn **Generate Signed App Bundle or
APK**, sau đó chọn **Android App Bundle** thay vì APK.

## Kiểm tra bản build

Kiểm tra APK đã được ký:

```powershell
$sdkDir = "C:\Users\ducthanh276\AppData\Local\Android\Sdk"
$apksigner = Get-ChildItem "$sdkDir\build-tools\*\apksigner.bat" |
    Sort-Object FullName -Descending |
    Select-Object -First 1

& $apksigner.FullName verify --verbose --print-certs ".\duong-dan-den-file.apk"
```

Kết quả hợp lệ phải hiển thị `Verifies`.

## Một số lỗi thường gặp

### Gradle đang chạy bằng Java 8

Kiểm tra:

```powershell
.\gradlew.bat --version
```

Nếu kết quả hiển thị JVM 1.8, đặt lại `JAVA_HOME` và `Path` theo phần build APK.

### NDK không xác định được hệ điều hành host

Đặt lại biến môi trường:

```powershell
$env:HOST_OS = "windows"
```

Sau đó chạy lại Gradle.

### Thiếu source trong `moonlight-common-c`

Khởi tạo lại submodule:

```powershell
git submodule sync --recursive
git submodule update --init --recursive
```
