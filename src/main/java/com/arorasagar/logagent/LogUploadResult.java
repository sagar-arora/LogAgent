package com.arorasagar.logagent;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
public class LogUploadResult {
  public String logPath;
  public long uploadSize;
}
