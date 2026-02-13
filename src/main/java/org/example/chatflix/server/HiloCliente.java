package org.example.chatflix.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;

// --- AÑADIDO ESTE IMPORT ---
import org.example.chatflix.server.dao.MensajeDAO;
import org.example.chatflix.server.dao.UsuarioDAO;
import org.example.chatflix.server.dao.GrupoDAO;
import org.example.chatflix.model.Usuario;

public class HiloCliente implements Runnable {

    private Socket socket;
    private DataInputStream entrada;
    private DataOutputStream salida;

    // DAOs
    private UsuarioDAO uDao;
    private GrupoDAO gDao;
    private MensajeDAO mDao; // --- AÑADIDA DECLARACIÓN QUE FALTABA ---

    private Usuario usuario;

    public HiloCliente(Socket socket) {
        this.socket = socket;
        try {
            // Inicializamos TODOS los DAOs
            this.uDao = new UsuarioDAO(GestorBaseDatos.conectar());
            this.gDao = new GrupoDAO(GestorBaseDatos.conectar());
            this.mDao = new MensajeDAO(GestorBaseDatos.conectar()); // --- AÑADIDA INICIALIZACIÓN ---

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
                // System.out.println("Comando: " + mensajeRecibido); // Descomenta si quieres depurar

                if (mensajeRecibido.startsWith("LOGIN|")) {
                    String[] partes = mensajeRecibido.split("\\|");
                    String user = partes[1];
                    String pass = partes[2];

                    boolean loginCorrecto = uDao.validarLogin(user, pass);

                    if (!loginCorrecto) {
                        if (uDao.obtenerIdPorNombre(user) == -1) {
                            if (uDao.registrarUsuario(user, pass)) {
                                System.out.println("Nuevo usuario registrado: " + user);
                                loginCorrecto = true;
                            }
                        }
                    }

                    if (loginCorrecto) {
                        int id = uDao.obtenerIdPorNombre(user);
                        this.usuario = new Usuario(id, user);

                        salida.writeUTF("LOGIN_OK");
                        Servidor.clientesConectados.add(this);
                        System.out.println("Cliente autenticado: " + user);

                        List<String> favs = uDao.obtenerFavoritos(this.usuario.getId());
                        if (!favs.isEmpty()) {
                            String csv = String.join(",", favs);
                            salida.writeUTF("LISTA_FAVORITOS|" + csv);
                        }

                        List<String> todos = uDao.obtenerTodosLosNombres();
                        StringBuilder listaUsers = new StringBuilder();
                        for (String u : todos) {
                            if (!u.equals(user)) {
                                boolean online = Servidor.clientesConectados.stream()
                                        .anyMatch(c -> c.usuario != null && c.usuario.getNombre().equals(u));
                                listaUsers.append(u).append(":").append(online ? "ON" : "OFF").append(",");
                            }
                        }
                        salida.writeUTF("LISTA_USUARIOS|" + listaUsers);

                        List<String> misGrupos = gDao.obtenerGruposDeUsuario(this.usuario.getId());
                        for (String g : misGrupos) {
                            // Reusamos el comando GRUPO_CREADO para que el cliente lo añada a su lista
                            salida.writeUTF("GRUPO_CREADO|" + g);
                        }

                        broadcast("STATUS|" + user + "|ON");

                    } else {
                        salida.writeUTF("LOGIN_ERROR");
                    }
                }

                // --- MENSAJE PRIVADO ---
                else if (mensajeRecibido.startsWith("PV|")) {
                    String[] p = mensajeRecibido.split("\\|", 3);
                    String destino = p[1];
                    String texto = p[2];

                    int idDestino = uDao.obtenerIdPorNombre(destino);

                    if(idDestino != -1) {
                        // CORRECCIÓN: Añadimos "TEXTO" como 4º argumento
                        mDao.guardarMensaje(this.usuario.getId(), idDestino, texto, "TEXTO");
                    }

                    // 2. Enviar si está online
                    boolean enviado = false;
                    for (HiloCliente h : Servidor.clientesConectados) {
                        if (h.usuario != null && h.usuario.getNombre().equals(destino)) {
                            h.enviarMensaje("MSG|" + this.usuario.getNombre() + ": " + texto);
                            enviado = true;
                            break;
                        }
                    }
                    // Me lo mando a mí mismo para confirmación visual
                    this.enviarMensaje("MSG|Yo: " + texto);
                }

                // --- GESTIÓN FAVORITOS ---
                else if (mensajeRecibido.startsWith("ADD_FAV|")) {
                    String nombreFav = mensajeRecibido.split("\\|")[1];
                    int idFav = uDao.obtenerIdPorNombre(nombreFav);
                    if (idFav != -1) uDao.agregarFavorito(this.usuario.getId(), idFav);
                }
                else if (mensajeRecibido.startsWith("DEL_FAV|")) {
                    String nombreFav = mensajeRecibido.split("\\|")[1];
                    int idFav = uDao.obtenerIdPorNombre(nombreFav);
                    if (idFav != -1) uDao.eliminarFavorito(this.usuario.getId(), idFav);
                }

                // --- COMANDOS DE ARCHIVOS (PROTOCOLO SEGURO) ---
                else if (mensajeRecibido.startsWith("FILE_B64|")) {
                    String[] partes = mensajeRecibido.split("\\|");
                    String destino = partes[1]; // Ahora aquí llegará "[Grupo] LQSA"

                    // 1. LEER TODO (IMPORTANTE para no bloquear el socket)
                    int len = entrada.readInt();
                    byte[] buffer = new byte[len];
                    entrada.readFully(buffer);
                    String base64 = new String(buffer);

                    // 2. GUARDAR EN BD
                    String nombreDestinoLimpio = destino.replace("[Grupo] ", ""); // Aquí sí limpiamos para buscar en BD

                    if (destino.startsWith("[Grupo] ")) {
                        // AHORA SÍ ENTRARÁ AQUÍ
                        int idGrupo = gDao.obtenerIdPorNombre(nombreDestinoLimpio);
                        if(idGrupo != -1) {
                            mDao.guardarMensajeGrupo(this.usuario.getId(), idGrupo, base64, "ARCHIVO");
                            System.out.println("Imagen guardada en grupo: " + nombreDestinoLimpio);
                        }
                    } else {
                        int idDestino = uDao.obtenerIdPorNombre(nombreDestinoLimpio);
                        if(idDestino != -1) mDao.guardarMensaje(this.usuario.getId(), idDestino, base64, "ARCHIVO");
                    }

                    // 3. REENVIAR
                    byte[] dataEnviar = base64.getBytes();

                    if (destino.startsWith("[Grupo] ")) {
                        int idGrupo = gDao.obtenerIdPorNombre(nombreDestinoLimpio);
                        List<Integer> miembros = gDao.obtenerIdsMiembros(idGrupo);

                        for (HiloCliente h : Servidor.clientesConectados) {
                            if (h.usuario != null && miembros.contains(h.usuario.getId()) && !h.usuario.getNombre().equals(this.usuario.getNombre())) {
                                h.salida.writeUTF("FILE_RX|" + destino + "|" + this.usuario.getNombre());
                                h.salida.writeInt(dataEnviar.length);
                                h.salida.write(dataEnviar);
                                h.salida.flush();
                            }
                        }
                    } else {
                        // Reenvío privado...
                        for (HiloCliente h : Servidor.clientesConectados) {
                            if (h.usuario != null && h.usuario.getNombre().equals(nombreDestinoLimpio)) {
                                h.salida.writeUTF("FILE_RX|" + this.usuario.getNombre() + "|" + this.usuario.getNombre());
                                h.salida.writeInt(dataEnviar.length);
                                h.salida.write(dataEnviar);
                                h.salida.flush();
                            }
                        }
                    }
                }
                // --- GRUPOS ---
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
                        // Guardar mensaje de grupo
                        mDao.guardarMensajeGrupo(this.usuario.getId(), idGrupo, texto, "TEXTO");

                        List<Integer> miembros = gDao.obtenerIdsMiembros(idGrupo);
                        for (HiloCliente h : Servidor.clientesConectados) {
                            if (h.usuario != null && miembros.contains(h.usuario.getId()) && !h.usuario.getNombre().equals(this.usuario.getNombre())) {
                                h.enviarMensaje("MSG|[Grupo] " + nombreGrupo + ": " + this.usuario.getNombre() + ": " + texto);
                            }
                        }
                        this.enviarMensaje("MSG|Yo: " + texto);
                    }
                }
                else if (mensajeRecibido.startsWith("KICK_USER|")) {
                    String[] partes = mensajeRecibido.split("\\|");
                    String nombreGrupo = partes[1];
                    String usuarioAExpulsar = partes[2];

                    int idGrupo = gDao.obtenerIdPorNombre(nombreGrupo);
                    int idUserKick = uDao.obtenerIdPorNombre(usuarioAExpulsar);

                    if (idGrupo != -1 && idUserKick != -1) {
                        if (gDao.eliminarUsuarioDeGrupo(idUserKick, idGrupo)) {
                            for (HiloCliente h : Servidor.clientesConectados) {
                                if (h.usuario != null && h.usuario.getNombre().equals(usuarioAExpulsar)) {
                                    h.enviarMensaje("MSG|Sistema: Has sido expulsado del grupo " + nombreGrupo);
                                }
                            }
                        }
                    }
                }

                // HISTORIAL PRIVADO
                else if (mensajeRecibido.startsWith("GET_HISTORIAL|")) {
                    String otroUsuario = mensajeRecibido.split("\\|")[1];
                    int idOtro = uDao.obtenerIdPorNombre(otroUsuario);
                    if(idOtro != -1) {
                        List<String> msgs = mDao.obtenerHistorialPrivado(this.usuario.getId(), idOtro);
                        for(String m : msgs) {
                            String[] datos = m.split("\\|", 3);
                            String tipo = datos[0];
                            String rem = datos[1];
                            String cont = datos[2];

                            if (tipo.equals("ARCHIVO")) {
                                // ENVIO SEGURO
                                salida.writeUTF("FILE_RX|" + rem + "|" + rem); // Header
                                byte[] b = cont.getBytes();
                                salida.writeInt(b.length); // Size
                                salida.write(b);           // Data
                            } else {
                                salida.writeUTF("MSG|" + rem + ": " + cont);
                            }
                        }
                    }
                }

                // HISTORIAL GRUPO
                else if (mensajeRecibido.startsWith("GET_HISTORIAL_GRUPO|")) {
                    String nombreGrupo = mensajeRecibido.split("\\|")[1];
                    int idGrupo = gDao.obtenerIdPorNombre(nombreGrupo);
                    if(idGrupo != -1) {
                        List<String> msgs = mDao.obtenerHistorialGrupo(idGrupo);
                        for(String m : msgs) {
                            String[] datos = m.split("\\|", 3);
                            String tipo = datos[0];
                            String rem = datos[1];
                            String cont = datos[2];

                            if (tipo.equals("ARCHIVO")) {
                                // ENVIO SEGURO
                                salida.writeUTF("FILE_RX|[Grupo] " + nombreGrupo + "|" + rem);
                                byte[] b = cont.getBytes();
                                salida.writeInt(b.length);
                                salida.write(b);
                            } else {
                                salida.writeUTF("MSG|[Grupo] " + nombreGrupo + ": " + rem + ": " + cont);
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