package org.example.chatflix.model;

import java.io.Serializable;

public class Usuario implements Serializable {
    private int id;
    private String nombre;

    // Constructor vacío
    public Usuario() {}

    // Constructor completo
    public Usuario(int id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
}