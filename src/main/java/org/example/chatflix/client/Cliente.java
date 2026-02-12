package org.example.chatflix.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.paint.Color;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;

public class Cliente extends Application {

    private Stage escenarioPrincipal;

    // VARIABLES GLOBALES DE CONEXIÓN
    private Socket socket;
    private DataOutputStream salida;
    private DataInputStream entrada;
    private String nombreUsuario;

    // COMPONENTES DE LA INTERFAZ
    private ScrollPane scrollMensajes;
    private VBox contenedorMensajes;
    private TextField campoMensaje;

    // NUEVAS LISTAS SEPARADAS
    private ListView<String> listaGrupos;
    private ListView<String> listaContactos;
    private String destinatarioActual = null; // Guardamos a quién escribimos (Usuario o [Grupo] Nombre)

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

    // --- PANTALLA 2: CHAT PRINCIPAL (DISEÑO FINAL) ---
    private void mostrarVentanaChat() {
        // 1. ZONA DE MENSAJES
        contenedorMensajes = new VBox(10);
        contenedorMensajes.setPadding(new Insets(15));
        scrollMensajes = new ScrollPane(contenedorMensajes);
        scrollMensajes.setFitToWidth(true);
        scrollMensajes.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Cargar CSS
        try {
            String css = this.getClass().getResource("/estilos.css").toExternalForm();
            scrollMensajes.getStylesheets().add(css);
        } catch (Exception e) { System.out.println("Error CSS: " + e.getMessage()); }


        // --- PANEL IZQUIERDO: GRUPOS Y USUARIOS ---

        // A) Lista de Grupos (Arriba)
        listaGrupos = new ListView<>();
        listaGrupos.setPrefHeight(150);
        listaGrupos.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                listaContactos.getSelectionModel().clearSelection(); // Desmarcar usuarios
                seleccionarDestinatario(newVal, true);
            }
        });

        // B) Lista de Usuarios (Abajo - Con Puntos de Color CORREGIDOS)
        listaContactos = new ListView<>();

        listaContactos.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                // Importante: Limpiar estilo si está vacío
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;"); // Limpiar fondo
                } else {
                    // item viene como "Pepe (Online)"
                    String nombre = item.split(" \\(")[0];
                    boolean isOnline = item.contains("(Online)");

                    // --- CORRECCIÓN DEL PUNTO ---
                    // Usamos Color.WEB para un verde/rojo más bonito y directo
                    javafx.scene.shape.Circle punto = new javafx.scene.shape.Circle(5);
                    if (isOnline) {
                        punto.setFill(Color.web("#4CAF50")); // Verde semáforo
                    } else {
                        punto.setFill(Color.web("#F44336")); // Rojo semáforo
                    }
                    // -----------------------------

                    Label lblNombre = new Label(nombre);
                    // Forzamos el color de letra negro aquí también por seguridad
                    lblNombre.setStyle("-fx-font-weight: bold; -fx-text-fill: #000000;");

                    HBox fila = new HBox(10, punto, lblNombre);
                    fila.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(fila);
                    setText(null);
                }
            }
        });
        listaContactos.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                listaGrupos.getSelectionModel().clearSelection(); // Desmarcar grupos
                seleccionarDestinatario(newVal, false);
            }
        });
        VBox.setVgrow(listaContactos, Priority.ALWAYS); // Ocupar espacio sobrante

        // Botón Crear Grupo
        Button btnCrearGrupo = new Button("Nuevo Grupo +");
        btnCrearGrupo.setMaxWidth(Double.MAX_VALUE);
        btnCrearGrupo.setOnAction(e -> crearGrupoDialogo());

        VBox panelIzquierdo = new VBox(10, new Label("GRUPOS"), listaGrupos, btnCrearGrupo, new Separator(), new Label("USUARIOS"), listaContactos);
        panelIzquierdo.setPadding(new Insets(10));
        panelIzquierdo.setPrefWidth(200);
        panelIzquierdo.setStyle("-fx-background-color: rgba(255,255,255,0.5);");


        // --- PANEL INFERIOR ---
        campoMensaje = new TextField();
        campoMensaje.setPromptText("Escribe un mensaje...");
        HBox.setHgrow(campoMensaje, Priority.ALWAYS);

        Button btnEnviar = new Button("➤");
        btnEnviar.getStyleClass().add("boton-enviar");
        btnEnviar.setOnAction(e -> enviarMensaje());

        Button btnAdjuntar = new Button("📎");
        btnAdjuntar.setOnAction(e -> enviarArchivo());

        Button btnEmoji = new Button("😀");
        btnEmoji.setOnAction(e -> mostrarSelectorEmojis());

        HBox panelInferior = new HBox(10, btnAdjuntar, btnEmoji, campoMensaje, btnEnviar);
        panelInferior.setPadding(new Insets(10));
        panelInferior.setAlignment(Pos.CENTER);

        // Montaje final
        BorderPane root = new BorderPane();
        root.setCenter(scrollMensajes);
        root.setLeft(panelIzquierdo);
        root.setBottom(panelInferior);

        Scene scene = new Scene(root, 800, 600);
        escenarioPrincipal.setTitle("ChatFlix - " + nombreUsuario);
        escenarioPrincipal.setScene(scene);
        escenarioPrincipal.centerOnScreen();
    }

    // --- LÓGICA DE CONEXIÓN ---
    private void conectarYEntrar(String user, String pass, Label lblEstado) {
        lblEstado.setText("Conectando...");
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 12345);
                salida = new DataOutputStream(socket.getOutputStream());
                entrada = new DataInputStream(socket.getInputStream());

                salida.writeUTF("LOGIN|" + user + "|" + pass);
                String respuesta = entrada.readUTF();

                Platform.runLater(() -> {
                    if (respuesta.startsWith("LOGIN_OK")) {
                        this.nombreUsuario = user;
                        mostrarVentanaChat();

                        // Hilo de Escucha
                        new Thread(this::bucleLecturaServidor).start();

                    } else {
                        lblEstado.setText("Error Login");
                        lblEstado.setStyle("-fx-text-fill: red;");
                        cerrarConexion();
                    }
                });
            } catch (IOException ex) {
                Platform.runLater(() -> lblEstado.setText("Error de Conexión"));
            }
        }).start();
    }

    private void bucleLecturaServidor() {
        try {
            while (true) {
                String mensajeServer = entrada.readUTF();

                if (mensajeServer.startsWith("MSG|")) {
                    String texto = mensajeServer.substring(4);
                    agregarMensaje(texto);
                }
                else if (mensajeServer.startsWith("STATUS|")) {
                    String[] partes = mensajeServer.split("\\|");
                    Platform.runLater(() -> actualizarListaContactos(partes[1], partes[2]));
                }
                else if (mensajeServer.startsWith("LISTA_USUARIOS|")) {
                    String lista = mensajeServer.substring(15);
                    Platform.runLater(() -> {
                        listaContactos.getItems().clear();
                        for (String userStr : lista.split(",")) {
                            String[] data = userStr.split(":");
                            actualizarListaContactos(data[0], data[1]);
                        }
                    });
                }
                else if (mensajeServer.startsWith("GRUPO_CREADO|") || mensajeServer.startsWith("GRUPO_INVITACION|")) {
                    String nombreG = mensajeServer.split("\\|")[1];
                    Platform.runLater(() -> {
                        if (!listaGrupos.getItems().contains(nombreG)) listaGrupos.getItems().add(nombreG);
                    });
                }
                else if (mensajeServer.startsWith("FILE_RECIBIDO|")) {
                    String[] p = mensajeServer.split("\\|");
                    String deQuien = p[1];
                    int size = Integer.parseInt(p[3]);
                    byte[] data = new byte[size];
                    entrada.readFully(data);
                    agregarImagen(data, deQuien);
                }
            }
        } catch (IOException e) {
            Platform.runLater(() -> agregarMensaje("Sistema: Desconectado."));
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private void seleccionarDestinatario(String seleccion, boolean esGrupo) {
        contenedorMensajes.getChildren().clear();
        try {
            if (esGrupo) {
                // seleccion es solo el nombre, ej: "IQSA"
                this.destinatarioActual = "[Grupo] " + seleccion;
                salida.writeUTF("GET_HISTORIAL_GRUPO|" + seleccion);
            } else {
                // seleccion es "Pepe (Online)", extraemos nombre
                String nombreUser = seleccion.split(" \\(")[0];
                this.destinatarioActual = nombreUser;
                salida.writeUTF("GET_HISTORIAL|" + nombreUser);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void actualizarListaContactos(String nombre, String estado) {
        String item = nombre + (estado.equals("ON") ? " (Online)" : " (Offline)");
        // Buscar si existe para reemplazar o añadir
        int idx = -1;
        for(int i=0; i<listaContactos.getItems().size(); i++) {
            if(listaContactos.getItems().get(i).startsWith(nombre + " ")) {
                idx = i; break;
            }
        }
        if(idx != -1) listaContactos.getItems().set(idx, item);
        else listaContactos.getItems().add(item);
    }

    private void enviarMensaje() {
        String texto = campoMensaje.getText();
        if (texto.isEmpty() || destinatarioActual == null) return;
        try {
            if (destinatarioActual.startsWith("[Grupo] ")) {
                String grupo = destinatarioActual.replace("[Grupo] ", "");
                salida.writeUTF("PV_GRUPO|" + grupo + "|" + texto);
            } else {
                salida.writeUTF("PV|" + destinatarioActual + "|" + texto);
            }
            campoMensaje.clear();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void enviarArchivo() {
        if (destinatarioActual == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Selecciona un chat primero.");
            a.show();
            return;
        }
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));
        File f = fc.showOpenDialog(escenarioPrincipal);

        if (f != null) {
            new Thread(() -> {
                try {
                    byte[] bytes = Files.readAllBytes(f.toPath());
                    String destino = destinatarioActual.replace("[Grupo] ", ""); // Limpiamos si es grupo

                    // IMPORTANTE: El servidor necesita saber si es grupo o no para reenviarlo bien.
                    // Con tu protocolo actual "FILE|Destino|...", el servidor busca por nombre.
                    // Si tienes un grupo y un usuario con mismo nombre podría haber conflicto,
                    // pero asumimos nombres únicos por ahora.

                    salida.writeUTF("FILE|" + destino + "|" + f.getName() + "|" + bytes.length);
                    salida.write(bytes);
                    salida.flush();

                    Platform.runLater(() -> agregarImagen(bytes, "Yo"));
                } catch (IOException e) { e.printStackTrace(); }
            }).start();
        }
    }

    private void crearGrupoDialogo() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nuevo Grupo");
        dialog.setHeaderText("Crear grupo");
        dialog.setContentText("Nombre:");
        dialog.showAndWait().ifPresent(nombreG -> {
            if(!nombreG.trim().isEmpty()) {
                try {
                    salida.writeUTF("CREAR_GRUPO|" + nombreG.trim());
                    // Invitar gente (simplificado)
                    mostrarDialogoInvitacion(nombreG.trim());
                } catch (IOException e) { e.printStackTrace(); }
            }
        });
    }

    private void mostrarDialogoInvitacion(String nombreGrupo) {
        Stage stage = new Stage();
        ListView<String> listaInv = new ListView<>();
        // Llenamos con los contactos actuales (limpiando el estado)
        for(String s : listaContactos.getItems()) {
            listaInv.getItems().add(s.split(" \\(")[0]);
        }
        listaInv.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        Button btnInv = new Button("Invitar");
        btnInv.setOnAction(e -> {
            for(String user : listaInv.getSelectionModel().getSelectedItems()) {
                try { salida.writeUTF("INVITAR_GRUPO|" + nombreGrupo + "|" + user); }
                catch (IOException ex) {}
            }
            stage.close();
        });
        stage.setScene(new Scene(new VBox(10, new Label("Elige miembros:"), listaInv, btnInv), 200, 300));
        stage.show();
    }

    private void mostrarSelectorEmojis() {
        Stage stage = new Stage();
        javafx.scene.layout.FlowPane pane = new javafx.scene.layout.FlowPane(5,5);
        pane.setPadding(new Insets(10));
        String[] emojis = {"😀","😂","😍","😎","😭","😡","👍","👎","🔥","🎉"};
        for(String em : emojis) {
            Button b = new Button(em);
            b.setOnAction(e -> { campoMensaje.appendText(em); stage.close(); });
            pane.getChildren().add(b);
        }
        stage.setScene(new Scene(pane, 200, 150));
        stage.setTitle("Emojis");
        stage.show();
    }

    private void agregarMensaje(String mensaje) {
        Platform.runLater(() -> {
            Label lbl = new Label(mensaje);
            lbl.setWrapText(true);
            lbl.setMaxWidth(350);

            lbl.setStyle("-fx-text-fill: black; -fx-font-size: 14px;");

            boolean esMio = mensaje.contains(nombreUsuario + ":") || mensaje.startsWith("Yo:");
            lbl.getStyleClass().add(esMio ? "burbuja-enviada" : "burbuja-recibida");

            HBox caja = new HBox(lbl);
            caja.setAlignment(esMio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            if(esMio) HBox.setMargin(lbl, new Insets(0,10,0,50));
            else HBox.setMargin(lbl, new Insets(0,50,0,10));

            contenedorMensajes.getChildren().add(caja);
            scrollMensajes.layout();
            scrollMensajes.setVvalue(1.0);
        });
    }

    private void agregarImagen(byte[] data, String remitente) {
        Platform.runLater(() -> {
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(data));
                javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(img);
                view.setFitWidth(200);
                view.setPreserveRatio(true);

                boolean esMio = remitente.equals("Yo") || remitente.equals(nombreUsuario);
                VBox box = new VBox(5, new Label(esMio ? "Yo" : remitente), view);
                box.getStyleClass().add(esMio ? "burbuja-enviada" : "burbuja-recibida");

                HBox caja = new HBox(box);
                caja.setAlignment(esMio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                contenedorMensajes.getChildren().add(caja);
                scrollMensajes.layout();
                scrollMensajes.setVvalue(1.0);
            } catch (Exception e) {}
        });
    }

    private void cerrarConexion() {
        try { if(socket!=null) socket.close(); } catch(IOException e){}
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