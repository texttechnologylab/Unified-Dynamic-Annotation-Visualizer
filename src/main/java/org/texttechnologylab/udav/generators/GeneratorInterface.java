package org.texttechnologylab.udav.generators;

import org.texttechnologylab.udav.sources.DBAccess;
import java.sql.SQLException;

public interface GeneratorInterface {
    GeneratorInterface copy(String id);
    void saveToDB(DBAccess dbAccess) throws SQLException;
}
