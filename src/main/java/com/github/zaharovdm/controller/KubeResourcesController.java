package com.github.zaharovdm.controller;

import com.github.zaharovdm.dto.NamespaceDTO;
import com.github.zaharovdm.dto.PodDTO;
import com.github.zaharovdm.dto.RouteDTO;
import com.github.zaharovdm.dto.ServiceDTO;
import com.github.zaharovdm.kube.KubeClientProvider;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;


@RestController
public class KubeResourcesController {
    private final KubeClientProvider kubeClientProvider;

    public KubeResourcesController(KubeClientProvider kubeClientProvider) {
        this.kubeClientProvider = kubeClientProvider;
    }

    @GetMapping("/")
    public List<NamespaceDTO> getNamespaceInfo() throws IOException {
        KubernetesClient kubernetesClient = kubeClientProvider.get();
        OpenShiftClient openShiftClient = kubernetesClient.adapt(OpenShiftClient.class);
        List<String> nameProjects =
                openShiftClient.projects().list().getItems().stream().map(project -> project.getMetadata().getName()).collect(Collectors.toList());
        List<NamespaceDTO> namespaceDTOS = nameProjects.stream().map(name -> {
            List<RouteDTO> routeDTOS = openShiftClient.routes().inNamespace(name).list().getItems().stream()
                    .map(route -> new RouteDTO(route.getMetadata().getName(), route.getSpec().getHost())).collect(Collectors.toList());
            List<ServiceDTO> serviceDTOS = openShiftClient.services().inNamespace(name).list().getItems().stream()
                    .map(service -> new ServiceDTO(service.getSpec().getSelector(), service.getMetadata().getName()))
                    .collect(Collectors.toList());
            List<PodDTO> podDTOS = openShiftClient.pods().inNamespace(name).list().getItems().stream()
                    .map(pod -> new PodDTO(pod.getMetadata().getName(), pod.getStatus().getPhase().toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toList());
            return new NamespaceDTO(name, podDTOS, routeDTOS, serviceDTOS);
        }).collect(Collectors.toList());
        return namespaceDTOS;
    }

}
