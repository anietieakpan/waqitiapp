package com.waqiti.expense.service.impl;

import com.waqiti.expense.domain.ExpenseCategory;
import com.waqiti.expense.exception.CategoryNotFoundException;
import com.waqiti.expense.repository.ExpenseCategoryRepository;
import com.waqiti.expense.service.ExpenseCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {

    private final ExpenseCategoryRepository categoryRepository;

    @Override
    @Cacheable("categories")
    @Transactional(readOnly = true)
    public List<ExpenseCategory> getAllActiveCategories() {
        log.debug("Fetching all active categories");
        return categoryRepository.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExpenseCategory> getCategoryById(String categoryId) {
        log.debug("Fetching category: {}", categoryId);
        return categoryRepository.findByCategoryId(categoryId);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public ExpenseCategory createCategory(ExpenseCategory category) {
        log.info("Creating category: {}", category.getCategoryName());
        return categoryRepository.save(category);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public ExpenseCategory updateCategory(String categoryId, ExpenseCategory category) {
        log.info("Updating category: {}", categoryId);

        ExpenseCategory existing = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));

        existing.setCategoryName(category.getCategoryName());
        existing.setDescription(category.getDescription());
        existing.setIsActive(category.getIsActive());

        return categoryRepository.save(existing);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public void deleteCategory(String categoryId) {
        log.info("Deleting category: {}", categoryId);

        ExpenseCategory category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));

        category.setIsActive(false);
        categoryRepository.save(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseCategory> getCategoryHierarchy() {
        log.debug("Fetching category hierarchy");
        return categoryRepository.findAllActive().stream()
                .filter(c -> c.getParentCategory() == null)
                .collect(Collectors.toList());
    }
}
