package com.project.costestimator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HexagonalArchitectureTests {
    private static final Path JAVA_ROOT = Path.of("src", "main", "java", "com", "project", "costestimator");

    @Test
    void domainHasNoFrameworkOrAdapterDependencies() throws IOException {
        assertNoImports(
                JAVA_ROOT.resolve("domain"),
                "org.springframework.",
                "jakarta.",
                "io.swagger.",
                "com.project.costestimator.adapter.",
                "com.project.costestimator.application.",
                "com.project.costestimator.config.",
                "com.project.costestimator.controller.",
                "com.project.costestimator.repository.");
    }

    @Test
    void applicationCoreDependsOnPortsInsteadOfAdapters() throws IOException {
        assertNoImports(
                JAVA_ROOT.resolve("application"),
                "org.springframework.",
                "jakarta.",
                "io.swagger.",
                "com.project.costestimator.adapter.",
                "com.project.costestimator.config.",
                "com.project.costestimator.controller.",
                "com.project.costestimator.repository.");
    }

    @Test
    void webControllersUseInboundPortsInsteadOfConcreteApplicationServices() throws IOException {
        assertNoImports(
                JAVA_ROOT.resolve("controller"),
                "com.project.costestimator.application.service.",
                "com.project.costestimator.repository.",
                "com.project.costestimator.adapter.out.");
    }

    @Test
    void inMemoryRepositoriesImplementOutboundPorts() throws IOException {
        for (Path source : javaFiles(JAVA_ROOT.resolve("repository"))) {
            String code = Files.readString(source);
            assertThat(code)
                    .as("%s must implement an outbound repository port", source.getFileName())
                    .contains("com.project.costestimator.application.port.out.")
                    .contains(" implements ");
        }
    }

    private void assertNoImports(Path packageRoot, String... forbiddenImports) throws IOException {
        for (Path source : javaFiles(packageRoot)) {
            String code = Files.readString(source);
            for (String forbiddenImport : forbiddenImports) {
                assertThat(code)
                        .as("%s must not depend on %s", source, forbiddenImport)
                        .doesNotContain("import " + forbiddenImport);
            }
        }
    }

    private List<Path> javaFiles(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
