package org.acme.dto;


import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BaseDTO {


    @EqualsAndHashCode.Include
    protected Long id;
}
