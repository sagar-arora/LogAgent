package com.arorasagar.logagent;

import com.arorasagar.logagent.LogPusherConfig.LogConfig;
import com.arorasagar.logagent.endpoint.Endpoint;
import com.arorasagar.logagent.model.LogFile;
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
  private LogPusherConfig logPusherConfig;
  private Endpoint endpoint;

  public LogAgentManager(LogPusherConfig logPusherConfig, Endpoint endpoint) {
    logAgentRunning = new AtomicBoolean(true);
    logPusherExecutorService = Executors.newFixedThreadPool(2);
    logPusherSchedulerService = Executors.newScheduledThreadPool(1);
    this.logPusherConfig = logPusherConfig;
    this.endpoint = endpoint;

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
        Duration.standardSeconds(logPusherConfig.getDelayBetweenCycles()).getStandardSeconds(), TimeUnit.SECONDS);
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

  void updateLogConfigMap(Map<File, LogConfig> logConfigMap, List<LogConfig> logConfigs) {
    for (LogConfig logConfig : logConfigs) {
      List<File> discoveredFiles = FileUtils.discoverFiles(logConfig);

      for (File file : discoveredFiles) {
        if (!file.canRead()) {
          logger.warn("Skipping {}, since can't read", file.getAbsolutePath());
          continue;
        }

        LogConfig associatedLogConfig = logConfigMap.get(file);
        if (associatedLogConfig == null) {
          // first time discovery of a file map to this configuration itself
          associatedLogConfig = logConfig;
          logConfigMap.put(file, associatedLogConfig);
        }

        //logConfigMap.putIfAbsent(file, logConfig);
      }
    }
  }


  public File archiveFile(File fileToUpload) throws IOException {

    Path tmpDirectory = Paths.get(logPusherConfig.getTmpDir());
    if (!Files.exists(tmpDirectory) && Files.isReadable(tmpDirectory)) {
      Files.createFile(tmpDirectory);
    }
    Path compressedFileToUploadPath = FileUtils.getCompressedFilePathToUpload(new File(logPusherConfig.getTmpDir()), fileToUpload);
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
  public Map<File, LogFile> filterLogFiles(Map<File, LogConfig> logConfigMap,
                                           long startTime,
                                           Map<String, LogFile> recoveryMap)
      throws IOException {

    Map<File, LogFile> filesToUpload = new HashMap<>();

    for (Map.Entry<File, LogConfig> entry : logConfigMap.entrySet()) {

      File fileToUpload = entry.getKey();
      String fileToUploadPath = fileToUpload.getAbsolutePath();
      LogConfig logConfig = entry.getValue();
      long lastModifiedTime = fileToUpload.lastModified();

      // first case if the file is discovered by the given pattern in a directory and
      // the recovery map has already seen this file before then we just need to check if
      // the last modified time is greater than the last upload time for the file.
      if (recoveryMap.containsKey(fileToUploadPath) && LogUtils
          .fileNeedsUpload(fileToUpload, recoveryMap.get(fileToUploadPath))) {
        File compressedFileToUpload = archiveFile(fileToUpload);
        LogFile logFile = recoveryMap.get(fileToUploadPath);
        filesToUpload.put(compressedFileToUpload, logFile);
      } else if (!recoveryMap.containsKey(fileToUploadPath)) {
        File compressedFileToUpload = archiveFile(fileToUpload);

        LogFile logFile = LogFile.builder()
            .filePath(fileToUploadPath)
            .path(logConfig.getPath())
            .build();
        recoveryMap.put(fileToUploadPath, logFile);
        filesToUpload.put(compressedFileToUpload, logFile);
      }

      if (startTime - lastModifiedTime > Duration.standardSeconds(logPusherConfig.getRetentionPeriod()).getMillis()) {
        logger.info("Deleting the file {} as the file was last modified more than {} seconds ago.", filesToUpload,
            Duration.standardSeconds(logPusherConfig.getRetentionPeriod()));
        recoveryMap.remove(fileToUpload);
        org.apache.commons.io.FileUtils.deleteQuietly(fileToUpload);
        continue;
      }
    }

    return filesToUpload;
  }

  private void processLogConfigs() {
    Map<File, LogConfig> logConfigMap = new HashMap<>();
    updateLogConfigMap(logConfigMap, logPusherConfig.getLogConfigs());
    logger.info("Discovered total {} files.", logConfigMap.size());

    long iterationStartTime = System.currentTimeMillis();

    Map<File, LogFile> filesToUpload = new HashMap<>();
    Map<String, LogFile> recoveryMap = new HashMap<>();

    try {
        recoveryMap = LogUtils.readLogConfigMapFromDisk(new File(this.logPusherConfig.getRecoveryPath()));
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
      LogUtils.writeLogConfigMapToDisk(recoveryMap, new File(this.logPusherConfig.getRecoveryPath()));
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
