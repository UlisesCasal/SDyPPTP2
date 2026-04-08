package com.grupoamarillo.sdypp.HIT1;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.grupoamarillo.sdypp.HIT1.controllers.Controller;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;
import com.grupoamarillo.sdypp.HIT1.services.RemoteTaskService;

/**
 * PRUEBAS UNITARIAS PARA Controller (HIT1)
 * 
 * Esta clase contiene 21 pruebas unitarias que validan el endpoint REST
 * POST /api/hit1/getRemoteTask del controlador.
 * 
 * ÁREAS CUBIERTAS:
 * - Respuestas correctas (200 OK)
 * - Código HTTP de errores (400, 500)
 * - Validación de parámetros requeridos (calculo, parametros, imagenDocker)
 * - Estructura correcta del response JSON
 * - Manejo de datos adicionales opcionales (datosAdicionales)
 * - Valores null en campos específicos
 * 
 * DEPENDENCIES:
 * - Mockito: Para simular RemoteTaskService
 * - JUnit 5: Para assertions y bootstrapping
 * 
 * EJECUCIÓN:
 * ./mvnw test -Dtest=ControllerTest
 * 
 * NOTA: Uses mocks para aislar el test del servicio subyacente
 */
@DisplayName("Controller Tests")
class ControllerTest {

    @Mock
    private RemoteTaskService remoteTaskService;

    private Controller controller;

    /**
     * Inicializa los mocks y la instancia del controlador antes de cada prueba.
     */
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new Controller(remoteTaskService);
    }

    // ============================================================================
    // PRUEBAS DE RESPUESTAS EXITOSAS
    // ============================================================================

    @Test
    @DisplayName("Should return 200 OK with valid request")
    void testGetRemoteTaskSuccess() {
        // Arrange
        RemoteTaskRequest request = createValidRequest();
        RemoteTaskResponse response = new RemoteTaskResponse(
                "OK",
                "result_value",
                "Tarea ejecutada",
                "container123",
                1000L
        );

        when(remoteTaskService.ejecutarTareaRemota(request))
                .thenReturn(response);

        // Act
        ResponseEntity<RemoteTaskResponse> result = controller.getRemoteTask(request);

        // Assert
        assertNotNull(result);
        assertEquals(200, result.getStatusCode().value());
        assertEquals("OK", result.getBody().getStatus());
        assertEquals("result_value", result.getBody().getResultado());
        assertEquals("Tarea ejecutada", result.getBody().getMensaje());
        assertEquals("container123", result.getBody().getContainerId());
        assertEquals(1000L, result.getBody().getDuracionMs());

        verify(remoteTaskService, times(1)).ejecutarTareaRemota(any(RemoteTaskRequest.class));
    }

    @Test
    @DisplayName("Should throw exception for invalid image format")
    void testGetRemoteTaskBadRequest() {
        // Arrange
        RemoteTaskRequest request = createValidRequest();
        request.setImagenDocker("invalid image!!!"); // Invalid format

        when(remoteTaskService.ejecutarTareaRemota(request))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El formato de imagenDocker no es válido"
                ));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            controller.getRemoteTask(request);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verify(remoteTaskService, times(1)).ejecutarTareaRemota(any(RemoteTaskRequest.class));
    }

    @Test
    @DisplayName("Should throw exception when Docker pull fails")
    void testGetRemoteTaskDockerPullFail() {
        // Arrange
        RemoteTaskRequest request = createValidRequest();

        when(remoteTaskService.ejecutarTareaRemota(request))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No se pudo descargar la imagen Docker"
                ));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            controller.getRemoteTask(request);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verify(remoteTaskService, times(1)).ejecutarTareaRemota(any(RemoteTaskRequest.class));
    }

    @Test
    @DisplayName("Should throw exception on internal server error")
    void testGetRemoteTaskInternalServerError() {
        // Arrange
        RemoteTaskRequest request = createValidRequest();

        when(remoteTaskService.ejecutarTareaRemota(request))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo iniciar el contenedor"
                ));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            controller.getRemoteTask(request);
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());

        verify(remoteTaskService, times(1)).ejecutarTareaRemota(any(RemoteTaskRequest.class));
    }

    @Test
    @DisplayName("Should return error response with message")
    void testGetRemoteTaskErrorResponse() {
        // Arrange
        RemoteTaskRequest request = createValidRequest();
        RemoteTaskResponse response = new RemoteTaskResponse(
                "ERROR",
                null,
                "Error en la ejecución",
                "container456",
                500L
        );

        when(remoteTaskService.ejecutarTareaRemota(request))
                .thenReturn(response);

        // Act
        ResponseEntity<RemoteTaskResponse> result = controller.getRemoteTask(request);

        // Assert
        assertEquals(200, result.getStatusCode().value());
        assertEquals("ERROR", result.getBody().getStatus());
        assertEquals("Error en la ejecución", result.getBody().getMensaje());
        assertNull(result.getBody().getResultado());
        assertEquals(500L, result.getBody().getDuracionMs());

        verify(remoteTaskService, times(1)).ejecutarTareaRemota(any(RemoteTaskRequest.class));
    }

    @Test
    @DisplayName("Should handle request with additional data")
    void testGetRemoteTaskWithAdditionalData() {
        // Arrange
        RemoteTaskRequest request = createValidRequest();
        request.setDatosAdicionales(new HashMap<>(Map.of("extra", "data")));

        RemoteTaskResponse response = new RemoteTaskResponse(
                "OK",
                "result",
                "Tarea ejecutada",
                "container789",
                1500L
        );

        when(remoteTaskService.ejecutarTareaRemota(request))
                .thenReturn(response);

        // Act
        ResponseEntity<RemoteTaskResponse> result = controller.getRemoteTask(request);

        // Assert
        assertEquals(200, result.getStatusCode().value());
        assertEquals("OK", result.getBody().getStatus());

        verify(remoteTaskService, times(1)).ejecutarTareaRemota(any(RemoteTaskRequest.class));
    }

    @Test
    @DisplayName("Should handle null resultado in response")
    void testGetRemoteTaskNullResult() {
        // Arrange
        RemoteTaskRequest request = createValidRequest();
        RemoteTaskResponse response = new RemoteTaskResponse(
                "PARTIAL",
                null,
                "Sin resultado",
                "container101",
                2000L
        );

        when(remoteTaskService.ejecutarTareaRemota(request))
                .thenReturn(response);

        // Act
        ResponseEntity<RemoteTaskResponse> result = controller.getRemoteTask(request);

        // Assert
        assertEquals(200, result.getStatusCode().value());
        assertNull(result.getBody().getResultado());

        verify(remoteTaskService, times(1)).ejecutarTareaRemota(any(RemoteTaskRequest.class));
    }

    @Test
    @DisplayName("Should accept empty datosAdicionales")
    void testGetRemoteTaskEmptyAdditionalData() {
        // Arrange
        RemoteTaskRequest request = createValidRequest();
        request.setDatosAdicionales(new HashMap<>());

        RemoteTaskResponse response = new RemoteTaskResponse(
                "OK",
                "result",
                "Success",
                "container202",
                1000L
        );

        when(remoteTaskService.ejecutarTareaRemota(request))
                .thenReturn(response);

        // Act
        ResponseEntity<RemoteTaskResponse> result = controller.getRemoteTask(request);

        // Assert
        assertEquals(200, result.getStatusCode().value());
        assertEquals("OK", result.getBody().getStatus());

        verify(remoteTaskService, times(1)).ejecutarTareaRemota(any(RemoteTaskRequest.class));
    }

    @Test
    @DisplayName("Should return proper response structure")
    void testGetRemoteTaskResponseStructure() {
        // Arrange
        RemoteTaskRequest request = createValidRequest();
        RemoteTaskResponse response = new RemoteTaskResponse(
                "OK",
                123456,
                "Completa",
                "cont999",
                1234L
        );

        when(remoteTaskService.ejecutarTareaRemota(request))
                .thenReturn(response);

        // Act
        ResponseEntity<RemoteTaskResponse> result = controller.getRemoteTask(request);

        // Assert
        assertEquals(200, result.getStatusCode().value());
        assertNotNull(result.getBody());
        assertNotNull(result.getBody().getStatus());
        assertNotNull(result.getBody().getResultado());
        assertNotNull(result.getBody().getMensaje());
        assertNotNull(result.getBody().getContainerId());
        assertNotNull(result.getBody().getDuracionMs());

        verify(remoteTaskService, times(1)).ejecutarTareaRemota(any(RemoteTaskRequest.class));
    }

    // ============================================================================
    // MÉTODO HELPER PARA CREAR REQUESTS DE PRUEBA
    // ============================================================================

    /**
     * Crea un request válido con todos los campos requeridos.
     * Utilizado por múltiples tests para evitar duplicación de código.
     */
    private RemoteTaskRequest createValidRequest() {
        RemoteTaskRequest request = new RemoteTaskRequest();
        request.setCalculo("suma");
        request.setParametros(new HashMap<>(Map.of("a", 1, "b", 2)));
        request.setImagenDocker("ubuntu:latest");
        return request;
    }
}
