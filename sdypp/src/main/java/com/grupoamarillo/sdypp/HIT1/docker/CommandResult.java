package com.grupoamarillo.sdypp.HIT1.docker;

public record CommandResult(int exitCode, String stdout, String stderr) {
}
