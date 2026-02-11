package org.example.chatflix.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {

    private static final int PUERTO = 12345; // Puerto donde escuchará el servidor

    public static void main(String[] args) {
        System.out.println("--- Iniciando Servidor ---");

        // 1. Inicializar la Base de Datos (Requisito indispensable)
        System.out.println("Verificando base de datos...");
        GestorBaseDatos.inicializar();

        // 2. Iniciar el Socket del Servidor (Requisito 3 - Sockets TCP)
        try (ServerSocket servidor = new ServerSocket(PUERTO)) {
            System.out.println("Servidor esperando conexiones en el puerto " + PUERTO + "...");

            // Bucle infinito para aceptar clientes
            while (true) {
                // El servidor se queda "congelado" aquí hasta que alguien se conecta
                Socket cliente = servidor.accept();
                System.out.println("¡Nuevo cliente conectado!: " + cliente.getInetAddress());

                // AQUÍ LANZAREMOS EL HILO MÁS ADELANTE (Requisito Multihilo)
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }
}