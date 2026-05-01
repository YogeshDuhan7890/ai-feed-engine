package com.yogesh.service;

import com.yogesh.model.Block;
import com.yogesh.model.User;
import com.yogesh.repository.BlockRepository;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    /** Block karo — follow bhi automatically hata do */
    @Transactional
    public Map<String, Object> block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId))
            throw new IllegalArgumentException("Apne aap ko block nahi kar sakte");

        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId))
            return Map.of("blocked", true, "message", "Pehle se blocked hai");

        // Block save karo
        Block b = new Block();
        b.setBlockerId(blockerId);
        b.setBlockedId(blockedId);
        blockRepository.save(b);

        // Follow bhi hata do dono taraf se
        if (followRepository.existsAnyByFollowerIdAndFollowingId(blockerId, blockedId))
            followRepository.deleteByFollowerIdAndFollowingId(blockerId, blockedId);
        if (followRepository.existsAnyByFollowerIdAndFollowingId(blockedId, blockerId))
            followRepository.deleteByFollowerIdAndFollowingId(blockedId, blockerId);

        return Map.of("blocked", true, "message", "User block ho gaya");
    }

    /** Unblock karo */
    @Transactional
    public Map<String, Object> unblock(Long blockerId, Long blockedId) {
        if (!blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId))
            return Map.of("blocked", false, "message", "Blocked nahi tha");

        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
        return Map.of("blocked", false, "message", "Unblock ho gaya");
    }

    /** Check karo blocked hai ya nahi */
    public boolean isBlocked(Long blockerId, Long blockedId) {
        return blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    /** Meri block list */
    public List<Map<String, Object>> getBlockList(Long userId) {
        List<Block> blocks = blockRepository.findByBlockerIdOrderByCreatedAtDesc(userId);
        if (blocks.isEmpty()) return List.of();

        List<Long> blockedIds = blocks.stream().map(Block::getBlockedId).collect(Collectors.toList());
        Map<Long, User> userMap = userRepository.findAllById(blockedIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        return blocks.stream().map(b -> {
            User u = userMap.get(b.getBlockedId());
            Map<String, Object> item = new HashMap<>();
            item.put("userId", b.getBlockedId());
            item.put("name", u != null ? u.getName() : "Unknown");
            item.put("avatar", u != null ? u.getAvatar() : null);
            item.put("blockedAt", b.getCreatedAt());
            return item;
        }).collect(Collectors.toList());
    }
}
