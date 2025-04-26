# Nhận diện xe ưu tiên

Ứng dụng nhận diện phương tiện ưu tiên (xe cứu thương, xe cảnh sát, xe cứu hỏa) sử dụng camera.

## Cấu trúc dự án

- **JavaFX Client**: Giao diện người dùng xây dựng bằng JavaFX để hiển thị camera và kết quả
- **Python FastAPI Server**: Backend AI xử lý dữ liệu hình ảnh và nhận diện phương tiện ưu tiên

## Yêu cầu hệ thống

- Java 17 hoặc cao hơn
- Python 3.8 hoặc cao hơn
- Camera (webcam) hoạt động trên thiết bị
- Maven (hoặc sử dụng mvnw đi kèm)

## Hướng dẫn cài đặt

### 1. Python FastAPI Server

1. Cài đặt các thư viện Python cần thiết:
```bash
pip install fastapi uvicorn python-multipart opencv-python shutil numpy
```

2. Tạo file `main.py` với nội dung sau:
```python
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
import shutil
import os
from detect import load_models, detect_from_image, detect_from_webcam

app = FastAPI(title="Priority Vehicle Detection API")

# Load models at startup
try:
    custom_model, yolo_model = load_models()
except Exception as e:
    print(f"Failed to load models: {e}")
    raise Exception("Model loading failed")

@app.post("/detect/image")
async def detect_image(file: UploadFile = File(...)):
    """
    Detect priority vehicles in an uploaded image.
    Returns a JSON list of detections.
    """
    try:
        # Save the uploaded file temporarily
        temp_file_path = f"temp_{file.filename}"
        with open(temp_file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)

        # Run detection
        detections = detect_from_image(temp_file_path, custom_model, yolo_model)

        # Clean up temporary file
        os.remove(temp_file_path)

        # Return detection results as JSON
        return JSONResponse(content={"detections": detections})
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error processing image: {str(e)}")

@app.get("/detect/webcam")
async def detect_webcam(duration: int = 5):
    """
    Detect priority vehicles in a short webcam capture.
    Duration parameter specifies how many seconds to capture (default: 5).
    Returns a JSON list of detections.
    """
    try:
        detections = detect_from_webcam(custom_model, yolo_model, duration)
        return JSONResponse(content={"detections": detections})
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error processing webcam: {str(e)}")

@app.get("/")
async def root():
    return {"status": "ok", "message": "Priority Vehicle Detection API is running"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

3. Chạy server FastAPI:
```bash
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

### 2. Java Client

1. Biên dịch và chạy ứng dụng Java:
```bash
./mvnw clean javafx:run
```

## Cách sử dụng

1. Khởi động Python FastAPI server trước
2. Khởi động ứng dụng JavaFX
3. Sử dụng các nút trên giao diện để:
   - **Nhận diện từ Ảnh**: Tải lên hình ảnh để phân tích
   - **Bật Webcam**: Sử dụng webcam trên máy tính để nhận diện trực tiếp
   - **Server Webcam API**: Sử dụng webcam từ server Python để nhận diện

## Xử lý sự cố

- **Camera không hoạt động**: Kiểm tra quyền truy cập camera trong hệ thống
- **Không kết nối được API Server**: Đảm bảo server Python đang chạy tại http://localhost:8000
- **Lỗi "Cannot invoke..."**: Kiểm tra file âm thanh trong thư mục resources

## Ghi chú

- Ứng dụng sẽ tự động lưu hình ảnh phát hiện xe ưu tiên vào thư mục "detected_images"
- Cảnh báo AVCaptureDevice là bình thường và không ảnh hưởng đến hoạt động của ứng dụng 