package com.arorasagar.logagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.arorasagar.logagent.utils.FileUtils;
import lombok.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public final class LogPusherConfig {

  List<LogConfig> logConfigs;
  String tmpDir;
  int delayBetweenCycles;
  String recoveryPath;
  int retentionPeriod;
  String endPointType;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static final class Path {
    private String bucket;
    private String object;
  }

  @Getter
  @Setter
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static final class LogConfig {
    private String localDirectory;
    private List<Pattern> includes;
    private List<Pattern> excludes;
    private Path path;
    private boolean isRecursive;

    private String extractSubPath(String input) {
      String path2Match = localDirectory + (localDirectory.endsWith(File.separator) ? "" : File.separator);
      int startIdx = input.indexOf(path2Match);
      if (startIdx != 0) {
        return null;
      }
      return FileUtils.makeRelative(input.substring(path2Match.length()));
    }

    public Matcher findMatch(String input) {
      String path = extractSubPath(input);
      if (path == null) {
        return null;
      }

      Matcher matcher = null;
      for (int i = 0; excludes != null && i < excludes.size(); i++) {
        matcher = excludes.get(i).matcher(path);
        if (matcher.matches()) {
          return null;
        }
      }

      for (int i = 0; includes != null && i < includes.size(); i++) {
        matcher = includes.get(i).matcher(path);
        if (matcher.matches()) {
          return matcher;
        }
      }
      return null;
    }

    public boolean accepts(String path) {
      return findMatch(path) != null;
    }
  }


  public static LogPusherConfig fromJsonFile(File jsonFile) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    LogPusherConfig
        logPusherConfig = objectMapper.readValue(jsonFile, LogPusherConfig.class);

    if (logPusherConfig.getLogConfigs() == null || logPusherConfig.getLogConfigs().size() == 0) {

    }

    if (logPusherConfig.delayBetweenCycles <= 0) {

    }

    return logPusherConfig;
  }

}
