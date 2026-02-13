package org.example.chatflix.client;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.util.HashSet;
import java.util.Set;

public class VentanaChat {

    private Stage stage;
    private Cliente clienteLogica;
    private String usuarioPropio;

    // Componentes UI
    private VBox contenedorMensajes;
    private ScrollPane scrollMensajes;
    private TextField campoMensaje;
    private Label lblChatHeader;

    // Listas
    private ListView<String> listaGrupos;
    private ListView<String> listaFavoritos;
    private ListView<String> listaContactos;

    // Datos locales
    private Set<String> setFavoritos = new HashSet<>();
    private String destinatarioActual = null;

    public VentanaChat(Stage stage, Cliente clienteLogica, String usuarioPropio) {
        this.stage = stage;
        this.clienteLogica = clienteLogica;
        this.usuarioPropio = usuarioPropio;
    }

    public void mostrar() {
        // 1. CHAT CENTRAL
        contenedorMensajes = new VBox(10);
        contenedorMensajes.setPadding(new Insets(20));
        scrollMensajes = new ScrollPane(contenedorMensajes);
        scrollMensajes.setFitToWidth(true);
        scrollMensajes.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // 2. HEADER
        lblChatHeader = new Label("Selecciona un chat");
        lblChatHeader.getStyleClass().add("chat-header-label");
        Button btnGestionar = new Button("⚙ Gestionar");
        btnGestionar.getStyleClass().add("boton-pequeno");
        btnGestionar.setVisible(false);
        btnGestionar.setOnAction(e -> gestionarGrupo());

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, lblChatHeader, spacer, btnGestionar);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("chat-header");

        // 3. PANEL IZQUIERDO
        listaGrupos = new ListView<>();
        listaGrupos.setPrefHeight(100);
        // Listener GRUPOS
        listaGrupos.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if(nv!=null) {
                listaContactos.getSelectionModel().clearSelection();
                listaFavoritos.getSelectionModel().clearSelection();
                seleccionarChat(nv, true);
                btnGestionar.setVisible(true);
            }
        });

        listaFavoritos = new ListView<>();
        listaFavoritos.setPrefHeight(100);
        configurarCelda(listaFavoritos, true);
        // Listener FAVORITOS
        listaFavoritos.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if(nv!=null) {
                listaContactos.getSelectionModel().clearSelection();
                listaGrupos.getSelectionModel().clearSelection();
                seleccionarChat(nv, false);
                btnGestionar.setVisible(false);
            }
        });

        listaContactos = new ListView<>();
        configurarCelda(listaContactos, false);
        // Listener TODOS
        listaContactos.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if(nv!=null) {
                listaGrupos.getSelectionModel().clearSelection();
                listaFavoritos.getSelectionModel().clearSelection();
                seleccionarChat(nv, false);
                btnGestionar.setVisible(false);
            }
        });
        VBox.setVgrow(listaContactos, Priority.ALWAYS);

        Button btnCrearG = new Button("+");
        btnCrearG.getStyleClass().add("boton-pequeno");
        btnCrearG.setOnAction(e -> clienteLogica.crearGrupo());

        Region spacerG = new Region(); HBox.setHgrow(spacerG, Priority.ALWAYS);
        HBox headerG = new HBox(new Label("GRUPOS"), spacerG, btnCrearG);

        VBox panelIzq = new VBox(5, headerG, listaGrupos, new Separator(), new Label("FAVORITOS"), listaFavoritos, new Separator(), new Label("USUARIOS"), listaContactos);
        panelIzq.setPadding(new Insets(10));
        panelIzq.setPrefWidth(240);
        panelIzq.getStyleClass().add("panel-lateral");

        // 4. INFERIOR
        campoMensaje = new TextField();
        HBox.setHgrow(campoMensaje, Priority.ALWAYS);

        Button btnEnviar = new Button("➤"); btnEnviar.getStyleClass().add("boton-enviar");
        btnEnviar.setOnAction(e -> enviar());

        Button btnFoto = new Button("📎"); btnFoto.getStyleClass().add("boton-icono");
        btnFoto.setOnAction(e -> clienteLogica.enviarArchivo(stage, destinatarioActual));

        Button btnEmoji = crearBotonEmoji();

        HBox panelInf = new HBox(10, btnEmoji, btnFoto, campoMensaje, btnEnviar);
        panelInf.setPadding(new Insets(15));
        panelInf.setAlignment(Pos.CENTER);

        // MONTAJE
        BorderPane root = new BorderPane();
        root.setLeft(panelIzq);
        root.setCenter(new BorderPane(scrollMensajes, header, null, panelInf, null));

        Scene scene = new Scene(root, 950, 650);
        try { scene.getStylesheets().add(getClass().getResource("/estilos.css").toExternalForm()); } catch(Exception e){}

        stage.setScene(scene);
        stage.setTitle("ChatFlix - " + usuarioPropio);
        stage.centerOnScreen();
    }

    // --- MÉTODOS PÚBLICOS ---

    public String getDestinatarioActual() {
        return this.destinatarioActual;
    }

    public void agregarMensajeVisual(String remitente, String texto, boolean esMio) {
        // 1. Etiqueta del NOMBRE (Arriba, negrita y pequeñito)
        Label lblRem = new Label(esMio ? "Yo" : remitente);
        lblRem.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: " + (esMio ? "#1e5823" : "#444") + ";");

        // 2. Etiqueta del MENSAJE (Abajo, normal)
        Label lblTxt = new Label(texto);
        lblTxt.setWrapText(true);
        lblTxt.setMaxWidth(350);
        lblTxt.setStyle("-fx-font-size: 13px;"); // Un poco más grande para leer bien

        // 3. Contenedor vertical (VBox) para poner uno sobre otro
        VBox box = new VBox(2, lblRem, lblTxt);
        box.setPadding(new Insets(8, 12, 8, 12));

        // Asignamos la clase CSS de la burbuja
        box.getStyleClass().add(esMio ? "burbuja-enviada" : "burbuja-recibida");

        // 4. Alineación en el chat
        HBox fila = new HBox(box);
        fila.setAlignment(esMio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        // Márgenes para separar del borde
        if (esMio) HBox.setMargin(box, new Insets(5, 10, 5, 50));
        else HBox.setMargin(box, new Insets(5, 50, 5, 10));

        contenedorMensajes.getChildren().add(fila);

        // Auto-scroll hacia abajo
        scrollMensajes.layout();
        scrollMensajes.setVvalue(1.0);
    }

    public void agregarImagenVisual(byte[] data, String remitente, boolean esMio) {
        try {
            javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(data)));
            view.setFitWidth(200); view.setPreserveRatio(true);
            Label lbl = new Label(esMio ? "Yo" : remitente);
            lbl.setStyle("-fx-font-weight: bold;");

            VBox box = new VBox(5, lbl, view);
            box.setPadding(new Insets(10));
            box.getStyleClass().add(esMio ? "burbuja-enviada" : "burbuja-recibida");

            HBox fila = new HBox(box);
            fila.setAlignment(esMio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            contenedorMensajes.getChildren().add(fila);
        } catch(Exception e){}
    }

    public void actualizarLista(String nombre, String estado) {
        // No mostrarme a mí mismo
        if(nombre.equals(usuarioPropio)) return;

        actualizarItem(listaContactos, nombre, estado);
        if(setFavoritos.contains(nombre)) actualizarItem(listaFavoritos, nombre, estado);
    }

    public void agregarGrupo(String nombre) {
        if(!listaGrupos.getItems().contains(nombre)) listaGrupos.getItems().add(nombre);
    }

    public void setFavoritos(String[] favoritos) {
        setFavoritos.clear();
        listaFavoritos.getItems().clear();
        for(String f : favoritos) {
            if(!f.trim().isEmpty()) {
                setFavoritos.add(f);
                listaFavoritos.getItems().add(f + " (Offline)");
            }
        }
    }

    private void actualizarItem(ListView<String> lista, String nombre, String estado) {
        String nuevo = nombre + (estado.equals("ON") ? " (Online)" : " (Offline)");
        int idx = -1;
        for(int i=0; i<lista.getItems().size(); i++) {
            if(lista.getItems().get(i).startsWith(nombre + " ")) { idx=i; break; }
        }
        if(idx!=-1) lista.getItems().set(idx, nuevo);
        else if(lista == listaContactos) lista.getItems().add(nuevo);
    }

    private void seleccionarChat(String sel, boolean esGrupo) {
        // IMPORTANTE: Limpiamos los mensajes anteriores
        contenedorMensajes.getChildren().clear();

        String nombreLimpio = sel.split(" \\(")[0];
        // Aquí seteamos la variable que usa el filtro
        this.destinatarioActual = esGrupo ? "[Grupo] " + nombreLimpio : nombreLimpio;

        lblChatHeader.setText(esGrupo ? "Grupo: " + nombreLimpio : "Chat con " + nombreLimpio);
        clienteLogica.pedirHistorial(this.destinatarioActual);
    }

    private void enviar() {
        if(destinatarioActual!=null && !campoMensaje.getText().isEmpty()) {
            clienteLogica.enviarMensaje(destinatarioActual, campoMensaje.getText());
            campoMensaje.clear();
        }
    }

    private void configurarCelda(ListView<String> lista, boolean esFav) {
        lista.setCellFactory(lv -> new ListCell<String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if(empty || item==null) { setGraphic(null); setText(null); setStyle("-fx-background-color:transparent;"); }
                else {
                    String nom = item.split(" \\(")[0];
                    boolean online = item.contains("(Online)");
                    javafx.scene.shape.Circle c = new javafx.scene.shape.Circle(5, online ? Color.GREEN : Color.RED);
                    Label l = new Label(nom); l.setStyle("-fx-text-fill:black; -fx-font-weight:bold;");
                    HBox box = new HBox(10, c, l); box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box); setText(null);

                    ContextMenu menu = new ContextMenu();
                    if(esFav) {
                        MenuItem b = new MenuItem("Quitar Favorito");
                        b.setOnAction(e -> {
                            setFavoritos.remove(nom); listaFavoritos.getItems().remove(item);
                            clienteLogica.enviarComando("DEL_FAV|" + nom);
                        });
                        menu.getItems().add(b);
                    } else {
                        MenuItem a = new MenuItem("Añadir Favorito");
                        a.setOnAction(e -> {
                            if(!setFavoritos.contains(nom)) {
                                setFavoritos.add(nom); listaFavoritos.getItems().add(item);
                                clienteLogica.enviarComando("ADD_FAV|" + nom);
                            }
                        });
                        menu.getItems().add(a);
                    }
                    setContextMenu(menu);
                }
            }
        });
    }

    private void gestionarGrupo() {
        if(destinatarioActual!=null && destinatarioActual.startsWith("[Grupo] "))
            clienteLogica.gestionarGrupo(destinatarioActual.replace("[Grupo] ", ""));
    }

    private Button crearBotonEmoji() {
        Button btn = new Button("☺");
        btn.getStyleClass().add("boton-icono");

        // Creamos un menú emergente
        ContextMenu menu = new ContextMenu();

        // Lista de emojis populares
        String[] emojis = {
                "😀", "😂", "😍", "😎", "😭", "😡", "👍", "👎",
                "🎉", "❤", "💔", "🔥", "💩", "👻", "👀", "👋"
        };

        for (String e : emojis) {
            MenuItem item = new MenuItem(e);
            item.setStyle("-fx-font-size: 15px;");
            item.setOnAction(event -> {
                campoMensaje.appendText(e); // Añade el emoji al texto
                campoMensaje.requestFocus(); // Vuelve el foco al campo
            });
            menu.getItems().add(item);
        }

        btn.setOnAction(e -> menu.show(btn, javafx.geometry.Side.TOP, 0, 0));
        return btn;
    }
}