package com.ecommerce.api.auth.filter;

import com.ecommerce.api.auth.config.LoadTestAuthProperties;
import com.ecommerce.api.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 부하테스트 인증 우회의 기동 시 검사를 실제 {@code application.yaml}과 {@code application-loadtest.yaml}로 확인한다.
 * 필터와 설정 바인딩만 띄우므로 DB와 Redis는 필요 없다.
 */
class LoadTestAuthenticationFilterStartupTest {

    private static final String AUTH_ENABLED = "app.loadtest.auth.enabled=true";

    // 개발자 PC나 CI에 LOADTEST_AUTH_SECRET 같은 환경변수가 있어도 결과가 바뀌지 않게, 설정 파일 외의 값은 모두 막는다
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            })
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(FilterOnlyConfig.class);

    @Test
    @DisplayName("loadtest 프로필에서 우회를 켜고 비밀값을 설정하지 않으면 서버가 기동하지 않는다")
    void startup_loadtestProfileEnabledWithoutSecret_fails() {
        runner.withPropertyValues("spring.profiles.active=loadtest", AUTH_ENABLED)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context)).contains("LOADTEST_AUTH_SECRET");
                });
    }

    @Test
    @DisplayName("loadtest 프로필에서 우회를 켜지 않으면 비밀값 없이도 기동하고 필터 빈이 없다")
    void startup_loadtestProfileDisabledWithoutSecret_startsWithoutFilter() {
        runner.withPropertyValues("spring.profiles.active=loadtest")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(LoadTestAuthenticationFilter.class);
                });
    }

    @Test
    @DisplayName("비밀값이 15자이면 서버가 기동하지 않고 오류 메시지에 비밀값이 들어가지 않는다")
    void startup_secretOf15Chars_failsWithoutExposingSecret() {
        String secret = "a1b2c3d4e5f6g7h";
        assertThat(secret).hasSize(15);

        runner.withPropertyValues("spring.profiles.active=loadtest", AUTH_ENABLED, "app.loadtest.auth.secret=" + secret)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context)).contains("16").doesNotContain(secret);
                });
    }

    @Test
    @DisplayName("비밀값이 16자이면 서버가 기동하고 필터 빈이 생긴다")
    void startup_secretOf16Chars_startsWithFilter() {
        String secret = "a1b2c3d4e5f6g7h8";
        assertThat(secret).hasSize(16);

        runner.withPropertyValues("spring.profiles.active=loadtest", AUTH_ENABLED, "app.loadtest.auth.secret=" + secret)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LoadTestAuthenticationFilter.class);
                });
    }

    @Test
    @DisplayName("loadtest 프로필이 아니면 우회를 켜도 필터 빈이 없다")
    void startup_withoutLoadtestProfile_hasNoFilter() {
        runner.withPropertyValues("spring.profiles.active=dev", AUTH_ENABLED)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(LoadTestAuthenticationFilter.class);
                });
    }

    private static String rootCauseMessage(AssertableApplicationContext context) {
        Throwable failure = context.getStartupFailure();
        while (failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure.getMessage();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoadTestAuthProperties.class)
    @Import(LoadTestAuthenticationFilter.class)
    static class FilterOnlyConfig {

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }
    }
}
