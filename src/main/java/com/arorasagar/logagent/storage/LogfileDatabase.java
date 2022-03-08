package com.arorasagar.logagent.storage;

import com.arorasagar.logagent.model.LogFile;

public interface LogfileDatabase {

    void writeLogfile(LogFile logFile);

    LogFile getLogfile(String file);

    boolean isPresent(String file);

    boolean removeLogfile(String file);
}
