package com.yogesh.service;

import com.yogesh.model.Poll;
import com.yogesh.model.PollOption;
import com.yogesh.model.PollVote;
import com.yogesh.repository.PollOptionRepository;
import com.yogesh.repository.PollRepository;
import com.yogesh.repository.PollVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PollService {

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;

    @Transactional
    public Map<String, Object> createPoll(Long postId, String question, List<String> options, Integer expiresInHours) {
        if (postId == null || postId <= 0) {
            return Map.of("success", false, "message", "postId required");
        }
        if (pollRepository.findByPostId(postId).isPresent()) {
            return Map.of("success", false, "message", "Poll already exists for this post");
        }
        List<String> cleanOptions = Optional.ofNullable(options).orElse(Collections.emptyList()).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(4)
                .toList();

        if (cleanOptions.size() < 2) {
            return Map.of("success", false, "message", "Min 2 options required");
        }

        Poll poll = new Poll();
        poll.setPostId(postId);
        poll.setQuestion(question != null ? question.trim() : null);

        if (expiresInHours != null && expiresInHours > 0) {
            poll.setExpiresAt(LocalDateTime.now().plusHours(Math.min(168, expiresInHours))); // max 7 days
        }

        Poll saved = pollRepository.save(poll);

        for (String opt : cleanOptions) {
            PollOption o = new PollOption();
            o.setPollId(saved.getId());
            o.setText(opt);
            pollOptionRepository.save(o);
        }

        return Map.of("success", true, "pollId", saved.getId());
    }

    public Map<String, Object> getPollForPost(Long postId, Long viewerUserId) {
        Poll poll = pollRepository.findByPostId(postId).orElse(null);
        if (poll == null) {
            return Map.of("exists", false);
        }
        return buildPollResponse(poll, viewerUserId);
    }

    @Transactional
    public Map<String, Object> vote(Long postId, Long optionId, Long userId) {
        Poll poll = pollRepository.findByPostId(postId).orElse(null);
        if (poll == null) {
            return Map.of("success", false, "message", "Poll not found");
        }
        if (poll.getExpiresAt() != null && poll.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Map.of("success", false, "message", "Poll expired");
        }

        List<PollOption> opts = pollOptionRepository.findByPollIdOrderByIdAsc(poll.getId());
        boolean optionValid = opts.stream().anyMatch(o -> o.getId().equals(optionId));
        if (!optionValid) {
            return Map.of("success", false, "message", "Invalid option");
        }

        PollVote existing = pollVoteRepository.findByPollIdAndUserId(poll.getId(), userId).orElse(null);
        if (existing == null) {
            PollVote v = new PollVote();
            v.setPollId(poll.getId());
            v.setOptionId(optionId);
            v.setUserId(userId);
            pollVoteRepository.save(v);
        } else {
            // allow change vote (simple)
            existing.setOptionId(optionId);
            pollVoteRepository.save(existing);
        }

        return Map.of("success", true, "poll", buildPollResponse(poll, userId));
    }

    private Map<String, Object> buildPollResponse(Poll poll, Long viewerUserId) {
        List<PollOption> options = pollOptionRepository.findByPollIdOrderByIdAsc(poll.getId());
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] r : pollVoteRepository.countByOption(poll.getId())) {
            Long optId = r[0] != null ? ((Number) r[0]).longValue() : null;
            Long cnt = r[1] != null ? ((Number) r[1]).longValue() : 0L;
            if (optId != null) {
                counts.put(optId, cnt);
            }
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        Long myOptionId = null;
        if (viewerUserId != null) {
            myOptionId = pollVoteRepository.findByPollIdAndUserId(poll.getId(), viewerUserId)
                    .map(PollVote::getOptionId).orElse(null);
        }

        List<Map<String, Object>> opts = options.stream().map(o -> {
            long c = counts.getOrDefault(o.getId(), 0L);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("text", o.getText());
            m.put("votes", c);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", poll.getId());
        res.put("postId", poll.getPostId());
        res.put("question", poll.getQuestion());
        res.put("expiresAt", poll.getExpiresAt());
        res.put("totalVotes", total);
        res.put("myOptionId", myOptionId);
        res.put("options", opts);
        return res;
    }
}

