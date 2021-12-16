package com.arorasagar.logagent.endpoint;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

import java.io.File;

public class S3Endpoint implements Endpoint {

  public static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain";
  public static final String CONTENT_TYPE_BINARY = "application/octet-stream";
  public static final String CONTENT_TYPE_TEXT_HTML = "text/html";
  public static final String CONTENT_ENCODING_GZIP = "gzip";

  private final AmazonS3 s3Client;

  public S3Endpoint() {
     this(AmazonS3ClientBuilder.standard()
        .withRegion(Regions.US_EAST_1)
        .build());
  }

  public S3Endpoint(AmazonS3 s3Client) {
    this.s3Client = s3Client;
  }

  @Override
  public void uploadData(File file, String key, String bucket, String md5)
      throws InterruptedException {
    PutObjectRequest request = new PutObjectRequest(bucket, key, file);
    ObjectMetadata metaData = new ObjectMetadata();
    if (key.endsWith(".html") || key.endsWith(".html.gz")) {
      metaData.setContentType(CONTENT_TYPE_TEXT_HTML);
    } else if (key.endsWith(".bin")) {
      metaData.setContentType(CONTENT_TYPE_BINARY);
    } else {
      metaData.setContentType(CONTENT_TYPE_TEXT_PLAIN);
    }
    if (!key.endsWith(".bin")) {
      metaData.setContentEncoding(CONTENT_ENCODING_GZIP);
    }
    request.setMetadata(metaData);

    s3Client.putObject(request);
  }
}
