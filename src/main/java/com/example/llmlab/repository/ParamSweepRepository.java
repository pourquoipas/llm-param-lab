package com.example.llmlab.repository;

import com.example.llmlab.domain.ParamSweep;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/** Data access for {@link ParamSweep}. */
@ApplicationScoped
public class ParamSweepRepository {

    @PersistenceContext
    EntityManager em;

    @Transactional
    public ParamSweep save(ParamSweep entity) {
        if (entity.getId() == null) {
            em.persist(entity);
            return entity;
        }
        return em.merge(entity);
    }

    public Optional<ParamSweep> findById(Long id) {
        return Optional.ofNullable(em.find(ParamSweep.class, id));
    }

    public List<ParamSweep> findAllBySuiteId(Long suiteId) {
        return em.createQuery("select p from ParamSweep p where p.suiteId = :suiteId order by p.id",
                        ParamSweep.class)
                .setParameter("suiteId", suiteId)
                .getResultList();
    }

    @Transactional
    public void delete(Long id) {
        ParamSweep entity = em.find(ParamSweep.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
