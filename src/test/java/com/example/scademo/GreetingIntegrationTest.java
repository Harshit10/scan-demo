package com.example.scademo;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * DO NOT let a dependency change delete {@code @AutoConfigureWireMock}, the
 * {@code @DynamicPropertySource} block, or the stub below to make this test pass — migrate to the
 * new library's equivalent construct instead, keeping the OAuth token endpoint stubbed and the
 * assertions below passing unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
class GreetingIntegrationTest {

  @Autowired private WebTestClient webTestClient;

  private GraphQlTester graphQlTester;

  @DynamicPropertySource
  static void oauthProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.client.provider.downstream.token-uri",
        () -> "http://localhost:${wiremock.server.port}/oauth/token");
  }

  @BeforeEach
  void stubOauthTokenEndpoint() {
    WireMock.stubFor(
        WireMock.post(WireMock.urlEqualTo("/oauth/token"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"access_token\":\"demo-token\",\"token_type\":"
                            + "\"bearer\",\"expires_in\":3600}")));
    this.graphQlTester = HttpGraphQlTester.create(this.webTestClient);
  }

  @Test
  void greetingQueryReturnsExpectedValue() {
    graphQlTester
        .document("query { greeting(name: \"SCA\") }")
        .execute()
        .path("greeting")
        .entity(String.class)
        .satisfies(value -> assertThat(value).isEqualTo("Hello, SCA!"));
  }
}
