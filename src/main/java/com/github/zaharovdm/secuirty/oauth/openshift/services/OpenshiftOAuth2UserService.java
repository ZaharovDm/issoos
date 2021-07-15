package com.github.zaharovdm.secuirty.oauth.openshift.services;

import lombok.SneakyThrows;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequestEntityConverter;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Кастомная реализация {@link OAuth2UserService}, настроенная дла работы с openshift oauth server.
 */
public class OpenshiftOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private static final String MISSING_USER_INFO_URI_ERROR_CODE = "missing_user_info_uri";
    private static final String MISSING_USER_NAME_ATTRIBUTE_ERROR_CODE = "missing_user_name_attribute";
    private static final String INVALID_USER_INFO_RESPONSE_ERROR_CODE = "invalid_user_info_response";
    private static final ParameterizedTypeReference<Map<String, Object>> PARAMETERIZED_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final Converter<OAuth2UserRequest, RequestEntity<?>> requestEntityConverter = new OAuth2UserRequestEntityConverter();
    private final RestOperations restOperations;

    public OpenshiftOAuth2UserService() {
        this.restOperations = createRestTemplate();
    }

    @Override
    public OAuth2User loadUser(final OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        Assert.notNull(userRequest, "userRequest cannot be null");

        checkUserInfoEndpoint(userRequest);

        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();
        checkUserAttributeName(userRequest, userNameAttributeName);

        RequestEntity<?> request = this.requestEntityConverter.convert(userRequest);
        ResponseEntity<Map<String, Object>> response;

        try {
            response = this.restOperations.exchange(request, PARAMETERIZED_RESPONSE_TYPE);
        } catch (OAuth2AuthorizationException ex) {
            OAuth2Error oauth2Error = ex.getError();
            StringBuilder errorDetails = getErrorDetailsOauth2AuthorizationException(userRequest, oauth2Error);
            oauth2Error = new OAuth2Error(INVALID_USER_INFO_RESPONSE_ERROR_CODE,
                    "An error occurred while attempting to retrieve the UserInfo Resource: " + errorDetails, null);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString(), ex);
        } catch (RestClientException ex) {
            OAuth2Error oauth2Error = new OAuth2Error(INVALID_USER_INFO_RESPONSE_ERROR_CODE,
                    "An error occurred while attempting to retrieve the UserInfo Resource: " + ex.getMessage(), null);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString(), ex);
        }

        Map<String, Object> userAttributes = response.getBody();
        prepareUserAttributes(userAttributes);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new OAuth2UserAuthority(userAttributes));
        OAuth2AccessToken token = userRequest.getAccessToken();
        for (String authority : token.getScopes()) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + authority));
        }

        return new DefaultOAuth2User(authorities, userAttributes, userNameAttributeName);
    }

    private StringBuilder getErrorDetailsOauth2AuthorizationException(OAuth2UserRequest userRequest, OAuth2Error oauth2Error) {
        StringBuilder errorDetails = new StringBuilder();
        errorDetails.append("Error details: [");
        errorDetails.append("UserInfo Uri: ").append(
                userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri());
        errorDetails.append(", Error Code: ").append(oauth2Error.getErrorCode());
        if (oauth2Error.getDescription() != null) {
            errorDetails.append(", Error Description: ").append(oauth2Error.getDescription());
        }
        errorDetails.append("]");
        return errorDetails;
    }

    private void checkUserAttributeName(OAuth2UserRequest userRequest, String userNameAttributeName) {
        if (!StringUtils.hasText(userNameAttributeName)) {
            OAuth2Error oauth2Error = new OAuth2Error(
                    MISSING_USER_NAME_ATTRIBUTE_ERROR_CODE,
                    "Missing required \"user name\" attribute name in UserInfoEndpoint for Client Registration: " +
                            userRequest.getClientRegistration().getRegistrationId(),
                    null
            );
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
    }

    private void checkUserInfoEndpoint(OAuth2UserRequest userRequest) {
        if (!StringUtils.hasText(userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri())) {
            OAuth2Error oauth2Error = new OAuth2Error(
                    MISSING_USER_INFO_URI_ERROR_CODE,
                    "Missing required UserInfo Uri in UserInfoEndpoint for Client Registration: " +
                            userRequest.getClientRegistration().getRegistrationId(),
                    null
            );
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
    }

    /**
     * Подготавливает полученные user атрибуты, путем вынеса имя пользователя из metadata на первый уровень вложения.
     * P.S: Доработка для работы с oauth server-ом.
     */
    private void prepareUserAttributes(final Map<String, Object> userAttributes) {
        Map<String, String> metadata = (Map<String, String>) userAttributes.get("metadata");
        String name = metadata.get("name");
        userAttributes.put("name", name);
    }


    private RestTemplate createRestTemplate() {
        HttpComponentsClientHttpRequestFactory requestFactory = prepareHttpRequestFactoryTrustAll();

        RestTemplate restTemplate = new RestTemplate();

        restTemplate.setRequestFactory(requestFactory);
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        return restTemplate;
    }

    @SneakyThrows
    private HttpComponentsClientHttpRequestFactory prepareHttpRequestFactoryTrustAll() {
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
        return requestFactory;
    }
}
