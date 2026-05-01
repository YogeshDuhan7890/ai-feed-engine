package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.BookmarkService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final UserRepository userRepository;

    /** POST /api/bookmarks/{postId} — toggle save/unsave */
    @PostMapping("/{postId}")
    public Map<String, Object> toggle(@PathVariable Long postId, Authentication auth) {
        User user = getUser(auth);
        return bookmarkService.toggle(user.getId(), postId);
    }

    /** GET /api/bookmarks — meri saari saved videos */
    @GetMapping
    public List<Map<String, Object>> getSaved(
            Authentication auth,
            @RequestParam(value = "collection", required = false) String collection) {
        User user = getUser(auth);
        return bookmarkService.getSaved(user.getId(), collection);
    }

    /** GET /api/bookmarks/collections — folders + counts */
    @GetMapping("/collections")
    public List<Map<String, Object>> collections(Authentication auth) {
        User user = getUser(auth);
        return bookmarkService.getCollections(user.getId());
    }

    /** POST /api/bookmarks/{postId}/collection — move to folder */
    @PostMapping("/{postId}/collection")
    public Map<String, Object> setCollection(
            @PathVariable Long postId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        User user = getUser(auth);
        String name = body != null && body.get("collectionName") != null ? body.get("collectionName").toString() : null;
        return bookmarkService.setCollection(user.getId(), postId, name);
    }

    /** GET /api/bookmarks/{postId}/status — save kiya hai ya nahi */
    @GetMapping("/{postId}/status")
    public Map<String, Object> status(@PathVariable Long postId, Authentication auth) {
        User user = getUser(auth);
        boolean saved = bookmarkService.isSaved(user.getId(), postId);
        return Map.of("saved", saved);
    }

    private User getUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}