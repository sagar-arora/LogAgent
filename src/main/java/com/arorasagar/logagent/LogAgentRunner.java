package com.arorasagar.logagent;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.arorasagar.logagent.endpoint.S3Endpoint;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Hello world!
 *
 */
public class LogAgentRunner
{
    private static File getConfigFile() {
        return new File("Config.json");
    }

    public LogAgentRunner(File configFile) {
        if (configFile != null && configFile.exists()) {
            try {
                System.out.println("Parsing the configuration file...");
                LogPusherConfig logPusherConfig = LogPusherConfig.fromJsonFile(getConfigFile());
            } catch (FileNotFoundException  e) {

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main( String[] args ) throws IOException {
        AmazonS3 amazonS3 = AmazonS3ClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .build();

        LogPusherConfig logPusherConfig = LogPusherConfig.fromJsonFile(getConfigFile());
        LogAgentManager
            logAgentManager = new LogAgentManager(logPusherConfig, new S3Endpoint(amazonS3));

        logAgentManager.serviceInit();
        logAgentManager.start();
    }
}
