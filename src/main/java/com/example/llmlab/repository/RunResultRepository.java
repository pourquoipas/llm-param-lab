package com.example.llmlab.repository;

import com.example.llmlab.domain.RunResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Data access for {@link RunResult}. */
@ApplicationScoped
public class RunResultRepository {

    @PersistenceContext
    EntityManager em;

    @Transactional
    public RunResult save(RunResult entity) {
        if (entity.getId() == null) {
            em.persist(entity);
            return entity;
        }
        return em.merge(entity);
    }

    public Optional<RunResult> findById(Long id) {
        return Optional.ofNullable(em.find(RunResult.class, id));
    }

    public List<RunResult> findBySuiteId(Long suiteId) {
        return em.createQuery("select r from RunResult r where r.suiteId = :suiteId order by r.id",
                        RunResult.class)
                .setParameter("suiteId", suiteId)
                .getResultList();
    }

    /** Latest {@code createdAt} across the suite's results, or empty if the suite has no results. */
    public Optional<LocalDateTime> findLatestRunAt(Long suiteId) {
        List<LocalDateTime> result = em.createQuery(
                        "select max(r.createdAt) from RunResult r where r.suiteId = :suiteId",
                        LocalDateTime.class)
                .setParameter("suiteId", suiteId)
                .getResultList();
        return result.stream().filter(Objects::nonNull).findFirst();
    }

    public List<RunResult> findByTestCaseId(Long testCaseId) {
        return em.createQuery("select r from RunResult r where r.testCaseId = :testCaseId order by r.id",
                        RunResult.class)
                .setParameter("testCaseId", testCaseId)
                .getResultList();
    }

    @Transactional
    public void delete(Long id) {
        RunResult entity = em.find(RunResult.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    /** Wipes the whole table (used by the admin clean, called first in FK order). */
    @Transactional
    public void deleteAll() {
        em.createNativeQuery("DELETE FROM run_result").executeUpdate();
    }
}
