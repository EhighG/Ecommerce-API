package com.ecommerce.api.auth.config;

import com.ecommerce.api.auth.LoginFailureHandler;
import com.ecommerce.api.auth.LoginSuccessHandler;
import com.ecommerce.api.auth.filter.JsonUsernamePasswordAuthenticationFilter;
import com.ecommerce.api.user.enums.UserRole;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.http.HttpMethod.*;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder PATH = PathPatternRequestMatcher.withDefaults();

    private static RequestMatcher matcher(HttpMethod method, String pattern) {
        return PATH.matcher(method, pattern);
    }

    private static RequestMatcher matcher(String pattern) {
        return PATH.matcher(pattern);
    }

    private static RequestMatcher[] adminMatchers() {
        return new RequestMatcher[] {
                matcher(POST, "/products/category"),
                matcher(DELETE, "/products/category/{categoryId}"),

                matcher(GET, "/media/uploaded-images"),

                matcher(GET, "/users"),

                matcher(POST, "/coupons/events"),
        };
    }

    private static RequestMatcher[] allMatchers() {
        return new RequestMatcher[] {
                matcher(GET, "/auth/csrf"),
                matcher(POST, "/auth/login"),
                matcher(POST, "/auth/logout"),

                matcher(POST, "/users"),

                matcher(GET, "/products"),
                matcher(GET, "/products/{productId}"),
                matcher(GET, "/products/category"),

                matcher(GET, "/reviews"),
        };
    }

    private static RequestMatcher[] buyerMatchers() {
        return new RequestMatcher[]{
                matcher("/cart-items/{*path}"),

                matcher(POST, "/orders"),
                matcher(GET, "/orders/me"),
                matcher(GET, "/orders/{orderId}"),

                matcher(PATCH, "/order-items/{orderItemId}/confirm"),

                matcher(POST, "/products/{productId}/reviews"),
                matcher(PATCH, "/reviews/{reviewId}"),

                matcher(GET, "/coupons/events/{couponEventId}"),
                matcher(POST, "/coupons/events/{couponEventId}/issue"),
        };
    }

    private static RequestMatcher[] sellerMatchers() {
        return new RequestMatcher[] {
                matcher(POST, "/products"),
                matcher(PATCH, "/products/{*path}"),
                matcher(DELETE, "/products/{productId}"),

                matcher(PATCH, "/order-items/{orderItemId}/ship"),
                matcher(PATCH, "/order-items/{orderItemId}/deliver"),

                matcher(POST, "/media/{*path}"),
        };
    }

    private static RequestMatcher[] userMatchers() {
        return new RequestMatcher[] {
                matcher(GET, "/users/{userId}"),
                matcher("/users/me/{*path}"),

                matcher(GET, "/order-items"),
                matcher(GET, "/order-items/{orderItemId}"),
                matcher(PATCH, "/order-items/{orderItemId}/cancel"),
        };
    }

    // 서비스 보안설정과 분리해둠
    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        // 모니터링 기능수행 외의 actuator용 포트 접근은 인프라 설정에서 막아둠
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AuthenticationManager authenticationManager,
                                            ObjectMapper objectMapper
    ) throws Exception {
        JsonUsernamePasswordAuthenticationFilter authenticationFilter = new JsonUsernamePasswordAuthenticationFilter(objectMapper);

        SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
        authenticationFilter.setAuthenticationManager(authenticationManager);
        authenticationFilter.setSecurityContextRepository(securityContextRepository);
        authenticationFilter.setAuthenticationSuccessHandler(new LoginSuccessHandler());
        authenticationFilter.setAuthenticationFailureHandler(new LoginFailureHandler());

        http
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                )
                // URL 인가 규칙
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(adminMatchers()).hasRole(UserRole.ADMIN.name())
                        .requestMatchers(allMatchers()).permitAll()
                        .requestMatchers(buyerMatchers()).hasRole(UserRole.BUYER.name())
                        .requestMatchers(sellerMatchers()).hasRole(UserRole.SELLER.name())
                        .requestMatchers(userMatchers()).authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // 예외 핸들링
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) -> res.sendError(HttpServletResponse.SC_FORBIDDEN))
                )
                // 세션 정책
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) // 기본값. 필요한 경우에 세션 생성
                )
                // CORS
                .cors(withDefaults())
                // CSRF
                //
                .csrf(withDefaults())
                // 구현한 로그인 필터 추가
                // formLogin을 disable하면서 비워진 UsernamePasswordAuthenticationFilter자리에 구현한 필터를 삽입
                .addFilterAt(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 로그아웃 관련 설정
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                        })
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) {
        return configuration.getAuthenticationManager();
    }
}
