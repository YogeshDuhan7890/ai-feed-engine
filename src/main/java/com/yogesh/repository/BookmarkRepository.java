// ===== BookmarkRepository.java =====
package com.yogesh.repository;

import com.yogesh.model.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    Optional<Bookmark> findByUserIdAndPostId(Long userId, Long postId);

    List<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Bookmark> findByUserIdAndCollectionNameOrderByCreatedAtDesc(Long userId, String collectionName);

    @Query("""
            SELECT COALESCE(b.collectionName, '') as name, COUNT(b) as cnt
            FROM Bookmark b
            WHERE b.userId = :userId
            GROUP BY COALESCE(b.collectionName, '')
            ORDER BY COUNT(b) DESC
            """)
    List<Object[]> countByCollection(@Param("userId") Long userId);

    long countByPostId(Long postId);

    void deleteByUserIdAndPostId(Long userId, Long postId);

    void deleteByUserId(Long userId);
}
