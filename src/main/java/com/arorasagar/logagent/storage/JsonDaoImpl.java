package com.arorasagar.logagent.storage;

import com.arorasagar.logagent.model.LogFile;

public class JsonDaoImpl implements LogfileDatabase {

    @Override
    public void writeLogfile(LogFile logFile) {

    }

    @Override
    public LogFile getLogfile(String file) {
        return null;
    }

    @Override
    public boolean isPresent(String file) {
        return false;
    }

    @Override
    public boolean removeLogfile(String file) {
        return false;
    }
}
