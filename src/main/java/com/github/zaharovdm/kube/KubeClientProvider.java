package com.github.zaharovdm.kube;

import com.github.zaharovdm.configuration.MainConfiguration;
import com.github.zaharovdm.secuirty.oauth.openshift.services.OpenshiftOAuth2UserRegistry;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@AllArgsConstructor
public class KubeClientProvider implements Supplier<KubernetesClient> {

    private final OpenshiftOAuth2UserRegistry openshiftOAuth2UserRegistry;
    private final MainConfiguration mainConfiguration;

    @Override
    public KubernetesClient get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String accessToken = openshiftOAuth2UserRegistry.getAccessToken(authentication.getName());

        OpenShiftConfigBuilder openShiftConfigBuilder = new OpenShiftConfigBuilder()
                .withOauthToken(accessToken);
        openShiftConfigBuilder.withTrustCerts(true);
        String kubeApiUrl = mainConfiguration.getKubeApiUrl();
        if (kubeApiUrl != null && !kubeApiUrl.isEmpty()) {
            openShiftConfigBuilder.withMasterUrl(kubeApiUrl);
        }
        return new DefaultKubernetesClient(openShiftConfigBuilder.build());
    }
}
