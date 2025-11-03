package org.texttechnologylab.udav.example;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.texttechnologylab.udav.importer.service.DynamicTableService;

import java.util.List;

//@Component
//@ConditionalOnProperty(name = "app.database-generator.enabled", havingValue = "true", matchIfMissing = true)
public class TableLister implements ApplicationRunner {
    private final DynamicTableService dynamicTableService;

    public TableLister(DynamicTableService dynamicTableService) {
        this.dynamicTableService = dynamicTableService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> tables = dynamicTableService.listTables();
        int width = tables.stream().mapToInt(String::length).max().orElse(0);
        String border = "+" + "-".repeat(width + 2) + "+";
        System.out.println(border);
        for (String name : tables) {
            System.out.printf("| %-" + width + "s |%n", name);
        }
        System.out.println(border);
    }
}
