package com.waqiti.common.mapper;

import org.mapstruct.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Base mapper interface for entity-DTO conversions
 * 
 * This interface provides the foundation for all MapStruct mappers in the system,
 * ensuring consistent mapping behavior and configuration across all services.
 * 
 * @param <E> Entity type
 * @param <D> DTO type
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@MapperConfig(
    componentModel = "spring",
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.WARN,
    unmappedSourcePolicy = ReportingPolicy.WARN,
    builder = @Builder(disableBuilder = false)
)
public interface BaseMapper<E, D> {
    
    /**
     * Converts entity to DTO
     * 
     * @param entity Source entity
     * @return Converted DTO
     */
    D toDto(E entity);
    
    /**
     * Converts DTO to entity
     * 
     * @param dto Source DTO
     * @return Converted entity
     */
    E toEntity(D dto);
    
    /**
     * Converts list of entities to list of DTOs
     * 
     * @param entities Source entities
     * @return List of converted DTOs
     */
    List<D> toDtoList(List<E> entities);
    
    /**
     * Converts list of DTOs to list of entities
     * 
     * @param dtos Source DTOs
     * @return List of converted entities
     */
    List<E> toEntityList(List<D> dtos);
    
    /**
     * Converts set of entities to set of DTOs
     * 
     * @param entities Source entities
     * @return Set of converted DTOs
     */
    Set<D> toDtoSet(Set<E> entities);
    
    /**
     * Converts set of DTOs to set of entities
     * 
     * @param dtos Source DTOs
     * @return Set of converted entities
     */
    Set<E> toEntitySet(Set<D> dtos);
    
    /**
     * Converts stream of entities to stream of DTOs
     * 
     * @param entities Source entity stream
     * @return Stream of converted DTOs
     */
    default Stream<D> toDtoStream(Stream<E> entities) {
        return entities != null ? entities.map(this::toDto) : Stream.empty();
    }
    
    /**
     * Converts stream of DTOs to stream of entities
     * 
     * @param dtos Source DTO stream
     * @return Stream of converted entities
     */
    default Stream<E> toEntityStream(Stream<D> dtos) {
        return dtos != null ? dtos.map(this::toEntity) : Stream.empty();
    }
    
    /**
     * Updates entity from DTO
     * 
     * @param dto Source DTO
     * @param entity Target entity to update
     */
    @BeanMapping(
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
    )
    void updateEntityFromDto(D dto, @MappingTarget E entity);
    
    /**
     * Updates DTO from entity
     * 
     * @param entity Source entity
     * @param dto Target DTO to update
     */
    @BeanMapping(
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
    )
    void updateDtoFromEntity(E entity, @MappingTarget D dto);
    
    /**
     * Performs partial update of entity from DTO
     * Only non-null fields are updated
     * 
     * @param dto Source DTO with partial data
     * @param entity Target entity to update
     */
    @BeanMapping(
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
    )
    void partialUpdate(D dto, @MappingTarget E entity);
}