package com.grupoamarillo.sdypp.HIT1.dtos;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoteTaskRequest {
    @NotBlank
    private String calculo;
    @NotEmpty
    private Map<String, Object> parametros;
    
    private Map<String, Object> datosAdicionales;
    @NotBlank
    private String imagenDocker;
    
    //DEFINO EL CONTRATO DE LA COMUNICACIÓN
    
}
