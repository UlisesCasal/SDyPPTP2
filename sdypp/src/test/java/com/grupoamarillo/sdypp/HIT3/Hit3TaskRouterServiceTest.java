package com.grupoamarillo.sdypp.HIT3;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;
import com.grupoamarillo.sdypp.HIT1.services.RemoteTaskService;
import com.grupoamarillo.sdypp.HIT3.services.Hit3ClusterCoordinatorService;
import com.grupoamarillo.sdypp.HIT3.services.Hit3TaskRouterService;

class Hit3TaskRouterServiceTest {

    @Test
    void routeWhenClusterIsDisabledShouldUseRemoteTaskService() {
        RemoteTaskService remoteTaskService = mock(RemoteTaskService.class);
        Hit3ClusterCoordinatorService clusterService = mock(Hit3ClusterCoordinatorService.class);
        Hit3TaskRouterService routerService = new Hit3TaskRouterService(remoteTaskService, clusterService);

        RemoteTaskRequest request = new RemoteTaskRequest("sumar", Map.of("a", 2, "b", 3), null, "imagen:test");
        RemoteTaskResponse expected = new RemoteTaskResponse("OK", Map.of("resultado", 5), "done", "c3", 12L);
        when(clusterService.isEnabled()).thenReturn(false);
        when(remoteTaskService.ejecutarTareaRemota(request)).thenReturn(expected);

        RemoteTaskResponse actual = routerService.route(request);

        assertSame(expected, actual);
        verify(clusterService).isEnabled();
        verify(remoteTaskService).ejecutarTareaRemota(request);
    }

    @Test
    void routeWhenClusterIsEnabledShouldDelegateToClusterService() {
        RemoteTaskService remoteTaskService = mock(RemoteTaskService.class);
        Hit3ClusterCoordinatorService clusterService = mock(Hit3ClusterCoordinatorService.class);
        Hit3TaskRouterService routerService = new Hit3TaskRouterService(remoteTaskService, clusterService);

        RemoteTaskRequest request = new RemoteTaskRequest("sumar", Map.of("a", 4, "b", 1), null, "imagen:test");
        RemoteTaskResponse expected = new RemoteTaskResponse("OK", Map.of("resultado", 5), "done", "c4", 13L);
        when(clusterService.isEnabled()).thenReturn(true);
        when(clusterService.routeIncomingTask(request)).thenReturn(expected);

        RemoteTaskResponse actual = routerService.route(request);

        assertSame(expected, actual);
        verify(clusterService).isEnabled();
        verify(clusterService).routeIncomingTask(request);
    }
}
