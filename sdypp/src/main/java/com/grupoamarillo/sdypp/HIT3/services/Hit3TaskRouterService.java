package com.grupoamarillo.sdypp.HIT3.services;

import org.springframework.stereotype.Service;

import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;
import com.grupoamarillo.sdypp.HIT1.services.RemoteTaskService;

@Service
public class Hit3TaskRouterService {
    //Servicio original del HIT1 que ejecuta la tarea dockerizada
    private final RemoteTaskService remoteTaskService;

    //Nuevo coordinador del cluster HIT3
    //Se encarga de Lider + Bully + Asignación
    private final Hit3ClusterCoordinatorService clusterService; 
    
    public Hit3TaskRouterService(RemoteTaskService remoteTaskService, Hit3ClusterCoordinatorService clusterService){
        this.remoteTaskService = remoteTaskService;
        this.clusterService = clusterService;
    }

    public RemoteTaskResponse route(RemoteTaskRequest request){
        //Si HIT3 no esta habilitado se comporta igual que HIT1
        if(!clusterService.isEnabled()){
            return remoteTaskService.ejecutarTareaRemota(request);
        }

        //Si HIT3 esta habilitado, usa logica de cluster (El lider decide)
        return clusterService.routeIncomingTask(request);
    }
    
}
