/* =============================================
   PERFORMANCE.JS — AI Feed Frontend
   ✅ Lazy image loading (IntersectionObserver)
   ✅ Video preload optimization
   ✅ Resource prefetching
   ✅ DOM batch updates
   ✅ Debounce/throttle helpers
   ✅ Memory management (video cleanup)
   ✅ Network-aware loading
============================================= */

(function() {

	/* ── 1. Lazy Image Loading ── */
	var lazyObserver = null;

	function initLazyImages() {
		if (!('IntersectionObserver' in window)) {
			// Fallback: load all images
			document.querySelectorAll('img[data-src]').forEach(function(img) {
				img.src = img.dataset.src;
			});
			return;
		}

		lazyObserver = new IntersectionObserver(function(entries) {
			entries.forEach(function(entry) {
				if (!entry.isIntersecting) return;
				var img = entry.target;
				if (img.dataset.src) {
					img.src = img.dataset.src;
					img.removeAttribute('data-src');
				}
				if (img.dataset.srcset) {
					img.srcset = img.dataset.srcset;
					img.removeAttribute('data-srcset');
				}
				img.classList.add('loaded');
				lazyObserver.unobserve(img);
			});
		}, { rootMargin: '200px 0px' }); // 200px ahead load karo

		document.querySelectorAll('img[data-src]').forEach(function(img) {
			lazyObserver.observe(img);
		});
	}

	/* ── 2. Video Memory Management ── */
	// Max active video elements to avoid memory issues
	var MAX_ACTIVE_VIDEOS = 5;

	function cleanupDistantVideos(currentIndex) {
		var reels = document.querySelectorAll('.reel');
		reels.forEach(function(reel, i) {
			var video = reel.querySelector('video');
			if (!video) return;
			var distance = Math.abs(i - currentIndex);
			if (distance > MAX_ACTIVE_VIDEOS) {
				// Free memory — remove src from far-away videos
				if (video.src && !video.dataset.originalSrc) {
					video.dataset.originalSrc = video.src;
				}
				video.pause();
				video.src = '';
				video.load();
			} else if (video.dataset.originalSrc && !video.src) {
				// Restore when coming back into range
				video.src = video.dataset.originalSrc;
				video.load();
			}
		});
	}

	/* ── 3. Network-aware loading ── */
	var connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
	var isSlow = false;

	if (connection) {
		isSlow = connection.effectiveType === '2g' || connection.effectiveType === 'slow-2g';
		connection.addEventListener('change', function() {
			isSlow = connection.effectiveType === '2g' || connection.effectiveType === 'slow-2g';
			document.body.dataset.network = connection.effectiveType;
		});
	}
	document.body.dataset.network = connection ? connection.effectiveType : '4g';

	/* ── 4. Prefetch next page resources ── */
	function prefetchRoute(url) {
		if (!url || document.querySelector('link[href="' + url + '"]')) return;
		var link = document.createElement('link');
		link.rel = 'prefetch';
		link.href = url;
		document.head.appendChild(link);
	}

	// Prefetch common routes on idle
	if ('requestIdleCallback' in window) {
		requestIdleCallback(function() {
			prefetchRoute('/css/main.css');
			prefetchRoute('/js/reels.js');
		}, { timeout: 3000 });
	}

	/* ── 5. Debounce / Throttle ── */
	function debounce(fn, delay) {
		var timer;
		return function() {
			var args = arguments;
			var ctx = this;
			clearTimeout(timer);
			timer = setTimeout(function() { fn.apply(ctx, args); }, delay);
		};
	}

	function throttle(fn, limit) {
		var lastCall = 0;
		return function() {
			var now = Date.now();
			if (now - lastCall >= limit) {
				lastCall = now;
				return fn.apply(this, arguments);
			}
		};
	}

	/* ── 6. DOM Batch Updates ── */
	function batchDOMUpdate(fn) {
		if ('requestAnimationFrame' in window) {
			requestAnimationFrame(fn);
		} else {
			setTimeout(fn, 16);
		}
	}

	/* ── 7. Image src helper — lazy version ── */
	function lazyImg(src, alt, className, style) {
		var img = document.createElement('img');
		img.dataset.src = src;
		img.alt = alt || '';
		if (className) img.className = className;
		if (style) img.style.cssText = style;
		// Placeholder blur
		img.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="1" height="1"%3E%3C/svg%3E';
		if (lazyObserver) lazyObserver.observe(img);
		else img.src = src; // fallback
		return img;
	}

	/* ── 8. Scroll performance — passive listeners ── */
	function addPassiveListener(el, event, fn) {
		el.addEventListener(event, fn, { passive: true });
	}

	/* ── 9. Page Visibility API — pause on hidden ── */
	document.addEventListener('visibilitychange', function() {
		if (document.hidden) {
			// Pause all playing videos
			document.querySelectorAll('video').forEach(function(v) {
				if (!v.paused) {
					v.dataset.wasPlaying = '1';
					v.pause();
				}
			});
		} else {
			// Resume
			document.querySelectorAll('video[data-wasPlaying="1"]').forEach(function(v) {
				v.removeAttribute('data-wasPlaying');
				v.play().catch(function() { });
			});
		}
	});

	/* ── 10. Preconnect to external resources ── */
	function addPreconnect(url) {
		if (document.querySelector('link[href="' + url + '"]')) return;
		var link = document.createElement('link');
		link.rel = 'preconnect';
		link.href = url;
		link.crossOrigin = 'anonymous';
		document.head.appendChild(link);
	}

	addPreconnect('https://fonts.googleapis.com');
	addPreconnect('https://fonts.gstatic.com');

	/* ── Init ── */
	document.addEventListener('DOMContentLoaded', initLazyImages);

	// Re-run on dynamic content
	var domObserver = new MutationObserver(debounce(function(mutations) {
		mutations.forEach(function(m) {
			m.addedNodes.forEach(function(node) {
				if (node.nodeType !== 1) return;
				var imgs = node.querySelectorAll ? node.querySelectorAll('img[data-src]') : [];
				imgs.forEach(function(img) {
					if (lazyObserver) lazyObserver.observe(img);
				});
			});
		});
	}, 100));

	domObserver.observe(document.body, { childList: true, subtree: true });

	/* ── Expose utilities ── */
	window.perf = {
		debounce: debounce,
		throttle: throttle,
		batchDOMUpdate: batchDOMUpdate,
		lazyImg: lazyImg,
		addPassiveListener: addPassiveListener,
		cleanupVideos: cleanupDistantVideos,
		prefetchRoute: prefetchRoute,
		isSlow: function() { return isSlow; }
	};

})();
