package com.example.llmlab.repository;

import com.example.llmlab.domain.RunResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
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
}
