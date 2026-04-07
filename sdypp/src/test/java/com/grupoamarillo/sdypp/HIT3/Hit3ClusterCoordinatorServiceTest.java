package com.grupoamarillo.sdypp.HIT3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;
import com.grupoamarillo.sdypp.HIT1.services.RemoteTaskService;
import com.grupoamarillo.sdypp.HIT3.config.Hit3ClusterProperties;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3CoordinatorRequest;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3HeartbeatRequest;
import com.grupoamarillo.sdypp.HIT3.services.Hit3ClusterCoordinatorService;

import tools.jackson.databind.ObjectMapper;

class Hit3ClusterCoordinatorServiceTest {

    @Test
    void routeIncomingTaskWhenClusterDisabledShouldDelegateToRemoteTaskService() {
        RemoteTaskService remoteTaskService = mock(RemoteTaskService.class);
        Hit3ClusterCoordinatorService service = new Hit3ClusterCoordinatorService(
                buildProperties(false, 1, "1:localhost:8080"),
                remoteTaskService,
                new ObjectMapper());

        RemoteTaskRequest request = new RemoteTaskRequest("sumar", Map.of("a", 1, "b", 2), null, "imagen:test");
        RemoteTaskResponse expected = new RemoteTaskResponse("OK", Map.of("resultado", 3), "done", "c1", 10L);
        when(remoteTaskService.ejecutarTareaRemota(request)).thenReturn(expected);

        RemoteTaskResponse actual = service.routeIncomingTask(request);

        assertSame(expected, actual);
        verify(remoteTaskService).ejecutarTareaRemota(request);
    }

    @Test
    void assignTaskAsLeaderWhenNodeIsNotLeaderShouldThrowConflict() {
        RemoteTaskService remoteTaskService = mock(RemoteTaskService.class);
        Hit3ClusterCoordinatorService service = new Hit3ClusterCoordinatorService(
                buildProperties(true, 1, "1:localhost:8080"),
                remoteTaskService,
                new ObjectMapper());
        RemoteTaskRequest request = new RemoteTaskRequest("sumar", Map.of("a", 1), null, "imagen:test");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.assignTaskAsLeader(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void assignTaskAsLeaderWhenNodeIsLeaderShouldExecuteLocalTask() {
        RemoteTaskService remoteTaskService = mock(RemoteTaskService.class);
        Hit3ClusterCoordinatorService service = new Hit3ClusterCoordinatorService(
                buildProperties(true, 1, "1:localhost:8080,2:localhost:8081"),
                remoteTaskService,
                new ObjectMapper());
        service.handleCoordinator(new Hit3CoordinatorRequest(1));

        RemoteTaskRequest request = new RemoteTaskRequest("sumar", Map.of("a", 1), null, "imagen:test");
        RemoteTaskResponse expected = new RemoteTaskResponse("OK", Map.of("resultado", 1), "done", "c2", 11L);
        when(remoteTaskService.ejecutarTareaRemota(request)).thenReturn(expected);

        RemoteTaskResponse actual = service.assignTaskAsLeader(request);

        assertTrue(service.isLeader());
        assertSame(expected, actual);
        verify(remoteTaskService).ejecutarTareaRemota(request);
    }

    @Test
    void handleHeartbeatShouldSyncLeaderWhenItWasUnknown() {
        RemoteTaskService remoteTaskService = mock(RemoteTaskService.class);
        Hit3ClusterCoordinatorService service = new Hit3ClusterCoordinatorService(
                buildProperties(true, 1, "1:localhost:8080,2:localhost:8081"),
                remoteTaskService,
                new ObjectMapper());

        var heartbeatResponse = service.handleHeartbeat(new Hit3HeartbeatRequest(2, 2));
        var clusterStatus = service.getClusterStatus();

        assertEquals(2, heartbeatResponse.getLeaderId());
        assertFalse(heartbeatResponse.isLeader());
        assertEquals(2, clusterStatus.getLeaderId());
        assertTrue(clusterStatus.getNodes().stream().anyMatch(node -> node.getNodeId() == 2 && node.isAlive()));
    }

    @Test
    void startElectionWithSingleNodeShouldBecomeLeader() {
        RemoteTaskService remoteTaskService = mock(RemoteTaskService.class);
        Hit3ClusterCoordinatorService service = new Hit3ClusterCoordinatorService(
                buildProperties(true, 3, "3:localhost:8080"),
                remoteTaskService,
                new ObjectMapper());

        service.startElection();
        var status = service.getClusterStatus();

        assertTrue(status.isLeader());
        assertEquals(3, status.getLeaderId());
        assertFalse(status.isElectionInProgress());
    }

    private Hit3ClusterProperties buildProperties(boolean enabled, int nodeId, String nodes) {
        Hit3ClusterProperties properties = new Hit3ClusterProperties();
        properties.setEnabled(enabled);
        properties.setNodeId(nodeId);
        properties.setSelfHost("localhost");
        properties.setSelfPort(8080 + nodeId);
        properties.setNodes(nodes);
        properties.setHeartbeatIntervalMs(1000);
        properties.setHeartbeatTimeoutMs(3000);
        properties.setElectionTimeoutMs(200);
        return properties;
    }
}
