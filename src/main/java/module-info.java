module org.example.chatflix {
    requires javafx.controls;
    requires javafx.fxml;

    requires java.sql;
    requires org.xerial.sqlitejdbc;

    opens org.example.chatflix to javafx.fxml;
    exports org.example.chatflix.client;
    exports org.example.chatflix.server;
    // exports org.example.chatflix.model;
    opens org.example.chatflix.server to javafx.fxml;
    opens org.example.chatflix.client to javafx.fxml;
}