package com.arorasagar.logagent.model;

import com.arorasagar.logagent.LogAgentConfig;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class LogFile {
  @Id
  private String filePath;
  private Long lastUploadedTs;
  private String lastUploadedMD5Hash;
  //private LogAgentConfig.Path path;
}
