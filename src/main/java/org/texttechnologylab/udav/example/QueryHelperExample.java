package org.texttechnologylab.udav.example;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.texttechnologylab.udav.database.QueryHelper;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// This example demonstrates how to use the QueryHelper class to perform a simple query
// Uncomment the @Component annotation to enable this example in a Spring Boot application
// @Component
public class QueryHelperExample implements ApplicationRunner {
    private final DataSource dataSource;

    public QueryHelperExample(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        DSLContext dslContext = DSL.using(dataSource.getConnection());
        QueryHelper q = new QueryHelper(dslContext);

        // Adjusted to reference the _NULL table
        Table<?> nullTable = q.table("cas");

        // Only select the filename field
        Field<Object> file = q.field("cas", "filename");

        Result<? extends Record> result = q.dsl()
                .selectDistinct(file) // This is equivalent to SELECT DISTINCT
                .from(nullTable)
                .fetch();

        // Collect filenames into a new ArrayList<String>
        List<String> filenames = new ArrayList<>();
        for (Record record : result) {
            Object value = record.getValue(file);
            if (value != null) {
                filenames.add(value.toString());
            }
        }

        filenames.forEach(record -> {
            System.out.println("FILENAME: " + record);
            System.out.println("-----");
        });

    }

    public void test() {
//        DSLContext create = DSL.using(dataSource.getConnection());
//        QueryHelper q = new QueryHelper(create);
//
//        Table<?> pos = q.table("pos");
//        Field<Object> begin = q.field("pos", "begin");
//        Field<Object> end = q.field("pos", "end");
//        Field<Object> coarse = q.field("pos", "coarsevalue");
//        Field<Object> file = q.field("pos", "filename");
//
//        Result<? extends Record> result = q.dsl()
//                .select(begin, end, coarse, file)
//                .from(pos)
//                .where(file.eq("ID21200100.xmi"))
//                .fetch();
//
//        result.forEach(record -> {
//            System.out.println("BEGIN: " + record.getValue(begin));
//            System.out.println("END: " + record.getValue(end));
//            System.out.println("COARSEVALUE: " + record.getValue(coarse));
//            System.out.println("FILENAME: " + record.getValue(file));
//            System.out.println("-----");
//        });
    }
}
