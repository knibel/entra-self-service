package com.knibel.entraselfservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void restTemplateUsesPatchCapableRequestFactory() {
        RestTemplate restTemplate = new SecurityConfig().restTemplate();

        assertThat(restTemplate.getRequestFactory()).isInstanceOf(JdkClientHttpRequestFactory.class);
    }
}
