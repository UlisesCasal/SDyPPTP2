package com.grupoamarillo.sdypp.HIT3.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hit3CoordinatorRequest {
    // líder definitivo elegido por Bully
    private int leaderId;
}
