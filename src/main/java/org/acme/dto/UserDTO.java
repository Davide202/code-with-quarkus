package org.acme.dto;


import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true,callSuper = true)
public class UserDTO extends BaseDTO{

    private AccountDTO account;
    private String nome;
    private String cognome;
}
