package com.github.zaharovdm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class ServiceDTO {
    Map<String, String> selectors;
    String name;
}
