package com.grupoamarillo.sdypp.HIT1.dtos;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskServiceRequest {
    private String calculo;
    private Map<String, Object> parametros;
    private Map<String, Object> datosAdicionales;
}
