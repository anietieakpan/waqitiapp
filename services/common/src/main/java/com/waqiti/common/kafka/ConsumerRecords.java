package com.waqiti.common.kafka;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom ConsumerRecords implementation for Kafka message consumption
 * with enhanced functionality for the Waqiti platform.
 */
public class ConsumerRecords<K, V> implements Iterable<ConsumerRecord<K, V>> {
    
    private final Map<TopicPartition, List<ConsumerRecord<K, V>>> records;
    
    public ConsumerRecords() {
        this.records = new ConcurrentHashMap<>();
    }
    
    public ConsumerRecords(Map<TopicPartition, List<ConsumerRecord<K, V>>> records) {
        this.records = new ConcurrentHashMap<>(records);
    }
    
    /**
     * Add a consumer record to the collection
     */
    public void add(ConsumerRecord<K, V> record) {
        TopicPartition tp = new TopicPartition(record.getTopic(), record.getPartition());
        records.computeIfAbsent(tp, k -> new ArrayList<>()).add(record);
    }
    
    /**
     * Get all records for a specific topic
     */
    public Iterable<ConsumerRecord<K, V>> records(String topic) {
        List<ConsumerRecord<K, V>> topicRecords = new ArrayList<>();
        for (Map.Entry<TopicPartition, List<ConsumerRecord<K, V>>> entry : records.entrySet()) {
            if (entry.getKey().getTopic().equals(topic)) {
                topicRecords.addAll(entry.getValue());
            }
        }
        return topicRecords;
    }
    
    /**
     * Get all records for a specific topic partition
     */
    public List<ConsumerRecord<K, V>> records(TopicPartition partition) {
        return records.getOrDefault(partition, Collections.emptyList());
    }
    
    /**
     * Get all partitions that have records
     */
    public Set<TopicPartition> partitions() {
        return new HashSet<>(records.keySet());
    }
    
    /**
     * Check if the collection is empty
     */
    public boolean isEmpty() {
        return records.isEmpty() || records.values().stream().allMatch(List::isEmpty);
    }
    
    /**
     * Get the total count of records
     */
    public int count() {
        return records.values().stream().mapToInt(List::size).sum();
    }
    
    /**
     * Get count of records for a specific topic
     */
    public int count(String topic) {
        return (int) records.entrySet().stream()
            .filter(entry -> entry.getKey().getTopic().equals(topic))
            .mapToInt(entry -> entry.getValue().size())
            .sum();
    }
    
    /**
     * Get all topics that have records
     */
    public Set<String> getTopics() {
        return records.keySet().stream()
            .map(TopicPartition::getTopic)
            .collect(HashSet::new, Set::add, Set::addAll);
    }
    
    /**
     * Clear all records
     */
    public void clear() {
        records.clear();
    }
    
    /**
     * Create an iterator over all records
     */
    @Override
    public Iterator<ConsumerRecord<K, V>> iterator() {
        List<ConsumerRecord<K, V>> allRecords = new ArrayList<>();
        for (List<ConsumerRecord<K, V>> partitionRecords : records.values()) {
            allRecords.addAll(partitionRecords);
        }
        return allRecords.iterator();
    }
    
    /**
     * Get records as a map grouped by topic partition
     */
    public Map<TopicPartition, List<ConsumerRecord<K, V>>> asMap() {
        return new HashMap<>(records);
    }
    
    /**
     * Create a new ConsumerRecords with filtered records based on predicate
     */
    public ConsumerRecords<K, V> filter(java.util.function.Predicate<ConsumerRecord<K, V>> predicate) {
        Map<TopicPartition, List<ConsumerRecord<K, V>>> filteredRecords = new HashMap<>();
        
        for (Map.Entry<TopicPartition, List<ConsumerRecord<K, V>>> entry : records.entrySet()) {
            List<ConsumerRecord<K, V>> filteredList = entry.getValue().stream()
                .filter(predicate)
                .collect(ArrayList::new, List::add, List::addAll);
            
            if (!filteredList.isEmpty()) {
                filteredRecords.put(entry.getKey(), filteredList);
            }
        }
        
        return new ConsumerRecords<>(filteredRecords);
    }
    
    /**
     * Get the earliest offset across all partitions
     */
    public long getEarliestOffset() {
        return records.values().stream()
            .flatMap(List::stream)
            .mapToLong(ConsumerRecord::getOffset)
            .min()
            .orElse(-1L);
    }
    
    /**
     * Get the latest offset across all partitions
     */
    public long getLatestOffset() {
        return records.values().stream()
            .flatMap(List::stream)
            .mapToLong(ConsumerRecord::getOffset)
            .max()
            .orElse(-1L);
    }
    
    /**
     * Get summary information about the consumer records
     */
    public String getSummary() {
        int totalRecords = count();
        int partitionCount = records.size();
        Set<String> topics = getTopics();
        
        return String.format("ConsumerRecords[topics=%s, partitions=%d, records=%d]", 
            topics, partitionCount, totalRecords);
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ConsumerRecords<?, ?> that = (ConsumerRecords<?, ?>) obj;
        return Objects.equals(records, that.records);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(records);
    }
}