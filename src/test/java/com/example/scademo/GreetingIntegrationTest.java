package com.example.scademo;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

/**
 * DO NOT let an SCA/version-bump fix delete {@code @EnableWireMock}, the
 * {@code @DynamicPropertySource} block, or the stub below to make this test pass. If a dependency
 * bump breaks this class, the correct fix is to migrate it to whatever the new library calls the
 * equivalent construct (e.g. a renamed annotation, a new WireMock server-management API) while
 * keeping the same behavior: the OAuth token endpoint stays stubbed by WireMock and the assertions
 * below keep passing unchanged.
 *
 * <p>DIRECT EDIT (disclosed, not OpenRewrite — no recipe exists for this migration, confirmed by
 * searching the OpenRewrite catalog before editing). Spring Cloud Contract 5.x dropped
 * {@code @AutoConfigureWireMock} in favor of the official WireMock 3 + Spring Boot integration
 * ({@code org.wiremock.integrations:wiremock-spring-boot});
 * {@code @EnableWireMock}/{@code @ConfigureWireMock} below is that equivalent — it still registers
 * the same {@code wiremock.server.port} property, so {@code @DynamicPropertySource} needs no
 * change. This was also fixed ahead of the OpenRewrite run because the compile error it causes
 * blocks rewriteDryRun/rewriteRun from parsing the test source set at all.
 *
 * <p>OpenRewrite's AddAutoConfigureWebTestClient recipe fired here too (added
 * {@code @AutoConfigureWebTestClient} + its import) but was reverted after applying: the class
 * lives in a module (providing org.springframework.boot.webtestclient.autoconfigure) that isn't
 * resolvable on this project's classpath, so the recipe's change didn't compile standalone. Adding
 * that missing module would mean fixing the pre-existing, unrelated "no qualifying bean of type
 * WebTestClient" gap as a drive-by of this dependency bump — so that gap is left exactly as it's
 * always been, not silently patched here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock({@ConfigureWireMock(port = 0)})
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
