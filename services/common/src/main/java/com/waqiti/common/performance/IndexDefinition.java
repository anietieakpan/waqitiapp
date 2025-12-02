package com.waqiti.common.performance;

import lombok.Builder;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * Database index definition
 */
@Data
@Builder
public class IndexDefinition {
    
    private String name;
    private List<String> columns;
    private boolean unique;
    private boolean composite;
    
    public static IndexDefinition create(String name, String column) {
        return IndexDefinition.builder()
            .name(name)
            .columns(Arrays.asList(column))
            .unique(false)
            .composite(false)
            .build();
    }
    
    public static IndexDefinition createUnique(String name, String column) {
        return IndexDefinition.builder()
            .name(name)
            .columns(Arrays.asList(column))
            .unique(true)
            .composite(false)
            .build();
    }
    
    public static IndexDefinition createComposite(String name, String... columns) {
        return IndexDefinition.builder()
            .name(name)
            .columns(Arrays.asList(columns))
            .unique(false)
            .composite(true)
            .build();
    }
    
    public static IndexDefinition createUniqueComposite(String name, String... columns) {
        return IndexDefinition.builder()
            .name(name)
            .columns(Arrays.asList(columns))
            .unique(true)
            .composite(true)
            .build();
    }
    
    public String getColumnsString() {
        return String.join(", ", columns);
    }
    
    public int getColumnCount() {
        return columns.size();
    }
}