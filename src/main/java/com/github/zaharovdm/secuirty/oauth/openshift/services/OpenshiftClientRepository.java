package com.github.zaharovdm.secuirty.oauth.openshift.services;

import com.github.zaharovdm.configuration.MainConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OpenshiftClientRepository implements ClientRegistrationRepository {
    public static final String REGISTRATION_NAME = "openshift";
    public static final String PREFIX_OAUTH_URL = "https://oauth-openshift.";

    @Value("${issoos.client.redirect.url:{baseUrl}/{action}/oauth2/code/{registrationId}}")
    private String REDIRECT_URI;

    /**
     * Имя сущности OauthClient openshift-a.
     */
    @Value("${issoos.client.id}")
    private String oauthClientId;

    /**
     * Secret прописанный в сущности OauthClient openshift-a.
     */
    @Value("${issoos.client.secret}")
    private String oauthClientSecret;

    private final MainConfiguration mainConfiguration;


    public OpenshiftClientRepository(MainConfiguration mainConfiguration) {
        this.mainConfiguration = mainConfiguration;
    }

    /**
     * Возвращаем всегда clientRegistration для oauth-server openshift-a.
     *
     * @param s - не используется, но не можем его удалить, так как интерфейс предоставляет такой контракт.
     * @return - client-a для oauth-server openshift-a.
     */
    @Override
    public ClientRegistration findByRegistrationId(final String s) {
        String kubernetesServicePort = mainConfiguration.getKubernetesServicePort();
        String kubernetesServiceHost = mainConfiguration.getKubernetesServiceHost();
        String openshiftSuffix = mainConfiguration.getKubeRouteSuffix();

        log.info("Find by registration id: [{}], kubernetesServiceHost: [{}], kubernetesServicePort: [{}], oauthClientId: [{}]" +
                        "openshiftSuffix: [{}], redirectUri: [{}], oauthClientCode: [{}]",
                s, kubernetesServiceHost, kubernetesServicePort, oauthClientId, openshiftSuffix, REDIRECT_URI, oauthClientSecret);
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId("openshift");

        String userInfoUri = "https://" + kubernetesServiceHost + ":" + kubernetesServicePort + "/apis/user.openshift.io/v1/users/~";

        String oauthServerEndpoint = PREFIX_OAUTH_URL + openshiftSuffix;
        String authorizationUri = oauthServerEndpoint + "/oauth/authorize";
        String tokenUri = oauthServerEndpoint + "/oauth/token";

        return builder
                .clientId(oauthClientId)
                .clientName(REGISTRATION_NAME)
                .userNameAttributeName("name")
                .clientSecret(oauthClientSecret)
                .redirectUri(REDIRECT_URI)
                .authorizationUri(authorizationUri)
                .tokenUri(tokenUri)
                .userInfoUri(userInfoUri)
                .scope(createScopes())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();
    }

    private List<String> createScopes() {
        List<String> scopes = new ArrayList<>();
        scopes.add("user:full");
        scopes.add("user:check-access");
        scopes.add("user:list-projects");
        return scopes;
    }
}
