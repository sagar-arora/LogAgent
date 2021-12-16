package com.arorasagar.logagent.model;

public enum EndPointType {
  S3("S3"), GCS("GCS");

  private final String endpointVal;
  EndPointType(String endpointVal) {
    this.endpointVal = endpointVal;
  }

  public String getEndpointVal() {
    return endpointVal;
  }
}
