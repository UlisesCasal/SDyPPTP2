package com.grupoamarillo.sdypp.HIT3.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hit3ElectionRequest {
    // ID del nodo que está iniciando la elección
    private int candidateId;
}
