module org.example.nhandienxeuutien {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires org.json;
    requires javafx.media;
    requires org.bytedeco.javacv;
    requires org.bytedeco.opencv;
    requires java.desktop;
    opens org.example.nhandienxeuutien to javafx.fxml;
    exports org.example.nhandienxeuutien;
}