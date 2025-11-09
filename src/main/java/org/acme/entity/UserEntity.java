package org.acme.entity;



import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "tb_user_quarkus")
@Data
public class UserEntity extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "USER_ACCOUNT",nullable = false)
    private AccountEntity account;

    @Column(name = "USER_NOME", length = 40)
    private String nome;

    @Column(name = "USER_COGNOME", length = 40)
    private String cognome;


}
