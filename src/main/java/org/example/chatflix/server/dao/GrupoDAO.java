package org.example.chatflix.server.dao;

import org.example.chatflix.server.GestorBaseDatos;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GrupoDAO {

    private Connection conexion;

    // CONSTRUCTOR QUE RECIBE LA CONEXIÓN
    public GrupoDAO(Connection conexion) {
        this.conexion = conexion;
    }

    public boolean crearGrupo(String nombreGrupo, int idAdmin) {
        String sqlGrupo = "INSERT INTO grupos (nombre_grupo, id_admin) VALUES (?, ?)";
        String sqlMiembro = "INSERT INTO miembros_grupo (id_grupo, id_usuario) VALUES (?, ?)";

        Connection conn = null;

        try {
            conn = GestorBaseDatos.conectar();
            conn.setAutoCommit(false); // INICIO TRANSACCIÓN

            // 1. Insertar el Grupo
            PreparedStatement pstmtGrupo = conn.prepareStatement(sqlGrupo, Statement.RETURN_GENERATED_KEYS);
            pstmtGrupo.setString(1, nombreGrupo);
            pstmtGrupo.setInt(2, idAdmin);
            pstmtGrupo.executeUpdate();

            // 2. Obtener el ID del grupo recién creado
            ResultSet rs = pstmtGrupo.getGeneratedKeys();
            int idGrupo = 0;
            if (rs.next()) {
                idGrupo = rs.getInt(1);
            }

            // 3. Insertar al Admin como miembro
            PreparedStatement pstmtMiembro = conn.prepareStatement(sqlMiembro);
            pstmtMiembro.setInt(1, idGrupo);
            pstmtMiembro.setInt(2, idAdmin);
            pstmtMiembro.executeUpdate();

            conn.commit(); // CONFIRMAR CAMBIOS
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            return false;
        } finally {
            try { if (conn != null) conn.setAutoCommit(true); conn.close(); } catch (SQLException e) {}
        }
    }

    // Método para saber en qué grupos estoy (para listarlos al entrar)
    public List<String> obtenerGruposUsuario(int idUsuario) {
        List<String> grupos = new ArrayList<>();
        String sql = """
            SELECT g.nombre_grupo 
            FROM grupos g
            JOIN miembros_grupo mg ON g.id_grupo = mg.id_grupo
            WHERE mg.id_usuario = ?
        """;

        try (Connection conn = GestorBaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                grupos.add(rs.getString("nombre_grupo"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return grupos;
    }

    public boolean agregarMiembro(int idGrupo, int idUsuario) {
        String sql = "INSERT OR IGNORE INTO miembros_grupo (id_grupo, id_usuario) VALUES (?, ?)";
        try (Connection conn = GestorBaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idGrupo);
            pstmt.setInt(2, idUsuario);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean eliminarUsuarioDeGrupo(int idUsuario, int idGrupo) {
        String sql = "DELETE FROM miembros_grupo WHERE id_usuario = ? AND id_grupo = ?";
        try (Connection conn = GestorBaseDatos.conectar();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idGrupo);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int obtenerIdPorNombre(String nombreGrupo) {
        String sql = "SELECT id_grupo FROM grupos WHERE nombre_grupo = ?";
        try (Connection conn = GestorBaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreGrupo);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_grupo");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1; // No encontrado
    }

    public List<Integer> obtenerIdsMiembros(int idGrupo) {
        List<Integer> miembros = new ArrayList<>();
        String sql = "SELECT id_usuario FROM miembros_grupo WHERE id_grupo = ?";

        try (Connection conn = GestorBaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idGrupo);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                miembros.add(rs.getInt("id_usuario"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return miembros;
    }
}