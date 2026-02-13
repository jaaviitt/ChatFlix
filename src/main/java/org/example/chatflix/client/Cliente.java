package org.example.chatflix.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.control.Alert;

import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.TextInputDialog;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;

public class Cliente extends Application {

    private Stage stagePrincipal;
    private Socket socket;
    private DataOutputStream salida;
    private DataInputStream entrada;
    private String nombreUsuario;

    // Referencia a la ventana del chat para poder actualizarla
    private VentanaChat ventanaChat;

    @Override
    public void start(Stage stage) {
        this.stagePrincipal = stage;
        // Al arrancar, mostramos el Login
        new VentanaLogin(stage, this).mostrar();
    }

    // --- LÓGICA DE CONEXIÓN ---
    public void conectarYEntrar(String user, String pass, Label lblEstado) {
        lblEstado.setText("Conectando...");
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
                        // CREAMOS Y MOSTRAMOS EL CHAT
                        this.ventanaChat = new VentanaChat(stagePrincipal, this, nombreUsuario);
                        ventanaChat.mostrar();

                        // Iniciamos el hilo de escucha
                        new Thread(this::bucleLectura).start();
                    } else {
                        lblEstado.setText("Error Login");
                        cerrar();
                    }
                });
            } catch (IOException e) { Platform.runLater(() -> lblEstado.setText("Error Conexión")); }
        }).start();
    }

    private void bucleLectura() {
        try {
            while (true) {
                String msg = entrada.readUTF();

                if (msg.startsWith("MSG|")) {
                    procesarMensajeTexto(msg.substring(4));
                }
                else if (msg.startsWith("LISTA_FAVORITOS|")) {
                    String[] favs = msg.substring(16).split(",");
                    Platform.runLater(() -> ventanaChat.setFavoritos(favs));
                }
                else if (msg.startsWith("STATUS|")) {
                    String[] p = msg.split("\\|");
                    Platform.runLater(() -> ventanaChat.actualizarLista(p[1], p[2]));
                }
                else if (msg.startsWith("LISTA_USUARIOS|")) {
                    String l = msg.substring(15);
                    Platform.runLater(() -> {
                        for (String u : l.split(",")) {
                            String[] d = u.split(":");
                            ventanaChat.actualizarLista(d[0], d[1]);
                        }
                    });
                }
                else if (msg.startsWith("GRUPO_CREADO|") || msg.startsWith("GRUPO_INVITACION|")) {
                    String g = msg.split("\\|")[1];
                    Platform.runLater(() -> ventanaChat.agregarGrupo(g));
                }

                else if (msg.startsWith("FILE_RX|")) {
                    // Formato: FILE_RX|RemitenteChat|UsuarioOrigen
                    String[] p = msg.split("\\|");
                    String remitenteChat = p[1];
                    String usuarioOrigen = p[2];

                    // 1. LEER EL CONTENIDO GIGANTE APARTE
                    int tamano = entrada.readInt();
                    byte[] buffer = new byte[tamano];
                    entrada.readFully(buffer);
                    String base64 = new String(buffer); // Reconstruimos el Base64

                    // 2. LÓGICA VISUAL
                    String chatAbierto = ventanaChat.getDestinatarioActual();
                    boolean esMio = usuarioOrigen.equals("Yo") || usuarioOrigen.equals(nombreUsuario);

                    if (remitenteChat.startsWith("[Grupo] ")) {
                        if (chatAbierto != null && chatAbierto.equals(remitenteChat)) {
                            byte[] imgData = java.util.Base64.getDecoder().decode(base64);
                            Platform.runLater(() -> ventanaChat.agregarImagenVisual(imgData, usuarioOrigen, esMio));
                        }
                    } else {
                        if (esMio || (chatAbierto != null && chatAbierto.equals(remitenteChat))) {
                            byte[] imgData = java.util.Base64.getDecoder().decode(base64);
                            Platform.runLater(() -> ventanaChat.agregarImagenVisual(imgData, usuarioOrigen, esMio));
                        }
                    }
                }
            }
        } catch (IOException e) { System.out.println("Desconectado"); }
    }

    // --- MÉTODOS DE APOYO ---
    private void procesarMensajeTexto(String msg) {
        // Variables por defecto
        String remitenteVisual = "Sistema";
        String contenidoVisual = msg;
        String chatRemitenteLogico = "Sistema"; // Para saber si es el chat que tenemos abierto

        boolean esMio = msg.startsWith("Yo:") || msg.contains(nombreUsuario + ":");

        // --- LÓGICA DE PARSEO ---

        if (msg.startsWith("Yo:")) {
            remitenteVisual = "Yo";
            contenidoVisual = msg.substring(3).trim();
            chatRemitenteLogico = "Yo"; // Siempre se pinta
        }
        // CASO GRUPO: "MSG|[Grupo] NombreGrupo: Usuario: Texto"
        else if (msg.startsWith("[Grupo] ")) {

            int primerDosPuntos = msg.indexOf(":");
            int segundoDosPuntos = msg.indexOf(":", primerDosPuntos + 1);

            if (primerDosPuntos != -1 && segundoDosPuntos != -1) {
                chatRemitenteLogico = msg.substring(0, primerDosPuntos).trim();

                remitenteVisual = msg.substring(primerDosPuntos + 1, segundoDosPuntos).trim();

                contenidoVisual = msg.substring(segundoDosPuntos + 1).trim();
            } else {
                chatRemitenteLogico = msg;
            }
        }
        // CASO PRIVADO
        else if (msg.contains(":")) {
            String[] partes = msg.split(":", 2);
            remitenteVisual = partes[0].trim();
            contenidoVisual = partes[1].trim();
            chatRemitenteLogico = remitenteVisual;
        }

        // --- FILTRO VISUAL ---
        String chatAbierto = ventanaChat.getDestinatarioActual();

        // Variables finales para lambda
        String finalRemitente = remitenteVisual;
        String finalContenido = contenidoVisual;

        if (esMio || (chatAbierto != null && chatAbierto.equals(chatRemitenteLogico))) {
            Platform.runLater(() -> ventanaChat.agregarMensajeVisual(finalRemitente, finalContenido, esMio));
        }
    }

    private void procesarArchivo(String msg) {
        try {
            // Formato recibido: FILE_RECIBIDO|RemitenteChat|UsuarioQueEnvia|Base64
            String[] p = msg.split("\\|", 4);
            String remitenteChat = p[1]; // Quien me habla (o el grupo)
            String usuarioOrigen = p[2]; // Quien mandó la foto
            String base64 = p[3];

            // Filtro visual (igual que el de texto)
            String chatAbierto = ventanaChat.getDestinatarioActual();
            boolean esMio = usuarioOrigen.equals("Yo") || usuarioOrigen.equals(nombreUsuario);

            // Si es grupo, ajustamos el remitente para el filtro
            if (remitenteChat.startsWith("[Grupo] ")) {
                if (chatAbierto != null && chatAbierto.equals(remitenteChat)) {
                    byte[] data = java.util.Base64.getDecoder().decode(base64);
                    Platform.runLater(() -> ventanaChat.agregarImagenVisual(data, usuarioOrigen, esMio));
                }
            } else {
                // Privado
                if (esMio || (chatAbierto != null && chatAbierto.equals(remitenteChat))) {
                    byte[] data = java.util.Base64.getDecoder().decode(base64);
                    Platform.runLater(() -> ventanaChat.agregarImagenVisual(data, usuarioOrigen, esMio));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void enviarMensaje(String destino, String texto) {
        try {
            if (destino.startsWith("[Grupo] ")) salida.writeUTF("PV_GRUPO|" + destino.replace("[Grupo] ", "") + "|" + texto);
            else salida.writeUTF("PV|" + destino + "|" + texto);
        } catch (IOException e) {}
    }

    public void pedirHistorial(String destino) {
        try {
            if (destino.startsWith("[Grupo] ")) salida.writeUTF("GET_HISTORIAL_GRUPO|" + destino.replace("[Grupo] ", ""));
            else salida.writeUTF("GET_HISTORIAL|" + destino);
        } catch (IOException e) {}
    }

    public void enviarComando(String cmd) {
        try { salida.writeUTF(cmd); } catch (IOException e) {}
    }

    public void enviarArchivo(Stage stage, String destino) {
        if(destino == null) return;
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes", "*.jpg", "*.png"));
        File f = fc.showOpenDialog(stage);
        if(f!=null) {
            new Thread(() -> {
                try {
                    byte[] b = Files.readAllBytes(f.toPath());
                    // 1. Convertir imagen a texto Base64
                    String base64 = java.util.Base64.getEncoder().encodeToString(b);

                    // 2. Preparar los bytes del texto
                    byte[] datosBase64 = base64.getBytes();

                    // 3. Enviar Cabecera (comando corto)
                    salida.writeUTF("FILE_B64|" + destino + "|" + f.getName());

                    // 4. Enviar TAMAÑO y DATOS
                    salida.writeInt(datosBase64.length);
                    salida.write(datosBase64);
                    salida.flush();

                    // Pintarlo en mi pantalla directamente
                    Platform.runLater(() -> ventanaChat.agregarImagenVisual(b, "Yo", true));
                } catch(Exception e){ e.printStackTrace(); }
            }).start();
        }
    }

    public void crearGrupo() {
        TextInputDialog d = new TextInputDialog(); d.setTitle("Crear Grupo"); d.setContentText("Nombre:");
        d.showAndWait().ifPresent(n -> enviarComando("CREAR_GRUPO|" + n.trim()));
    }

    public void gestionarGrupo(String grupo) {
        // Creamos un diálogo con botones personalizados
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Gestión de Grupo: " + grupo);
        alert.setHeaderText("¿Qué quieres hacer con el grupo " + grupo + "?");
        alert.setContentText("Elige una opción:");

        javafx.scene.control.ButtonType btnInvitar = new javafx.scene.control.ButtonType("Invitar Usuario");
        javafx.scene.control.ButtonType btnEchar = new javafx.scene.control.ButtonType("Expulsar Miembro");
        javafx.scene.control.ButtonType btnCancelar = new javafx.scene.control.ButtonType("Cancelar", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(btnInvitar, btnEchar, btnCancelar);

        alert.showAndWait().ifPresent(tipo -> {
            if (tipo == btnInvitar) {
                // Diálogo para escribir el nombre a invitar
                TextInputDialog d = new TextInputDialog();
                d.setTitle("Invitar a " + grupo);
                d.setContentText("Nombre del usuario a invitar:");
                d.showAndWait().ifPresent(u -> enviarComando("INVITAR_GRUPO|" + grupo + "|" + u.trim()));
            }
            else if (tipo == btnEchar) {
                // Diálogo para escribir a quién echar
                TextInputDialog d = new TextInputDialog();
                d.setTitle("Expulsar de " + grupo);
                d.setContentText("Nombre del usuario a expulsar:");
                d.showAndWait().ifPresent(u -> enviarComando("KICK_USER|" + grupo + "|" + u.trim()));
            }
        });
    }

    private void cerrar() { try { if(socket!=null) socket.close(); } catch(IOException e){} }
    @Override public void stop() throws Exception { cerrar(); super.stop(); }
    public static void main(String[] args) { launch(); }
}