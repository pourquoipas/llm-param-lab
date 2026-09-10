package com.example.llmlab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A named collection of test cases plus the evaluation strategy (expected output or judge LLM).
 * Mirrors the {@code test_suite} table.
 */
@Entity
@Table(name = "test_suite")
public class TestSuite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Lob
    @Column(name = "expected_output")
    private String expectedOutput;

    @Enumerated(EnumType.STRING)
    @Column(name = "expected_output_mode", nullable = false)
    private ExpectedOutputMode expectedOutputMode = ExpectedOutputMode.NONE;

    @Column(name = "judge_model_id")
    private Long judgeModelId;

    @Lob
    @Column(name = "judge_prompt")
    private String judgePrompt;

    /** Judge temperature. Null → 0.0 (deterministic). */
    @Column(name = "judge_temperature")
    private Double judgeTemperature;

    @Column(name = "judge_top_p")
    private Double judgeTopP;

    @Column(name = "judge_seed")
    private Integer judgeSeed;

    @Lob
    @Column(name = "seeds")
    private String seeds;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected TestSuite() {
        // JPA
    }

    public TestSuite(String name) {
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public ExpectedOutputMode getExpectedOutputMode() {
        return expectedOutputMode;
    }

    public void setExpectedOutputMode(ExpectedOutputMode expectedOutputMode) {
        this.expectedOutputMode = expectedOutputMode;
    }

    public Long getJudgeModelId() {
        return judgeModelId;
    }

    public void setJudgeModelId(Long judgeModelId) {
        this.judgeModelId = judgeModelId;
    }

    public String getJudgePrompt() {
        return judgePrompt;
    }

    public void setJudgePrompt(String judgePrompt) {
        this.judgePrompt = judgePrompt;
    }

    public Double getJudgeTemperature() {
        return judgeTemperature;
    }

    public void setJudgeTemperature(Double judgeTemperature) {
        this.judgeTemperature = judgeTemperature;
    }

    public Double getJudgeTopP() {
        return judgeTopP;
    }

    public void setJudgeTopP(Double judgeTopP) {
        this.judgeTopP = judgeTopP;
    }

    public Integer getJudgeSeed() {
        return judgeSeed;
    }

    public void setJudgeSeed(Integer judgeSeed) {
        this.judgeSeed = judgeSeed;
    }

    public String getSeeds() {
        return seeds;
    }

    public void setSeeds(String seeds) {
        this.seeds = seeds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
