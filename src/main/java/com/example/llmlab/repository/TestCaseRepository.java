package com.example.llmlab.repository;

import com.example.llmlab.domain.TestCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/** Data access for {@link TestCase}. */
@ApplicationScoped
public class TestCaseRepository {

    @PersistenceContext
    EntityManager em;

    @Transactional
    public TestCase save(TestCase entity) {
        if (entity.getId() == null) {
            em.persist(entity);
            return entity;
        }
        return em.merge(entity);
    }

    public Optional<TestCase> findById(Long id) {
        return Optional.ofNullable(em.find(TestCase.class, id));
    }

    public List<TestCase> findAllBySuiteId(Long suiteId) {
        return em.createQuery("select t from TestCase t where t.suiteId = :suiteId order by t.sortOrder, t.id",
                        TestCase.class)
                .setParameter("suiteId", suiteId)
                .getResultList();
    }

    @Transactional
    public void delete(Long id) {
        TestCase entity = em.find(TestCase.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
