package org.example.chatflix.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;

import org.example.chatflix.server.dao.UsuarioDAO;
import org.example.chatflix.server.dao.GrupoDAO;
import org.example.chatflix.model.Usuario;

public class HiloCliente implements Runnable {

    private Socket socket;
    private DataInputStream entrada;
    private DataOutputStream salida;
    private UsuarioDAO uDao;
    private GrupoDAO gDao;
    private Usuario usuario;

    public HiloCliente(Socket socket) {
        this.socket = socket;
        try {
            this.uDao = new UsuarioDAO(GestorBaseDatos.conectar());
            this.gDao = new GrupoDAO(GestorBaseDatos.conectar());

            this.entrada = new DataInputStream(socket.getInputStream());
            this.salida = new DataOutputStream(socket.getOutputStream());
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                String mensajeRecibido = entrada.readUTF();
                System.out.println("Comando recibido: " + mensajeRecibido); // Debug

                if (mensajeRecibido.startsWith("LOGIN|")) {
                    String[] partes = mensajeRecibido.split("\\|");
                    String user = partes[1];
                    String pass = partes[2];

                    // Lógica Login/Registro separada
                    boolean loginCorrecto = uDao.validarLogin(user, pass);

                    if (!loginCorrecto) {
                        // Si falla el login, intentamos registrar
                        // Primero verificamos si el usuario ya existe para no dar error de SQL
                        if (uDao.obtenerIdPorNombre(user) == -1) {
                            if (uDao.registrarUsuario(user, pass)) {
                                System.out.println("Nuevo usuario registrado: " + user);
                                loginCorrecto = true;
                            }
                        }
                    }

                    if (loginCorrecto) {
                        // Cargamos los datos del usuario completo
                        int id = uDao.obtenerIdPorNombre(user);
                        this.usuario = new Usuario(id, user); // Crea un objeto usuario simple

                        salida.writeUTF("LOGIN_OK");
                        Servidor.clientesConectados.add(this);
                        System.out.println("Cliente autenticado: " + user);

                        // A) Enviar lista de favoritos
                        List<String> favs = uDao.obtenerFavoritos(this.usuario.getId());
                        if (!favs.isEmpty()) {
                            String csv = String.join(",", favs);
                            salida.writeUTF("LISTA_FAVORITOS|" + csv);
                        }

                        // B) Enviar lista de todos los usuarios
                        List<String> todos = uDao.obtenerTodosLosNombres();
                        StringBuilder listaUsers = new StringBuilder();
                        for (String u : todos) {
                            if (!u.equals(user)) { // No me envíes a mí mismo
                                // Comprobamos si está online buscando en la lista del Servidor
                                boolean online = Servidor.clientesConectados.stream()
                                        .anyMatch(c -> c.usuario != null && c.usuario.getNombre().equals(u));
                                listaUsers.append(u).append(":").append(online ? "ON" : "OFF").append(",");
                            }
                        }
                        salida.writeUTF("LISTA_USUARIOS|" + listaUsers);

                        // C) Avisar a los demás que estoy ON
                        broadcast("STATUS|" + user + "|ON");

                    } else {
                        salida.writeUTF("LOGIN_ERROR");
                    }
                }

                // --- RESTO DE COMANDOS ---

                else if (mensajeRecibido.startsWith("PV|")) {
                    String[] p = mensajeRecibido.split("\\|", 3);
                    String destino = p[1];
                    String texto = p[2];

                    // Buscar al destinatario en la lista de conectados
                    boolean enviado = false;
                    for (HiloCliente h : Servidor.clientesConectados) {
                        if (h.usuario != null && h.usuario.getNombre().equals(destino)) {
                            h.enviarMensaje("MSG|" + this.usuario.getNombre() + ": " + texto);
                            enviado = true;
                            break;
                        }
                    }
                    // Me lo mando a mí mismo también para que salga en mi chat
                    this.enviarMensaje("MSG|Yo: " + texto);
                }

                else if (mensajeRecibido.startsWith("ADD_FAV|")) {
                    String nombreFav = mensajeRecibido.split("\\|")[1];
                    int idFav = uDao.obtenerIdPorNombre(nombreFav);
                    if (idFav != -1) {
                        uDao.agregarFavorito(this.usuario.getId(), idFav);
                    }
                }

                else if (mensajeRecibido.startsWith("DEL_FAV|")) {
                    String nombreFav = mensajeRecibido.split("\\|")[1];
                    int idFav = uDao.obtenerIdPorNombre(nombreFav);
                    if (idFav != -1) {
                        uDao.eliminarFavorito(this.usuario.getId(), idFav);
                    }
                }
                // --- COMANDOS DE ARCHIVOS (FOTOS) ---
                else if (mensajeRecibido.startsWith("FILE|")) {
                    // Formato: FILE|Destino|NombreArchivo|Tamaño
                    String[] partes = mensajeRecibido.split("\\|");
                    String destino = partes[1];
                    String nombreArchivo = partes[2];
                    int tamano = Integer.parseInt(partes[3]);

                    byte[] buffer = new byte[tamano];
                    entrada.readFully(buffer); // Leemos los bytes de la imagen

                    // Reenviar al destinatario
                    for (HiloCliente h : Servidor.clientesConectados) {
                        if (h.usuario != null && h.usuario.getNombre().equals(destino)) {
                            // Reenviamos: FILE_RECIBIDO|Remitente|NombreArchivo|Tamaño
                            h.salida.writeUTF("FILE_RECIBIDO|" + this.usuario.getNombre() + "|" + nombreArchivo + "|" + tamano);
                            h.salida.write(buffer); // Mandamos los bytes
                            h.salida.flush();
                        }
                    }
                    this.salida.writeUTF("FILE_RECIBIDO|Yo|" + nombreArchivo + "|" + tamano);
                    this.salida.write(buffer);
                    this.salida.flush();
                }

                // --- COMANDOS DE GRUPOS ---
                else if (mensajeRecibido.startsWith("CREAR_GRUPO|")) {
                    String nombreGrupo = mensajeRecibido.split("\\|")[1];
                    if (gDao.crearGrupo(nombreGrupo, this.usuario.getId())) {
                        int idGrupo = gDao.obtenerIdPorNombre(nombreGrupo);
                        gDao.agregarMiembro(idGrupo, this.usuario.getId());
                        salida.writeUTF("GRUPO_CREADO|" + nombreGrupo);
                    }
                }

                else if (mensajeRecibido.startsWith("INVITAR_GRUPO|")) {
                    String[] p = mensajeRecibido.split("\\|");
                    String grupo = p[1];
                    String usuarioInvitado = p[2];

                    int idGrupo = gDao.obtenerIdPorNombre(grupo);
                    int idInvitado = uDao.obtenerIdPorNombre(usuarioInvitado);

                    if (idGrupo != -1 && idInvitado != -1) {
                        gDao.agregarMiembro(idGrupo, idInvitado);
                        // Avisar al invitado si está online
                        for (HiloCliente h : Servidor.clientesConectados) {
                            if (h.usuario != null && h.usuario.getNombre().equals(usuarioInvitado)) {
                                h.enviarMensaje("GRUPO_INVITACION|" + grupo);
                            }
                        }
                    }
                }

                else if (mensajeRecibido.startsWith("PV_GRUPO|")) {
                    String[] p = mensajeRecibido.split("\\|", 3);
                    String nombreGrupo = p[1];
                    String texto = p[2];

                    int idGrupo = gDao.obtenerIdPorNombre(nombreGrupo);
                    if (idGrupo != -1) {
                        List<Integer> miembros = gDao.obtenerIdsMiembros(idGrupo);
                        for (HiloCliente h : Servidor.clientesConectados) {
                            // Enviar a todos los miembros MENOS a mí mismo (yo ya sé lo que escribí)
                            if (h.usuario != null && miembros.contains(h.usuario.getId()) && !h.usuario.getNombre().equals(this.usuario.getNombre())) {
                                h.enviarMensaje("MSG|[Grupo] " + nombreGrupo + ": " + this.usuario.getNombre() + ": " + texto);
                            }
                        }
                        // Confirmación para mí
                        this.enviarMensaje("MSG|Yo: " + texto);
                    }
                }

                // --- COMANDO DE EXPULSIÓN (KICK) ---
                else if (mensajeRecibido.startsWith("KICK_USER|")) {
                    String[] partes = mensajeRecibido.split("\\|");
                    String nombreGrupo = partes[1];
                    String usuarioAExpulsar = partes[2];

                    int idGrupo = gDao.obtenerIdPorNombre(nombreGrupo);
                    int idUserKick = uDao.obtenerIdPorNombre(usuarioAExpulsar);

                    if (idGrupo != -1 && idUserKick != -1) {
                        boolean eliminado = gDao.eliminarUsuarioDeGrupo(idUserKick, idGrupo);
                        if (eliminado) {
                            System.out.println("KICK: " + usuarioAExpulsar + " fuera de " + nombreGrupo);
                            // Avisar al expulsado
                            for (HiloCliente h : Servidor.clientesConectados) {
                                if (h.usuario != null && h.usuario.getNombre().equals(usuarioAExpulsar)) {
                                    h.enviarMensaje("MSG|Sistema: Has sido expulsado del grupo " + nombreGrupo);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado");
        } finally {
            if (this.usuario != null) {
                Servidor.clientesConectados.remove(this);
                broadcast("STATUS|" + this.usuario.getNombre() + "|OFF");
            }
            try { socket.close(); } catch (IOException e) {}
        }
    }

    public void enviarMensaje(String msg) {
        try { salida.writeUTF(msg); } catch (IOException e) {}
    }

    private void broadcast(String msg) {
        for (HiloCliente h : Servidor.clientesConectados) {
            h.enviarMensaje(msg);
        }
    }
}