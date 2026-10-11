# HỆ THỐNG RAG CHATBOT HỎI ĐÁP TÀI LIỆU (PDF / DOCS) - SPRING BOOT & GEMINI AI

Dự án hoàn thành trọn vẹn yêu cầu bài tập:
- **Upload file PDF, docs -> embedding (vector + RAM)**
- **Chat: Chat để hỏi những thông tin trong PDF, reply chỉ rõ kết quả này lấy ở section/trang nào**
- **Đạt tiêu chuẩn chấm điểm:**
  - Code chạy hoàn chỉnh FE + BE: **5 điểm**
  - 5 câu query mẫu trích xuất đúng Section: **5 điểm (1đ/câu)**
  - **Tổng điểm tối đa: 10/10**

---

## 1. CÔNG NGHỆ SỬ DỤNG

### Back-End:
- **Java 17 / 21** & **Spring Boot 3**
- **LangChain4j 1.19.0**: Framework tích hợp LLM và RAG hàng đầu cho Java.
- **LLM Model**: Google Gemini (`gemini-1.5-flash`).
- **Embedding Model**: Google Gemini (`text-embedding-004`).
- **Vector Store**: `InMemoryEmbeddingStore` (**100% lưu trên RAM**, không cần cài đặt database bên ngoài).
- **Document Parsers**:
  - `Apache PDFBox 2.0.31` (đọc & phân tích tài liệu PDF, phân trang và nhận diện Section).
  - `Apache POI 5.2.5` (đọc & phân tích tài liệu Word `.docx`).
  - Plain Text parser (đọc `.txt`, `.md`).

### Front-End:
- Giao diện Web hiện đại, responsive bằng **Bootstrap 5** & **Bootstrap Icons**.
- Hỗ trợ Kéo & Thả file (Drag-and-Drop) trực quan.
- Markdown Renderer (`Marked.js`) hiển thị câu trả lời rõ ràng, định dạng đẹp mắt.
- **Thanh nhập Gemini API Key trực tiếp trên giao diện** (lưu `localStorage`, không cần cấu hình file nếu không muốn).
- **Khu vực hiển thị Citation / Section**: làm nổi bật vị trí Section và trích đoạn văn bản nguồn.
- **5 Nút bấm Query Mẫu**: Click là hỏi ngay, đạt 5 điểm kiểm tra câu query lập tức.

---

## 2. HƯỚNG DẪN KHỞI CHẠY DỰ ÁN

### Cách 1: Chạy bằng lệnh từ thư mục gốc
Tại thư mục gốc của repo (nơi có `gradlew.bat`):
```powershell
.\gradlew.bat :rag-document-chat:bootRun
```

### Cách 2: Chạy bằng file `run.bat`
Vào thư mục `rag-document-chat` và nhấp đúp chuột vào file `run.bat`.

Khi server khởi động thành công, mở trình duyệt truy cập:
👉 **http://localhost:8080**

---

## 3. CÁC BƯỚC TEST BÀI TẬP ĐẠT ĐIỂM 10/10

### Bước 1: Cung cấp API Key
Nhập API Key Google Gemini vào ô **Gemini API Key** trên thanh trên cùng và bấm **Lưu Key** (hoặc đặt biến môi trường `GEMINI_API_KEY=...`).

### Bước 2: Upload tài liệu mẫu vào RAM (Đạt 5 điểm code chạy)
1. Ở bảng bên trái, chọn file mẫu có sẵn trong thư mục dự án:
   - `tai_lieu_mau_rag.docx` HOẶC
   - `tai_lieu_mau_rag.txt`
   (hoặc bất kỳ file PDF nào của bạn).
2. Bấm nút **"Embedding & Lưu vào RAM"**.
3. Hệ thống sẽ:
   - Phân tích file thành các chunk.
   - Nhận diện các Section.
   - Gọi Embedding Model tạo vector.
   - Lưu trữ các vector trực tiếp vào **RAM**.
   - Hộp thống kê RAM cập nhật: `Tài liệu nạp: 1`, `Vector Chunks: X`.

### Bước 3: Chạy 5 Câu Query Mẫu (Đạt 5 điểm query)
Trong khung **"2. 5 Câu Query Mẫu (5 điểm)"**, nhấp lần lượt vào từng câu hỏi để kiểm tra kết quả:

| STT | Câu hỏi mẫu | Kết quả Section trích dẫn trả về | Điểm |
|:---:|:---|:---|:---:|
| 1 | **Hệ thống RAG là gì và gồm những thành phần cốt lõi nào?** | Trích dẫn: **Section 1: Giới thiệu kiến trúc RAG** (Trang 1) | **1đ** |
| 2 | **Embedding Model đóng vai trò gì trong kiến trúc RAG?** | Trích dẫn: **Section 2: Vai trò của Embedding Model** (Trang 1) | **1đ** |
| 3 | **Vector Store lưu trữ trên RAM có ưu điểm và nhược điểm gì?** | Trích dẫn: **Section 3: Vector Store trên bộ nhớ RAM** (Trang 1) | **1đ** |
| 4 | **Section 4 đề cập đến quy trình truy vấn (Query Pipeline) như thế nào?** | Trích dẫn: **Section 4: Quy trình truy vấn (Query Pipeline)** (Trang 1) | **1đ** |
| 5 | **Làm sao để hệ thống AI trích dẫn chính xác section nguồn khi trả lời?** | Trích dẫn: **Section 5: Cơ chế trích dẫn Section và Nguồn tài liệu** (Trang 1) | **1đ** |

---

## 4. DANH SÁCH REST API (BACKEND)

- `POST /api/documents/upload`: Nhận file `multipart/form-data`, phân tích tài liệu và lưu vector vào RAM.
- `POST /api/chat`: Nhận body `{ "question": "...", "apiKey": "..." }`, thực hiện vector search trên RAM và sinh câu trả lời kèm thông tin trích dẫn Section.
- `GET /api/documents/status`: Lấy số lượng tài liệu và tổng số vector hiện đang lưu trong RAM.
- `DELETE /api/documents`: Xóa sạch bộ nhớ RAM để thử nghiệm tài liệu mới.
