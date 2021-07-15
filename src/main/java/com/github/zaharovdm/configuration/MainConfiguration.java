package com.github.zaharovdm.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class MainConfiguration {

    /**
     * Host kube сервиса. Берется из переменных окружения.
     */
    @Value("#{environment.KUBERNETES_SERVICE_HOST}")
    private String kubernetesServiceHost;

    /**
     * Port kube сервиса. Берется из переменных окружения.
     */
    @Value("#{environment.KUBERNETES_SERVICE_PORT}")
    private String kubernetesServicePort;

    /**
     * Имя кластера openshift-a, который добавляется в конце маршрута openshift-a
     * (Например для Code Ready Containers это apps-crc.testing)
     */
    @Value("${issoos.kube.route.suffix}")
    private String kubeRouteSuffix;

    @Value("${issoos.kube.api.url}")
    @Getter
    private String kubeApiUrl;

}
