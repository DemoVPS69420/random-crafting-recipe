# RandomCraft Release Studio

Mở `dist/RandomCraft-Release-Studio.exe`, hoặc `Start-Release-Studio.bat` nếu chạy bằng Python.

## Commit và đồng bộ GitHub

### Chọn nhiều phiên bản

1. Bấm **Nhiều phiên bản…**, chọn workspace chứa các thư mục như `1.12.2-Forge`, `1.21.1-NeoForge`, `26.3-Fabric`.
2. Chọn các dòng bằng Ctrl/Shift hoặc **Chọn tất cả**. Kiểm tra repository chung và nhánh đích của từng phiên bản.
3. Bấm **Chuẩn bị & xem trước**. Project chưa có `.git` được kết nối với nhánh tương ứng: dùng lịch sử remote nếu nhánh đã tồn tại, hoặc chuẩn bị nhánh mới. Source local được giữ nguyên; workflow/tài liệu remote còn thiếu được lấy về.
4. Xem danh sách file của từng nhánh, nhập nội dung commit rồi bấm **Commit & Push N nhánh**.

Mỗi project tạo commit riêng và được push lần lượt trong cùng thao tác. Nếu một nhánh lỗi, công cụ dừng, báo các nhánh đã push và chưa xử lý; các nhánh thành công không bị hoàn tác. Build output, cache, JDK, EXE và file token được loại khỏi lần import, Gradle wrapper JAR vẫn được giữ.

Danh sách tự dò dùng thư mục tên `<Minecraft>-<Loader>`; không chọn các checkout tham chiếu như `RepoBranches`, `Library-*` hoặc `Queue-*`. Nút Commit & Push riêng vẫn dùng được cho project nằm ở thư mục có tên khác.

### Một phiên bản

1. Chọn thư mục project có Git, ví dụ `26.3-Fabric`.
2. Bấm **Commit & Push**. Xem nhánh, địa chỉ origin và toàn bộ file sẽ được commit; nhập nội dung commit.
3. Xác nhận **Commit & Push**. Công cụ fetch thay đổi từ origin, lưu commit local, merge rồi push. Không cần chạy `git pull` riêng khi gặp lỗi `fetch first`.

Nếu thư mục chưa có Git, lần bấm đầu sẽ đề nghị kết nối với repository/nhánh được nhận diện. Sau khi kết nối, bấm lại để xem trước và commit/push. Có thể dùng bản 1.12.2 Forge; phiên bản mod được đọc từ `mcmod.info`, Gradle chạy với JDK 17 và compiler JDK 8 tìm từ `JAVA8_HOME` hoặc `.tools`.

Nhánh mới chưa có workflow sẽ chỉ được push source, không tự tạo release. Bạn vẫn có thể Build JAR và tạo release bằng GUI sau đó.

Nếu có xung đột, merge được hủy, commit local được giữ và danh sách file cần xử lý xuất hiện trong log. Công cụ không force-push. Nếu người khác push đúng lúc đang chạy, bấm lại để đồng bộ lần nữa.

Git dùng tài khoản đã đăng nhập qua Git Credential Manager/SSH trên máy. Ô GitHub token trong GUI phục vụ API release, không được ghi vào cấu hình Git. Source thay đổi sau lần xem trước sẽ yêu cầu xem lại trước khi commit.

**Nhánh 26.3-fabric hiện tự build và tạo/cập nhật release qua GitHub Actions sau khi push.** Với nhánh này, Commit & Push là đủ để kích hoạt workflow. Theo dõi tại [GitHub Actions](https://github.com/DemoVPS69420/random-crafting-recipe/actions). Muốn một phiên bản release mới, tăng `mod_version` trước khi commit. Hành vi phát hành phụ thuộc workflow của repository.

## Build và tạo release bằng GUI

Để build thử tại máy, bấm **Build JAR**. Nếu dùng nút tạo release thủ công, hãy đồng bộ Git trước, build lại, rồi chọn **Xem trước & tạo release**. Source phải sạch và đã push. Nếu workflow đã tạo tag/release tương ứng, dùng release đó; chức năng phát hành thủ công không ghi đè tag có sẵn.

## Đóng gói và kiểm tra

Trong thư mục `release-tool`:

```powershell
python -m unittest discover -v
powershell -ExecutionPolicy Bypass -File package.ps1
```

Các bài test Git dùng repository local tạm thời, gồm hai nhánh phân kỳ, xung đột, file đổi sau xem trước, nhánh mới và chặn file token. Không push lên GitHub khi chạy test.
