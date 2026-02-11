package org.example.chatflix.server;

import org.example.chatflix.model.Usuario;
import org.example.chatflix.server.dao.UsuarioDAO;
import java.io.*;
import java.net.Socket;

import org.example.chatflix.server.dao.MensajeDAO;

public class HiloCliente implements Runnable {

    private Socket socket;
    private DataInputStream entrada;
    private DataOutputStream salida;
    private Usuario usuario;

    public HiloCliente(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            entrada = new DataInputStream(socket.getInputStream());
            salida = new DataOutputStream(socket.getOutputStream());

            // 1. FASE LOGIN
            String mensaje = entrada.readUTF();
            if (mensaje.startsWith("LOGIN|")) {
                String[] partes = mensaje.split("\\|");
                String nombre = partes[1];
                String pass = partes[2];

                UsuarioDAO dao = new UsuarioDAO();
                Usuario u = dao.loginOregistrar(nombre, pass);

                if (u != null) {
                    this.usuario = u;
                    salida.writeUTF("LOGIN_OK|" + u.getId());
                    System.out.println("Usuario logueado: " + nombre);

                    // --- NUEVO: ENVIAR HISTORIAL AL ENTRAR ---
                    MensajeDAO mensajeDAO = new MensajeDAO(); // Creamos el DAO
                    for (String msgAntiguo : mensajeDAO.obtenerHistorial()) {
                        salida.writeUTF("MSG|" + msgAntiguo); // Se lo mandamos al cliente
                    }
                    // -----------------------------------------

                    // BUCLE INFINITO DEL CHAT
                    while (true) {
                        String mensajeRecibido = entrada.readUTF();

                        if (mensajeRecibido.startsWith("MSG|")) {
                            String texto = mensajeRecibido.substring(4);
                            String mensajeFinal = this.usuario.getNombre() + ": " + texto;

                            // --- NUEVO: GUARDAR EN BD ANTES DE ENVIAR ---
                            mensajeDAO.guardarMensaje(this.usuario.getId(), texto);
                            // --------------------------------------------

                            Servidor.broadcast("MSG|" + mensajeFinal, this);
                        }
                    }
                } else {
                    salida.writeUTF("LOGIN_ERROR");
                }
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + (usuario != null ? usuario.getNombre() : "Anónimo"));
            Servidor.clientesConectados.remove(this); // Lo borramos de la lista
        }
    }

    // Método que usará el Servidor para enviarnos cosas
    public void enviarMensaje(String texto) {
        try {
            salida.writeUTF(texto);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}