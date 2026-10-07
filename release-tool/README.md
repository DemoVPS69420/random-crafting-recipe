# RandomCraft Release Studio

Mở `dist/RandomCraft-Release-Studio.exe`, hoặc `Start-Release-Studio.bat` nếu chạy bằng Python.

## Commit và đồng bộ GitHub

1. Chọn thư mục project có Git, ví dụ `26.3-Fabric`.
2. Bấm **Commit & Push**. Xem nhánh, địa chỉ origin và toàn bộ file sẽ được commit; nhập nội dung commit.
3. Xác nhận **Commit & Push**. Công cụ fetch thay đổi từ origin, lưu commit local, merge rồi push. Không cần chạy `git pull` riêng khi gặp lỗi `fetch first`.

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
