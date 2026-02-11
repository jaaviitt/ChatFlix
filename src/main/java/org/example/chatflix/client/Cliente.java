package org.example.chatflix.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Cliente extends Application {

    private Stage escenarioPrincipal;

    // VARIABLES GLOBALES DE CONEXIÓN (Para usarlas en el chat)
    private Socket socket;
    private DataOutputStream salida;
    private DataInputStream entrada;
    private String nombreUsuario; // Guardamos quiénes somos

    // Componentes del Chat (Para poder modificarlos luego)
    private TextArea areaMensajes;
    private TextField campoMensaje;
    private ListView<String> listaUsuarios;

    @Override
    public void start(Stage stage) {
        this.escenarioPrincipal = stage;
        mostrarVentanaLogin();
    }

    // --- PANTALLA 1: LOGIN ---
    private void mostrarVentanaLogin() {
        Label lblTitulo = new Label("Bienvenido a ChatFlix");
        lblTitulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextField txtUsuario = new TextField();
        txtUsuario.setPromptText("Usuario");

        PasswordField txtPassword = new PasswordField();
        txtPassword.setPromptText("Contraseña");

        Button btnEntrar = new Button("Entrar / Registrarse");
        Label lblEstado = new Label("");

        btnEntrar.setOnAction(e -> {
            String usuario = txtUsuario.getText();
            String pass = txtPassword.getText();

            if (usuario.isEmpty() || pass.isEmpty()) {
                lblEstado.setText("Rellena todos los campos");
                lblEstado.setStyle("-fx-text-fill: red;");
            } else {
                conectarYEntrar(usuario, pass, lblEstado);
            }
        });

        VBox layout = new VBox(15, lblTitulo, txtUsuario, txtPassword, btnEntrar, lblEstado);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);

        Scene scene = new Scene(layout, 300, 250);
        escenarioPrincipal.setTitle("Login ChatFlix");
        escenarioPrincipal.setScene(scene);
        escenarioPrincipal.show();
    }

    // --- PANTALLA 2: CHAT PRINCIPAL (NUEVO) ---
    private void mostrarVentanaChat() {
        // 1. Panel Central (Historial de mensajes)
        areaMensajes = new TextArea();
        areaMensajes.setEditable(false); // Que no se pueda borrar lo escrito
        areaMensajes.setWrapText(true);

        // 2. Panel Inferior (Escribir mensaje)
        campoMensaje = new TextField();
        campoMensaje.setPromptText("Escribe un mensaje...");
        // Hacemos que el campo ocupe todo el ancho posible
        campoMensaje.setPrefWidth(300);

        Button btnEnviar = new Button("Enviar");
        // Acción al pulsar enviar (Por ahora solo visual)
        btnEnviar.setOnAction(e -> enviarMensaje());

        HBox panelInferior = new HBox(10, campoMensaje, btnEnviar);
        panelInferior.setPadding(new Insets(10));
        panelInferior.setAlignment(Pos.CENTER);

        // 3. Panel Izquierdo (Lista de Contactos)
        listaUsuarios = new ListView<>();
        listaUsuarios.getItems().add("Chat General");
        listaUsuarios.setPrefWidth(120);

        // 4. Montaje final (BorderPane es ideal para esto)
        BorderPane root = new BorderPane();
        root.setCenter(areaMensajes);
        root.setBottom(panelInferior);
        root.setLeft(listaUsuarios);

        Scene scene = new Scene(root, 600, 400);
        escenarioPrincipal.setTitle("ChatFlix - Usuario: " + nombreUsuario);
        escenarioPrincipal.setScene(scene);
        // Centrar la ventana en la pantalla
        escenarioPrincipal.centerOnScreen();
    }

    // --- LÓGICA DE CONEXIÓN ---
    private void conectarYEntrar(String user, String pass, Label lblEstado) {
        lblEstado.setText("Conectando...");
        lblEstado.setStyle("-fx-text-fill: black;");

        new Thread(() -> {
            try {
                // Guardamos la conexión en las variables globales
                socket = new Socket("localhost", 12345);
                salida = new DataOutputStream(socket.getOutputStream());
                entrada = new DataInputStream(socket.getInputStream());

                // Enviar Login
                salida.writeUTF("LOGIN|" + user + "|" + pass);

                // Esperar respuesta
                String respuesta = entrada.readUTF();

                Platform.runLater(() -> {
                    if (respuesta.startsWith("LOGIN_OK")) {
                        // ¡ÉXITO! Guardamos el nombre y cambiamos de pantalla
                        this.nombreUsuario = user;
                        mostrarVentanaChat(); // <--- AQUÍ OCURRE LA MAGIA

                        new Thread(() -> {
                            try {
                                while (true) {
                                    String mensajeServer = entrada.readUTF();

                                    // CASO 1: MENSAJE DE CHAT
                                    if (mensajeServer.startsWith("MSG|")) {
                                        String texto = mensajeServer.substring(4);
                                        Platform.runLater(() -> areaMensajes.appendText(texto + "\n"));
                                    }

                                    // CASO 2: ALGUIEN CAMBIA DE ESTADO (ENTRA/SALE)
                                    else if (mensajeServer.startsWith("STATUS|")) {
                                        String[] partes = mensajeServer.split("\\|");
                                        String nombre = partes[1];
                                        String estado = partes[2]; // ON o OFF

                                        Platform.runLater(() -> {
                                            if (estado.equals("ON")) {
                                                if (!listaUsuarios.getItems().contains(nombre)) {
                                                    listaUsuarios.getItems().add(nombre);
                                                    areaMensajes.appendText(">>> " + nombre + " se ha conectado.\n");
                                                }
                                            } else {
                                                listaUsuarios.getItems().remove(nombre);
                                                areaMensajes.appendText("<<< " + nombre + " se ha desconectado.\n");
                                            }
                                        });
                                    }

                                    // CASO 3: RECIBIR LISTA INICIAL
                                    else if (mensajeServer.startsWith("LISTA_USUARIOS|")) {
                                        String lista = mensajeServer.substring(15); // Quitamos la cabecera
                                        String[] nombres = lista.split(",");

                                        Platform.runLater(() -> {
                                            for (String nombre : nombres) {
                                                if (!listaUsuarios.getItems().contains(nombre)) {
                                                    listaUsuarios.getItems().add(nombre);
                                                }
                                            }
                                        });
                                    }
                                }
                            } catch (IOException e) {
                                Platform.runLater(() -> areaMensajes.appendText("Desconectado del servidor.\n"));
                            }
                        }).start();

                    } else {
                        lblEstado.setText("Credenciales incorrectas");
                        lblEstado.setStyle("-fx-text-fill: red;");
                        cerrarConexion();
                    }
                });

            } catch (IOException ex) {
                Platform.runLater(() -> {
                    lblEstado.setText("Error: Servidor no responde");
                    lblEstado.setStyle("-fx-text-fill: red;");
                });
            }
        }).start();
    }

    private void enviarMensaje() {
        String texto = campoMensaje.getText();
        if (!texto.isEmpty()) {
            try {
                salida.writeUTF("MSG|" + texto);
                campoMensaje.clear();
            } catch (IOException e) {
                areaMensajes.appendText("Error al enviar mensaje.\n");
            }
        }
    }

    private void cerrarConexion() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void stop() throws Exception {
        cerrarConexion(); // Cerrar socket al cerrar la ventana
        super.stop();
    }

    public static void main(String[] args) {
        launch();
    }
}