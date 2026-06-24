package com.aegis.repository;

import com.aegis.dto.CategoryCount;
import com.aegis.dto.SourceCount;
import com.aegis.dto.ThreatHeatmapCell;
import com.aegis.entity.AgentInsight;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AgentInsightRepository extends JpaRepository<AgentInsight, Long> {

    long countByThreatLevelGreaterThanEqual(int minLevel);

    List<AgentInsight> findByNewsIdOrderByProcessedAtDesc(Long newsId);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            WHERE i.id IN :ids
            ORDER BY i.processedAt DESC
            """)
    List<AgentInsight> findByIdInWithNews(@Param("ids") List<Long> ids);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            ORDER BY i.processedAt DESC
            """)
    List<AgentInsight> findLatestWithNews(Pageable pageable);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            WHERE n.competitorName = :name
            ORDER BY i.processedAt DESC
            """)
    List<AgentInsight> findLatestWithNewsByCompetitor(@Param("name") String competitorName, Pageable pageable);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            WHERE i.threatLevel >= :minLevel
            ORDER BY i.threatLevel DESC, i.processedAt DESC
            """)
    List<AgentInsight> findHighThreat(@Param("minLevel") int minLevel);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            WHERE i.threatLevel >= :minLevel
            ORDER BY i.threatLevel DESC, i.processedAt DESC
            """)
    List<AgentInsight> findHighThreat(@Param("minLevel") int minLevel, Pageable pageable);

    @Query("""
            SELECT COUNT(i) FROM AgentInsight i
            JOIN i.news n
            WHERE (:competitor = '' OR n.competitorName = :competitor)
            AND (:category = '' OR i.category = :category)
            AND (:minThreat = 0 OR i.threatLevel >= :minThreat)
            AND (:search = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(i.summary, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            AND COALESCE(n.publishedAt, i.processedAt) >= :dateFrom
            AND COALESCE(n.publishedAt, i.processedAt) < :dateTo
            """)
    long countFeed(
            @Param("competitor") String competitor,
            @Param("category") String category,
            @Param("minThreat") int minThreat,
            @Param("search") String search,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            WHERE (:competitor = '' OR n.competitorName = :competitor)
            AND (:category = '' OR i.category = :category)
            AND (:minThreat = 0 OR i.threatLevel >= :minThreat)
            AND (:search = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(i.summary, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            AND COALESCE(n.publishedAt, i.processedAt) >= :dateFrom
            AND COALESCE(n.publishedAt, i.processedAt) < :dateTo
            ORDER BY i.processedAt DESC
            """)
    List<AgentInsight> findFeedProcessedDesc(
            @Param("competitor") String competitor,
            @Param("category") String category,
            @Param("minThreat") int minThreat,
            @Param("search") String search,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            Pageable pageable);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            WHERE (:competitor = '' OR n.competitorName = :competitor)
            AND (:category = '' OR i.category = :category)
            AND (:minThreat = 0 OR i.threatLevel >= :minThreat)
            AND (:search = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(i.summary, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            AND COALESCE(n.publishedAt, i.processedAt) >= :dateFrom
            AND COALESCE(n.publishedAt, i.processedAt) < :dateTo
            ORDER BY n.publishedAt DESC NULLS LAST, i.processedAt DESC
            """)
    List<AgentInsight> findFeedPublishedDesc(
            @Param("competitor") String competitor,
            @Param("category") String category,
            @Param("minThreat") int minThreat,
            @Param("search") String search,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            Pageable pageable);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            WHERE (:competitor = '' OR n.competitorName = :competitor)
            AND (:category = '' OR i.category = :category)
            AND (:minThreat = 0 OR i.threatLevel >= :minThreat)
            AND (:search = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(COALESCE(i.summary, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            AND COALESCE(n.publishedAt, i.processedAt) >= :dateFrom
            AND COALESCE(n.publishedAt, i.processedAt) < :dateTo
            ORDER BY i.threatLevel DESC, i.processedAt DESC
            """)
    List<AgentInsight> findFeedThreatDesc(
            @Param("competitor") String competitor,
            @Param("category") String category,
            @Param("minThreat") int minThreat,
            @Param("search") String search,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateTo") OffsetDateTime dateTo,
            Pageable pageable);

    @Query("""
            SELECT new com.aegis.dto.CategoryCount(i.category, COUNT(i))
            FROM AgentInsight i
            JOIN i.news n
            WHERE n.competitorName = :competitor
            GROUP BY i.category
            ORDER BY COUNT(i) DESC
            """)
    List<CategoryCount> countByCategoryForCompetitor(@Param("competitor") String competitor);

    @Query("""
            SELECT new com.aegis.dto.SourceCount(n.sourceType, COUNT(i))
            FROM AgentInsight i
            JOIN i.news n
            WHERE n.competitorName = :competitor
            GROUP BY n.sourceType
            ORDER BY COUNT(i) DESC
            """)
    List<SourceCount> countBySourceForCompetitor(@Param("competitor") String competitor);

    @Query("""
            SELECT COUNT(i) FROM AgentInsight i
            JOIN i.news n
            WHERE n.competitorName = :competitor
            """)
    long countByCompetitor(@Param("competitor") String competitor);

    @Query("""
            SELECT COUNT(i) FROM AgentInsight i
            JOIN i.news n
            WHERE n.competitorName = :competitor AND i.threatLevel >= :minLevel
            """)
    long countHighThreatByCompetitor(@Param("competitor") String competitor, @Param("minLevel") int minLevel);

    @Query("""
            SELECT new com.aegis.dto.CategoryCount(i.category, COUNT(i))
            FROM AgentInsight i
            WHERE COALESCE(i.processedAt, CURRENT_TIMESTAMP) >= :since
            GROUP BY i.category
            ORDER BY COUNT(i) DESC
            """)
    List<CategoryCount> countByCategorySince(@Param("since") OffsetDateTime since);

    @Query("""
            SELECT new com.aegis.dto.SourceCount(n.sourceType, COUNT(i))
            FROM AgentInsight i
            JOIN i.news n
            WHERE COALESCE(i.processedAt, CURRENT_TIMESTAMP) >= :since
            GROUP BY n.sourceType
            ORDER BY COUNT(i) DESC
            """)
    List<SourceCount> countBySourceSince(@Param("since") OffsetDateTime since);

    @Query("""
            SELECT new com.aegis.dto.ThreatHeatmapCell(n.competitorName, COUNT(i))
            FROM AgentInsight i
            JOIN i.news n
            WHERE i.threatLevel >= :minLevel
            AND COALESCE(i.processedAt, CURRENT_TIMESTAMP) >= :since
            GROUP BY n.competitorName
            ORDER BY COUNT(i) DESC
            """)
    List<ThreatHeatmapCell> countHighThreatByCompetitorSince(
            @Param("since") OffsetDateTime since,
            @Param("minLevel") int minLevel);
}
