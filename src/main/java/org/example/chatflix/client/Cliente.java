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
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.*;

public class Cliente extends Application {

    private Stage escenarioPrincipal;

    // VARIABLES GLOBALES DE CONEXIÓN
    private Socket socket;
    private DataOutputStream salida;
    private DataInputStream entrada;
    private String nombreUsuario; // Aquí guardamos tu nombre

    // COMPONENTES DE LA NUEVA INTERFAZ (Burbujas)
    private ScrollPane scrollMensajes;   // La ventana con scroll
    private VBox contenedorMensajes;     // La caja donde se apilan los mensajes

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

    // --- PANTALLA 2: CHAT PRINCIPAL (MODIFICADO PARA BURBUJAS) ---
    private void mostrarVentanaChat() {
        // 1. ZONA DE MENSAJES (NUEVA: ScrollPane + VBox)
        contenedorMensajes = new VBox(10); // 10px de espacio entre burbujas
        contenedorMensajes.setPadding(new Insets(15)); // Margen interno

        scrollMensajes = new ScrollPane(contenedorMensajes);
        scrollMensajes.setFitToWidth(true); // Que las burbujas se adapten al ancho
        scrollMensajes.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // Sin scroll horizontal

        // IMPORTANTE: Cargar el CSS
        try {
            String css = this.getClass().getResource("/estilos.css").toExternalForm();
            scrollMensajes.getStylesheets().add(css);
        } catch (Exception e) {
            System.out.println("Error cargando CSS: " + e.getMessage());
        }

        // 2. Panel Inferior (Escribir mensaje)
        campoMensaje = new TextField();
        campoMensaje.setPromptText("Escribe un mensaje...");
        campoMensaje.setPrefWidth(300);

        Button btnEnviar = new Button("Enviar");
        btnEnviar.setOnAction(e -> enviarMensaje());

        Button btnAdjuntar = new Button("📎");
        btnAdjuntar.setOnAction(e -> enviarArchivo());

        Button btnEmoji = new Button("😀");
        btnEmoji.setOnAction(e -> mostrarSelectorEmojis());

        HBox panelInferior = new HBox(5, btnAdjuntar, btnEmoji, campoMensaje, btnEnviar);        panelInferior.setPadding(new Insets(10));
        panelInferior.setAlignment(Pos.CENTER);

        // 3. Panel Izquierdo (Lista de Contactos)
        listaUsuarios = new ListView<>();
        listaUsuarios.setPrefWidth(150);

        // Listener para cargar historial al hacer clic
        listaUsuarios.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // LIMPIEZA DE PANTALLA (NUEVO MÉTODO)
                contenedorMensajes.getChildren().clear();

                try {
                    String nombreLimpio;
                    if (newVal.startsWith("[Grupo] ")) {
                        String nombreGrupo = newVal.replace("[Grupo] ", "");
                        salida.writeUTF("GET_HISTORIAL_GRUPO|" + nombreGrupo);
                    } else {
                        String nombreUser = newVal.split(" \\(")[0];
                        salida.writeUTF("GET_HISTORIAL|" + nombreUser);
                    }
                } catch (IOException e) { e.printStackTrace(); }
            }
        });

        // Botón Crear Grupo
        Button btnCrearGrupo = new Button("Crear Grupo");
        btnCrearGrupo.setMaxWidth(Double.MAX_VALUE);
        btnCrearGrupo.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Nuevo Grupo");
            dialog.setHeaderText("Paso 1: Nombre del grupo");
            dialog.setContentText("Nombre:");

            dialog.showAndWait().ifPresent(nombreG -> {
                if (!nombreG.trim().isEmpty()) {
                    try {
                        salida.writeUTF("CREAR_GRUPO|" + nombreG.trim());

                        // Selección múltiple para invitar
                        Stage stageInvite = new Stage();
                        stageInvite.setTitle("Invitar a " + nombreG);

                        ListView<String> listaParaInvitar = new ListView<>();
                        for(String s : listaUsuarios.getItems()) {
                            if(!s.startsWith("[Grupo]")) {
                                listaParaInvitar.getItems().add(s.split(" \\(")[0]);
                            }
                        }
                        listaParaInvitar.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

                        Button btnAceptar = new Button("Invitar Seleccionados");
                        btnAceptar.setOnAction(ev -> {
                            ObservableList<String> seleccionados = listaParaInvitar.getSelectionModel().getSelectedItems();
                            for(String invitado : seleccionados) {
                                try {
                                    salida.writeUTF("INVITAR_GRUPO|" + nombreG + "|" + invitado);
                                } catch (IOException ex) { ex.printStackTrace(); }
                            }
                            stageInvite.close();
                        });

                        VBox layout = new VBox(10, new Label("Elige usuarios (CTRL+Click):"), listaParaInvitar, btnAceptar);
                        layout.setPadding(new Insets(10));
                        stageInvite.setScene(new Scene(layout, 250, 300));
                        stageInvite.show();

                    } catch (IOException ex) { ex.printStackTrace(); }
                }
            });
        });

        VBox panelIzquierdo = new VBox(10);
        panelIzquierdo.setPadding(new Insets(10));
        panelIzquierdo.getChildren().addAll(new Label("Contactos:"), listaUsuarios, btnCrearGrupo);
        VBox.setVgrow(listaUsuarios, Priority.ALWAYS);

        // Montaje final
        BorderPane root = new BorderPane();
        root.setCenter(scrollMensajes); // AQUÍ VA EL NUEVO SCROLLPANE
        root.setBottom(panelInferior);
        root.setLeft(panelIzquierdo);

        Scene scene = new Scene(root, 700, 500); // Un poco más grande para que luzca
        escenarioPrincipal.setTitle("ChatFlix - Usuario: " + nombreUsuario);
        escenarioPrincipal.setScene(scene);
        escenarioPrincipal.centerOnScreen();
    }

    // --- LÓGICA DE CONEXIÓN ---
    private void conectarYEntrar(String user, String pass, Label lblEstado) {
        lblEstado.setText("Conectando...");
        lblEstado.setStyle("-fx-text-fill: black;");

        new Thread(() -> {
            try {
                socket = new Socket("localhost", 12345);
                salida = new DataOutputStream(socket.getOutputStream());
                entrada = new DataInputStream(socket.getInputStream());

                salida.writeUTF("LOGIN|" + user + "|" + pass);
                String respuesta = entrada.readUTF();

                Platform.runLater(() -> {
                    if (respuesta.startsWith("LOGIN_OK")) {
                        this.nombreUsuario = user; // Guardamos el nombre aquí
                        mostrarVentanaChat();

                        // HILO DE ESCUCHA
                        new Thread(() -> {
                            try {
                                while (true) {
                                    String mensajeServer = entrada.readUTF();

                                    // CASO 1: MENSAJE DE CHAT -> BURBUJA
                                    if (mensajeServer.startsWith("MSG|")) {
                                        String texto = mensajeServer.substring(4);
                                        // Usamos el nuevo método de burbujas
                                        agregarMensaje(texto);
                                    }
                                    else if (mensajeServer.startsWith("STATUS|")) {
                                        String[] partes = mensajeServer.split("\\|");
                                        Platform.runLater(() -> actualizarListaUsuarios(partes[1], partes[2]));
                                    }
                                    else if (mensajeServer.startsWith("LISTA_USUARIOS|")) {
                                        String lista = mensajeServer.substring(15);
                                        Platform.runLater(() -> {
                                            listaUsuarios.getItems().clear();
                                            for (String userStr : lista.split(",")) {
                                                String[] data = userStr.split(":");
                                                actualizarListaUsuarios(data[0], data[1]);
                                            }
                                        });
                                    }
                                    else if (mensajeServer.startsWith("GRUPO_CREADO|")) {
                                        String nombreGrupo = mensajeServer.split("\\|")[1];
                                        Platform.runLater(() -> listaUsuarios.getItems().add(0, "[Grupo] " + nombreGrupo));
                                    }
                                    else if (mensajeServer.startsWith("GRUPO_INVITACION|")) {
                                        String nombreG = mensajeServer.split("\\|")[1];
                                        Platform.runLater(() -> {
                                            if (!listaUsuarios.getItems().contains("[Grupo] " + nombreG)) {
                                                listaUsuarios.getItems().add(0, "[Grupo] " + nombreG);
                                            }
                                        });
                                    }
                                    else if (mensajeServer.startsWith("FILE_RECIBIDO|")) {
                                        String[] p = mensajeServer.split("\\|");
                                        String deQuien = p[1];
                                        String nombreArchivo = p[2];
                                        int size = Integer.parseInt(p[3]);
                                        byte[] data = new byte[size];
                                        entrada.readFully(data);

                                        agregarImagen(data, deQuien);
                                    }
                                }
                            } catch (IOException e) {
                                Platform.runLater(() -> agregarMensaje("Sistema: Desconectado del servidor."));
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
        String seleccionado = listaUsuarios.getSelectionModel().getSelectedItem();

        if (!texto.isEmpty() && seleccionado != null) {
            try {
                if (seleccionado.startsWith("[Grupo] ")) {
                    String nombreGrupo = seleccionado.replace("[Grupo] ", "");
                    salida.writeUTF("PV_GRUPO|" + nombreGrupo + "|" + texto);
                } else {
                    String nombreUser = seleccionado.split(" \\(")[0];
                    salida.writeUTF("PV|" + nombreUser + "|" + texto);
                }
                campoMensaje.clear();
                // Nota: No añadimos el mensaje aquí manualmente, esperamos al eco del servidor
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void enviarArchivo() {
        String seleccionado = listaUsuarios.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            agregarMensaje("Sistema: Selecciona a alguien antes de enviar un archivo.");
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Seleccionar Imagen");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg")
        );

        java.io.File archivo = fileChooser.showOpenDialog(escenarioPrincipal);

        if (archivo != null) {
            // Ejecutamos en un hilo aparte para no congelar la pantalla mientras se lee el archivo
            new Thread(() -> {
                try {
                    byte[] bytesArchivo = java.nio.file.Files.readAllBytes(archivo.toPath());
                    String destino = seleccionado.split(" \\(")[0];
                    if (seleccionado.startsWith("[Grupo] ")) {
                        destino = seleccionado.replace("[Grupo] ", "");
                    }

                    // Enviamos el comando y luego los bytes
                    salida.writeUTF("FILE|" + destino + "|" + archivo.getName() + "|" + bytesArchivo.length);
                    salida.write(bytesArchivo);
                    salida.flush();

                    // Pintamos la imagen en nuestro chat
                    Platform.runLater(() -> agregarImagen(bytesArchivo, "Yo"));

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }).start();
        }
    }

    private void agregarImagen(byte[] data, String remitente) {
        Platform.runLater(() -> {
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(data));
                javafx.scene.image.ImageView vistaImagen = new javafx.scene.image.ImageView(img);
                vistaImagen.setFitWidth(250);
                vistaImagen.setPreserveRatio(true);

                // CORRECCIÓN AQUÍ: Detectar si soy yo
                boolean esMio = remitente.equals("Yo") || remitente.equals(nombreUsuario);

                VBox contenedorBurbuja = new VBox(5, new Label(esMio ? "Yo" : remitente), vistaImagen);
                contenedorBurbuja.getStyleClass().add(esMio ? "burbuja-enviada" : "burbuja-recibida");

                HBox fila = new HBox(contenedorBurbuja);
                fila.setAlignment(esMio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

                // Márgenes para separar de los bordes
                if (esMio) HBox.setMargin(contenedorBurbuja, new Insets(5, 10, 5, 50));
                else HBox.setMargin(contenedorBurbuja, new Insets(5, 50, 5, 10));

                contenedorMensajes.getChildren().add(fila);
                scrollMensajes.layout();
                scrollMensajes.setVvalue(1.0);

            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void mostrarSelectorEmojis() {
        Stage emojiStage = new Stage();
        String[] emojis = {"😀", "😂", "😍", "😎", "🤔", "👍", "🔥", "🚀", "🎉", "❤️"};

        javafx.scene.layout.FlowPane pane = new javafx.scene.layout.FlowPane(5, 5);
        pane.setPadding(new Insets(10));

        for (String s : emojis) {
            Button b = new Button(s);
            b.setOnAction(e -> {
                campoMensaje.appendText(s); // Lo añade al mensaje actual
                emojiStage.close();
            });
            pane.getChildren().add(b);
        }

        emojiStage.setScene(new Scene(pane, 200, 100));
        emojiStage.setTitle("Emojis");
        emojiStage.show();
    }

    private void actualizarListaUsuarios(String nombre, String estado) {
        String textoMostrar = nombre + (estado.equals("ON") ? " (Online)" : " (Offline)");
        int indice = -1;
        for (int i = 0; i < listaUsuarios.getItems().size(); i++) {
            if (listaUsuarios.getItems().get(i).startsWith(nombre + " ")) {
                indice = i;
                break;
            }
        }
        if (indice != -1) listaUsuarios.getItems().set(indice, textoMostrar);
        else listaUsuarios.getItems().add(textoMostrar);
    }

    // --- MÉTODO ESTRELLA: CREAR BURBUJAS ---
    private void agregarMensaje(String mensaje) {
        Platform.runLater(() -> {
            Label lblTexto = new Label(mensaje);
            lblTexto.setWrapText(true);
            lblTexto.setMaxWidth(350);

            // Detectar si soy yo (buscando "Yo:" o "MiNombre:")
            boolean esMio = mensaje.contains(nombreUsuario + ":") || mensaje.startsWith("Yo:");

            if (esMio) {
                lblTexto.getStyleClass().add("burbuja-enviada");
            } else {
                lblTexto.getStyleClass().add("burbuja-recibida");
            }

            HBox caja = new HBox(lblTexto);
            if (esMio) {
                caja.setAlignment(Pos.CENTER_RIGHT);
                HBox.setMargin(lblTexto, new Insets(0, 10, 0, 50));
            } else {
                caja.setAlignment(Pos.CENTER_LEFT);
                HBox.setMargin(lblTexto, new Insets(0, 50, 0, 10));
            }

            contenedorMensajes.getChildren().add(caja);
            scrollMensajes.layout();
            scrollMensajes.setVvalue(1.0); // Bajar scroll al fondo
        });
    }

    private void cerrarConexion() {
        try { if (socket != null) socket.close(); } catch (IOException e) {}
    }

    @Override
    public void stop() throws Exception {
        cerrarConexion();
        super.stop();
    }

    public static void main(String[] args) {
        launch();
    }
}