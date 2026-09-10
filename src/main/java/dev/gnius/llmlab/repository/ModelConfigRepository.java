package dev.gnius.llmlab.repository;

import dev.gnius.llmlab.domain.ModelConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/** Data access for {@link ModelConfig}. */
@ApplicationScoped
public class ModelConfigRepository {

    @PersistenceContext
    EntityManager em;

    @Transactional
    public ModelConfig save(ModelConfig entity) {
        if (entity.getId() == null) {
            em.persist(entity);
            return entity;
        }
        return em.merge(entity);
    }

    public Optional<ModelConfig> findById(Long id) {
        return Optional.ofNullable(em.find(ModelConfig.class, id));
    }

    public List<ModelConfig> findAll() {
        return em.createQuery("select m from ModelConfig m order by m.name", ModelConfig.class)
                .getResultList();
    }

    public Optional<ModelConfig> findByName(String name) {
        return em.createQuery("select m from ModelConfig m where m.name = :name", ModelConfig.class)
                .setParameter("name", name)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** The single model currently flagged active (the model under test). */
    public Optional<ModelConfig> findActive() {
        return em.createQuery("select m from ModelConfig m where m.isActive = true", ModelConfig.class)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Transactional
    public void delete(Long id) {
        ModelConfig entity = em.find(ModelConfig.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    /** Wipes the whole table (used by the admin clean, called last in FK order). */
    @Transactional
    public void deleteAll() {
        em.createNativeQuery("DELETE FROM model_config").executeUpdate();
    }
}
