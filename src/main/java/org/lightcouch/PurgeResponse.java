package org.lightcouch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class PurgeResponse {

    @JsonProperty("purge_seq")
    private String purgeSeq;
    
    public String getPurgeSeq() {
        return purgeSeq;
    }

    public void setPurgeSeq(String purgeSeq) {
        this.purgeSeq = purgeSeq;
    }

    private Map<String,List<String>> purged;

    public Map<String,List<String>> getPurged() {
        return purged;
    }

    public void setPurged(Map<String,List<String>> purged) {
        this.purged = purged;
    }
    
}
