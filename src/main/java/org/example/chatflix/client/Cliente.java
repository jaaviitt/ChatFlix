package org.example.chatflix.client;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.Socket;

public class Cliente extends Application {

    @Override
    public void start(Stage stage) {
        String mensajeEstado;

        // Intentamos conectar al servidor (localhost:12345)
        try {
            Socket socket = new Socket("localhost", 12345);
            mensajeEstado = "¡Conectado al servidor con éxito!";
            // OJO: Aquí es donde más adelante escucharemos mensajes

        } catch (IOException e) {
            mensajeEstado = "Error: No se pudo conectar al servidor.\n" + e.getMessage();
        }

        // Interfaz visual simple
        Label label = new Label(mensajeEstado);
        StackPane root = new StackPane(label);
        Scene scene = new Scene(root, 400, 300);

        stage.setTitle("Cliente Chat PSP");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}