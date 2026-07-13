package com.voicesecure.testing;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class MainMethodSuiteTest {
    @Test
    void runsModuleOwnedMainMethodTests() throws Exception {
        Path classesDirectory = Path.of(System.getProperty("voicesecure.testClassesDirectory"));
        List<String> testClasses = discoverTests(classesDirectory);
        assertFalse(testClasses.isEmpty(), "Each Maven module must own at least one *Tests class under src/test/java");

        for (String className : testClasses) invokeMain(className);
    }

    private static List<String> discoverTests(Path classesDirectory) throws Exception {
        List<String> classes = new ArrayList<>();
        try (var files = Files.walk(classesDirectory)) {
            files.filter(Files::isRegularFile)
                    .map(classesDirectory::relativize)
                    .map(Path::toString)
                    .filter(name -> name.endsWith("Tests.class"))
                    .filter(name -> !name.contains("$"))
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .map(name -> name.replace('/', '.').replace('\\', '.'))
                    .sorted(Comparator.naturalOrder())
                    .forEach(classes::add);
        }
        return classes;
    }

    private static void invokeMain(String className) throws Exception {
        Method main = Class.forName(className).getMethod("main", String[].class);
        if (!Modifier.isStatic(main.getModifiers())) throw new IllegalStateException(className + ".main must be static");
        try {
            main.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) throw checked;
            if (cause instanceof Error error) throw error;
            throw exception;
        }
    }
}
