package com.github.zaharovdm.secuirty.oauth.openshift.services;

import lombok.Getter;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

@Service
public class OpenshiftOAuth2UserRegistry {
    @Getter
    private final InMemoryOAuth2AuthorizedClientService inMemoryOAuth2AuthorizedClientService;

    public OpenshiftOAuth2UserRegistry(final OpenshiftClientRepository openshiftClientRegistration) {
        this.inMemoryOAuth2AuthorizedClientService = new InMemoryOAuth2AuthorizedClientService(openshiftClientRegistration);
    }

    public String getAccessToken(final String userName) {
        OAuth2AuthorizedClient oAuth2AuthorizedClient = inMemoryOAuth2AuthorizedClientService
                .loadAuthorizedClient(OpenshiftClientRepository.REGISTRATION_NAME, userName);
        if (oAuth2AuthorizedClient == null || oAuth2AuthorizedClient.getAccessToken() == null) {
            throw new RuntimeException("This user does not have a token.");
        }
        return oAuth2AuthorizedClient.getAccessToken().getTokenValue();
    }
}
