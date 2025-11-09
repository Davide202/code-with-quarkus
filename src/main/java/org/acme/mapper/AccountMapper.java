package org.acme.mapper;


import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.AccountDTO;
import org.acme.entity.AccountEntity;

@ApplicationScoped
public class AccountMapper
extends AbstractMapper<AccountEntity, AccountDTO>
{
    @Override
    public AccountEntity fromDtoToEntity(AccountDTO in) {
        AccountEntity o = new AccountEntity();
        o.setId(in.getId());
        o.setUsername(in.getUsername());
        o.setPassword(in.getPassword());
        return o;
    }

    @Override
    public AccountDTO fromEntityToDto(AccountEntity in) {
        AccountDTO o = new AccountDTO();
        o.setId(in.getId());
        o.setUsername(in.getUsername());
        o.setPassword(in.getPassword());
        return o;
    }
}
