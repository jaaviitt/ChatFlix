package org.example.chatflix.server;

import org.example.chatflix.server.dao.GrupoDAO;
import org.example.chatflix.server.dao.MensajeDAO;
import org.example.chatflix.server.dao.UsuarioDAO;
import org.example.chatflix.model.Usuario;
import java.io.*;
import java.net.Socket;
import java.util.List;

public class HiloCliente implements Runnable {

    private Socket socket;
    private DataInputStream entrada;
    private DataOutputStream salida;
    private Usuario usuario;

    // Instancias DAO
    private UsuarioDAO uDao = new UsuarioDAO();
    private MensajeDAO mDao = new MensajeDAO();
    private GrupoDAO gDao = new GrupoDAO();

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

                    // 1. AVISAR A TODOS (Broadcast)
                    Servidor.broadcast("STATUS|" + usuario.getNombre() + "|ON", this);

                    // 2. ENVIARME LA LISTA COMPLETA DE USUARIOS
                    List<String> todosLosUsuarios = uDao.obtenerTodosLosNombres();
                    StringBuilder sb = new StringBuilder();

                    for (String nombreUser : todosLosUsuarios) {
                        if (nombreUser.equals(this.usuario.getNombre())) continue;

                        boolean isOnline = false;
                        for (HiloCliente conectado : Servidor.clientesConectados) {
                            if (conectado.getNombreUsuario() != null && conectado.getNombreUsuario().equals(nombreUser)) {
                                isOnline = true;
                                break;
                            }
                        }
                        sb.append(nombreUser).append(":").append(isOnline ? "ON" : "OFF").append(",");
                    }

                    if (sb.length() > 0) {
                        salida.writeUTF("LISTA_USUARIOS|" + sb.toString());
                    }

                    // 3. RECUPERAR MIS GRUPOS DE LA BD (Aunque haya estado Offline)
                    List<String> misGrupos = gDao.obtenerGruposUsuario(this.usuario.getId());
                    for (String grupo : misGrupos) {
                        // Enviamos el comando de invitación para que el cliente lo pinte en la lista
                        salida.writeUTF("GRUPO_INVITACION|" + grupo);
                    }

                    // --- BUCLE DEL CHAT ---
                    while (true) {
                        String mensajeRecibido = entrada.readUTF();

                        if (mensajeRecibido.startsWith("PV|")) {
                            String[] seg = mensajeRecibido.split("\\|", 3);
                            String destino = seg[1];
                            String texto = seg[2];

                            int idDest = uDao.obtenerIdPorNombre(destino);
                            if (idDest != -1) {
                                mDao.guardarMensajePrivado(this.usuario.getId(), idDest, texto);
                                String outMsg = "(PV) " + this.usuario.getNombre() + ": " + texto;

                                for (HiloCliente h : Servidor.clientesConectados) {
                                    if (h.usuario != null && h.usuario.getNombre().equals(destino)) {
                                        h.enviarMensaje("MSG|" + outMsg);
                                        break;
                                    }
                                }
                                this.enviarMensaje("MSG|" + outMsg);
                            }
                        }
                        else if (mensajeRecibido.startsWith("GET_HISTORIAL|")) {
                            String nombreOtro = mensajeRecibido.split("\\|")[1];
                            int idOtro = uDao.obtenerIdPorNombre(nombreOtro);
                            if (idOtro != -1) {
                                List<String> historial = mDao.obtenerHistorialPrivado(this.usuario.getId(), idOtro);
                                for (String m : historial) enviarMensaje("MSG|" + m);
                            }
                        }
                        else if (mensajeRecibido.startsWith("CREAR_GRUPO|")) {
                            String nombreGrupo = mensajeRecibido.split("\\|")[1];
                            // Usamos el gDao de la clase, no creamos uno nuevo
                            boolean exito = gDao.crearGrupo(nombreGrupo, this.usuario.getId());
                            if (exito) {
                                enviarMensaje("GRUPO_CREADO|" + nombreGrupo);
                                enviarMensaje("MSG|Sistema: Has creado el grupo '" + nombreGrupo + "'.");
                            } else {
                                enviarMensaje("MSG|Sistema: Error al crear el grupo.");
                            }
                        }
                        else if (mensajeRecibido.startsWith("INVITAR_GRUPO|")) {
                            String[] partesInv = mensajeRecibido.split("\\|");
                            String grupo = partesInv[1];
                            String invitado = partesInv[2];

                            int idUser = uDao.obtenerIdPorNombre(invitado);
                            int idGrupo = gDao.obtenerIdPorNombre(grupo);

                            if (idUser != -1 && idGrupo != -1) {
                                if (gDao.añadirMiembro(idGrupo, idUser)) {
                                    for (HiloCliente hc : Servidor.clientesConectados) {
                                        if (hc.getNombreUsuario() != null && hc.getNombreUsuario().equals(invitado)) {
                                            hc.enviarMensaje("GRUPO_INVITACION|" + grupo);
                                        }
                                    }
                                    enviarMensaje("MSG|Sistema: " + invitado + " añadido a " + grupo);
                                }
                            }
                        }
                        else if (mensajeRecibido.startsWith("PV_GRUPO|")) {
                            String[] partesGrupo = mensajeRecibido.split("\\|", 3);
                            String nombreGrupo = partesGrupo[1];
                            String texto = partesGrupo[2];

                            int idGrupo = gDao.obtenerIdPorNombre(nombreGrupo);
                            if (idGrupo != -1) {
                                mDao.guardarMensajeEnGrupo(this.usuario.getId(), idGrupo, texto);
                                List<Integer> miembrosIds = gDao.obtenerIdsMiembros(idGrupo);
                                String formato = "[Grupo " + nombreGrupo + "] " + this.usuario.getNombre() + ": " + texto;

                                for (HiloCliente hc : Servidor.clientesConectados) {
                                    if (hc.usuario != null && miembrosIds.contains(hc.usuario.getId())) {
                                        hc.enviarMensaje("MSG|" + formato);
                                    }
                                }
                            }
                        }
                        else if (mensajeRecibido.startsWith("GET_HISTORIAL_GRUPO|")) {
                            String nombreGrupo = mensajeRecibido.split("\\|")[1];
                            int idGrupo = gDao.obtenerIdPorNombre(nombreGrupo);
                            if (idGrupo != -1) {
                                List<String> historial = mDao.obtenerHistorialGrupo(idGrupo);
                                for (String msg : historial) enviarMensaje("MSG|" + msg);
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