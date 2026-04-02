package com.grupoamarillo.sdypp.HIT1.docker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class DockerCommandRunner {
    public CommandResult run(List<String> command) {

        try{
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            String stdout;
            try (BufferedReader out = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                stdout = out.lines().reduce("", (a, b) -> a + (a.isEmpty() ? "" : "\n") + b);
            }
            String stderr;
            try (BufferedReader err = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                stderr = err.lines().reduce("", (a, b) -> a + (a.isEmpty() ? "" : "\n") + b);
            }
            int exit = process.waitFor();
            return new CommandResult(exit, stdout, stderr);
        }catch(Exception e){
            throw new RuntimeException("Error ejecutando comando Docker", e);
        }
        

    }
}
