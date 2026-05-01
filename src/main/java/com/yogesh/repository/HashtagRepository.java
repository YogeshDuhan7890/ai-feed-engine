package com.yogesh.repository;

import com.yogesh.model.Hashtag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

	Optional<Hashtag> findByTag(String tag);

	// BUG FIX 1: findByNameContainingIgnoreCase → findByTagContainingIgnoreCase
	// Hashtag model mein field ka naam "tag" hai, "name" nahi.
	// SearchApiController mein h.getName() → h.getTag() bhi fix kiya.
	List<Hashtag> findByTagContainingIgnoreCase(String query);

	// Trending hashtags
	@Query("SELECT h FROM Hashtag h ORDER BY h.postCount DESC")
	List<Hashtag> findTopTrending(Pageable pageable);

	List<Hashtag> findByTagContainingIgnoreCaseOrderByPostCountDesc(String query);

	// Pageable overload — SearchService ke liye
	List<Hashtag> findByTagContainingIgnoreCaseOrderByPostCountDesc(String query, Pageable pageable);
}