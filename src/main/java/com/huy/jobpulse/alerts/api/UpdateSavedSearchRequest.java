package com.huy.jobpulse.alerts.api;

import com.huy.jobpulse.jobs.domain.JobSource;
import com.huy.jobpulse.jobs.domain.RemotePolicy;
import jakarta.validation.constraints.Size;

public class UpdateSavedSearchRequest {

    @Size(max = 100)
    private String name;

    @Size(max = 150)
    private String query;

    @Size(max = 255)
    private String company;

    private JobSource source;
    private RemotePolicy remotePolicy;

    @Size(max = 500)
    private String location;

    private Boolean enabled;

    private boolean namePresent;
    private boolean queryPresent;
    private boolean companyPresent;
    private boolean sourcePresent;
    private boolean remotePolicyPresent;
    private boolean locationPresent;
    private boolean enabledPresent;

    public UpdateSavedSearchRequest() {
    }

    public String name() { return name; }
    public String query() { return query; }
    public String company() { return company; }
    public JobSource source() { return source; }
    public RemotePolicy remotePolicy() { return remotePolicy; }
    public String location() { return location; }
    public Boolean enabled() { return enabled; }
    public boolean namePresent() { return namePresent; }
    public boolean queryPresent() { return queryPresent; }
    public boolean companyPresent() { return companyPresent; }
    public boolean sourcePresent() { return sourcePresent; }
    public boolean remotePolicyPresent() { return remotePolicyPresent; }
    public boolean locationPresent() { return locationPresent; }
    public boolean enabledPresent() { return enabledPresent; }

    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    public void setQuery(String query) {
        this.query = query;
        this.queryPresent = true;
    }

    public void setCompany(String company) {
        this.company = company;
        this.companyPresent = true;
    }

    public void setSource(JobSource source) {
        this.source = source;
        this.sourcePresent = true;
    }

    public void setRemotePolicy(RemotePolicy remotePolicy) {
        this.remotePolicy = remotePolicy;
        this.remotePolicyPresent = true;
    }

    public void setLocation(String location) {
        this.location = location;
        this.locationPresent = true;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
        this.enabledPresent = true;
    }
}
