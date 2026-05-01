package com.yogesh.service;

import com.yogesh.model.Bookmark;
import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.BookmarkRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /** Toggle bookmark — save karo ya hata do */
    @Transactional
    public Map<String, Object> toggle(Long userId, Long postId) {
        boolean exists = bookmarkRepository.existsByUserIdAndPostId(userId, postId);
        if (exists) {
            bookmarkRepository.deleteByUserIdAndPostId(userId, postId);
            return Map.of("saved", false, "message", "Bookmark hata diya");
        } else {
            Bookmark b = new Bookmark();
            b.setUserId(userId);
            b.setPostId(postId);
            bookmarkRepository.save(b);
            return Map.of("saved", true, "message", "Bookmark save ho gaya");
        }
    }

    /** User ke saare saved posts */
    public List<Map<String, Object>> getSaved(Long userId) {
        return getSaved(userId, null);
    }

    /** User ke saved posts (optional folder filter) */
    public List<Map<String, Object>> getSaved(Long userId, String collection) {
        List<Bookmark> bookmarks;
        if (collection == null || collection.isBlank()) {
            bookmarks = bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId);
        } else {
            bookmarks = bookmarkRepository.findByUserIdAndCollectionNameOrderByCreatedAtDesc(userId, collection.trim());
        }
        if (bookmarks.isEmpty()) return List.of();

        List<Long> postIds = bookmarks.stream().map(Bookmark::getPostId).collect(Collectors.toList());
        Map<Long, Post> postMap = postRepository.findAllById(postIds)
                .stream().collect(Collectors.toMap(Post::getId, p -> p));

        // Post ke userId batch fetch
        Set<Long> userIds = postMap.values().stream()
                .map(Post::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Bookmark b : bookmarks) {
            Post p = postMap.get(b.getPostId());
            if (p == null || p.getVideoUrl() == null) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("postId", p.getId());
            item.put("videoUrl", p.getVideoUrl());
            item.put("content", p.getContent() != null ? p.getContent() : "");
            item.put("bookmarkId", b.getId());
            item.put("savedAt", b.getCreatedAt());
            item.put("collectionName", b.getCollectionName());

            User u = p.getUserId() != null ? userMap.get(p.getUserId()) : null;
            item.put("userName", u != null ? u.getName() : "user");
            item.put("userAvatar", u != null ? u.getAvatar() : null);
            item.put("userId", p.getUserId());

            result.add(item);
        }
        return result;
    }

    /** Kisi post pe kitne bookmarks hain */
    public long countByPost(Long postId) {
        return bookmarkRepository.countByPostId(postId);
    }

    /** Check karo user ne save kiya hai ya nahi */
    public boolean isSaved(Long userId, Long postId) {
        return bookmarkRepository.existsByUserIdAndPostId(userId, postId);
    }

    /** Set/move bookmark to a collection (folder). Empty => remove from collection. */
    @Transactional
    public Map<String, Object> setCollection(Long userId, Long postId, String collectionName) {
        Bookmark b = bookmarkRepository.findByUserIdAndPostId(userId, postId)
                .orElseThrow(() -> new RuntimeException("Bookmark not found"));

        String clean = collectionName == null ? null : collectionName.trim();
        if (clean != null && clean.isBlank()) {
            clean = null;
        }
        if (clean != null && clean.length() > 120) {
            clean = clean.substring(0, 120);
        }

        b.setCollectionName(clean);
        bookmarkRepository.save(b);
        return Map.of("success", true, "collectionName", clean);
    }

    /** List collections with counts (includes "" for uncategorized). */
    public List<Map<String, Object>> getCollections(Long userId) {
        List<Object[]> rows = bookmarkRepository.countByCollection(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            String name = r[0] != null ? r[0].toString() : "";
            long cnt = r[1] != null ? ((Number) r[1]).longValue() : 0L;
            result.add(Map.of("name", name, "count", cnt));
        }
        return result;
    }
}