package org.acme.entity;


import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "tb_account_quarkus")
@Data
public class AccountEntity extends BaseEntity {

    @Column(name = "ACCOUNT_USERNAME", length = 40, unique = true)
    private String username;

    @Column(name = "ACCOUNT_PASSWORD", length = 40)
    private String password;
}
