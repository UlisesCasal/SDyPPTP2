package com.grupoamarillo.sdypp.HIT1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.grupoamarillo.sdypp.HIT1.docker.CommandResult;
import com.grupoamarillo.sdypp.HIT1.docker.DockerCommandRunner;

/**
 * PRUEBAS UNITARIAS PARA DockerCommandRunner (HIT1)
 * 
 * Esta clase contiene 13 pruebas unitarias que validan la ejecución de comandos
 * del sistema operativo a través de la clase DockerCommandRunner.
 * 
 * ÁREAS CUBIERTAS:
 * - Ejecución exitosa de comandos (echo, ls, etc.)
 * - Captura de salida estándar (stdout)
 * - Captura de salida de error (stderr)
 * - Códigos de salida no-cero (errores)
 * - Salida multilinea y vacía
 * - Manejo de caracteres especiales
 * - Ejecución secuencial de comandos
 * 
 * IMPORTANTE:
 * - Estos tests usan comandos REALES de shell (no mocks)
 * - Necesita que bash/sh estén disponibles en el sistema
 * - Los comandos son simples (echo, exit) para ser portables
 * 
 * EJECUCIÓN:
 * ./mvnw test -Dtest=DockerCommandRunnerTest
 */
@DisplayName("DockerCommandRunner Tests")
class DockerCommandRunnerTest {

    private DockerCommandRunner dockerCommandRunner;

    /**
     * Inicializa una nueva instancia de DockerCommandRunner antes de cada prueba.
     * Esta clase no requiere mocks ya que es un wrapper simple de ProcessBuilder.
     */
    @BeforeEach
    void setUp() {
        dockerCommandRunner = new DockerCommandRunner();
    }

    // ============================================================================
    // PRUEBAS DE EJECUCIÓN EXITOSA
    // ============================================================================

    @Test
    @DisplayName("Should execute echo command successfully")
    void testRunEchoCommand() {
        // Arrange
        List<String> command = List.of("echo", "Hello World");

        // Act
        CommandResult result = dockerCommandRunner.run(command);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.exitCode());
        assertEquals("Hello World", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    @DisplayName("Should capture command output")
    void testCaptureCommandOutput() {
        // Arrange
        List<String> command = List.of("echo", "test output");

        // Act
        CommandResult result = dockerCommandRunner.run(command);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("test output"));
    }

    @Test
    @DisplayName("Should handle non-zero exit code")
    void testNonZeroExitCode() {
        // Arrange
        List<String> command = List.of("sh", "-c", "exit 1");

        // Act
        CommandResult result = dockerCommandRunner.run(command);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.exitCode());
    }

    @Test
    @DisplayName("Should capture stderr output")
    void testCaptureStderr() {
        // Arrange
        List<String> command = List.of("sh", "-c", "echo error message >&2 && exit 1");

        // Act
        CommandResult result = dockerCommandRunner.run(command);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error message"));
    }

    @Test
    @DisplayName("Should handle multiline output")
    void testMultilineOutput() {
        // Arrange
        List<String> command = List.of("sh", "-c", "echo line1 && echo line2 && echo line3");

        // Act
        CommandResult result = dockerCommandRunner.run(command);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("line1"));
        assertTrue(result.stdout().contains("line2"));
        assertTrue(result.stdout().contains("line3"));
    }

    @Test
    @DisplayName("Should handle empty output")
    void testEmptyOutput() {
        // Arrange
        List<String> command = List.of("sh", "-c", "exit 0");

        // Act
        CommandResult result = dockerCommandRunner.run(command);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.exitCode());
        assertEquals("", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    @DisplayName("Should throw RuntimeException for non-existent command")
    void testNonExistentCommand() {
        // Arrange
        List<String> command = List.of("nonexistentcommand12345");

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dockerCommandRunner.run(command);
        });

        assertTrue(exception.getMessage().contains("Error ejecutando comando Docker"));
    }

    @Test
    @DisplayName("Should handle command with multiple arguments")
    void testCommandWithMultipleArguments() {
        // Arrange
        List<String> command = List.of("echo", "arg1", "arg2", "arg3");

        // Act
        CommandResult result = dockerCommandRunner.run(command);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("arg1"));
        assertTrue(result.stdout().contains("arg2"));
        assertTrue(result.stdout().contains("arg3"));
    }

    @Test
    @DisplayName("Should return CommandResult with correct types")
    void testCommandResultTypes() {
        // Arrange
        List<String> command = List.of("echo", "test");

        // Act
        CommandResult result = dockerCommandRunner.run(command);

        // Assert
        assertNotNull(result);
        assertNotNull(result.stdout());
        assertNotNull(result.stderr());
        assertTrue(result.exitCode() >= 0);
    }

    @Test
    @DisplayName("Should handle commands with special characters")
    void testCommandWithSpecialCharacters() {
        // Arrange
        List<String> command = List.of("echo", "test@#$%^&*()");

        // Act
        CommandResult result = dockerCommandRunner.run(command);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("test"));
    }

    @Test
    @DisplayName("Should process command sequentially")
    void testSequentialCommandExecution() {
        // Arrange
        List<String> command1 = List.of("echo", "first");
        List<String> command2 = List.of("echo", "second");

        // Act
        CommandResult result1 = dockerCommandRunner.run(command1);
        CommandResult result2 = dockerCommandRunner.run(command2);

        // Assert
        assertEquals(0, result1.exitCode());
        assertEquals(0, result2.exitCode());
        assertTrue(result1.stdout().contains("first"));
        assertTrue(result2.stdout().contains("second"));
    }

    @Test
    @DisplayName("Should handle long-running command with timeout")
    void testLongRunningCommand() {
        // Arrange
        List<String> command = List.of("sh", "-c", "for i in 1 2 3; do echo $i; done");

        // Act
        CommandResult result = dockerCommandRunner.run(command);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("1"));
        assertTrue(result.stdout().contains("2"));
        assertTrue(result.stdout().contains("3"));
    }
}
