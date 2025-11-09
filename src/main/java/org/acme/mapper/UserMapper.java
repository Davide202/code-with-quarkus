package org.acme.mapper;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.dto.UserDTO;
import org.acme.entity.UserEntity;

@ApplicationScoped
//@RequiredArgsConstructor
public class UserMapper
extends AbstractMapper<UserEntity, UserDTO>
{

    @Inject
    private AccountMapper accountMapper;


    @Override
    public UserEntity fromDtoToEntity(UserDTO in) {
        UserEntity o = new UserEntity();
        o.setId(in.getId());
        o.setNome(in.getNome());
        o.setCognome(in.getCognome());
        o.setAccount(accountMapper.fromDtoToEntity(in.getAccount()));
        return o;
    }

    @Override
    public UserDTO fromEntityToDto(UserEntity in) {
        UserDTO o = new UserDTO();
        o.setId(in.getId());
        o.setNome(in.getNome());
        o.setCognome(in.getCognome());
        o.setAccount(accountMapper.fromEntityToDto(in.getAccount()));
        return o;
    }
}
