package com.aims.assembly.config;

import com.aims.assembly.properties.OpenSearchProperties;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OpenSearchConfig {

    private final OpenSearchProperties openSearchProperties;

    @Bean(destroyMethod = "close")
    public OpenSearchTransport openSearchTransport() {
        HttpHost host = new HttpHost(
                openSearchProperties.getScheme(),
                openSearchProperties.getHost(),
                openSearchProperties.getPort()
        );

        return ApacheHttpClient5TransportBuilder.builder(host).build();
    }

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport openSearchTransport) {
        return new OpenSearchClient(openSearchTransport);
    }
}
