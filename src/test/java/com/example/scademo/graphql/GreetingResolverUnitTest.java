package com.example.scademo.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

class GreetingResolverUnitTest {

  @Test
  void greetingFormatsName() {
    GreetingResolver resolver = new GreetingResolver(new RestTemplateBuilder());
    assertThat(resolver.greeting("World")).isEqualTo("Hello, World!");
  }
}
