package com.arorasagar.logagent.model;

import java.util.Optional;

public enum EndpointType {
  S3("S3"),
  GCS("GCS");

  private final String endpointVal;

  EndpointType(String endpointVal) {
    this.endpointVal = endpointVal;
  }

  public String getEndpointVal() {
    return endpointVal;
  }

  public Optional<EndpointType> getEndpoint(String endpointVal) {

    Optional<EndpointType> endpoint = Optional.empty();
    switch (endpointVal.toLowerCase()) {
      case "s3" :
        endpoint = Optional.of(EndpointType.S3);
        break;
      case "gcs":
        endpoint = Optional.of(EndpointType.GCS);
        break;
    }

    return endpoint;
  }
}
