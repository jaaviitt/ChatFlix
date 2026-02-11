package org.example.chatflix.server.dao;

import org.example.chatflix.server.GestorBaseDatos;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MensajeDAO {

    public void guardarMensajePrivado(int idOrigen, int idDestino, String contenido) {
        // IMPORTANTE: Ponemos id_destino_grupo a NULL explícitamente
        String sql = "INSERT INTO mensajes (id_origen, id_destino_usuario, id_destino_grupo, contenido) VALUES (?, ?, NULL, ?)";

        try (Connection conn = GestorBaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, idOrigen);
            pstmt.setInt(2, idDestino);
            pstmt.setString(3, contenido);
            pstmt.executeUpdate();
            System.out.println("DEBUG: Guardado mensaje de " + idOrigen + " a " + idDestino);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> obtenerHistorialPrivado(int idYo, int idOtro) {
        List<String> historial = new ArrayList<>();
        // Busca mensajes enviados por MI a ÉL o por ÉL a MÍ
        String sql = """
            SELECT u.nombre_usuario, m.contenido 
            FROM mensajes m 
            JOIN usuarios u ON m.id_origen = u.id_usuario 
            WHERE (m.id_origen = ? AND m.id_destino_usuario = ?) 
               OR (m.id_origen = ? AND m.id_destino_usuario = ?)
            ORDER BY m.fecha_envio ASC
        """;

        try (Connection conn = GestorBaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idYo);
            pstmt.setInt(2, idOtro);
            pstmt.setInt(3, idOtro);
            pstmt.setInt(4, idYo);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                historial.add("(PV) " + rs.getString("nombre_usuario") + ": " + rs.getString("contenido"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return historial;
    }
}