package com.yogesh.repository;

import com.yogesh.model.PostHashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, Long> {

    List<PostHashtag> findByHashtagId(Long hashtagId);

    List<PostHashtag> findByPostId(Long postId);

    void deleteByPostId(Long postId);

    @Query("SELECT ph.postId FROM PostHashtag ph WHERE ph.hashtagId = :hashtagId")
    List<Long> findPostIdsByHashtagId(@Param("hashtagId") Long hashtagId);
}