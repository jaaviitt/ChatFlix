package org.example.chatflix.server;

import org.example.chatflix.server.dao.MensajeDAO;
import org.example.chatflix.server.dao.UsuarioDAO;
import org.example.chatflix.model.Usuario;
import java.io.*;
import java.net.Socket;
import java.util.List; // IMPORTANTE

public class HiloCliente implements Runnable {

    private Socket socket;
    private DataInputStream entrada;
    private DataOutputStream salida;
    private Usuario usuario;

    // Instancias DAO
    private UsuarioDAO uDao = new UsuarioDAO();
    private MensajeDAO mDao = new MensajeDAO();

    public HiloCliente(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            entrada = new DataInputStream(socket.getInputStream());
            salida = new DataOutputStream(socket.getOutputStream());

            // --- FASE LOGIN ---
            String mensaje = entrada.readUTF();
            if (mensaje.startsWith("LOGIN|")) {
                String[] partes = mensaje.split("\\|");
                Usuario u = uDao.loginOregistrar(partes[1], partes[2]);

                if (u != null) {
                    this.usuario = u;
                    salida.writeUTF("LOGIN_OK|" + u.getId());

                    // AVISAR A TODOS (Broadcast de estado)
                    Servidor.broadcast("STATUS|" + usuario.getNombre() + "|ON", this);

                    // ENVIARME LA LISTA DE CONECTADOS (Sin Chat General)
                    StringBuilder sb = new StringBuilder();
                    for (HiloCliente cliente : Servidor.clientesConectados) {
                        if (cliente != this && cliente.usuario != null) {
                            sb.append(cliente.usuario.getNombre()).append(",");
                        }
                    }
                    if (sb.length() > 0) {
                        salida.writeUTF("LISTA_USUARIOS|" + sb.toString());
                    }

                    // --- BUCLE DEL CHAT ---
                    while (true) {
                        String mensajeRecibido = entrada.readUTF();

                        if (mensajeRecibido.startsWith("PV|")) {
                            // FORMATO: PV|Destinatario|Contenido
                            String[] seg = mensajeRecibido.split("\\|", 3);
                            String destino = seg[1];
                            String texto = seg[2];

                            int idDest = uDao.obtenerIdPorNombre(destino);

                            if (idDest != -1) {
                                // 1. GUARDAR EN BD (Persistencia)
                                mDao.guardarMensajePrivado(this.usuario.getId(), idDest, texto);

                                // 2. ENVIAR A DESTINATARIO SI ESTÁ ONLINE
                                String outMsg = "(PV) " + this.usuario.getNombre() + ": " + texto;
                                boolean enviado = false;
                                for (HiloCliente h : Servidor.clientesConectados) {
                                    if (h.usuario != null && h.usuario.getNombre().equals(destino)) {
                                        h.enviarMensaje("MSG|" + outMsg);
                                        enviado = true;
                                        break;
                                    }
                                }
                                // 3. ENVIARME A MÍ MISMO (Para verlo en pantalla)
                                this.enviarMensaje("MSG|" + outMsg);
                            }
                        }
                        else if (mensajeRecibido.startsWith("GET_HISTORIAL|")) {
                            String nombreOtro = mensajeRecibido.split("\\|")[1];
                            int idOtro = uDao.obtenerIdPorNombre(nombreOtro);

                            if (idOtro != -1) {
                                List<String> historial = mDao.obtenerHistorialPrivado(this.usuario.getId(), idOtro);
                                for (String m : historial) {
                                    enviarMensaje("MSG|" + m);
                                }
                            }
                        }
                    }
                } else {
                    salida.writeUTF("LOGIN_ERROR");
                }
            }
        } catch (IOException e) {
            Servidor.clientesConectados.remove(this);
            if (usuario != null) Servidor.broadcast("STATUS|" + usuario.getNombre() + "|OFF", this);
        }
    }

    public void enviarMensaje(String msg) {
        try { salida.writeUTF(msg); } catch (IOException e) {}
    }
    public String getNombreUsuario() {
        return (usuario != null) ? usuario.getNombre() : null;
    }
}