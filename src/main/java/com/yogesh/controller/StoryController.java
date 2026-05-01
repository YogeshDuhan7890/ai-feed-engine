package com.yogesh.controller;

import com.yogesh.model.Story;
import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.ModerationService;
import com.yogesh.service.StoryService;
import com.yogesh.util.FileStorageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService    storyService;
    private final UserRepository  userRepository;
    private final ModerationService moderationService;

    /** GET /api/stories/feed */
    @GetMapping("/feed")
    public List<Map<String, Object>> feed(Authentication auth) {
        return storyService.getFeedStories(me(auth).getId());
    }

    /** POST /api/stories/upload — file upload */
    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "caption", defaultValue = "") String caption,
            @RequestParam(value = "privacy", defaultValue = "PUBLIC") String privacy,
            Authentication auth) throws Exception {

        if (file.isEmpty()) return Map.of("success", false, "message", "File empty hai");
        if (!caption.isBlank()) {
            moderationService.assertAllowed(caption, "Story caption");
        }

        String ext = FileStorageUtil.validateAllowedUpload(file);
        String mediaType = "mp4".equals(ext) ? "VIDEO" : "IMAGE";

        // Save file
        String folder = FileStorageUtil.createDateFolder("uploads/stories/");
        String filename = FileStorageUtil.generateFileName(ext);
        java.nio.file.Path path = java.nio.file.Paths.get(folder + filename);
        try {
            file.transferTo(path);

            String mediaUrl = "/" + path.toString().replace("\\", "/");
            Story story = storyService.createWithPrivacy(me(auth).getId(), mediaUrl, mediaType, caption, privacy);
            return Map.of("success", true, "storyId", story.getId(), "mediaUrl", mediaUrl);
        } catch (Exception ex) {
            try {
                java.nio.file.Files.deleteIfExists(path);
            } catch (Exception ignore) {
            }
            throw ex;
        }
    }

    /** POST /api/stories — media story */
    @PostMapping
    public Story create(
            @RequestParam String mediaUrl,
            @RequestParam(defaultValue = "IMAGE") String mediaType,
            @RequestParam(required = false) String caption,
            @RequestParam(defaultValue = "PUBLIC") String privacy,
            Authentication auth) {
        if (caption != null && !caption.isBlank()) {
            moderationService.assertAllowed(caption, "Story caption");
        }
        return storyService.create(me(auth).getId(), mediaUrl, mediaType, caption, privacy);
    }

    /** POST /api/stories/text — text story */
    @PostMapping("/text")
    public Story createText(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        moderationService.assertAllowed(body.get("textContent"), "Story text");
        return storyService.createTextStory(
            me(auth).getId(),
            body.get("textContent"),
            body.get("bgColor"),
            body.get("textColor"),
            body.getOrDefault("privacy", "PUBLIC")
        );
    }

    /** POST /api/stories/{id}/view */
    @PostMapping("/{id}/view")
    public Map<String, String> view(@PathVariable Long id, Authentication auth) {
        storyService.markViewed(id, me(auth).getId());
        return Map.of("status", "ok");
    }

    /** POST /api/stories/{id}/react */
    @PostMapping("/{id}/react")
    public Map<String, Object> react(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        return storyService.reactToStory(id, me(auth).getId(), body.get("emoji"));
    }

    /** DELETE /api/stories/{id} */
    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id, Authentication auth) {
        storyService.delete(id, me(auth).getId());
        return Map.of("status", "deleted");
    }

    /** GET /api/stories/{id}/viewers */
    @GetMapping("/{id}/viewers")
    public List<Map<String, Object>> viewers(@PathVariable Long id, Authentication auth) {
        return storyService.getViewers(id, me(auth).getId());
    }

    private User me(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}
