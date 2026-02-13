package org.example.chatflix.client;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class VentanaLogin {

    private Cliente clienteLogica;
    private Stage stage;

    public VentanaLogin(Stage stage, Cliente clienteLogica) {
        this.stage = stage;
        this.clienteLogica = clienteLogica;
    }

    public void mostrar() {
        Label lblTitulo = new Label("Bienvenido a ChatFlix");
        lblTitulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextField txtUsuario = new TextField();
        txtUsuario.setPromptText("Usuario");

        PasswordField txtPassword = new PasswordField();
        txtPassword.setPromptText("Contraseña");

        Button btnEntrar = new Button("Entrar / Registrarse");
        Label lblEstado = new Label("");

        btnEntrar.setOnAction(e -> {
            String u = txtUsuario.getText();
            String p = txtPassword.getText();
            if (u.isEmpty() || p.isEmpty()) {
                lblEstado.setText("Rellena todo");
                lblEstado.setStyle("-fx-text-fill: red;");
            } else {
                // Llamamos a la lógica del Cliente principal
                clienteLogica.conectarYEntrar(u, p, lblEstado);
            }
        });

        VBox layout = new VBox(15, lblTitulo, txtUsuario, txtPassword, btnEntrar, lblEstado);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        stage.setScene(new Scene(layout, 300, 250));
        stage.setTitle("Login ChatFlix");
        stage.show();
    }
}