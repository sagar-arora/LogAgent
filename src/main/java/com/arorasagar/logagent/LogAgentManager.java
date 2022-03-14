package com.arorasagar.logagent;

import com.arorasagar.logagent.LogAgentConfig.LogFileConfig;
import com.arorasagar.logagent.endpoint.Endpoint;
import com.arorasagar.logagent.model.LogFile;
import com.arorasagar.logagent.storage.LogFileDao;
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
  private LogFileDao logFileDao;

  public LogAgentManager(LogAgentConfig logAgentConfig,
                         Endpoint endpoint,
                         LogFileDao logFileDao) {
    logAgentRunning = new AtomicBoolean(true);
    logPusherExecutorService = Executors.newFixedThreadPool(2);
    logPusherSchedulerService = Executors.newScheduledThreadPool(1);
    this.logAgentConfig = logAgentConfig;
    this.endpoint = endpoint;

    this.logFileDao = logFileDao;

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
        logger.error("logAgentExecutorService shutdown timed out.");
      }

      if (!logPusherSchedulerTerminated) {
        logger.error("logAgentSchedulerTerminated shutdown timed out.");
      }

    } catch (InterruptedException e) {
      logger.error(e.getMessage());
    }
  }

  void discoverFilesAndUpdateLogConfigMap(Map<File, LogFileConfig> matchingFiles, List<LogFileConfig> logFileConfigs) {
    for (LogFileConfig logFileConfig : logFileConfigs) {
      List<File> discoveredFiles = FileUtils.discoverFiles(logFileConfig);

      for (File file : discoveredFiles) {
        if (!file.canRead()) {
          logger.warn("Skipping {}, since can't read", file.getAbsolutePath());
          continue;
        }

        LogFileConfig associatedLogFileConfig = matchingFiles.get(file);
        if (associatedLogFileConfig == null) {
          // first time discovery of a file, map to this configuration itself
          associatedLogFileConfig = logFileConfig;
          matchingFiles.put(file, associatedLogFileConfig);
        }
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
  public Map<File, LogFile> filterLogFiles(Map<File, LogFileConfig> matchingFiles)
      throws IOException {

    Map<File, LogFile> filesToUpload = new HashMap<>();

    for (Map.Entry<File, LogFileConfig> entry : matchingFiles.entrySet()) {

      File fileToUpload = entry.getKey();
      String fileToUploadPath = fileToUpload.getAbsolutePath();
      long lastModifiedTime = fileToUpload.lastModified();

      // first case if the file is discovered by the given pattern in a directory and
      // the recovery map has already seen this file before then we just need to check if
      // the last modified time is greater than the last upload time for the file.

      LogFile logFile = logFileDao.getLogfile(fileToUploadPath);
      boolean isPresentInDb = logFile != null;
      if (isPresentInDb && LogUtils.fileNeedsUpload(fileToUpload, logFile)) {
        File compressedFileToUpload = archiveFile(fileToUpload);
        filesToUpload.put(compressedFileToUpload, logFile);
      } else if (!isPresentInDb) {
        // discovered for the first time.
        File compressedFileToUpload = archiveFile(fileToUpload);

        logFile = LogFile.builder()
                .filePath(fileToUploadPath)
                .lastUploadedMD5Hash(FileUtils.calculateMd5ofFile(compressedFileToUpload))
                .archivedPath(compressedFileToUpload.getAbsolutePath())
                .build();
        logFileDao.writeLogfile(logFile);

        filesToUpload.put(compressedFileToUpload, logFile);
      }

      if (System.currentTimeMillis() - lastModifiedTime > Duration.standardSeconds(logAgentConfig.getRetentionPeriod()).getMillis()) {
        logger.info("Deleting the file {} as the file was last modified more than {} seconds ago.", filesToUpload,
            Duration.standardSeconds(logAgentConfig.getRetentionPeriod()));
       // recoveryMap.remove(fileToUpload);
        org.apache.commons.io.FileUtils.deleteQuietly(fileToUpload);

        // check if the archived files exist in the temp folder.

        if (logFileDao.getLogfile(fileToUploadPath) != null) {
          String archivedFilePath = logFileDao.getLogfile(fileToUploadPath).getArchivedPath();
          if (archivedFilePath != null && Files.exists(Paths.get(archivedFilePath))) {
            File archivedFile = new File(archivedFilePath);
            org.apache.commons.io.FileUtils.deleteQuietly(archivedFile);
          }
        }
      }
    }

    return filesToUpload;
  }

  private void processLogConfigs() {
    Map<File, LogFileConfig> matchingFiles = new HashMap<>();
    discoverFilesAndUpdateLogConfigMap(matchingFiles, logAgentConfig.getLogFileConfigs());
    logger.info("Discovered total {} files.", matchingFiles.size());

   // long startTime = System.currentTimeMillis();

    Map<File, LogFile> filesToUpload = new HashMap<>();
    Map<String, LogFile> recoveryMap = new HashMap<>();

    try {
      //  recoveryMap = LogUtils.readLogConfigMapFromDisk(new File(this.logAgentConfig.getRecoveryPath()));
       filesToUpload = filterLogFiles(matchingFiles);
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
      logger.info("Exception occurred while uploading the files {}", e);
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
