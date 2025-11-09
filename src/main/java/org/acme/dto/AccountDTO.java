package org.acme.dto;


import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(onlyExplicitlyIncluded = true,callSuper = true)
@Data
public class AccountDTO extends BaseDTO{

    private String username;
    private String password;
}
