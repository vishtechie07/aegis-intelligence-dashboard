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

    boolean existsBySourceUrl(String sourceUrl);

    List<CompetitorNews> findByCompetitorNameOrderByPublishedAtDesc(String competitorName);

    @Query("SELECT n FROM CompetitorNews n ORDER BY n.publishedAt DESC LIMIT :limit")
    List<CompetitorNews> findLatest(@Param("limit") int limit);

    @Query("SELECT n FROM CompetitorNews n WHERE n.createdAt >= :since ORDER BY n.createdAt DESC")
    List<CompetitorNews> findSince(@Param("since") OffsetDateTime since);

    @Query("SELECT n.id FROM CompetitorNews n ORDER BY n.id")
    List<Long> findAllIds();
}
