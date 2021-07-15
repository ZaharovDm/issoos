package com.github.zaharovdm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class NamespaceDTO {

    String name;
    List<PodDTO> podDTOList;
    List<RouteDTO> routeDTOList;
    List<ServiceDTO> serviceDTOList;
}
