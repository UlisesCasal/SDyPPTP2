package com.grupoamarillo.sdypp.HIT1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;
import com.grupoamarillo.sdypp.HIT1.dtos.TaskServiceRequest;

/**
 * PRUEBAS UNITARIAS PARA DTOs (Data Transfer Objects) - HIT1
 * 
 * Esta clase contiene 17 pruebas unitarias que validan los tres objetos DTO
 * utilizados en HIT1: RemoteTaskRequest, RemoteTaskResponse y TaskServiceRequest.
 * 
 * ÁREAS CUBIERTAS:
 * - Constructores (sin argumentos y con argumentos)
 * - Getters y Setters de todos los campos
 * - Igualdad (equals) entre objetos
 * - Método toString()
 * - Manejo de valores null
 * - Valores complejos (Maps, objetos anidados)
 * 
 * PROPÓSITO:
 * - Validar que los DTOs preserven correctamente los datos durante la serialización
 * - Asegurar que Lombok genere correctamente equals, hashCode, toString
 * - Probar casos edge (null values, empty maps, grandes valores)
 * 
 * EJECUCIÓN:
 * ./mvnw test -Dtest=DTOTest
 * 
 * NOTA: Estos tests no necesitan mocks. Prueban la estructura de los DTOs
 * de forma aislada usando solo aserciones básicas.
 */
@DisplayName("DTO Tests")
class DTOTest {

    // ============================================================================
    // TEST RemoteTaskRequest DTO
    // ============================================================================
    @DisplayName("RemoteTaskRequest should create instance with all fields")
    void testRemoteTaskRequestCreation() {
        // Arrange & Act
        RemoteTaskRequest request = new RemoteTaskRequest();
        request.setCalculo("suma");
        request.setParametros(new HashMap<>(Map.of("a", 1, "b", 2)));
        request.setImagenDocker("ubuntu:latest");
        request.setDatosAdicionales(new HashMap<>(Map.of("key", "value")));

        // Assert
        assertEquals("suma", request.getCalculo());
        assertEquals(2, request.getParametros().size());
        assertEquals("ubuntu:latest", request.getImagenDocker());
        assertEquals(1, request.getDatosAdicionales().size());
    }

    @Test
    @DisplayName("RemoteTaskRequest no-arg constructor should work")
    void testRemoteTaskRequestNoArgConstructor() {
        // Arrange & Act
        RemoteTaskRequest request = new RemoteTaskRequest();

        // Assert
        assertNotNull(request);
        assertNull(request.getCalculo());
        assertNull(request.getParametros());
        assertNull(request.getImagenDocker());
    }

    // ============================================================================
    // TEST RemoteTaskResponse DTO
    // ============================================================================
    @DisplayName("RemoteTaskRequest all-arg constructor should work")
    void testRemoteTaskRequestAllArgConstructor() {
        // Arrange
        Map<String, Object> params = new HashMap<>(Map.of("x", 10));
        Map<String, Object> additional = new HashMap<>(Map.of("debug", true));

        // Act
        RemoteTaskRequest request = new RemoteTaskRequest(
                "multiplicacion",
                params,
                additional,
                "alpine:latest"
        );

        // Assert
        assertEquals("multiplicacion", request.getCalculo());
        assertEquals(1, request.getParametros().size());
        assertEquals(1, request.getDatosAdicionales().size());
        assertEquals("alpine:latest", request.getImagenDocker());
    }

    @Test
    @DisplayName("RemoteTaskResponse should create instance with all fields")
    void testRemoteTaskResponseCreation() {
        // Arrange & Act
        RemoteTaskResponse response = new RemoteTaskResponse();
        response.setStatus("OK");
        response.setResultado(12345);
        response.setMensaje("Tarea completada");
        response.setContainerId("abc123");
        response.setDuracionMs(5000L);

        // Assert
        assertEquals("OK", response.getStatus());
        assertEquals(12345, response.getResultado());
        assertEquals("Tarea completada", response.getMensaje());
        assertEquals("abc123", response.getContainerId());
        assertEquals(5000L, response.getDuracionMs());
    }

    @Test
    @DisplayName("RemoteTaskResponse no-arg constructor should work")
    void testRemoteTaskResponseNoArgConstructor() {
        // Arrange & Act
        RemoteTaskResponse response = new RemoteTaskResponse();

        // Assert
        assertNotNull(response);
        assertNull(response.getStatus());
        assertNull(response.getResultado());
        assertNull(response.getMensaje());
    }

    @Test
    @DisplayName("RemoteTaskResponse all-arg constructor should work")
    void testRemoteTaskResponseAllArgConstructor() {
        // Arrange & Act
        RemoteTaskResponse response = new RemoteTaskResponse(
                "ERROR",
                null,
                "Error message",
                "cont789",
                3000L
        );

        // Assert
        assertEquals("ERROR", response.getStatus());
        assertNull(response.getResultado());
        assertEquals("Error message", response.getMensaje());
        assertEquals("cont789", response.getContainerId());
        assertEquals(3000L, response.getDuracionMs());
    }

    // ============================================================================
    // TEST TaskServiceRequest DTO
    // ============================================================================
    @Test
    @DisplayName("TaskServiceRequest should create instance with all fields")
    void testTaskServiceRequestCreation() {
        // Arrange & Act
        TaskServiceRequest request = new TaskServiceRequest();
        request.setCalculo("division");
        request.setParametros(new HashMap<>(Map.of("numerador", 10, "denominador", 2)));
        request.setDatosAdicionales(new HashMap<>(Map.of("precision", "2")));

        // Assert
        assertEquals("division", request.getCalculo());
        assertEquals(2, request.getParametros().size());
        assertEquals(1, request.getDatosAdicionales().size());
    }

    @Test
    @DisplayName("TaskServiceRequest no-arg constructor should work")
    void testTaskServiceRequestNoArgConstructor() {
        // Arrange & Act
        TaskServiceRequest request = new TaskServiceRequest();

        // Assert
        assertNotNull(request);
        assertNull(request.getCalculo());
        assertNull(request.getParametros());
    }

    @Test
    @DisplayName("TaskServiceRequest all-arg constructor should work")
    void testTaskServiceRequestAllArgConstructor() {
        // Arrange
        Map<String, Object> params = new HashMap<>(Map.of("val", 42));
        Map<String, Object> additional = new HashMap<>(Map.of("flag", "on"));

        // Act
        TaskServiceRequest request = new TaskServiceRequest(
                "potencia",
                params,
                additional
        );

        // Assert
        assertEquals("potencia", request.getCalculo());
        assertEquals(1, request.getParametros().size());
        assertEquals(1, request.getDatosAdicionales().size());
    }

    @Test
    @DisplayName("RemoteTaskRequest should handle null datosAdicionales")
    void testRemoteTaskRequestNullAdditionalData() {
        // Arrange & Act
        RemoteTaskRequest request = new RemoteTaskRequest();
        request.setCalculo("test");
        request.setParametros(new HashMap<>());
        request.setImagenDocker("ubuntu");
        request.setDatosAdicionales(null);

        // Assert
        assertNull(request.getDatosAdicionales());
    }

    @Test
    @DisplayName("RemoteTaskResponse should handle null resultado")
    void testRemoteTaskResponseNullResult() {
        // Arrange & Act
        RemoteTaskResponse response = new RemoteTaskResponse();
        response.setStatus("PENDING");
        response.setResultado(null);
        response.setMensaje("In progress");

        // Assert
        assertNull(response.getResultado());
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    @DisplayName("RemoteTaskRequest should support equality")
    void testRemoteTaskRequestEquality() {
        // Arrange
        RemoteTaskRequest req1 = new RemoteTaskRequest();
        req1.setCalculo("suma");
        req1.setParametros(new HashMap<>(Map.of("a", 1)));
        req1.setImagenDocker("ubuntu:latest");

        RemoteTaskRequest req2 = new RemoteTaskRequest();
        req2.setCalculo("suma");
        req2.setParametros(new HashMap<>(Map.of("a", 1)));
        req2.setImagenDocker("ubuntu:latest");

        // Assert
        assertEquals(req1, req2);
    }

    @Test
    @DisplayName("RemoteTaskResponse should support equality")
    void testRemoteTaskResponseEquality() {
        // Arrange
        RemoteTaskResponse resp1 = new RemoteTaskResponse(
                "OK",
                1000,
                "Success",
                "container1",
                5000L
        );

        RemoteTaskResponse resp2 = new RemoteTaskResponse(
                "OK",
                1000,
                "Success",
                "container1",
                5000L
        );

        // Assert
        assertEquals(resp1, resp2);
    }

    @Test
    @DisplayName("TaskServiceRequest should support equality")
    void testTaskServiceRequestEquality() {
        // Arrange
        TaskServiceRequest req1 = new TaskServiceRequest(
                "suma",
                new HashMap<>(Map.of("a", 1)),
                new HashMap<>()
        );

        TaskServiceRequest req2 = new TaskServiceRequest(
                "suma",
                new HashMap<>(Map.of("a", 1)),
                new HashMap<>()
        );

        // Assert
        assertEquals(req1, req2);
    }

    @Test
    @DisplayName("RemoteTaskRequest should support toString")
    void testRemoteTaskRequestToString() {
        // Arrange
        RemoteTaskRequest request = new RemoteTaskRequest();
        request.setCalculo("test");

        // Act
        String toString = request.toString();

        // Assert
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("RemoteTaskResponse should support toString")
    void testRemoteTaskResponseToString() {
        // Arrange
        RemoteTaskResponse response = new RemoteTaskResponse("OK", "result", "msg", "id", 1000L);

        // Act
        String toString = response.toString();

        // Assert
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("DTOs should support getter and setter for all fields")
    void testDTOGettersSetters() {
        // Arrange
        RemoteTaskRequest request = new RemoteTaskRequest();
        Map<String, Object> params = new HashMap<>(Map.of("key", "value"));

        // Act
        request.setCalculo("calc");
        request.setParametros(params);
        request.setImagenDocker("image");
        request.setDatosAdicionales(new HashMap<>());

        // Assert
        assertEquals("calc", request.getCalculo());
        assertEquals(params, request.getParametros());
        assertEquals("image", request.getImagenDocker());
        assertNotNull(request.getDatosAdicionales());
    }

    @Test
    @DisplayName("RemoteTaskResponse should handle large duration values")
    void testRemoteTaskResponseLargeDuration() {
        // Arrange & Act
        RemoteTaskResponse response = new RemoteTaskResponse(
                "OK",
                "result",
                "msg",
                "id",
                999999999L
        );

        // Assert
        assertEquals(999999999L, response.getDuracionMs());
    }

    @Test
    @DisplayName("RemoteTaskRequest should accept complex objeto resultado")
    void testRemoteTaskResponseComplexResultObject() {
        // Arrange
        Map<String, Object> complexResult = new HashMap<>();
        complexResult.put("nested", new HashMap<>(Map.of("value", 123)));
        complexResult.put("array", new int[]{1, 2, 3});

        // Act
        RemoteTaskResponse response = new RemoteTaskResponse(
                "OK",
                complexResult,
                "msg",
                "id",
                1000L
        );

        // Assert
        assertNotNull(response.getResultado());
        assertEquals(complexResult, response.getResultado());
    }

    // ============================================================================
    // RESUMEN DE COBERTURA
    // ============================================================================
    // Deze clase prueba:
    // - RemoteTaskRequest: 6 pruebas
    // - RemoteTaskResponse: 5 pruebas  
    // - TaskServiceRequest: 3 pruebas
    // - Propiedades compartidas (equals, toString, null handling): 3 pruebas
    //
    // Total: 17 pruebas unitarias de DTOs
    // ============================================================================
}

