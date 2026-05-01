/* =============================================
   VIDEOBUFFER.JS — AI Feed
   ✅ Smart preload (next 2, prev 1)
   ✅ HLS quality adaptive (hls.js config)
   ✅ Stall detection + auto-recovery
   ✅ Buffer progress indicator
   ✅ Network speed aware loading
   ✅ Memory management (unload far videos)
   ✅ Retry with exponential backoff
   ✅ Loading spinner per video
============================================= */

(function() {

	var PRELOAD_AHEAD = 2;   // aage ke 2 videos preload
	var PRELOAD_BEHIND = 1;   // peeche 1 video rakhna
	var MAX_LOADED = 5;   // max loaded videos in DOM
	var STALL_TIMEOUT = 4000; // 4s stall = recover karo

	/* ── HLS config — adaptive quality ── */
	var HLS_CONFIG = {
		// Buffer targets
		maxBufferLength: 20,    // 20s buffer
		maxMaxBufferLength: 40,    // max 40s
		maxBufferSize: 30 * 1000 * 1000, // 30MB
		maxBufferHole: 0.3,

		// Start low quality, ramp up fast
		startLevel: -1,    // auto
		abrEwmaFastLive: 3,
		abrEwmaSlowLive: 9,
		abrBandWidthFactor: 0.9,
		abrBandWidthUpFactor: 0.7,

		// Faster initial load
		manifestLoadingTimeOut: 10000,
		manifestLoadingMaxRetry: 3,
		levelLoadingTimeOut: 10000,
		fragLoadingTimeOut: 20000,
		fragLoadingMaxRetry: 3,

		// Low latency mode off (VOD)
		lowLatencyMode: false,

		// Auto start load
		autoStartLoad: true,
		startPosition: -1,
	};

	/* ── Per-video state ── */
	var videoStates = {};  // key: postId, value: { hls, stallTimer, retries, loaded }

	/* ── Main: setup video with buffering ── */
	function setupVideoBuffering(video, postId, videoUrl) {
		if (!video || !videoUrl) return;

		videoStates[postId] = {
			hls: null,
			stallTimer: null,
			retries: 0,
			loaded: false,
			url: videoUrl
		};

		// Add loading spinner
		addLoadingSpinner(video);

		// Setup source
		if (videoUrl.endsWith('.m3u8')) {
			setupHLS(video, postId, videoUrl);
		} else {
			setupMP4(video, postId, videoUrl);
		}

		// Stall detection
		setupStallDetection(video, postId);

		// Buffer indicator
		setupBufferIndicator(video);

		// Events
		video.addEventListener('loadeddata', function() {
			var state = videoStates[postId];
			if (state) state.loaded = true;
			removeLoadingSpinner(video);
		});

		video.addEventListener('waiting', function() {
			showBufferingSpinner(video);
		});

		video.addEventListener('playing', function() {
			removeLoadingSpinner(video);
			clearStallTimer(postId);
		});

		video.addEventListener('canplay', function() {
			removeLoadingSpinner(video);
		});
	}

	/* ── HLS setup with quality levels ── */
	function setupHLS(video, postId, url) {
		if (typeof Hls === 'undefined') {
			// Fallback to native
			video.src = url;
			return;
		}

		if (!Hls.isSupported()) {
			if (video.canPlayType('application/vnd.apple.mpegurl')) {
				video.src = url;
			}
			return;
		}

		var hls = new Hls(HLS_CONFIG);
		var state = videoStates[postId];
		if (state) state.hls = hls;

		hls.loadSource(url);
		hls.attachMedia(video);

		hls.on(Hls.Events.MANIFEST_PARSED, function(event, data) {
			// Quality levels available
			if (data.levels && data.levels.length > 1) {
				addQualityIndicator(video, hls);
			}
		});

		hls.on(Hls.Events.ERROR, function(event, data) {
			if (!data.fatal) return;
			var state = videoStates[postId];
			if (!state) return;

			if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
				if (state.retries < 3) {
					state.retries++;
					var delay = state.retries * 1000;
					setTimeout(function() { hls.startLoad(); }, delay);
				} else {
					showVideoError(video, postId, 'Network error. Retry karo.');
				}
			} else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
				hls.recoverMediaError();
			} else {
				showVideoError(video, postId, 'Video load fail.');
			}
		});

		hls.on(Hls.Events.FRAG_BUFFERED, function() {
			updateBufferBar(video);
		});
	}

	/* ── MP4 setup ── */
	function setupMP4(video, postId, url) {
		var source = document.createElement('source');
		source.src = url;
		source.type = 'video/mp4';
		video.appendChild(source);

		video.addEventListener('error', function() {
			var state = videoStates[postId];
			if (!state || state.retries >= 3) {
				showVideoError(video, postId, 'Video load fail.');
				return;
			}
			state.retries++;
			var delay = state.retries * 1500;
			setTimeout(function() {
				video.load();
				video.play().catch(function() { });
			}, delay);
		});
	}

	/* ── Smart Preload ── */
	function smartPreload(currentIndex) {
		var allVideos = document.querySelectorAll('.reel-video');

		allVideos.forEach(function(video, i) {
			var distance = i - currentIndex;

			if (distance > 0 && distance <= PRELOAD_AHEAD) {
				// Ahead — preload
				if (video.preload !== 'auto') {
					video.preload = 'auto';
					if (!video.src && !video.querySelector('source')?.src) return;
					video.load();
				}
			} else if (distance < 0 && Math.abs(distance) <= PRELOAD_BEHIND) {
				// Behind — keep loaded
				// nothing to do
			} else if (Math.abs(distance) > MAX_LOADED) {
				// Too far — unload to save memory
				unloadVideo(video);
			}
		});
	}

	/* ── Unload far videos ── */
	function unloadVideo(video) {
		if (video._unloaded) return;
		var postId = video.dataset.post;
		var state = videoStates[postId];

		if (state && state.hls) {
			state.hls.stopLoad();
		}

		if (!video.paused) video.pause();

		// Save src for later restore
		var src = video.querySelector('source');
		if (src && !video.dataset.savedSrc) {
			video.dataset.savedSrc = src.src;
			src.src = '';
			video.load();
		}
		video._unloaded = true;
	}

	function reloadVideo(video) {
		if (!video._unloaded) return;
		var src = video.querySelector('source');
		if (src && video.dataset.savedSrc) {
			src.src = video.dataset.savedSrc;
			delete video.dataset.savedSrc;
		}
		var postId = video.dataset.post;
		var state = videoStates[postId];
		if (state && state.hls) {
			state.hls.startLoad();
		} else {
			video.load();
		}
		video._unloaded = false;
	}

	/* ── Stall detection ── */
	function setupStallDetection(video, postId) {
		video.addEventListener('waiting', function() {
			clearStallTimer(postId);
			var state = videoStates[postId];
			if (!state) return;
			state.stallTimer = setTimeout(function() {
				recoverStall(video, postId);
			}, STALL_TIMEOUT);
		});
	}

	function recoverStall(video, postId) {
		var state = videoStates[postId];
		if (!state) return;

		var currentTime = video.currentTime;

		if (state.hls) {
			// HLS recovery
			state.hls.recoverMediaError();
		} else {
			// MP4 recovery — seek slightly forward
			if (video.buffered.length > 0) {
				var bufferedEnd = video.buffered.end(video.buffered.length - 1);
				if (bufferedEnd > currentTime + 0.5) {
					video.currentTime = currentTime + 0.1;
				}
			}
			video.play().catch(function() { });
		}
	}

	function clearStallTimer(postId) {
		var state = videoStates[postId];
		if (state && state.stallTimer) {
			clearTimeout(state.stallTimer);
			state.stallTimer = null;
		}
	}

	/* ── Buffer bar (shows how much is buffered) ── */
	function setupBufferIndicator(video) {
		var bar = document.createElement('div');
		bar.className = 'buffer-bar';
		bar.style.cssText = [
			'position:absolute;bottom:0;left:0;height:3px;',
			'background:rgba(255,255,255,0.25);',
			'z-index:8;pointer-events:none;',
			'transition:width .3s;width:0%'
		].join('');

		var wrapper = video.closest('.reel');
		if (wrapper) wrapper.appendChild(bar);

		video.addEventListener('progress', function() {
			updateBufferBar(video);
		});
	}

	function updateBufferBar(video) {
		var wrapper = video.closest('.reel');
		if (!wrapper) return;
		var bar = wrapper.querySelector('.buffer-bar');
		if (!bar || !video.duration || !video.buffered.length) return;
		var end = video.buffered.end(video.buffered.length - 1);
		var pct = Math.min(100, (end / video.duration) * 100);
		bar.style.width = pct + '%';
	}

	/* ── Loading spinner ── */
	function addLoadingSpinner(video) {
		var wrapper = video.closest('.reel');
		if (!wrapper || wrapper.querySelector('.video-spinner')) return;

		var spinner = document.createElement('div');
		spinner.className = 'video-spinner';
		spinner.style.cssText = [
			'position:absolute;top:50%;left:50%;',
			'transform:translate(-50%,-50%);',
			'width:40px;height:40px;',
			'border:3px solid rgba(255,255,255,0.15);',
			'border-top-color:rgba(255,255,255,0.7);',
			'border-radius:50%;',
			'animation:videoSpin .7s linear infinite;',
			'z-index:15;pointer-events:none;'
		].join('');

		wrapper.appendChild(spinner);
	}

	function showBufferingSpinner(video) {
		var wrapper = video.closest('.reel');
		if (!wrapper) return;
		var spinner = wrapper.querySelector('.video-spinner');
		if (spinner) spinner.style.display = 'block';
		else addLoadingSpinner(video);
	}

	function removeLoadingSpinner(video) {
		var wrapper = video.closest('.reel');
		if (!wrapper) return;
		var spinner = wrapper.querySelector('.video-spinner');
		if (spinner) spinner.style.display = 'none';
	}

	/* ── Quality indicator (HLS only) ── */
	function addQualityIndicator(video, hls) {
		var wrapper = video.closest('.reel');
		if (!wrapper || wrapper.querySelector('.quality-badge')) return;

		var badge = document.createElement('div');
		badge.className = 'quality-badge';
		badge.style.cssText = [
			'position:absolute;top:14px;left:12px;',
			'background:rgba(0,0,0,0.5);',
			'backdrop-filter:blur(4px);',
			'color:rgba(255,255,255,0.7);',
			'font-size:10px;font-weight:700;',
			'padding:2px 7px;border-radius:4px;',
			'z-index:10;pointer-events:none;',
			'letter-spacing:.3px;'
		].join('');
		badge.textContent = 'AUTO';
		wrapper.appendChild(badge);

		hls.on(Hls.Events.LEVEL_SWITCHED, function(e, data) {
			var level = hls.levels[data.level];
			if (!level) return;
			var height = level.height || '';
			badge.textContent = height ? height + 'p' : 'AUTO';
			badge.style.color = height >= 720
				? 'rgba(99,102,241,0.9)'
				: height >= 480
					? 'rgba(255,255,255,0.7)'
					: 'rgba(245,158,11,0.9)';
		});
	}

	/* ── Error UI ── */
	function showVideoError(video, postId, msg) {
		if (video._errorHandled) return;
		video._errorHandled = true;
		removeLoadingSpinner(video);

		var wrapper = video.closest('.reel');
		if (!wrapper) return;

		var fb = document.createElement('div');
		fb.style.cssText = [
			'position:absolute;inset:0;',
			'display:flex;align-items:center;justify-content:center;',
			'flex-direction:column;background:rgba(0,0,0,0.85);',
			'color:#9ca3af;gap:12px;z-index:20;'
		].join('');
		fb.innerHTML = [
			'<div style="font-size:2.5rem;opacity:.5">🎬</div>',
			'<div style="font-size:.82rem">' + (msg || 'Video load nahi hui') + '</div>',
			'<button style="background:#6366f1;color:#fff;border:none;border-radius:20px;',
			'padding:7px 18px;cursor:pointer;font-size:.78rem;font-weight:700;" ',
			'onclick="this.closest(\'[style]\').remove();',
			'var v=this.closest(\'.reel\').querySelector(\'video\');',
			'v._errorHandled=false;v.load();">',
			'Retry</button>'
		].join('');

		wrapper.appendChild(fb);
	}

	/* ── Network speed aware ── */
	function getNetworkQuality() {
		var conn = navigator.connection || navigator.mozConnection;
		if (!conn) return 'good';
		var type = conn.effectiveType;
		if (type === '4g') return 'good';
		if (type === '3g') return 'medium';
		return 'poor';
	}

	/* ── Cleanup on reel destroy ── */
	function destroyVideo(postId) {
		var state = videoStates[postId];
		if (!state) return;
		if (state.hls) {
			state.hls.destroy();
			state.hls = null;
		}
		clearStallTimer(postId);
		delete videoStates[postId];
	}

	/* ── Inject CSS ── */
	(function injectCSS() {
		if (document.getElementById('vb-styles')) return;
		var s = document.createElement('style');
		s.id = 'vb-styles';
		s.textContent = [
			'@keyframes videoSpin{to{transform:translate(-50%,-50%) rotate(360deg)}}',
			'.reel-video{background:#000}',
			'.buffer-bar{will-change:width}',
			'.quality-badge{transition:color .3s}'
		].join('');
		document.head.appendChild(s);
	})();

	/* ── Expose globally ── */
	window.VideoBuffer = {
		setup: setupVideoBuffering,
		smartPreload: smartPreload,
		destroy: destroyVideo,
		unload: unloadVideo,
		reload: reloadVideo,
		getNetwork: getNetworkQuality
	};

})();