package com.grupoamarillo.sdypp.HIT1.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoteTaskResponse {
    //ESTABLEZCO EL MODELO DE DATOS DE LA RESPONSE
    private String status; 

    private Object resultado;

    private String mensaje;

    private String containerId;
  
    private Long duracionMs;
}
