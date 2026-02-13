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
import javafx.scene.paint.Color; // Importante para los puntos de color

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;

public class Cliente extends Application {

    private Stage escenarioPrincipal;

    // VARIABLES GLOBALES
    private Socket socket;
    private DataOutputStream salida;
    private DataInputStream entrada;
    private String nombreUsuario;

    // GUI
    private ScrollPane scrollMensajes;
    private VBox contenedorMensajes;
    private TextField campoMensaje;
    private Label lblChatHeader; // Título del chat

    private ListView<String> listaGrupos;
    private ListView<String> listaContactos;
    private String destinatarioActual = null;

    @Override
    public void start(Stage stage) {
        this.escenarioPrincipal = stage;
        mostrarVentanaLogin();
    }

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
            String u = txtUsuario.getText();
            String p = txtPassword.getText();
            if (u.isEmpty() || p.isEmpty()) {
                lblEstado.setText("Rellena todo");
                lblEstado.setStyle("-fx-text-fill: red;");
            } else {
                conectarYEntrar(u, p, lblEstado);
            }
        });

        VBox layout = new VBox(15, lblTitulo, txtUsuario, txtPassword, btnEntrar, lblEstado);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);
        escenarioPrincipal.setScene(new Scene(layout, 300, 250));
        escenarioPrincipal.setTitle("Login");
        escenarioPrincipal.show();
    }

    private void mostrarVentanaChat() {
        // 1. ZONA CENTRAL (Mensajes)
        contenedorMensajes = new VBox(10);
        contenedorMensajes.setPadding(new Insets(20)); // Margen generoso alrededor

        scrollMensajes = new ScrollPane(contenedorMensajes);
        scrollMensajes.setFitToWidth(true);
        scrollMensajes.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // IMPORTANTE: El fondo transparente se gestiona via CSS global ahora

        // 2. CABECERA (Header)
        lblChatHeader = new Label("Selecciona un chat");
        lblChatHeader.getStyleClass().add("chat-header-label");

        HBox headerBox = new HBox(lblChatHeader);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getStyleClass().add("chat-header"); // Clase CSS blanca
        headerBox.setMaxWidth(Double.MAX_VALUE);

        // 3. PANEL IZQUIERDO
        listaGrupos = new ListView<>();
        listaGrupos.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                listaContactos.getSelectionModel().clearSelection();
                seleccionarDestinatario(n, true);
            }
        });

        listaContactos = new ListView<>();
        listaContactos.setCellFactory(lv -> new ListCell<String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null); setText(null); setStyle("-fx-background-color: transparent;");
                } else {
                    String nombre = item.split(" \\(")[0];
                    boolean isOnline = item.contains("(Online)");

                    javafx.scene.shape.Circle punto = new javafx.scene.shape.Circle(5);
                    punto.setFill(isOnline ? Color.web("#4CAF50") : Color.web("#F44336")); // Verde/Rojo

                    Label lblNombre = new Label(nombre);
                    lblNombre.setStyle("-fx-font-weight: bold; -fx-text-fill: black;"); // Texto negro forzado

                    HBox fila = new HBox(10, punto, lblNombre);
                    fila.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(fila); setText(null);
                }
            }
        });
        listaContactos.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                listaGrupos.getSelectionModel().clearSelection();
                seleccionarDestinatario(n, false);
            }
        });

        VBox.setVgrow(listaGrupos, Priority.ALWAYS);
        VBox.setVgrow(listaContactos, Priority.ALWAYS);

        Button btnCrearGrupo = new Button("Nuevo Grupo +");
        btnCrearGrupo.setMaxWidth(Double.MAX_VALUE);
        btnCrearGrupo.setOnAction(e -> crearGrupoDialogo());

        VBox panelIzquierdo = new VBox(10, new Label("GRUPOS"), listaGrupos, btnCrearGrupo, new Separator(), new Label("USUARIOS"), listaContactos);
        panelIzquierdo.setPadding(new Insets(10));
        panelIzquierdo.setPrefWidth(240);
        panelIzquierdo.getStyleClass().add("panel-lateral"); // Clase CSS

        // 4. PANEL INFERIOR
        campoMensaje = new TextField();
        campoMensaje.setPromptText("Escribe un mensaje...");
        HBox.setHgrow(campoMensaje, Priority.ALWAYS);

        Button btnEnviar = new Button("➤");
        btnEnviar.getStyleClass().add("boton-enviar"); // Clase CSS
        btnEnviar.setOnAction(e -> enviarMensaje());

        Button btnAdjuntar = new Button("📎");
        btnAdjuntar.getStyleClass().add("boton-icono");
        btnAdjuntar.setOnAction(e -> enviarArchivo());

        Button btnEmoji = new Button("😀");
        btnEmoji.getStyleClass().add("boton-icono");
        btnEmoji.setOnAction(e -> mostrarSelectorEmojis());

        HBox panelInferior = new HBox(10, btnAdjuntar, btnEmoji, campoMensaje, btnEnviar);
        panelInferior.setPadding(new Insets(15));
        panelInferior.setAlignment(Pos.CENTER);
        panelInferior.setStyle("-fx-background-color: transparent;");

        // MONTAJE
        BorderPane panelDerecho = new BorderPane();
        panelDerecho.setTop(headerBox);
        panelDerecho.setCenter(scrollMensajes);
        panelDerecho.setBottom(panelInferior);

        BorderPane root = new BorderPane();
        root.setCenter(panelDerecho);
        root.setLeft(panelIzquierdo);

        Scene scene = new Scene(root, 900, 650);

        // --- AQUÍ ESTÁ LA CLAVE DEL ARREGLO ---
        // Cargamos el CSS en la ESCENA, no en un componente suelto.
        try {
            String css = this.getClass().getResource("/estilos.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception e) {
            System.err.println("ERROR CRÍTICO: No se encuentra estilos.css en resources.");
        }
        // ---------------------------------------

        escenarioPrincipal.setTitle("ChatFlix - " + nombreUsuario);
        escenarioPrincipal.setScene(scene);
        escenarioPrincipal.centerOnScreen();
    }

    // --- MÉTODOS LÓGICOS ---
    private void conectarYEntrar(String user, String pass, Label lbl) {
        lbl.setText("Conectando...");
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 12345);
                salida = new DataOutputStream(socket.getOutputStream());
                entrada = new DataInputStream(socket.getInputStream());
                salida.writeUTF("LOGIN|" + user + "|" + pass);
                String resp = entrada.readUTF();

                Platform.runLater(() -> {
                    if (resp.startsWith("LOGIN_OK")) {
                        this.nombreUsuario = user;
                        mostrarVentanaChat();
                        new Thread(this::bucleLectura).start();
                    } else {
                        lbl.setText("Error Login");
                        cerrar();
                    }
                });
            } catch (IOException e) { Platform.runLater(() -> lbl.setText("Error Conexión")); }
        }).start();
    }

    private void bucleLectura() {
        try {
            while (true) {
                String msg = entrada.readUTF();
                if (msg.startsWith("MSG|")) agregarMensaje(msg.substring(4));
                else if (msg.startsWith("STATUS|")) {
                    String[] p = msg.split("\\|");
                    Platform.runLater(() -> actLista(p[1], p[2]));
                }
                else if (msg.startsWith("LISTA_USUARIOS|")) {
                    String l = msg.substring(15);
                    Platform.runLater(() -> {
                        listaContactos.getItems().clear();
                        for (String u : l.split(",")) {
                            String[] d = u.split(":");
                            actLista(d[0], d[1]);
                        }
                    });
                }
                else if (msg.startsWith("GRUPO_CREADO|") || msg.startsWith("GRUPO_INVITACION|")) {
                    String g = msg.split("\\|")[1];
                    Platform.runLater(() -> { if (!listaGrupos.getItems().contains(g)) listaGrupos.getItems().add(g); });
                }
                else if (msg.startsWith("FILE_RECIBIDO|")) {
                    String[] p = msg.split("\\|");
                    int size = Integer.parseInt(p[3]);
                    byte[] d = new byte[size];
                    entrada.readFully(d);
                    agregarImagen(d, p[1]);
                }
            }
        } catch (IOException e) { Platform.runLater(() -> agregarMensaje("Desconectado.")); }
    }

    private void actLista(String nom, String est) {
        String item = nom + (est.equals("ON") ? " (Online)" : " (Offline)");
        int idx = -1;
        for (int i = 0; i < listaContactos.getItems().size(); i++) {
            if (listaContactos.getItems().get(i).startsWith(nom + " ")) { idx = i; break; }
        }
        if (idx != -1) listaContactos.getItems().set(idx, item);
        else listaContactos.getItems().add(item);
    }

    private void seleccionarDestinatario(String sel, boolean esGrupo) {
        contenedorMensajes.getChildren().clear();
        try {
            if (esGrupo) {
                lblChatHeader.setText("Grupo: " + sel);
                destinatarioActual = "[Grupo] " + sel;
                salida.writeUTF("GET_HISTORIAL_GRUPO|" + sel);
            } else {
                String u = sel.split(" \\(")[0];
                lblChatHeader.setText("Chat con " + u);
                destinatarioActual = u;
                salida.writeUTF("GET_HISTORIAL|" + u);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void enviarMensaje() {
        String t = campoMensaje.getText();
        if (t.isEmpty() || destinatarioActual == null) return;
        try {
            if (destinatarioActual.startsWith("[Grupo] ")) {
                salida.writeUTF("PV_GRUPO|" + destinatarioActual.replace("[Grupo] ", "") + "|" + t);
            } else {
                salida.writeUTF("PV|" + destinatarioActual + "|" + t);
            }
            campoMensaje.clear();
        } catch (IOException e) {}
    }

    private void enviarArchivo() {
        if (destinatarioActual == null) return;
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Imágenes", "*.jpg", "*.png", "*.jpeg"));
        File f = fc.showOpenDialog(escenarioPrincipal);
        if (f != null) {
            new Thread(() -> {
                try {
                    byte[] b = Files.readAllBytes(f.toPath());
                    String dest = destinatarioActual.replace("[Grupo] ", "");
                    salida.writeUTF("FILE|" + dest + "|" + f.getName() + "|" + b.length);
                    salida.write(b); salida.flush();
                    Platform.runLater(() -> agregarImagen(b, "Yo"));
                } catch (IOException e) {}
            }).start();
        }
    }

    // --- MÉTODOS VISUALES DEL CHAT ---
    private void agregarMensaje(String mensajeCompleto) {
        Platform.runLater(() -> {
            String remitente;
            String contenido;

            // 1. Detectar si el mensaje es mío (Buscamos mi nombre o "Yo")
            boolean esMio = mensajeCompleto.startsWith("Yo:") ||
                    mensajeCompleto.contains(nombreUsuario + ":");

            // 2. Lógica de SEPARACIÓN (Parsing)
            if (mensajeCompleto.startsWith("Yo:")) {
                remitente = "Yo";
                contenido = mensajeCompleto.substring(3).trim();
            } else {
                int indiceDosPuntos = mensajeCompleto.indexOf(':');
                if (indiceDosPuntos != -1) {
                    String nombreSucio = mensajeCompleto.substring(0, indiceDosPuntos).trim();

                    // --- AQUÍ ESTÁ LA CLAVE PARA GRUPOS ---
                    // Limpiamos cualquier prefijo que el servidor pueda mandar
                    remitente = nombreSucio.replace("(PV)", "")
                            .replace("(Grp)", "")    // Prefijo típico de grupos
                            .replace("[Grupo]", "") // Por si acaso usamos este
                            .trim();
                    // ---------------------------------------

                    contenido = mensajeCompleto.substring(indiceDosPuntos + 1).trim();
                } else {
                    remitente = "Sistema";
                    contenido = mensajeCompleto;
                }
            }

            // Si soy yo, fuerzo que el nombre sea "Yo" para que quede mejor (opcional)
            if (esMio) remitente = "Yo";

            // 3. Crear Etiquetas
            Label lblRemitente = new Label(remitente);
            // Estilo: Negrita, pequeño y del color del sistema (negro)
            lblRemitente.setStyle("-fx-font-weight: bold; -fx-text-fill: #333; -fx-font-size: 11px; -fx-padding: 0 0 2 0;");

            Label lblContenido = new Label(contenido);
            lblContenido.setWrapText(true);
            lblContenido.setMaxWidth(350);
            lblContenido.setStyle("-fx-text-fill: black; -fx-font-size: 14px;");

            // 4. Montar la Burbuja Vertical (Nombre arriba, texto abajo)
            VBox burbujaBox = new VBox(3, lblRemitente, lblContenido);
            burbujaBox.setPadding(new Insets(8, 12, 8, 12)); // Padding cómodo
            burbujaBox.getStyleClass().add(esMio ? "burbuja-enviada" : "burbuja-recibida");

            // 5. Alinear a derecha o izquierda
            HBox cajaAlineacion = new HBox(burbujaBox);
            cajaAlineacion.setAlignment(esMio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

            // Márgenes laterales
            if (esMio) HBox.setMargin(burbujaBox, new Insets(5, 10, 5, 50));
            else HBox.setMargin(burbujaBox, new Insets(5, 50, 5, 10));

            // 6. Añadir al chat
            contenedorMensajes.getChildren().add(cajaAlineacion);
            scrollMensajes.layout();
            scrollMensajes.setVvalue(1.0);
        });
    }

    private void agregarImagen(byte[] data, String remitente) {
        Platform.runLater(() -> {
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(data));
                javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(img);
                view.setFitWidth(200); view.setPreserveRatio(true);

                boolean esMio = remitente.equals("Yo") || remitente.equals(nombreUsuario);

                // CORRECCIÓN ETIQUETA NOMBRE: Color negro explícito
                Label lblNombre = new Label(esMio ? "Yo" : remitente);
                lblNombre.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-padding: 0 0 5 0;");

                VBox box = new VBox(0, lblNombre, view);
                box.setPadding(new Insets(10)); // Padding interno de la burbuja
                box.getStyleClass().add(esMio ? "burbuja-enviada" : "burbuja-recibida");

                HBox caja = new HBox(box);
                caja.setAlignment(esMio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                if (esMio) HBox.setMargin(box, new Insets(5, 10, 5, 50));
                else HBox.setMargin(box, new Insets(5, 50, 5, 10));

                contenedorMensajes.getChildren().add(caja);
                scrollMensajes.layout();
                scrollMensajes.setVvalue(1.0);
            } catch (Exception e) {}
        });
    }

    // UTILS
    private void crearGrupoDialogo() {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Crear Grupo"); d.setContentText("Nombre:");
        d.showAndWait().ifPresent(n -> {
            try { if(!n.trim().isEmpty()) { salida.writeUTF("CREAR_GRUPO|" + n.trim()); invitarGente(n.trim()); } } catch(IOException e){}
        });
    }
    private void invitarGente(String g) {
        Stage s = new Stage();
        ListView<String> l = new ListView<>();
        for(String i : listaContactos.getItems()) l.getItems().add(i.split(" \\(")[0]);
        l.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        Button b = new Button("Invitar");
        b.setOnAction(e -> {
            for(String u : l.getSelectionModel().getSelectedItems()) {
                try { salida.writeUTF("INVITAR_GRUPO|" + g + "|" + u); } catch(IOException ex){}
            }
            s.close();
        });
        s.setScene(new Scene(new VBox(10, l, b), 200, 300)); s.show();
    }
    private void mostrarSelectorEmojis() {
        Stage s = new Stage();
        javafx.scene.layout.FlowPane p = new javafx.scene.layout.FlowPane(5,5);
        p.setPadding(new Insets(10));
        String[] em = {"😀","😂","😍","😎","😭","😡","👍","👎","🔥","🎉"};
        for(String e : em) {
            Button b = new Button(e);
            b.setOnAction(ev -> { campoMensaje.appendText(e); s.close(); });
            p.getChildren().add(b);
        }
        s.setScene(new Scene(p, 200, 150)); s.show();
    }
    private void cerrar() { try { if(socket!=null) socket.close(); } catch(IOException e){} }
    @Override public void stop() throws Exception { cerrar(); super.stop(); }
    public static void main(String[] args) { launch(); }
}