package org.example.nhandienxeuutien;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.AudioClip;
import javafx.stage.FileChooser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Controller {

    @FXML private Label statusLabel;
    @FXML private TextArea resultsArea;
    @FXML private ImageView webcamView;
    @FXML private Button btnDetectImage;
    @FXML private Button btnCallWebcamAPI;

    // Audio Configuration
    private final Map<String, AudioClip> alertSounds = new HashMap<>();
    private AudioClip currentAudio;
    private String lastDetectedVehicle;
    private boolean audioEnabled = true;

    // API Configuration
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private boolean apiErrorShown = false;
    private volatile boolean isApiCameraActive = false;
    private Thread apiStreamThread = null;
    private String apiUrl = "http://localhost:8000";
    private String videoFrameEndpoint = "/video_frame";
    private boolean useFallbackEndpoint = false;
    private ExecutorService executorService;
    private AtomicBoolean isStreaming = new AtomicBoolean(false);
    private long lastUpdateTime = 0;
    private static final double FRAME_RATE = 10.0; // 10 FPS
    private static final int MAX_ERRORS = 10; // Ngưỡng lỗi

    @FXML
    public void initialize() {
        // Debug thêm đường dẫn
        appendResult("Kiểm tra các đường dẫn resource:");
        String[] pathsToCheck = {
                "/sounds/ambulance.wav",
                "sounds/ambulance.wav",
                "/org/example/nhandienxeuutien/sounds/ambulance.wav",
                "org/example/nhandienxeuutien/sounds/ambulance.wav"
        };

        for (String path : pathsToCheck) {
            URL url = getClass().getResource(path);
            appendResult("Đường dẫn '" + path + "': " + (url != null ? "TỒN TẠI" : "KHÔNG TỒN TẠI"));
        }

        setupAlertSounds();
        executorService = Executors.newFixedThreadPool(2);

        // Check API connection at startup
        new Thread(() -> {
            if (checkApiConnection()) {
                appendResult("Đã kết nối thành công với API server");
            } else {
                appendResult("Không thể kết nối với API server. Kiểm tra lại cài đặt hoặc khởi động server.");
            }
        }).start();
    }

    private void setupAlertSounds() {
        try {
            appendResult("Đang tải âm thanh cảnh báo...");
            boolean anySoundLoaded = false;

            // Thử nhiều cách đường dẫn khác nhau
            String[] ambulancePaths = {"/sounds/ambulance.wav", "sounds/ambulance.wav",
                    "/org/example/nhandienxeuutien/sounds/ambulance.wav"};
            String[] firePaths = {"/sounds/fire.wav", "sounds/fire.wav",
                    "/org/example/nhandienxeuutien/sounds/fire.wav"};
            String[] policePaths = {"/sounds/police.wav", "sounds/police.wav",
                    "/org/example/nhandienxeuutien/sounds/police.wav"};

            // Thử tải âm thanh từ nhiều đường dẫn
            if (loadSoundFromPaths("ambulance", ambulancePaths)) anySoundLoaded = true;
            if (loadSoundFromPaths("fire", firePaths)) anySoundLoaded = true;
            if (loadSoundFromPaths("police", policePaths)) anySoundLoaded = true;

            // Thử tải từ file system nếu không tìm được trong resources
            if (!anySoundLoaded) {
                appendResult("Thử tải âm thanh từ thư mục hiện tại...");

                // Thử tìm file trong thư mục sounds bên ngoài jar
                String baseDir = new File("").getAbsolutePath();
                String soundsDir = baseDir + File.separator + "sounds";
                File soundsDirFile = new File(soundsDir);
                if (!soundsDirFile.exists()) {
                    soundsDirFile.mkdir();
                    appendResult("Đã tạo thư mục: " + soundsDir);
                }

                if (addSoundFromFileSystem("ambulance", soundsDir + File.separator + "ambulance.wav")) anySoundLoaded = true;
                if (addSoundFromFileSystem("fire", soundsDir + File.separator + "fire.wav")) anySoundLoaded = true;
                if (addSoundFromFileSystem("police", soundsDir + File.separator + "police.wav")) anySoundLoaded = true;
            }

            if (!anySoundLoaded) {
                appendResult("Không tìm thấy bất kỳ file âm thanh nào. Tính năng cảnh báo âm thanh bị vô hiệu hóa.");
                audioEnabled = false;
            } else {
                appendResult("Tải âm thanh hoàn tất");
            }
        } catch (Exception e) {
            appendResult("Lỗi tải âm thanh: " + e.getMessage());
            audioEnabled = false;
        }
    }

    private boolean loadSoundFromPaths(String key, String[] paths) {
        for (String path : paths) {
            if (addSoundIfExists(key, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean addSoundFromFileSystem(String key, String filePath) {
        try {
            File soundFile = new File(filePath);
            if (soundFile.exists()) {
                AudioClip clip = new AudioClip(soundFile.toURI().toString());
                alertSounds.put(key.toLowerCase(), clip);
                appendResult("Đã tải âm thanh từ file system: " + key + " - " + filePath);
                return true;
            } else {
                appendResult("Không tìm thấy file âm thanh: " + filePath);
                return false;
            }
        } catch (Exception e) {
            appendResult("Lỗi tải âm thanh " + key + " từ file system: " + e.getMessage());
            return false;
        }
    }

    private boolean addSoundIfExists(String key, String resourcePath) {
        try {
            URL resourceUrl = getClass().getResource(resourcePath);
            if (resourceUrl != null) {
                AudioClip clip = new AudioClip(resourceUrl.toString());
                alertSounds.put(key.toLowerCase(), clip);
                appendResult("Đã tải âm thanh: " + key + " từ đường dẫn: " + resourcePath);
                return true;
            } else {
                // Chỉ ghi log khi debug chi tiết
                //appendResult("Không tìm thấy file âm thanh: " + resourcePath);
                return false;
            }
        } catch (Exception e) {
            appendResult("Lỗi tải âm thanh " + key + ": " + e.getMessage());
            return false;
        }
    }

    // Button Handlers
    @FXML
    private void handleImageDetection() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png")
        );
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            processImage(selectedFile);
        }
    }

    @FXML
    private void handleCallWebcamAPI() {
        if (isApiCameraActive) {
            stopApiCamera();
        } else {
            new Thread(() -> {
                appendResult("Đang kết nối với camera từ API server...");
                streamWebcamFromAPI();
            }).start();
        }
    }

    // Image Processing
    private void processImage(File imageFile) {
        new Thread(() -> {
            try {
                byte[] imageData = Files.readAllBytes(imageFile.toPath());
                detectVehicles(imageData);
            } catch (Exception e) {
                appendResult("Lỗi đọc file ảnh: " + e.getMessage());
            }
        }).start();
    }

    private void detectVehicles(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            appendResult("Lỗi: Dữ liệu hình ảnh trống");
            return;
        }

        if (!checkApiConnection()) {
            return;
        }

        try {
            // Tạo boundary cho multipart request
            String boundary = "----JavaFormBoundary" + System.currentTimeMillis();

            // Tạo nội dung multipart
            byte[] requestBody = createMultipartRequestBody(imageData, boundary);

            // Tạo request với multipart/form-data
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/detect/image"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject result = new JSONObject(response.body());
                handleDetectionResult(result);
                saveDetectedImage(imageData, result);
            } else {
                appendResult("Lỗi API /detect/image: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            appendResult("Lỗi kết nối API /detect/image: " + e.getMessage());
        }
    }

    // Phương thức tạo dữ liệu multipart
    private byte[] createMultipartRequestBody(byte[] imageData, String boundary) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Bắt đầu với boundary
        baos.write(("--" + boundary + "\r\n").getBytes());
        // Thêm thông tin file
        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"image.jpg\"\r\n").getBytes());
        baos.write(("Content-Type: image/jpeg\r\n\r\n").getBytes());
        // Thêm dữ liệu file
        baos.write(imageData);
        baos.write(("\r\n--" + boundary + "--\r\n").getBytes());
        return baos.toByteArray();
    }

    private boolean checkApiConnection() {
        try {
            HttpRequest pingRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/"))
                    .timeout(Duration.ofMillis(500))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(pingRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (apiErrorShown) {
                    appendResult("Đã kết nối lại với server API thành công");
                    apiErrorShown = false;
                }
                return true;
            }
            if (!apiErrorShown) {
                appendResult("Không thể kết nối với server API. Đảm bảo server đang chạy tại " + apiUrl);
                appendResult("Hãy chạy lệnh: python -m uvicorn main:app --host 0.0.0.0 --port 8000");
                apiErrorShown = true;
            }
            return false;
        } catch (Exception e) {
            if (!apiErrorShown) {
                appendResult("Không thể kết nối với server API: " + e.getMessage());
                appendResult("Hãy chạy lệnh: python -m uvicorn main:app --host 0.0.0.0 --port 8000");
                apiErrorShown = true;
            }
            return false;
        }
    }

    // Result Handling
    private void handleDetectionResult(JSONObject result) {
        try {
            JSONArray detections = result.getJSONArray("detections");
            StringBuilder output = new StringBuilder();
            boolean priorityFound = false;
            String currentVehicle = null;

            for (int i = 0; i < detections.length(); i++) {
                JSONObject detection = detections.getJSONObject(i);
                String label = detection.getString("label");
                double confidence = detection.getDouble("confidence");

                if (isPriorityVehicle(label)) {
                    priorityFound = true;
                    currentVehicle = label.toLowerCase();
                    output.append(String.format("Phát hiện xe ưu tiên: %s (%.2f%%)\n", label, confidence * 100));
                }
            }

            if (!priorityFound) {
                output.append("Không phát hiện xe ưu tiên\n");
            }

            appendResult(output.toString());
            handlePriorityAlert(currentVehicle, priorityFound);
        } catch (Exception e) {
            appendResult("Lỗi xử lý kết quả phát hiện: " + e.getMessage());
        }
    }

    // Thêm các biến mới để theo dõi thời gian phát âm thanh
    private long lastAudioStartTime = 0;
    private static final long MIN_AUDIO_DURATION = 1500; // 1.5 giây tính bằng mili giây

    // Sửa lại phương thức handlePriorityAlert
    private void handlePriorityAlert(String vehicleType, boolean isPriority) {
        if (!audioEnabled) return;

        // Debug thêm để xem âm thanh
        if (isPriority && vehicleType != null) {
            appendResult("Chuẩn bị phát âm thanh cho loại xe: " + vehicleType);
            appendResult("Có sẵn trong map: " + alertSounds.containsKey(vehicleType));
        }

        Platform.runLater(() -> {
            long currentTime = System.currentTimeMillis();

            // Kiểm tra xem âm thanh hiện tại đã phát đủ thời gian tối thiểu chưa
            if (currentAudio != null && currentAudio.isPlaying() &&
                    (currentTime - lastAudioStartTime < MIN_AUDIO_DURATION)) {
                // Nếu chưa đủ thời gian tối thiểu, không dừng âm thanh hiện tại
                appendResult("Âm thanh đang phát, chờ hoàn thành (" +
                        (currentTime - lastAudioStartTime) + "/" + MIN_AUDIO_DURATION + "ms)");
                return;
            }

            // Dừng âm thanh hiện tại nếu đang phát và đã vượt quá thời gian tối thiểu
            if (currentAudio != null && currentAudio.isPlaying()) {
                currentAudio.stop();
            }

            if (isPriority && vehicleType != null) {
                // Thử tìm với nhiều chuỗi khác nhau
                AudioClip clip = alertSounds.get(vehicleType);
                if (clip == null) {
                    // Thử tìm bằng các tên thông dụng nếu không tìm được chính xác
                    if (vehicleType.contains("ambulance") || vehicleType.contains("cứu thương")) {
                        clip = alertSounds.get("ambulance");
                    } else if (vehicleType.contains("fire") || vehicleType.contains("cứu hỏa")) {
                        clip = alertSounds.get("fire");
                    } else if (vehicleType.contains("police") || vehicleType.contains("cảnh sát")) {
                        clip = alertSounds.get("police");
                    }
                }

                if (clip != null) {
                    // Thử phát âm thanh test
                    try {
                        appendResult("Đang phát âm thanh: " + vehicleType);
                        clip.play();
                        currentAudio = clip;
                        lastDetectedVehicle = vehicleType;
                        lastAudioStartTime = System.currentTimeMillis(); // Ghi lại thời điểm bắt đầu phát
                    } catch (Exception e) {
                        appendResult("Lỗi phát âm thanh: " + e.getMessage());
                    }
                } else {
                    appendResult("Không tìm thấy âm thanh cho loại xe: " + vehicleType);
                }
            }
        });
    }

    // Hàm test phát âm thanh
    private void testAudioPlay() {
        if (!alertSounds.isEmpty()) {
            try {
                String firstKey = alertSounds.keySet().iterator().next();
                AudioClip clip = alertSounds.get(firstKey);
                clip.play();
                appendResult("Đang phát âm thanh test: " + firstKey);
            } catch (Exception e) {
                appendResult("Lỗi phát âm thanh test: " + e.getMessage());
            }
        } else {
            appendResult("Không có âm thanh nào để test");
        }
    }

    // Utility Methods
    private boolean isPriorityVehicle(String label) {
        String lowerLabel = label.toLowerCase();
        return lowerLabel.contains("ambulance") ||
                lowerLabel.contains("fire") ||
                lowerLabel.contains("police") ||
                lowerLabel.contains("cứu thương") ||
                lowerLabel.contains("cứu hỏa") ||
                lowerLabel.contains("cảnh sát");
    }

    private void appendResult(String text) {
        Platform.runLater(() -> {
            resultsArea.appendText(text + "\n");
            System.out.println("[LOG] " + text);
        });
    }

    private void saveDetectedImage(byte[] imageData, JSONObject detectionResult) {
        try {
            File outputDir = new File("detected_images");
            if (!outputDir.exists()) {
                outputDir.mkdir();
            }

            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String vehicleType = "unknown";
            boolean hasDetection = false;

            if (detectionResult != null && detectionResult.has("detections")) {
                JSONArray detections = detectionResult.getJSONArray("detections");
                for (int i = 0; i < detections.length(); i++) {
                    JSONObject detection = detections.getJSONObject(i);
                    if (isPriorityVehicle(detection.getString("label"))) {
                        vehicleType = detection.getString("label").toLowerCase();
                        hasDetection = true;
                        break;
                    }
                }
            }

            if (hasDetection) {
                String filename = String.format("detected_%s_%s.jpg", vehicleType, timestamp);
                File outputFile = new File(outputDir, filename);
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(imageData);
                }
                appendResult("Đã lưu ảnh phát hiện: " + outputFile.getAbsolutePath());
            }
        } catch (Exception e) {
            appendResult("Lỗi lưu ảnh: " + e.getMessage());
        }
    }

    private void streamWebcamFromAPI() {
        if (!checkApiConnection()) {
            appendResult("Không thể kết nối với API server.");
            Platform.runLater(() -> {
                resetApiCameraButton();
                statusLabel.setText("Lỗi: Không thể kết nối với API");
            });
            return;
        }

        isApiCameraActive = true;
        useFallbackEndpoint = false;
        Platform.runLater(() -> {
            btnCallWebcamAPI.setText("⏹️ Dừng API Camera");
            btnCallWebcamAPI.setStyle("-fx-font-size: 14px; -fx-background-color: #f44336; -fx-text-fill: white;");
            statusLabel.setText("Đang kết nối camera API...");
        });

        apiStreamThread = new Thread(() -> {
            int errorCount = 0;
            long lastFrameTime = System.currentTimeMillis();
            boolean endpointNotFoundLogged = false;

            while (isApiCameraActive) {
                try {
                    if (!getFrameFromAPI()) {
                        errorCount++;
                        if (!useFallbackEndpoint && errorCount == 5) {
                            appendResult("Endpoint /video_frame không khả dụng, thử /detect/webcam...");
                            useFallbackEndpoint = true;
                        }
                        appendResult("Không nhận được frame từ API (" + errorCount + ")");
                        if (errorCount >= MAX_ERRORS) {
                            appendResult("Quá nhiều lỗi, dừng luồng camera API...");
                            break;
                        }
                        Thread.sleep(500);
                    } else {
                        errorCount = 0;
                        endpointNotFoundLogged = false;
                    }

                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - lastFrameTime;
                    long sleepTime = Math.max(0, (long)(1000.0 / FRAME_RATE - elapsed));
                    Thread.sleep(sleepTime);
                    lastFrameTime = System.currentTimeMillis();
                } catch (InterruptedException e) {
                    appendResult("Luồng camera API bị gián đoạn");
                    break;
                } catch (Exception e) {
                    appendResult("Lỗi không xác định trong luồng API: " + e.getMessage());
                    errorCount++;
                    if (errorCount >= MAX_ERRORS) {
                        break;
                    }
                }
            }

            stopApiCamera();
        });

        apiStreamThread.setDaemon(true);
        apiStreamThread.start();
    }

    private boolean getFrameFromAPI() {
        try {
            String endpoint = useFallbackEndpoint ? "/detect/webcam?duration=1" : videoFrameEndpoint;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + endpoint))
                    .header("Accept", useFallbackEndpoint ? "application/json" : "image/jpeg")
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();

            if (useFallbackEndpoint) {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JSONObject result = new JSONObject(response.body());
                    handleDetectionResult(result);
                    // Tạo ảnh giả lập nếu không có frame trực tiếp
                    byte[] sampleImage = createSampleImage(result);
                    if (sampleImage != null) {
                        updateImageView(sampleImage);
                    }
                    return true;
                } else {
                    appendResult("Lỗi API " + endpoint + ": HTTP " + response.statusCode());
                    return false;
                }
            } else {
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    updateImageView(response.body());
                    if (Math.random() < 0.1) {
                        detectVehicles(response.body());
                    }
                    return true;
                } else {
                    if (response.statusCode() == 404 && Math.random() < 0.1) {
                        appendResult("Lỗi API " + endpoint + ": HTTP 404 - Endpoint không tồn tại");
                    }
                    return false;
                }
            }
        } catch (Exception e) {
            appendResult("Lỗi kết nối API " + (useFallbackEndpoint ? "/detect/webcam" : videoFrameEndpoint) + ": " + e.getMessage());
            return false;
        }
    }

    private byte[] createSampleImage(JSONObject result) {
        try {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    640, 480, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = image.createGraphics();
            g.setColor(java.awt.Color.LIGHT_GRAY);
            g.fillRect(0, 0, 640, 480);
            g.setColor(java.awt.Color.GREEN);
            g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
            g.drawString(java.time.LocalDateTime.now().toString(), 10, 20);

            if (result.has("detections")) {
                JSONArray detections = result.getJSONArray("detections");
                for (int i = 0; i < detections.length(); i++) {
                    JSONObject detection = detections.getJSONObject(i);
                    String label = detection.getString("label");
                    double confidence = detection.getDouble("confidence");
                    int x = 50 + i * 100;
                    int y = 100 + i * 50;
                    int width = 80;
                    int height = 40;

                    if (isPriorityVehicle(label)) {
                        g.setColor(java.awt.Color.RED);
                    } else {
                        g.setColor(java.awt.Color.BLUE);
                    }
                    g.drawRect(x, y, width, height);
                    g.drawString(label + " " + String.format("%.2f", confidence), x, y - 5);
                }
            }

            g.dispose();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            appendResult("Lỗi tạo ảnh giả lập: " + e.getMessage());
            return null;
        }
    }

    private void updateImageView(byte[] imageData) {
        if (imageData == null || imageData.length == 0) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < 1000.0 / FRAME_RATE) return;

        Platform.runLater(() -> {
            try {
                Image image = new Image(new ByteArrayInputStream(imageData));
                webcamView.setImage(image);
                lastUpdateTime = currentTime;
                statusLabel.setText("Đang hiển thị camera API");
            } catch (Exception e) {
                appendResult("Lỗi hiển thị hình ảnh từ API: " + e.getMessage());
            }
        });
    }

    private void stopApiCamera() {
        isApiCameraActive = false;
        appendResult("Đã dừng camera từ API");

        if (apiStreamThread != null && apiStreamThread.isAlive()) {
            apiStreamThread.interrupt();
        }

        Platform.runLater(() -> {
            resetApiCameraButton();
            webcamView.setImage(null);
            statusLabel.setText("Sẵn sàng");
        });
    }

    private void resetApiCameraButton() {
        btnCallWebcamAPI.setText("📹 Server Webcam API");
        btnCallWebcamAPI.setStyle("-fx-font-size: 14px; -fx-background-color: #9C27B0; -fx-text-fill: white;");
    }
}