module com.example.group7fileflixserver {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;

    opens com.example.group7fileflixserver to javafx.fxml;
    exports com.example.group7fileflixserver;
}