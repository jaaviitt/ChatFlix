package org.example.chatflix.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

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
                else if (msg.startsWith("FILE_RECIBIDO|")) {
                    procesarArchivo(msg);
                }
            }
        } catch (IOException e) { System.out.println("Desconectado"); }
    }

    // --- MÉTODOS DE APOYO ---
    private void procesarMensajeTexto(String msg) {
        String remitente = "Sistema", contenido = msg;
        boolean esMio = msg.startsWith("Yo:") || msg.contains(nombreUsuario + ":");

        if (msg.startsWith("Yo:")) { remitente = "Yo"; contenido = msg.substring(3).trim(); }
        else if (msg.contains(":")) {
            String[] partes = msg.split(":", 2);
            remitente = partes[0].replace("(PV)", "").replace("(Grp)", "").replace("[Grupo]", "").trim();
            contenido = partes[1].trim();
        }

        String finalRemitente = remitente; String finalContenido = contenido;
        Platform.runLater(() -> ventanaChat.agregarMensajeVisual(finalRemitente, finalContenido, esMio));
    }

    private void procesarArchivo(String msg) throws IOException {
        String[] p = msg.split("\\|");
        int size = Integer.parseInt(p[3]);
        byte[] data = new byte[size];
        entrada.readFully(data);
        boolean esMio = p[1].equals("Yo") || p[1].equals(nombreUsuario);
        Platform.runLater(() -> ventanaChat.agregarImagenVisual(data, p[1], esMio));
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
                    String destLimpio = destino.replace("[Grupo] ", "");
                    salida.writeUTF("FILE|" + destLimpio + "|" + f.getName() + "|" + b.length);
                    salida.write(b); salida.flush();
                    Platform.runLater(() -> ventanaChat.agregarImagenVisual(b, "Yo", true));
                } catch(Exception e){}
            }).start();
        }
    }

    public void crearGrupo() {
        TextInputDialog d = new TextInputDialog(); d.setTitle("Crear Grupo"); d.setContentText("Nombre:");
        d.showAndWait().ifPresent(n -> enviarComando("CREAR_GRUPO|" + n.trim()));
    }

    public void gestionarGrupo(String grupo) {
        TextInputDialog d = new TextInputDialog(); d.setTitle("Expulsar"); d.setContentText("Usuario a echar de " + grupo + ":");
        d.showAndWait().ifPresent(u -> enviarComando("KICK_USER|" + grupo + "|" + u.trim()));
    }

    private void cerrar() { try { if(socket!=null) socket.close(); } catch(IOException e){} }
    @Override public void stop() throws Exception { cerrar(); super.stop(); }
    public static void main(String[] args) { launch(); }
}