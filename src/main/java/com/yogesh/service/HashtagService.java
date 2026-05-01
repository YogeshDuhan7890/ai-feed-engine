package com.yogesh.service;

import com.yogesh.model.Hashtag;
import com.yogesh.model.Post;
import com.yogesh.model.PostHashtag;
import com.yogesh.model.User;
import com.yogesh.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.yogesh.config.RedisCachePolicy.SEARCH;
import static com.yogesh.config.RedisCachePolicy.TRENDING;

@Service
@RequiredArgsConstructor
public class HashtagService {

	private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([\\w\\u0900-\\u097F]+)");

	private final HashtagRepository hashtagRepository;
	private final PostHashtagRepository postHashtagRepository;
	private final PostRepository postRepository;
	private final UserRepository userRepository;

	/**
	 * Caption se hashtags extract karo aur save karo. PostService.upload() mein
	 * call karo.
	 */
	@Transactional
	@Caching(evict = {
			@CacheEvict(value = SEARCH, allEntries = true),
			@CacheEvict(value = TRENDING, allEntries = true)
	})
	public void indexPost(Long postId, String caption) {
		if (caption == null || caption.isBlank())
			return;

		Matcher m = HASHTAG_PATTERN.matcher(caption);
		Set<String> tags = new LinkedHashSet<>();
		while (m.find())
			tags.add(m.group(1).toLowerCase());

		// Purane hashtags hata do
		postHashtagRepository.deleteByPostId(postId);

		for (String tag : tags) {
			// Hashtag find or create
			Hashtag hashtag = hashtagRepository.findByTag(tag).orElseGet(() -> {
				Hashtag h = new Hashtag();
				h.setTag(tag);
				return hashtagRepository.save(h);
			});

			// Post-Hashtag link
			PostHashtag ph = new PostHashtag();
			ph.setPostId(postId);
			ph.setHashtagId(hashtag.getId());
			postHashtagRepository.save(ph);

			// Count update
			hashtag.setPostCount(hashtag.getPostCount() + 1);
			hashtagRepository.save(hashtag);
		}
	}

	/** Hashtag search karo — posts return karo */
	public Map<String, Object> searchByTag(String tag, Long currentUserId) {
		String clean = tag.toLowerCase().replace("#", "").trim();
		Optional<Hashtag> opt = hashtagRepository.findByTag(clean);
		if (opt.isEmpty())
			return Map.of("tag", clean, "posts", List.of(), "count", 0);

		Hashtag hashtag = opt.get();
		List<Long> postIds = postHashtagRepository.findPostIdsByHashtagId(hashtag.getId());
		List<Post> posts = postRepository.findAllById(postIds);

		Set<Long> userIds = posts.stream().map(Post::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(User::getId, u -> u));

		List<Map<String, Object>> postList = posts.stream().map(p -> {
			Map<String, Object> item = new HashMap<>();
			item.put("postId", p.getId());
			item.put("videoUrl", p.getVideoUrl());
			item.put("content", p.getContent());
			User u = p.getUserId() != null ? userMap.get(p.getUserId()) : null;
			item.put("userName", u != null ? u.getName() : "user");
			item.put("userAvatar", u != null ? u.getAvatar() : null);
			item.put("userId", p.getUserId());
			return item;
		}).collect(Collectors.toList());

		return Map.of("tag", clean, "posts", postList, "count", hashtag.getPostCount());
	}

	/** Trending hashtags — top 20 */
	public List<Map<String, Object>> getTrending() {
		return hashtagRepository.findTopTrending(PageRequest.of(0, 20)).stream()
				.map(h -> Map.<String, Object>of("tag", h.getTag(), "count", h.getPostCount()))
				.collect(Collectors.toList());
	}

	/** Autocomplete search */
	public List<String> autocomplete(String query) {
		return hashtagRepository.findByTagContainingIgnoreCaseOrderByPostCountDesc(query.toLowerCase().replace("#", ""))
				.stream().limit(10).map(h -> "#" + h.getTag()).collect(Collectors.toList());
	}

	/** Caption mein #tags ko clickable HTML links banana (display ke liye) */
	public static String linkifyHashtags(String caption) {
		if (caption == null)
			return "";
		return HASHTAG_PATTERN.matcher(caption).replaceAll(mr -> "<a href=\"/hashtag/" + mr.group(1).toLowerCase()
				+ "\" class=\"hashtag-link\">#" + mr.group(1) + "</a>");
	}
}
