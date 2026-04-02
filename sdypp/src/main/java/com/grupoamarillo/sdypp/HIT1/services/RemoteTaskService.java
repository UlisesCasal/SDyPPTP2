package com.grupoamarillo.sdypp.HIT1.services;

import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;

public interface RemoteTaskService {
    //Defino los metodos que debera tener un servicio
    RemoteTaskResponse ejecutarTareaRemota(RemoteTaskRequest request);
}
