package org.example.chatflix.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MensajeDAO {

    private Connection conexion;

    public MensajeDAO(Connection conexion) {
        this.conexion = conexion;
    }

    // Modificado para aceptar TIPO
    public boolean guardarMensaje(int idOrigen, int idDestino, String contenido, String tipo) {
        String sql = "INSERT INTO mensajes (id_origen, id_destino_usuario, contenido, tipo_mensaje) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idOrigen);
            ps.setInt(2, idDestino);
            ps.setString(3, contenido);
            ps.setString(4, tipo); // 'TEXTO' o 'ARCHIVO'
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean guardarMensajeGrupo(int idOrigen, int idGrupo, String contenido, String tipo) {
        String sql = "INSERT INTO mensajes (id_origen, id_destino_grupo, contenido, tipo_mensaje) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idOrigen);
            ps.setInt(2, idGrupo);
            ps.setString(3, contenido);
            ps.setString(4, tipo);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // Devuelve lista formateada: "TIPO|Remitente|Contenido"
    public List<String> obtenerHistorialPrivado(int idUsuario1, int idUsuario2) {
        List<String> historial = new ArrayList<>();
        String sql = "SELECT u.nombre_usuario, m.contenido, m.tipo_mensaje FROM mensajes m " +
                "JOIN usuarios u ON m.id_origen = u.id_usuario " +
                "WHERE (m.id_origen = ? AND m.id_destino_usuario = ?) " +
                "   OR (m.id_origen = ? AND m.id_destino_usuario = ?) " +
                "ORDER BY m.fecha_envio ASC";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idUsuario1); ps.setInt(2, idUsuario2);
            ps.setInt(3, idUsuario2); ps.setInt(4, idUsuario1);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                // Formato: TIPO|Nombre|Contenido
                historial.add(rs.getString("tipo_mensaje") + "|" + rs.getString("nombre_usuario") + "|" + rs.getString("contenido"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return historial;
    }

    public List<String> obtenerHistorialGrupo(int idGrupo) {
        List<String> historial = new ArrayList<>();
        String sql = "SELECT u.nombre_usuario, m.contenido, m.tipo_mensaje FROM mensajes m " +
                "JOIN usuarios u ON m.id_origen = u.id_usuario " +
                "WHERE m.id_destino_grupo = ? " +
                "ORDER BY m.fecha_envio ASC";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idGrupo);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                historial.add(rs.getString("tipo_mensaje") + "|" + rs.getString("nombre_usuario") + "|" + rs.getString("contenido"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return historial;
    }
}