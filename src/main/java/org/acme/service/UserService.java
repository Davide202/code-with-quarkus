package org.acme.service;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Context;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.UserDTO;
import org.acme.entity.AccountEntity;
import org.acme.entity.BaseEntity;
import org.acme.entity.UserEntity;
import org.acme.mapper.UserMapper;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.jboss.logging.Logger;


@ApplicationScoped
public class UserService {

    //private static final Logger log = Logger.getLogger(UserService.class);

    @Inject
    Logger log;

    @Inject
    private UserMapper userMapper;

    @WithTransaction
    public Uni<List<UserDTO>> listUsers(){
        return UserEntity.findAll()
                .list()
                .map(x -> userMapper.fromPanacheEntityToDtoList(x));
    }


    @WithTransaction
    public Uni<UserDTO> insert(UserDTO userDTO) throws ExecutionException, InterruptedException {
        UserEntity userEntity = userMapper.fromDtoToEntity(userDTO);
        log.info("Saving " + userDTO.toString());
        //AccountEntity accountEntity = userEntity.getAccount();
        return userEntity.getAccount().persist()
                .onItem()
                .transform(panacheEntityBase -> {
                    AccountEntity baseEntity = (AccountEntity) panacheEntityBase;
                    Objects.requireNonNull(baseEntity.getId());
                    log.info("Saved Account " + baseEntity);
                    userEntity.getAccount().setId(baseEntity.getId());
                    return userEntity;
                })
                .chain(x -> {
                    log.info("Saving user " + x.toString());
                    return x.persist();
                })
                .onItem()
                .transform(x -> userMapper.fromEntityToDto((UserEntity) x));
    }


}
