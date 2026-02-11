package org.example.chatflix.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class GestorBaseDatos {

    // Nombre del archivo de la base de datos
    private static final String URL = "jdbc:sqlite:chat_proyecto.db";

    public static void inicializar() {
        // SQL para crear las tablas requeridas por el proyecto
        String sql = """
            -- Tabla de Usuarios (Requisito 2.1)
            CREATE TABLE IF NOT EXISTS usuarios (
                id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre_usuario TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            
            -- Tabla de Contactos (Requisito 2.2)
            CREATE TABLE IF NOT EXISTS contactos (
                id_usuario INTEGER,
                id_contacto INTEGER,
                fecha_agregado DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (id_usuario, id_contacto),
                FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
                FOREIGN KEY (id_contacto) REFERENCES usuarios(id_usuario)
            );

            -- Tabla de Grupos (Requisito 2.4)
            CREATE TABLE IF NOT EXISTS grupos (
                id_grupo INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre_grupo TEXT NOT NULL,
                id_admin INTEGER NOT NULL,
                fecha_creacion DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (id_admin) REFERENCES usuarios(id_usuario)
            );

            -- Miembros del Grupo
            CREATE TABLE IF NOT EXISTS miembros_grupo (
                id_grupo INTEGER,
                id_usuario INTEGER,
                fecha_union DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (id_grupo, id_usuario),
                FOREIGN KEY (id_grupo) REFERENCES grupos(id_grupo),
                FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
            );

            -- Tabla Unificada de Mensajes (Requisitos 2.3 y 2.4)
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

            // Ejecutamos la creación de tablas
            stmt.executeUpdate(sql);
            System.out.println("Base de datos verificada/creada correctamente.");

        } catch (SQLException e) {
            System.out.println("Error al inicializar la base de datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método para conectar (lo usaremos más adelante)
    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}