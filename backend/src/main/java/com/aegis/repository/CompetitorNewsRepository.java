package com.aegis.repository;

import com.aegis.entity.CompetitorNews;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface CompetitorNewsRepository extends JpaRepository<CompetitorNews, Long> {

    long countByCreatedAtGreaterThanEqual(OffsetDateTime since);

    @Query("SELECT COUNT(n) FROM CompetitorNews n WHERE NOT EXISTS (SELECT 1 FROM AgentInsight i WHERE i.news = n)")
    long countWithoutInsight();

    @Query("""
            SELECT COUNT(n) FROM CompetitorNews n
            WHERE n.createdAt >= :since
            AND NOT EXISTS (SELECT 1 FROM AgentInsight i WHERE i.news = n)
            """)
    long countWithoutInsightSince(@Param("since") OffsetDateTime since);

    @Query("SELECT COUNT(i) FROM AgentInsight i WHERE i.processedAt >= :since")
    long countInsightsSince(@Param("since") OffsetDateTime since);

    boolean existsBySourceUrl(String sourceUrl);

    List<CompetitorNews> findByCompetitorNameOrderByPublishedAtDesc(String competitorName);

    @Query("SELECT n FROM CompetitorNews n ORDER BY n.publishedAt DESC LIMIT :limit")
    List<CompetitorNews> findLatest(@Param("limit") int limit);

    @Query("SELECT n FROM CompetitorNews n WHERE n.createdAt >= :since ORDER BY n.createdAt DESC")
    List<CompetitorNews> findSince(@Param("since") OffsetDateTime since);

    @Query("SELECT n.id FROM CompetitorNews n ORDER BY n.id")
    List<Long> findAllIds();
}
