package com.github.zaharovdm.secuirty.oauth.openshift.configuration;

import com.github.zaharovdm.secuirty.oauth.openshift.services.OpenshiftClientRepository;
import com.github.zaharovdm.secuirty.oauth.openshift.services.OpenshiftOAuth2UserRegistry;
import com.github.zaharovdm.secuirty.oauth.openshift.services.OpenshiftOAuth2UserService;
import lombok.SneakyThrows;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final OpenshiftClientRepository openshiftClientRegistration;
    private final InMemoryOAuth2AuthorizedClientService inMemoryOAuth2AuthorizedOpenshiftClientService;

    public SecurityConfig(final OpenshiftClientRepository openshiftClientRegistration,
                          final OpenshiftOAuth2UserRegistry openshiftOAuth2UserRegistry) {
        this.openshiftClientRegistration = openshiftClientRegistration;
        this.inMemoryOAuth2AuthorizedOpenshiftClientService =
                openshiftOAuth2UserRegistry.getInMemoryOAuth2AuthorizedClientService();
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        http.csrf().disable();

        http
                .oauth2Login(this::prepareOpenshiftOauth2Login)
                .authorizeRequests()
                .antMatchers("/login**", "/login/**", "/callback/", "/webjars/**", "/error**")
                .permitAll()
                .anyRequest()
                .authenticated()
                .and().logout().logoutSuccessUrl("/oauth2/authorization/openshift").permitAll()
                .and()
                .oauth2Login();
    }

    private OAuth2LoginConfigurer<HttpSecurity> prepareOpenshiftOauth2Login(final OAuth2LoginConfigurer<HttpSecurity> oauth2Login) {
        DefaultAuthorizationCodeTokenResponseClient oAuth2AccessTokenResponseClient = new DefaultAuthorizationCodeTokenResponseClient();
        //Подменяем restOperation, в данный момент для включения trustAll = true, в целевом варианте вам следует подложить сертификаты
        oAuth2AccessTokenResponseClient.setRestOperations(trustAllRestTemplateForTokenResponseClient());

        return oauth2Login
                .authorizedClientRepository(new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(inMemoryOAuth2AuthorizedOpenshiftClientService))
                .userInfoEndpoint(userInfoEndpointConfig ->
                        userInfoEndpointConfig.userService(new OpenshiftOAuth2UserService()))
                .tokenEndpoint(tokenEndpointConfig ->
                        tokenEndpointConfig.accessTokenResponseClient(oAuth2AccessTokenResponseClient))
                .clientRegistrationRepository(new InMemoryClientRegistrationRepository(openshiftClientRegistration.findByRegistrationId(null)));
    }

    @SneakyThrows
    private RestTemplate trustAllRestTemplateForTokenResponseClient() {
        TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

        SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();

        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(csf)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory();

        requestFactory.setHttpClient(httpClient);

        RestTemplate restTemplate = new RestTemplate(Arrays.asList(
                new FormHttpMessageConverter(), new OAuth2AccessTokenResponseHttpMessageConverter()));
        restTemplate.setRequestFactory(requestFactory);
        return restTemplate;
    }
}
