package com.example.llmlab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * A single prompt pair within a suite. Each case carries its own {@code systemPrompt}
 * (nullable — if null, no system message is sent) so different personas can be tested
 * against the same user prompts and parameters.
 * Mirrors the {@code test_case} table.
 */
@Entity
@Table(name = "test_case")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "suite_id", nullable = false)
    private Long suiteId;

    @Column(name = "name", nullable = false)
    private String name;

    @Lob
    @Column(name = "system_prompt")
    private String systemPrompt;

    @Lob
    @Column(name = "user_prompt", nullable = false)
    private String userPrompt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    protected TestCase() {
        // JPA
    }

    public TestCase(Long suiteId, String name, String systemPrompt, String userPrompt, int sortOrder) {
        this.suiteId = suiteId;
        this.name = name;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
