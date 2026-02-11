package org.example.chatflix.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class GestorBaseDatos {

    private static final String URL = "jdbc:sqlite:chat_proyecto.db";

    public static void inicializar() {
        // SQL unificado para crear la infraestructura (Requisitos 2.1 a 2.4)
        String sql = """
            CREATE TABLE IF NOT EXISTS usuarios (
                id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre_usuario TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            
            CREATE TABLE IF NOT EXISTS contactos (
                id_usuario INTEGER,
                id_contacto INTEGER,
                fecha_agregado DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (id_usuario, id_contacto),
                FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
                FOREIGN KEY (id_contacto) REFERENCES usuarios(id_usuario)
            );

            CREATE TABLE IF NOT EXISTS grupos (
                id_grupo INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre_grupo TEXT NOT NULL,
                id_admin INTEGER NOT NULL,
                fecha_creacion DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (id_admin) REFERENCES usuarios(id_usuario)
            );

            CREATE TABLE IF NOT EXISTS miembros_grupo (
                id_grupo INTEGER,
                id_usuario INTEGER,
                fecha_union DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (id_grupo, id_usuario),
                FOREIGN KEY (id_grupo) REFERENCES grupos(id_grupo),
                FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
            );

            CREATE TABLE IF NOT EXISTS mensajes (
                id_mensaje INTEGER PRIMARY KEY AUTOINCREMENT,
                id_origen INTEGER NOT NULL,
                id_destino_usuario INTEGER,
                id_destino_grupo INTEGER,
                contenido TEXT NOT NULL,
                tipo_mensaje TEXT CHECK(tipo_mensaje IN ('TEXTO', 'ARCHIVO')) DEFAULT 'TEXTO',
                fecha_envio DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (id_origen) REFERENCES usuarios(id_usuario),
                FOREIGN KEY (id_destino_usuario) REFERENCES usuarios(id_usuario),
                FOREIGN KEY (id_destino_grupo) REFERENCES grupos(id_grupo),
                CHECK (
                    (id_destino_usuario IS NOT NULL AND id_destino_grupo IS NULL) OR 
                    (id_destino_usuario IS NULL AND id_destino_grupo IS NOT NULL)
                )
            );
        """;

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {

            // Creamos la estructura de tablas (sin insertar datos basura)
            stmt.executeUpdate(sql);
            System.out.println("Base de datos inicializada: Estructura de Usuarios y Grupos lista.");

        } catch (SQLException e) {
            System.err.println("Error al inicializar la base de datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}