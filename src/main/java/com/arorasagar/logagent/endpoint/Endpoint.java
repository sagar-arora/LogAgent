package com.arorasagar.logagent.endpoint;

import java.io.File;

public interface Endpoint {
  void uploadData(File file, String key, String bucket, String md5)
      throws Exception;
}
