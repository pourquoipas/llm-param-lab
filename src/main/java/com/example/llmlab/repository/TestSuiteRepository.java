package com.example.llmlab.repository;

import com.example.llmlab.domain.TestSuite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/** Data access for {@link TestSuite}. */
@ApplicationScoped
public class TestSuiteRepository {

    @PersistenceContext
    EntityManager em;

    @Transactional
    public TestSuite save(TestSuite entity) {
        if (entity.getId() == null) {
            em.persist(entity);
            return entity;
        }
        return em.merge(entity);
    }

    public Optional<TestSuite> findById(Long id) {
        return Optional.ofNullable(em.find(TestSuite.class, id));
    }

    public List<TestSuite> findAll() {
        return em.createQuery("select s from TestSuite s order by s.name", TestSuite.class)
                .getResultList();
    }

    public List<TestSuite> findByJudgeModelId(Long judgeModelId) {
        return em.createQuery("select s from TestSuite s where s.judgeModelId = :judgeModelId", TestSuite.class)
                .setParameter("judgeModelId", judgeModelId)
                .getResultList();
    }

    @Transactional
    public void delete(Long id) {
        TestSuite entity = em.find(TestSuite.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
