package com.aims.assembly.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "opensearch")
public class OpenSearchProperties {

    private String scheme = "http";
    private String host = "localhost";
    private int port = 9200;
}
