package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.charts.ChartHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public abstract class Widget implements ChartHandler {
    public static final String WIDGETS_PACKAGE_PATH = "org.texttechnologylab.udav.widgets";

    protected final GeneratorDataRepository repo;
    protected final ObjectMapper mapper;


    protected String resolveGeneratorType(String schema, String generatorId) {
        if (repo == null) return null;
        return repo.loadGeneratorType(schema, generatorId).orElse(null);
    }

    // Overwrite if diagram should have a custom tex definition
    public String toTex(JsonNode jsonNode) { return null; }

    // Overwrite if diagram should have a custom csv definition
    public String toCsv(JsonNode jsonNode) { return null; }

    public static Widget constructWidget(String className, GeneratorDataRepository repo, ObjectMapper mapper) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (className.contains(".")) {
            throw new IllegalArgumentException("Class name can't contain \".\".");
        }
        Class<?> widgetClass = Class.forName(WIDGETS_PACKAGE_PATH + "." + className);
        return (Widget) widgetClass.getDeclaredConstructor(GeneratorDataRepository.class, ObjectMapper.class).newInstance(repo, mapper);
    }
    public static Widget constructWidget(String className) throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return constructWidget(className, null, null);
    }

    /**
     * Caches the constructor lookup for {@code className}, including negative results.
     *
     * <p>Callers use widget construction as a "does this widget define an intrinsic csv/tex
     * converter?" probe and fall back to the generic converter when it fails. Going through
     * {@link #constructWidget} for that probe costs a {@link Class#forName} miss plus a
     * fully populated stack trace per exported item, which is on the hot path for
     * bulk {@code tex}/{@code csv} exports. Returning an empty {@link Optional} from a cached
     * lookup keeps the same fallback semantics without the exception.
     */
    public static Optional<Widget> tryConstructWidget(String className, GeneratorDataRepository repo, ObjectMapper mapper) {
        if (className == null || className.isBlank() || className.contains(".")) {
            return Optional.empty();
        }

        Constructor<?> constructor = CONSTRUCTOR_CACHE
                .computeIfAbsent(className, Widget::lookupConstructor)
                .orElse(null);
        if (constructor == null) {
            return Optional.empty();
        }

        try {
            return Optional.of((Widget) constructor.newInstance(repo, mapper));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    public static Optional<Widget> tryConstructWidget(String className) {
        return tryConstructWidget(className, null, null);
    }

    private static final ConcurrentHashMap<String, Optional<Constructor<?>>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    private static Optional<Constructor<?>> lookupConstructor(String className) {
        try {
            Class<?> widgetClass = Class.forName(WIDGETS_PACKAGE_PATH + "." + className);
            if (!Widget.class.isAssignableFrom(widgetClass)) {
                return Optional.empty();
            }
            return Optional.of(widgetClass.getDeclaredConstructor(GeneratorDataRepository.class, ObjectMapper.class));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return Optional.empty();
        }
    }
}
