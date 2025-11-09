package org.acme.mapper;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import org.acme.dto.BaseDTO;
import org.acme.entity.BaseEntity;

import java.util.Collection;
import java.util.List;

public interface Mapper<Entity extends PanacheEntityBase,Dto extends BaseDTO> {

    Entity fromDtoToEntity(Dto in);
    Dto fromEntityToDto(Entity in);

    List<Entity> fromDtoToEntityList(List<Dto> in);
    List<Dto> fromEntityToDtoList(List<Entity> in);
}
