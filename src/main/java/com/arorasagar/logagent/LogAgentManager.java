package com.arorasagar.logagent;

import com.arorasagar.logagent.LogAgentConfig.LogFileConfig;
import com.arorasagar.logagent.endpoint.Endpoint;
import com.arorasagar.logagent.model.LogFile;
import com.arorasagar.logagent.storage.LogfileDatabase;
import com.arorasagar.logagent.utils.FileUtils;
import com.arorasagar.logagent.utils.LogUtils;
import com.google.common.annotations.VisibleForTesting;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogAgentManager extends Thread {

  private final AtomicBoolean logAgentRunning;
  private final Logger logger = LoggerFactory.getLogger(LogAgentManager.class);
  private final ExecutorService logPusherExecutorService;
  private final ScheduledExecutorService logPusherSchedulerService;
  private LogAgentConfig logAgentConfig;
  private Endpoint endpoint;
  private LogfileDatabase logfileDatabase;

  public LogAgentManager(LogAgentConfig logAgentConfig,
                         Endpoint endpoint,
                         LogfileDatabase logfileDatabase) {
    logAgentRunning = new AtomicBoolean(true);
    logPusherExecutorService = Executors.newFixedThreadPool(2);
    logPusherSchedulerService = Executors.newScheduledThreadPool(1);
    this.logAgentConfig = logAgentConfig;
    this.endpoint = endpoint;

    this.logfileDatabase = logfileDatabase;

    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      @Override
      public void run() {
        logger.debug("Service has been shutdown.");
      }
    }));
  }

  /**
   * Starts the scheduler service to push logs on a regular interval.
   */
  public void serviceInit() {
    logPusherSchedulerService.scheduleWithFixedDelay(new LogAgentScheduler(), 0,
        Duration.standardSeconds(logAgentConfig.getDelayBetweenCycles()).getStandardSeconds(), TimeUnit.SECONDS);
  }

  public void run() {
    logger.info("Starting log pusher service.");

    while (logAgentRunning.get()) {
      try {
        // TODO
        TimeUnit.MILLISECONDS.sleep(1);
      } catch (InterruptedException e) {

      }
    }
  }

  public void shutdown() {
    logAgentRunning.set(false);
    logPusherExecutorService.shutdown();
    logPusherSchedulerService.shutdown();

    try {
      boolean logPusherExecutorTerminated = logPusherExecutorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
      boolean logPusherSchedulerTerminated = logPusherSchedulerService.awaitTermination(1000, TimeUnit.MILLISECONDS);

      if (!logPusherExecutorTerminated) {
        logger.error("logPusherExecutorService shutdown timed out.");
      }

      if (!logPusherSchedulerTerminated) {
        logger.error("logPusherSchedulerTerminated shutdown timed out.");
      }

    } catch (InterruptedException e) {
      logger.error(e.getMessage());
    }
  }

  void updateLogConfigMap(Map<File, LogFileConfig> logConfigMap, List<LogFileConfig> logFileConfigs) {
    for (LogFileConfig logFileConfig : logFileConfigs) {
      List<File> discoveredFiles = FileUtils.discoverFiles(logFileConfig);

      for (File file : discoveredFiles) {
        if (!file.canRead()) {
          logger.warn("Skipping {}, since can't read", file.getAbsolutePath());
          continue;
        }

        LogFileConfig associatedLogFileConfig = logConfigMap.get(file);
        if (associatedLogFileConfig == null) {
          // first time discovery of a file map to this configuration itself
          associatedLogFileConfig = logFileConfig;
          logConfigMap.put(file, associatedLogFileConfig);
        }

        //logConfigMap.putIfAbsent(file, logConfig);
      }
    }
  }


  public File archiveFile(File fileToUpload) throws IOException {

    Path tmpDirectory = Paths.get(logAgentConfig.getTmpDir());
    if (!Files.exists(tmpDirectory) && Files.isReadable(tmpDirectory)) {
      Files.createFile(tmpDirectory);
    }
    Path compressedFileToUploadPath = FileUtils.getCompressedFilePathToUpload(new File(logAgentConfig.getTmpDir()), fileToUpload);
    if (!Files.exists(compressedFileToUploadPath)) {
      Path parent = compressedFileToUploadPath.getParent();
      if (!Files.exists(parent)) {
        Files.createDirectories(parent);
      }
      Files.createFile(compressedFileToUploadPath);
    }
    logger.info("Path found : {}", compressedFileToUploadPath.toString());
    FileUtils.compressGzip(fileToUpload.toPath(), compressedFileToUploadPath);
    return compressedFileToUploadPath.toFile();
  }

  @VisibleForTesting
  public Map<File, LogFile> filterLogFiles(Map<File, LogFileConfig> logConfigMap,
                                           long startTime,
                                           Map<String, LogFile> recoveryMap)
      throws IOException {

    Map<File, LogFile> filesToUpload = new HashMap<>();

    for (Map.Entry<File, LogFileConfig> entry : logConfigMap.entrySet()) {

      File fileToUpload = entry.getKey();
      String fileToUploadPath = fileToUpload.getAbsolutePath();
      LogFileConfig logFileConfig = entry.getValue();
      long lastModifiedTime = fileToUpload.lastModified();

      // first case if the file is discovered by the given pattern in a directory and
      // the recovery map has already seen this file before then we just need to check if
      // the last modified time is greater than the last upload time for the file.
 /*     if (recoveryMap.containsKey(fileToUploadPath) && LogUtils
          .fileNeedsUpload(fileToUpload, recoveryMap.get(fileToUploadPath))) {
        File compressedFileToUpload = archiveFile(fileToUpload);
        LogFile logFile = recoveryMap.get(fileToUploadPath);
        filesToUpload.put(compressedFileToUpload, logFile);
      } else if (!recoveryMap.containsKey(fileToUploadPath)) {
        File compressedFileToUpload = archiveFile(fileToUpload);

        LogFile logFile = LogFile.builder()
            .filePath(fileToUploadPath)
            .path(logFileConfig.getPath())
            .build();
        recoveryMap.put(fileToUploadPath, logFile);
        filesToUpload.put(compressedFileToUpload, logFile);
      }*/

      if (logfileDatabase.isPresent(fileToUploadPath) &&
              LogUtils.fileNeedsUpload(fileToUpload, logfileDatabase.getLogfile(fileToUploadPath))) {
        File compressedFileToUpload = archiveFile(fileToUpload);
        LogFile logFile = logfileDatabase.getLogfile(fileToUploadPath);
        filesToUpload.put(compressedFileToUpload, logFile);
      } else if (!logfileDatabase.isPresent(fileToUploadPath)) {
        File compressedFileToUpload = archiveFile(fileToUpload);

        LogFile logFile = LogFile.builder()
                .filePath(fileToUploadPath)
                //.path(logFileConfig.getPath())
                .build();
        logfileDatabase.writeLogfile(logFile);
        filesToUpload.put(compressedFileToUpload, logFile);
      }


      if (startTime - lastModifiedTime > Duration.standardSeconds(logAgentConfig.getRetentionPeriod()).getMillis()) {
        logger.info("Deleting the file {} as the file was last modified more than {} seconds ago.", filesToUpload,
            Duration.standardSeconds(logAgentConfig.getRetentionPeriod()));
       // recoveryMap.remove(fileToUpload);
        org.apache.commons.io.FileUtils.deleteQuietly(fileToUpload);
      }
    }

    return filesToUpload;
  }

  private void processLogConfigs() {
    Map<File, LogFileConfig> logConfigMap = new HashMap<>();
    updateLogConfigMap(logConfigMap, logAgentConfig.getLogFileConfigs());
    logger.info("Discovered total {} files.", logConfigMap.size());

    long iterationStartTime = System.currentTimeMillis();

    Map<File, LogFile> filesToUpload = new HashMap<>();
    Map<String, LogFile> recoveryMap = new HashMap<>();

    try {
        recoveryMap = LogUtils.readLogConfigMapFromDisk(new File(this.logAgentConfig.getRecoveryPath()));
       filesToUpload = filterLogFiles(logConfigMap, iterationStartTime, recoveryMap);
    } catch (Exception e) {
      logger.info("Exception occurred while filtering the files {}", e.getMessage());
    }
    logger.info("Found total {} files to upload after filtering.", filesToUpload.size());

    List<Future<LogUploadResult>> futureResults = new ArrayList<>();

    try {
      for (Map.Entry<File, LogFile> entry : filesToUpload.entrySet()) {
        File fileToUpload = entry.getKey();
        LogFile logFile = entry.getValue();
        logger.info("here....");
        Future<LogUploadResult> logUploadResultFuture =
            logPusherExecutorService.submit(new LogUploader(fileToUpload, logFile, endpoint));
        futureResults.add(logUploadResultFuture);
      }

      for(Future<LogUploadResult> future : futureResults) {
        future.get();
      }

    } catch (Exception e) {
      logger.info("Exception occurred while uploading the files {}", e.getMessage());
    }

    try {
      LogUtils.writeLogConfigMapToDisk(recoveryMap, new File(this.logAgentConfig.getRecoveryPath()));
    } catch (Exception e) {
      logger.info("Exception occurred while writing the recovery map to disk: {}", e.getMessage());
    }

  }

  private final class LogAgentScheduler implements Runnable {

    @Override
    public void run() {
      processLogConfigs();
    }
  }
}
