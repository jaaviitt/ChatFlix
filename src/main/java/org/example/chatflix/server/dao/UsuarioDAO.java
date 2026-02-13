package org.example.chatflix.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UsuarioDAO {

    private Connection conexion;

    public UsuarioDAO(Connection conexion) {
        this.conexion = conexion;
    }
    // --- REGISTRO Y LOGIN ---

    public boolean registrarUsuario(String nombre, String password) {
        String sql = "INSERT INTO usuarios (nombre_usuario, password_hash) VALUES (?, ?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, password);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean validarLogin(String nombre, String password) {
        String sql = "SELECT * FROM usuarios WHERE nombre_usuario = ? AND password_hash = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) { return false; }
    }

    // --- MÉTODOS DE UTILIDAD ---

    public int obtenerIdPorNombre(String nombre) {
        // CORREGIDO: id_usuario y nombre_usuario
        String sql = "SELECT id_usuario FROM usuarios WHERE nombre_usuario = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id_usuario");
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    // --- GESTIÓN DE FAVORITOS ---

    public boolean agregarFavorito(int idUsuario, int idFavorito) {
        String sql = "INSERT OR IGNORE INTO favoritos (id_usuario, id_favorito) VALUES (?, ?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idFavorito);
            int filas = ps.executeUpdate();
            if(filas > 0) System.out.println("DAO: Favorito agregado correctamente.");
            return filas > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean eliminarFavorito(int idUsuario, int idFavorito) {
        String sql = "DELETE FROM favoritos WHERE id_usuario = ? AND id_favorito = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idFavorito);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public List<String> obtenerFavoritos(int idUsuario) {
        List<String> lista = new ArrayList<>();
        String sql = "SELECT u.nombre_usuario FROM usuarios u " +
                "JOIN favoritos f ON u.id_usuario = f.id_favorito " +
                "WHERE f.id_usuario = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(rs.getString("nombre_usuario"));
            }
        } catch (SQLException e) {
            System.err.println("DAO ERROR GET FAVS: " + e.getMessage());
        }
        return lista;
    }

    public List<String> obtenerTodosLosNombres() {
        List<String> usuarios = new ArrayList<>();
        String sql = "SELECT nombre_usuario FROM usuarios";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                usuarios.add(rs.getString("nombre_usuario"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return usuarios;
    }
}