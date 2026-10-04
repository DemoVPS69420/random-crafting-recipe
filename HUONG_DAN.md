# RandomCraft 26.3 và Release Studio

## File để dùng

- Mod: `build/libs/randomcraft-fabric-26.3-1.1.0.jar`.
- GUI Windows: `release-tool/dist/RandomCraft-Release-Studio.exe`.
- Source nằm trên nhánh Git `26.3-fabric` trong thư mục này.

Mod cần Minecraft Java **26.3**, Java **25**, Fabric Loader **0.19.5 trở lên** và Fabric API **0.161.0+26.3**. Chép JAR mod cùng Fabric API vào thư mục `mods`.

## Dùng GUI

1. Mở EXE. Không cần cài Python. Máy cần JDK và Git để build/release.
2. Chọn thư mục project chứa `gradle.properties`; ứng dụng tự nhận JDK trong `.tools` hoặc `JAVA_HOME`.
3. Bấm **Build JAR**, chờ thông báo thành công. Log hiển thị phía dưới.
4. Source phải được commit và push lên repository GitHub trước khi phát hành. Công cụ không tự commit hoặc push.
5. Nhập repository và GitHub token. Token cần quyền **Contents: write**; nếu commit sửa workflow thì có thể cần thêm **Workflows: write**. Token chỉ giữ trong bộ nhớ của ứng dụng.
6. Kiểm tra tag, tên, ghi chú; mặc định tạo **Bản nháp (draft)**. Bỏ chọn nếu muốn công khai sau khi upload xong.
7. Bấm **Xem trước & tạo release**, đọc thông tin và xác nhận. Bấm **Mở release** để xem kết quả.

Commit/push lần đầu, chạy trong thư mục `26.3-Fabric` sau khi xem lại thay đổi:

```powershell
git add .
git commit -m "Port RandomCraft to Minecraft 26.3 and add Release Studio"
git push -u origin 26.3-fabric
```

Nếu source hoặc JAR thay đổi sau khi build, hãy build lại. Nếu tag đã có, đổi `mod_version` trong `gradle.properties`, commit/push rồi build bản mới. Nếu upload lỗi, dùng URL bản nháp trong log để kiểm tra; công cụ không ghi đè hoặc xóa release cũ.

## Kiểm tra đã thực hiện

- Build bằng JDK 25 và Gradle 9.6.0.
- 5 bài test với recipe thật của Minecraft 26.3.
- 11 bài test luồng GitHub Release bằng API giả lập.
- Đóng gói và kiểm tra GUI trên Windows.

Chưa kiểm thử chơi trực tiếp hoặc đăng release thật bằng token GitHub. Recipe từ mod khác dùng cơ chế output riêng và việc cập nhật recipe book trong multiplayer cần kiểm tra trong game.
