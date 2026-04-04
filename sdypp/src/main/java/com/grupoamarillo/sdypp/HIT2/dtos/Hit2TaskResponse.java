package com.grupoamarillo.sdypp.HIT2.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hit2TaskResponse {
    private String status;
    private Object resultado;
    private String mensaje;
    private String containerId;
    private Long duracionMs;

    //Timestamp de Lamport del servidor
    private long lamportTimestamp;

    /** Posición que tuvo en la cola (para que el cliente vea el ordenamiento) */
    private long posicionEnCola;
}
