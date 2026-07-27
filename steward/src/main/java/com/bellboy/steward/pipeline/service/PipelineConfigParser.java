package com.bellboy.steward.pipeline.service;

// Searches the repo for a .bellboy.yml and maps to the POJOs made inside the dto

import com.bellboy.steward.pipeline.dto.config.BellboyConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

@Service
public class PipelineConfigParser {

    private final ObjectMapper yamlMapper;

    public PipelineConfigParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        // user adds a weird field we haven't defined? simply ignore it..
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public BellboyConfig parseConfiguration(Path workspacePath) throws IOException {
        File yamlFile = workspacePath.resolve(".bellboy.yml").toFile();

        if (!yamlFile.exists()) {
            // :( if someone writes .yaml.. who does ts
            yamlFile = workspacePath.resolve(".bellboy.yaml").toFile();
            if (!yamlFile.exists()) {
                throw new FileNotFoundException("Missing .bellboy.yml in the repository root. ts is invalid.");
            }
        }
        return yamlMapper.readValue(yamlFile, BellboyConfig.class);
    }
}