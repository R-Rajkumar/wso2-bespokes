package com.raj.gateway.bespokes.cache;

import java.io.Serializable;
import java.util.Map;

public class CoherenceCacheableResponse implements Serializable {

    private static final long serialVersionUID = 8259702359323973101L;
    private byte[] responseEnvelope;
    private String requestHash;
    private Map<String, Object> headerProperties;

    public CoherenceCacheableResponse() {
    }

    public byte[] getResponseEnvelope() {
        return this.responseEnvelope;
    }

    public void setResponseEnvelope(byte[] responseEnvelope) {
        this.responseEnvelope = responseEnvelope;
    }

    public String getRequestHash() {
        return this.requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public Map<String, Object> getHeaderProperties() {
        return this.headerProperties;
    }

    public void setHeaderProperties(Map<String, Object> headerProperties) {
        this.headerProperties = headerProperties;
    }
}
