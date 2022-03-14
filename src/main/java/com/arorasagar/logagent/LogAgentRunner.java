package com.arorasagar.logagent;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.arorasagar.logagent.endpoint.S3Endpoint;
import com.arorasagar.logagent.storage.H2DaoImpl;
import com.arorasagar.logagent.storage.LogFileDao;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


public class LogAgentRunner
{
    private static File getConfigFile() {
        return new File("Config.json");
    }

    public LogAgentRunner(File configFile) {
        if (configFile != null && configFile.exists()) {
            try {
                System.out.println("Parsing the configuration file...");
                LogAgentConfig logAgentConfig = LogAgentConfig.fromJsonFile(getConfigFile());
            } catch (FileNotFoundException  e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main( String[] args ) throws IOException {
        AmazonS3 amazonS3 = AmazonS3ClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .build();

        LogAgentConfig logAgentConfig = LogAgentConfig.fromJsonFile(getConfigFile());

        LogFileDao h2Dao = new H2DaoImpl();

        LogAgentManager
            logAgentManager = new LogAgentManager(logAgentConfig, new S3Endpoint(amazonS3), h2Dao);

        logAgentManager.serviceInit();
        logAgentManager.start();
    }
}
