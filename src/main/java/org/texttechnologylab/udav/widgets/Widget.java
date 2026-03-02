package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.charts.ChartHandler;

import java.lang.reflect.InvocationTargetException;

@RequiredArgsConstructor
public abstract class Widget implements ChartHandler {
    public static final String WIDGETS_PACKAGE_PATH = "org.texttechnologylab.udav.widgets";

    protected final GeneratorDataRepository repo;
    protected final ObjectMapper mapper;

    // Overwrite if diagram should have a custom tikz
    public String toTex(JsonNode jsonNode) { return null; }

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
}
