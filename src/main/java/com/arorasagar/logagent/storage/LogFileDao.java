package com.arorasagar.logagent.storage;

import com.arorasagar.logagent.model.LogFile;

public interface LogFileDao {

    void writeLogfile(LogFile logFile);

    LogFile getLogfile(String file);

    boolean isPresent(String file);

    boolean removeLogfile(String file);
}
