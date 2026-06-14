package com.aiplatform.knowledge.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "aiplatform.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchConfig {

    @Value("${aiplatform.elasticsearch.host:127.0.0.1}")
    private String host;

    @Value("${aiplatform.elasticsearch.port:9200}")
    private int port;

    @Value("${aiplatform.elasticsearch.scheme:http}")
    private String scheme;

    @Value("${aiplatform.elasticsearch.username:}")
    private String username;

    @Value("${aiplatform.elasticsearch.password:}")
    private String password;

    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        var builder = RestClient.builder(new HttpHost(host, port, scheme));
        if (username != null && !username.isBlank()) {
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider()));
        }
        return builder.build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    private org.apache.http.client.CredentialsProvider credentialsProvider() {
        var cp = new org.apache.http.impl.client.BasicCredentialsProvider();
        cp.setCredentials(org.apache.http.auth.AuthScope.ANY,
                new org.apache.http.auth.UsernamePasswordCredentials(username, password));
        return cp;
    }
}
