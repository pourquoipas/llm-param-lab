package dev.gnius.llmlab.repository;

import dev.gnius.llmlab.domain.Judge;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/** Data access for {@link Judge} (the named judge registry). */
@ApplicationScoped
public class JudgeRepository {

    @PersistenceContext
    EntityManager em;

    @Transactional
    public Judge save(Judge entity) {
        if (entity.getId() == null) {
            em.persist(entity);
            return entity;
        }
        return em.merge(entity);
    }

    public Optional<Judge> findById(Long id) {
        return Optional.ofNullable(em.find(Judge.class, id));
    }

    public List<Judge> findAll() {
        return em.createQuery("select j from Judge j order by j.name", Judge.class)
                .getResultList();
    }

    public Optional<Judge> findByName(String name) {
        return em.createQuery("select j from Judge j where j.name = :name", Judge.class)
                .setParameter("name", name)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Transactional
    public void delete(Long id) {
        Judge entity = em.find(Judge.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    /** Wipes the whole table (used by the admin clean, called before the suite/model FKs). */
    @Transactional
    public void deleteAll() {
        em.createNativeQuery("DELETE FROM judge").executeUpdate();
    }
}
