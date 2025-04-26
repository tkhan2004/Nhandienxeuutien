
# 🚨 Priority Vehicle Detection System (Nhận diện Xe Ưu Tiên)

Ứng dụng này giúp nhận diện **xe ưu tiên** như xe cứu thương, cứu hỏa, công an từ **ảnh hoặc webcam**. Hệ thống bao gồm:

- 🎯 **Backend**: Flask API viết bằng Python sử dụng mô hình YOLOv8 tùy chỉnh
- 💻 **Frontend GUI**: JavaFX app hỗ trợ tải ảnh hoặc phát hiện trực tiếp từ webcam
- 📦 Phát hiện theo thời gian thực và hiển thị kết quả với bounding box + nhãn

---

## ✅ Yêu cầu hệ thống

### 🐍 Python (cho backend)

- Python 3.8+
- Ultralytics YOLOv8
- Flask
- OpenCV-Python

### ☕ Java (cho frontend)

- Java JDK 17
- Maven
- JavaFX SDK 22
- OpenCV 4.9 (Java native lib)
- JavaCV (camera support)

---

## ⚙️ Cài đặt và chạy ứng dụng

### 1. Clone repo

```bash
git clone https://github.com/tkhan2004/Nhandienxeuutien.git
cd Nhandienxeuutien
```
```bash
https://github.com/tkhan2004/BackendNhandienxeuutien.git
```
---

### 2. Thiết lập môi trường backend (Python)

```bash
cd backend-api
pip install -r requirements.txt
```

> File `requirements.txt` bao gồm:
```
flask
opencv-python
ultralytics
```

👉 Khởi chạy server Flask:

```bash
python main.py
```

> Mặc định chạy tại: `http://127.0.0.1:5000` hoặc tuỳ set up thiết bị.

---

### 3. Cài đặt frontend JavaFX GUI

```bash
cd javafx-gui
mvn clean install
```

> Nếu bạn gặp lỗi thiếu thư viện native OpenCV, cần thêm dòng này vào phương thức `main()` trước khi khởi tạo JavaFX:

```java
System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
```

👉 Chạy ứng dụng:

```bash
mvn javafx:run
```

---

## 🧪 Kiểm thử API thủ công (Tùy chọn)

Bạn có thể thử nghiệm API với `curl` hoặc Postman:

```bash
curl -X POST http://localhost:5000/detect \
  -F file=@sample.jpg
```

Kết quả trả về dạng:

```json
{
  "detections": [
    {"label": "ambulance", "confidence": 0.95},
    {"label": "police", "confidence": 0.88}
  ]
}
```

---

## 🧠 Công nghệ sử dụng

- [YOLOv8](https://github.com/ultralytics/ultralytics)
- Flask (REST API)
- JavaFX
- OpenCV
- JavaCV
- FXML

---

## 📸 Giao diện JavaFX

- Nút "Mở webcam" hoặc "Chọn ảnh"
- Hiển thị ảnh đầu vào với khung và nhãn xe ưu tiên
- Âm thanh cảnh báo khi phát hiện xe ưu tiên
- Kết nối đến API Flask và xử lý bất đồng bộ

---

## 🛠 Ghi chú thêm

- Đảm bảo mô hình YOLOv8 (`best.pt`) đã được huấn luyện phù hợp với dữ liệu xe ưu tiên Việt Nam và đặt đúng vị trí nếu `detect.py` yêu cầu.
- Kiểm tra kỹ `System.loadLibrary("opencv_java490")` nếu lỗi `UnsatisfiedLinkError`.

---

## 🧑‍💻 Tác giả

- Khang Nguyễn (JavaFX GUI)
  
---
