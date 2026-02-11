package org.example.chatflix.server.dao;

import org.example.chatflix.server.GestorBaseDatos;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MensajeDAO {

    // Guardar un mensaje nuevo en la BD (Asignado al Grupo 1 - General)
    public void guardarMensaje(int idOrigen, String contenido) {
        // OJO: Añadimos 'id_destino_grupo' en la consulta
        String sql = "INSERT INTO mensajes (id_origen, id_destino_grupo, contenido, fecha_envio) VALUES (?, 1, ?, CURRENT_TIMESTAMP)";

        try (Connection conn = GestorBaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, idOrigen);
            // El 1 ya está puesto en la SQL (VALUES ..., 1, ...)
            pstmt.setString(2, contenido);

            pstmt.executeUpdate();
            System.out.println("Mensaje guardado en BD correctamente."); // Log para confirmar

        } catch (SQLException e) {
            System.err.println("Error al guardar mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Recuperar los últimos 50 mensajes (Historial)
    public List<String> obtenerHistorial() {
        List<String> historial = new ArrayList<>();
        // Hacemos un JOIN para obtener el nombre del usuario que escribió el mensaje
        String sql = """
            SELECT u.nombre_usuario, m.contenido 
            FROM mensajes m 
            JOIN usuarios u ON m.id_origen = u.id_usuario 
            ORDER BY m.fecha_envio ASC 
            LIMIT 50
        """;

        try (Connection conn = GestorBaseDatos.conectar();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String nombre = rs.getString("nombre_usuario");
                String texto = rs.getString("contenido");
                historial.add(nombre + ": " + texto);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return historial;
    }
}