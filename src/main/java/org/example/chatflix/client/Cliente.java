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
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.*;

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
        listaUsuarios.setPrefWidth(120);

        // Listener para cargar historial al hacer clic
        listaUsuarios.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                areaMensajes.clear();
                try {
                    if (newVal.startsWith("[Grupo] ")) {
                        // Es un grupo: extraemos el nombre real
                        String nombreGrupo = newVal.replace("[Grupo] ", "");
                        salida.writeUTF("GET_HISTORIAL_GRUPO|" + nombreGrupo);
                    } else {
                        // Es un usuario: limpiamos el (Online/Offline) y pedimos historial PV
                        String nombreUser = newVal.split(" \\(")[0];
                        salida.writeUTF("GET_HISTORIAL|" + nombreUser);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // 1. Creamos el botón
        Button btnCrearGrupo = new Button("Crear Grupo");
        btnCrearGrupo.setMaxWidth(Double.MAX_VALUE); // Para que ocupe todo el ancho

        // 2. Le damos la funcionalidad (el diálogo que pide el nombre)
        btnCrearGrupo.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Nuevo Grupo");
            dialog.setHeaderText("Paso 1: Nombre del grupo");
            dialog.setContentText("Nombre:");

            dialog.showAndWait().ifPresent(nombreG -> {
                if (!nombreG.trim().isEmpty()) {
                    try {
                        // 1. Creamos el grupo en el servidor
                        salida.writeUTF("CREAR_GRUPO|" + nombreG.trim());

                        // 2. Paso 2: Ventana para elegir miembros
                        Stage stageInvite = new Stage();
                        stageInvite.setTitle("Invitar a " + nombreG);

                        // Creamos una lista con los usuarios actuales
                        ListView<String> listaParaInvitar = new ListView<>();
                        for(String s : listaUsuarios.getItems()) {
                            if(!s.startsWith("[Grupo]")) {
                                listaParaInvitar.getItems().add(s.split(" \\(")[0]);
                            }
                        }
                        // Habilitamos selección múltiple
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

                        VBox layout = new VBox(10, new Label("Mantén CTRL para elegir varios:"), listaParaInvitar, btnAceptar);
                        layout.setPadding(new javafx.geometry.Insets(10));
                        stageInvite.setScene(new Scene(layout, 250, 300));
                        stageInvite.show();

                    } catch (IOException ex) { ex.printStackTrace(); }
                }
            });
        });

        // 3. ¡ESTA ES LA PARTE CLAVE!
        // Asegúrate de que añades el botón al VBox (panelIzquierdo)
        VBox panelIzquierdo = new VBox(10); // El '10' es el espacio entre elementos
        panelIzquierdo.setPadding(new javafx.geometry.Insets(10));
        panelIzquierdo.getChildren().addAll(
                new Label("Contactos y Grupos:"),
                listaUsuarios,
                btnCrearGrupo
        );
        // Esto obliga a la lista a expandirse y empujar al botón hacia abajo
        VBox.setVgrow(listaUsuarios, javafx.scene.layout.Priority.ALWAYS);

        // 4. Montaje final (BorderPane es ideal para esto)
        BorderPane root = new BorderPane();
        root.setCenter(areaMensajes);
        root.setBottom(panelInferior);

        root.setLeft(panelIzquierdo);

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

                                    // CASO 2: ALGUIEN CAMBIA DE ESTADO
                                    else if (mensajeServer.startsWith("STATUS|")) {
                                        String[] partes = mensajeServer.split("\\|");
                                        String nombre = partes[1];
                                        String estado = partes[2]; // ON u OFF

                                        Platform.runLater(() -> actualizarListaUsuarios(nombre, estado));
                                    }

                                    // CASO 3: RECIBIR LISTA INICIAL COMPLETA
                                    else if (mensajeServer.startsWith("LISTA_USUARIOS|")) {
                                        String lista = mensajeServer.substring(15);
                                        String[] usuariosConEstado = lista.split(",");

                                        Platform.runLater(() -> {
                                            listaUsuarios.getItems().clear(); // Limpiamos para evitar duplicados
                                            for (String userStr : usuariosConEstado) {
                                                // userStr viene como "Pepe:ON"
                                                String[] data = userStr.split(":");
                                                String nombre = data[0];
                                                String estado = data[1];
                                                actualizarListaUsuarios(nombre, estado);
                                            }
                                        });
                                    }
                                    // CASO 4: CREAR GRUPO
                                    else if (mensajeServer.startsWith("GRUPO_CREADO|")) {
                                        String nombreGrupo = mensajeServer.split("\\|")[1];
                                        Platform.runLater(() -> {
                                            // Lo añadimos con un prefijo para distinguirlo
                                            listaUsuarios.getItems().add(0, "[Grupo] " + nombreGrupo);
                                        });
                                    }
                                    // CASO 5: INVITAR AL GRUPO
                                    else if (mensajeServer.startsWith("GRUPO_INVITACION|")) {
                                        String nombreG = mensajeServer.split("\\|")[1];
                                        Platform.runLater(() -> {
                                            if (!listaUsuarios.getItems().contains("[Grupo] " + nombreG)) {
                                                listaUsuarios.getItems().add(0, "[Grupo] " + nombreG);
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
        String seleccionado = listaUsuarios.getSelectionModel().getSelectedItem();

        if (!texto.isEmpty() && seleccionado != null) {
            try {
                if (seleccionado.startsWith("[Grupo] ")) {
                    // Si es un grupo, enviamos con el comando especial de grupo
                    String nombreGrupo = seleccionado.replace("[Grupo] ", "");
                    salida.writeUTF("PV_GRUPO|" + nombreGrupo + "|" + texto);
                } else {
                    // Si es un usuario, limpiamos el estado y enviamos PV normal
                    String nombreUser = seleccionado.split(" \\(")[0];
                    salida.writeUTF("PV|" + nombreUser + "|" + texto);
                }
                campoMensaje.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Método auxiliar para gestionar la lista visualmente
    private void actualizarListaUsuarios(String nombre, String estado) {
        String textoMostrar = nombre + (estado.equals("ON") ? " (Online)" : " (Offline)");

        // 1. Buscamos si ya existe en la lista (con cualquier estado)
        int indice = -1;
        for (int i = 0; i < listaUsuarios.getItems().size(); i++) {
            String item = listaUsuarios.getItems().get(i);
            if (item.startsWith(nombre + " ")) { // Buscamos por el nombre
                indice = i;
                break;
            }
        }

        // 2. Si existe, lo actualizamos. Si no, lo añadimos.
        if (indice != -1) {
            listaUsuarios.getItems().set(indice, textoMostrar);
        } else {
            listaUsuarios.getItems().add(textoMostrar);
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