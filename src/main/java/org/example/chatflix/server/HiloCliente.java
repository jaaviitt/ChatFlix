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
                        // BLOQUE 2: HISTORIAL PRIVADO
                        else if (mensajeRecibido.startsWith("GET_HISTORIAL|")) {
                            String nombreOtro = mensajeRecibido.split("\\|")[1];
                            int idOtro = uDao.obtenerIdPorNombre(nombreOtro);

                            if (idOtro != -1) {
                                // Obtenemos la lista bruta de la BD
                                List<String> historial = mDao.obtenerHistorialPrivado(this.usuario.getId(), idOtro);
                                // Se la pasamos al "limpiador" que envía textos o fotos según toque
                                enviarHistorialConFotos(historial);
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
                        // BLOQUE 3: HISTORIAL GRUPO CON FOTOS
                        else if (mensajeRecibido.startsWith("GET_HISTORIAL_GRUPO|")) {
                            String nombreGrupo = mensajeRecibido.split("\\|")[1];
                            int idGrupo = gDao.obtenerIdPorNombre(nombreGrupo);

                            if (idGrupo != -1) {
                                List<String> historial = mDao.obtenerHistorialGrupo(idGrupo);
                                enviarHistorialConFotos(historial); // Método auxiliar abajo
                            }
                        }
                        // BLOQUE 1: RECEPCIÓN Y GUARDADO DE ARCHIVOS
                        else if (mensajeRecibido.startsWith("FILE|")) {
                            String[] partesFile = mensajeRecibido.split("\\|");
                            String destinoNombre = partesFile[1];
                            String nombreFichero = partesFile[2];
                            int tamano = Integer.parseInt(partesFile[3]);

                            byte[] buffer = new byte[tamano];
                            entrada.readFully(buffer);

                            // 1. GUARDAR EN DISCO (Carpeta uploads)
                            File folder = new File("uploads");
                            if (!folder.exists()) folder.mkdir();

                            // Nombre único para evitar sobrescribir
                            String nombreUnico = System.currentTimeMillis() + "_" + nombreFichero;
                            try (FileOutputStream fos = new FileOutputStream(new File(folder, nombreUnico))) {
                                fos.write(buffer);
                            }

                            // 2. VERIFICAR SI ES GRUPO O USUARIO
                            int idGrupo = gDao.obtenerIdPorNombre(destinoNombre); // Probamos si es grupo

                            if (idGrupo != -1) {
                                // --- ES UN GRUPO ---
                                // A. Guardar en BD como mensaje de grupo
                                mDao.guardarMensajeEnGrupo(this.usuario.getId(), idGrupo, "IMG:" + nombreUnico);

                                // B. Reenviar a los miembros conectados
                                List<Integer> miembrosIds = gDao.obtenerIdsMiembros(idGrupo);
                                for (HiloCliente hc : Servidor.clientesConectados) {
                                    // Enviamos a todos MENOS a mí mismo (yo ya la veo porque la acabo de subir)
                                    if (hc.usuario != null && miembrosIds.contains(hc.usuario.getId())
                                            && hc.usuario.getId() != this.usuario.getId()) {

                                        hc.enviarMensajeConArchivo(this.usuario.getNombre(), nombreUnico, buffer);
                                    }
                                }
                            } else {
                                // --- ES UN USUARIO PRIVADO ---
                                int idDestino = uDao.obtenerIdPorNombre(destinoNombre);
                                if (idDestino != -1) {
                                    // A. Guardar en BD privado
                                    mDao.guardarMensajePrivado(this.usuario.getId(), idDestino, "IMG:" + nombreUnico);

                                    // B. Reenviar al destinatario si está online
                                    for (HiloCliente hc : Servidor.clientesConectados) {
                                        if (hc.usuario != null && hc.usuario.getNombre().equals(destinoNombre)) {
                                            hc.enviarMensajeConArchivo(this.usuario.getNombre(), nombreUnico, buffer);
                                            break;
                                        }
                                    }
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

    public void enviarMensajeConArchivo(String remitente, String nombreFichero, byte[] contenido) {
        try {
            // 1. Avisamos al cliente: "Oye, te va un archivo de este remitente y este tamaño"
            salida.writeUTF("FILE_RECIBIDO|" + remitente + "|" + nombreFichero + "|" + contenido.length);

            // 2. Mandamos los bytes puros justo después
            salida.write(contenido);
            salida.flush();

            System.out.println("Servidor: Enviando " + nombreFichero + " de " + remitente + " a " + this.usuario.getNombre());
        } catch (IOException e) {
            System.err.println("Error al enviar archivo al cliente: " + e.getMessage());
        }
    }

    // Método auxiliar para procesar el historial y detectar imágenes
    private void enviarHistorialConFotos(List<String> historial) {
        for (String msg : historial) {
            // El formato de msg viene del DAO: "Nombre: Contenido" o "(Grp) Nombre: Contenido"
            // Buscamos si el contenido tiene la marca "IMG:"
            if (msg.contains(": IMG:")) {
                try {
                    // Separamos: [0] -> "Pepe", [1] -> "IMG:1234_foto.jpg"
                    String[] partes = msg.split(": IMG:");
                    String remitenteCompleto = partes[0]; // Puede ser "Pepe" o "(PV) Pepe"
                    String nombreArchivo = partes[1].trim();

                    // Limpiamos el nombre del remitente para que quede limpio en la burbuja
                    String remitenteLimpio = remitenteCompleto.replace("(PV) ", "").replace("(Grp) ", "").trim();

                    File f = new File("uploads/" + nombreArchivo);
                    if (f.exists()) {
                        byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                        enviarMensajeConArchivo(remitenteLimpio, nombreArchivo, bytes);
                    } else {
                        enviarMensaje("MSG|Sistema: La imagen " + nombreArchivo + " no se encuentra en el servidor.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                // Es texto normal
                enviarMensaje("MSG|" + msg);
            }
        }
    }

    public void enviarMensaje(String msg) {
        try { salida.writeUTF(msg); } catch (IOException e) {}
    }
    public String getNombreUsuario() {
        return (usuario != null) ? usuario.getNombre() : null;
    }
}