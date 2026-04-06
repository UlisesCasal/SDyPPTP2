package com.grupoamarillo.sdypp.HIT3.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hit3ElectionResponse {
    // true => "estoy vivo y tengo ID mayor, yo sigo la elección"
    private boolean ok;
    //nodo que responde
    private int responderId;
}
