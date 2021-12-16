package com.arorasagar.logagent.utils;

import com.arorasagar.logagent.model.LogFile;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class LogUtils {

  public static boolean fileNeedsUpload(File file2Upload, LogFile logFile) {
    if (file2Upload.length() == 0) {
      return false;
    }

    Long lastUploaded = logFile.getLastUploadedTs();

    return lastUploaded == null || lastUploaded < file2Upload.lastModified();
  }

  public static Map<String, LogFile> readLogConfigMapFromDisk(File file) throws IOException {
    Map<String, LogFile> map = new HashMap<>();
    try (
        InputStream inputStream = Files
            .newInputStream(Paths.get(file.getAbsolutePath()));
        JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
    ) {
      reader.beginArray();
      while (reader.hasNext()) {
        LogFile logFile = new Gson().fromJson(reader, LogFile.class);
        if (logFile != null && logFile.getFilePath() != null) {
          map.put(logFile.getFilePath(), logFile);
        }
      }
      reader.endArray();
    }

    return map;
  }

  public static void writeLogConfigMapToDisk(Map<String, LogFile> map, File file) throws IOException {
    try (
        OutputStream outputStream = Files
            .newOutputStream(Paths.get(file.getAbsolutePath()));
        JsonWriter writer = new JsonWriter(
            new OutputStreamWriter(outputStream));
    ) {
      writer.beginArray();
      for (Map.Entry<String, LogFile> entry : map.entrySet()) {
        LogFile logFile = entry.getValue();
        writer.beginObject();
        writer.name("filePath").value(logFile.getFilePath());
        writer.name("lastUploadedTs").value(logFile.getLastUploadedTs());
        writer.name("lastUploadedMD5Hash").value(logFile.getLastUploadedMD5Hash());
        writer.name("path");
        writer.beginObject();
        writer.name("bucket").value(logFile.getPath().getBucket());
        writer.name("object").value(logFile.getPath().getObject());
        writer.endObject();
        writer.endObject();
      }
      writer.endArray();
    }
  }

  private LogUtils() {}
}
