package org.example.chatflix.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Servidor {
    private static final int PUERTO = 12345;

    // LISTA DE CLIENTES CONECTADOS
    public static List<HiloCliente> clientesConectados = new ArrayList<>();

    public static void main(String[] args) {
        GestorBaseDatos.inicializar();

        try (ServerSocket servidor = new ServerSocket(PUERTO)) {
            System.out.println("Servidor ChatFlix escuchando en puerto " + PUERTO);

            while (true) {
                Socket cliente = servidor.accept();
                System.out.println("Nuevo cliente conectado.");

                // Creamos el hilo
                HiloCliente hilo = new HiloCliente(cliente);

                // Lo guardamos en la lista
                clientesConectados.add(hilo);

                new Thread(hilo).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Método para enviar un mensaje a TODOS (Broadcast)
    public static void broadcast(String mensaje, HiloCliente remitente) {
        for (HiloCliente cliente : clientesConectados) {
            cliente.enviarMensaje(mensaje);
        }
    }
}