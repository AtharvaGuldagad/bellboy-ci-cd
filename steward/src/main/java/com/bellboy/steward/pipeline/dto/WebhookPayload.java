package com.bellboy.steward.pipeline.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WebhookPayload {

    @JsonProperty("repo_url")
    private String repoUrl;

    @JsonProperty("branch")
    private String branch;

    @JsonProperty("commit_sha")
    private String commitSha;
}