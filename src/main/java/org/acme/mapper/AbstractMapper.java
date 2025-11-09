package org.acme.mapper;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import org.acme.dto.BaseDTO;
import org.acme.entity.BaseEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractMapper<Entity extends PanacheEntityBase,Dto extends BaseDTO>
implements Mapper<Entity,Dto>{


    public List<Entity> fromDtoToEntityList(List<Dto> in){
        return in.stream().map(this::fromDtoToEntity)
                .collect(Collectors.toCollection(ArrayList::new));
    }



    public List<Dto> fromEntityToDtoList(List<Entity> in){
        return in.stream().map(this::fromEntityToDto)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<Dto> fromPanacheEntityToDtoList(List<PanacheEntityBase> in){
        return in.stream()
                .map(panacheEntityBase -> (Entity)panacheEntityBase)
                .map(this::fromEntityToDto)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
