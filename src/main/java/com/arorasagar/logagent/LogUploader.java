package com.arorasagar.logagent;

import com.arorasagar.logagent.endpoint.Endpoint;
import com.arorasagar.logagent.model.LogFile;
import com.arorasagar.logagent.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.concurrent.Callable;

@Slf4j
public class LogUploader implements Callable<LogUploadResult> {

  private Endpoint endpoint;
  private File inputFile;
  private LogFile logFile;
  private LogAgentConfig.Path path;
  public LogUploader(File inputFile, LogFile logFile, Endpoint endpoint) {
    this.endpoint = endpoint;
    this.inputFile = inputFile;
    this.logFile = logFile;
    //this.path = logFile.getPath();
  }

  @Override
  public LogUploadResult call() {
    String md5HashOfFile;
    LogUploadResult result = new LogUploadResult();
    try {
      md5HashOfFile = FileUtils.calculateMd5ofFile(inputFile);
      if (md5HashOfFile.equals(logFile.getLastUploadedMD5Hash())) {
        log.info("No Ops since the last hash equals to the current one.");
        return result;
      }
      log.info("Starting the upload of the file: {}", inputFile.getAbsolutePath());
      endpoint.uploadData(inputFile, this.path.getObject() + "/" + inputFile.getName(), this.path
          .getBucket(), md5HashOfFile);
      log.info("Finished the upload of the file: {}", inputFile.getAbsolutePath());
      logFile.setLastUploadedMD5Hash(md5HashOfFile);
      logFile.setLastUploadedTs(System.currentTimeMillis());
      result.logPath = inputFile.getAbsolutePath();
      result.uploadSize = inputFile.length();
    } catch (Exception e) {
      log.info("Exception occurred while uploading the file {} : {}", inputFile.getAbsolutePath(), e.getMessage());
    }
    return result;
  }
}
