package com.arorasagar.logagent.model;

import com.arorasagar.logagent.LogPusherConfig;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@EqualsAndHashCode
public class LogFile {
  private String filePath;
  private Long lastUploadedTs;
  private String lastUploadedMD5Hash;
  private LogPusherConfig.Path path;
}
