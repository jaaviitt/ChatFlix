package org.example.chatflix.server.dao;

import org.example.chatflix.model.Usuario;
import org.example.chatflix.server.GestorBaseDatos;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

public class UsuarioDAO {

    /**
     * Intenta loguear al usuario.
     * Si el usuario NO existe, lo crea automáticamente (Registro).
     * Si SÍ existe, comprueba la contraseña.
     */
    public Usuario loginOregistrar(String nombre, String password) {
        String passCifrada = hashPassword(password); // Requisito de seguridad

        try (Connection conn = GestorBaseDatos.conectar()) {

            // 1. Buscamos si el usuario ya existe
            String sqlCheck = "SELECT id_usuario, password_hash FROM usuarios WHERE nombre_usuario = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlCheck)) {
                pstmt.setString(1, nombre);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    // --- EL USUARIO EXISTE: Verificamos contraseña ---
                    String passGuardada = rs.getString("password_hash");
                    if (passGuardada.equals(passCifrada)) {
                        return new Usuario(rs.getInt("id_usuario"), nombre);
                    } else {
                        System.out.println("Login fallido: Password incorrecta para " + nombre);
                        return null; // Contraseña mal
                    }
                }
            }

            // 2. Si no existe, lo --- REGISTRAMOS ---
            String sqlInsert = "INSERT INTO usuarios (nombre_usuario, password_hash) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, nombre);
                pstmt.setString(2, passCifrada);
                pstmt.executeUpdate();

                ResultSet rsKeys = pstmt.getGeneratedKeys();
                if (rsKeys.next()) {
                    System.out.println("Nuevo usuario registrado: " + nombre);
                    return new Usuario(rsKeys.getInt(1), nombre);
                }
            }

        } catch (SQLException e) {
            System.err.println("Error SQL: " + e.getMessage());
        }
        return null;
    }

    // --- Métodos Privados de Ayuda ---

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error al cifrar", e);
        }
    }
}