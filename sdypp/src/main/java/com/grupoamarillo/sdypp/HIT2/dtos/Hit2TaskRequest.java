package com.grupoamarillo.sdypp.HIT2.dtos;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hit2TaskRequest {
    @NotBlank
    private String calculo;
    @NotEmpty
    private Map<String, Object> parametros;

    private Map<String, Object> datosAdicionales;

    @NotBlank
    private String imagenDocker;

    /**
     * Timestamp lógico de Lamport del cliente.
     * Si el cliente no lo manda, se asume 0.
     */
    private long lamportTimestamp;
}
