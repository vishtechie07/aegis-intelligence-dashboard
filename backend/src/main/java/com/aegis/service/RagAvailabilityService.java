package com.aegis.service;

import com.aegis.config.RagConfig;
import com.aegis.config.RagProperties;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class RagAvailabilityService {

    private final RagProperties ragProperties;
    private final EntityManager entityManager;

    public RagAvailabilityService(
            RagProperties ragProperties,
            @Autowired(required = false) EntityManager entityManager) {
        this.ragProperties = ragProperties;
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public Set<Long> indexedNewsIds(Collection<Long> newsIds) {
        Set<Long> result = new HashSet<>();
        if (!ragProperties.enabled() || entityManager == null || newsIds == null || newsIds.isEmpty()) {
            return result;
        }
        List<String> idStrings = newsIds.stream().map(String::valueOf).toList();
        try {
            List<String> hits = entityManager.createNativeQuery("""
                    SELECT DISTINCT metadata->>'news_id'
                    FROM aegis_rag_store
                    WHERE metadata->>'news_id' IN (:ids)
                    """)
                    .setParameter("ids", idStrings)
                    .getResultList();
            for (Object hit : hits) {
                if (hit == null) continue;
                try {
                    result.add(Long.parseLong(hit.toString()));
                } catch (NumberFormatException ignored) {
                    /* skip malformed */
                }
            }
        } catch (Exception ex) {
            log.debug("[RAG] availability check skipped: {}", ex.getMessage());
        }
        return result;
    }
}
