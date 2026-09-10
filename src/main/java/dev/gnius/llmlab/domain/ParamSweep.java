package dev.gnius.llmlab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One dimension of a parameter sweep for a suite. {@code values} is a JSON array string
 * (e.g. {@code [0.3, 0.5, 1.0]}) parsed at runtime.
 * Mirrors the {@code param_sweep} table.
 */
@Entity
@Table(name = "param_sweep")
public class ParamSweep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "suite_id", nullable = false)
    private Long suiteId;

    @Column(name = "param_name", nullable = false)
    private String paramName;

    @Column(name = "param_values", nullable = false)
    private String values;

    protected ParamSweep() {
        // JPA
    }

    public ParamSweep(Long suiteId, String paramName, String values) {
        this.suiteId = suiteId;
        this.paramName = paramName;
        this.values = values;
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

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public String getValues() {
        return values;
    }

    public void setValues(String values) {
        this.values = values;
    }
}
