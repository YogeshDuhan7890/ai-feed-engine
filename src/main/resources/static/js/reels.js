/* ================================
   REELS.JS - COMPLETE VERSION
================================ */

let cursor = null
let loading = false
let currentLang = 'en'
let container = null
let activePostId = null
let retryCount = 0
let wasOffline = false
let mobileCursor = null
let feedSource = 'hybrid'

const MAX_REELS = 30
const MAX_RETRIES = 3
const RETRY_DELAYS = [2000, 5000, 10000]

/* ── CSRF ── */
function getCsrfToken() {
	const m = document.querySelector('meta[name="_csrf"]')
	if (m) return m.getAttribute('content')
	const c = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
	return c ? decodeURIComponent(c[1]) : ''
}
function getCsrfHeader() {
	const m = document.querySelector('meta[name="_csrf_header"]')
	return m ? m.getAttribute('content') : 'X-CSRF-TOKEN'
}
function csrfHeaders(extra = {}) {
	const t = getCsrfToken(), h = getCsrfHeader()
	return t ? { [h]: t, ...extra } : { ...extra }
}

/* ── Skeleton Styles ── */
function injectSkeletonStyles() {
	if (document.getElementById('__skeleton-styles')) return
	const s = document.createElement('style')
	s.id = '__skeleton-styles'
	s.textContent = `
		@keyframes shimmer {
			0%   { background-position: -700px 0 }
			100% { background-position:  700px 0 }
		}
		.skeleton-reel {
			height: var(--reel-height, 100vh); scroll-snap-align: start;
			position: relative; overflow: hidden; background: #111;
		}
		.skeleton-bg {
			position: absolute; inset: 0;
			background: linear-gradient(90deg, #1a1a1a 25%, #2a2a2a 50%, #1a1a1a 75%);
			background-size: 700px 100%;
			animation: shimmer 1.4s infinite linear;
		}
		.skeleton-foot { position: absolute; bottom: 80px; left: 16px; z-index: 2; }
		.skeleton-line {
			height: 14px; border-radius: 6px; margin-bottom: 10px;
			background: linear-gradient(90deg, #2a2a2a 25%, #3a3a3a 50%, #2a2a2a 75%);
			background-size: 700px 100%;
			animation: shimmer 1.4s infinite linear;
		}
		.skeleton-line.s { width: 120px; }
		.skeleton-line.l { width: 260px; }
		.reel-progress {
			position: absolute; bottom: 0; left: 0;
			height: 3px; background: #fff; width: 0%;
			z-index: 10; pointer-events: none;
		}
		.heart-burst {
			position: absolute; top: 50%; left: 50%;
			transform: translate(-50%, -50%) scale(0);
			font-size: 5rem; z-index: 20;
			animation: heartBurst 0.7s ease forwards;
			pointer-events: none;
		}
		@keyframes heartBurst {
			0%   { transform: translate(-50%, -50%) scale(0); opacity: 1; }
			50%  { transform: translate(-50%, -50%) scale(1.4); opacity: 1; }
			100% { transform: translate(-50%, -50%) scale(1); opacity: 0; }
		}
	`
	document.head.appendChild(s)
}

function showSkeletons(n = 3) {
	for (let i = 0; i < n; i++) {
		const div = document.createElement('div')
		div.className = 'skeleton-reel'
		div.innerHTML = `<div class="skeleton-bg"></div>
			<div class="skeleton-foot">
				<div class="skeleton-line s"></div>
				<div class="skeleton-line l"></div>
			</div>`
		container.appendChild(div)
	}
}
function removeSkeletons() { container.querySelectorAll('.skeleton-reel').forEach(el => el.remove()) }

/* ── INIT ── */
document.addEventListener('DOMContentLoaded', () => {
	injectSkeletonStyles()
	container = document.getElementById('feed')

	// FIX: Stories bar height dynamically set karo
	function adjustFeedPosition() {
		const storiesBar = document.querySelector('.stories-bar')
		const feedLayout = document.querySelector('.feed-layout')
		if (storiesBar && feedLayout) {
			const headerH = parseInt(getComputedStyle(document.documentElement).getPropertyValue('--header-h') || '60', 10)
			const storiesH = storiesBar.offsetHeight
			const total = headerH + storiesH
			const reelHeight = `calc(100vh - ${total}px)`
			document.documentElement.style.setProperty('--feed-offset', `${total}px`)
			document.documentElement.style.setProperty('--reel-height', reelHeight)
			feedLayout.style.marginTop = `${total}px`
			feedLayout.style.height = reelHeight
			document.querySelectorAll('.reel').forEach(reel => {
				reel.style.height = reelHeight
			})
		}
	}
	// Stories load hone ka wait karo phir adjust karo
	adjustFeedPosition()
	setTimeout(adjustFeedPosition, 500)
	setTimeout(adjustFeedPosition, 1500)
	window.addEventListener('resize', adjustFeedPosition)

	document.getElementById('languageSelect')?.addEventListener('change', e => {
		currentLang = e.target.value
		container.innerHTML = ''
		cursor = null; mobileCursor = null; feedSource = 'hybrid'; loading = false
		loadFeed(true)
	})

	let scrollTicking = false
	container?.addEventListener('scroll', () => {
		if (scrollTicking) return
		scrollTicking = true
		window.requestAnimationFrame(() => {
			const dist = container.scrollHeight - container.scrollTop - container.clientHeight
			if (dist < 600 && !loading) loadFeed()
			scrollTicking = false
		})
	})

	document.getElementById('closeCommentsBtn')?.addEventListener('click', closeComments)
	document.getElementById('backdrop')?.addEventListener('click', closeComments)
	document.getElementById('sendCommentBtn')?.addEventListener('click', submitComment)
	document.getElementById('commentInput')?.addEventListener('keypress', e => {
		if (e.key === 'Enter') submitComment()
	})

	initSwipeNav()
	initOfflineDetection()

	loadFeed(true)
})

/* ── Swipe Navigation ── */
function initSwipeNav() {
	if (!container) return
	document.addEventListener('keydown', event => {
		if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
		if (document.activeElement && /input|textarea|select/i.test(document.activeElement.tagName)) return

		const reels = [...container.querySelectorAll('.reel')]
		if (!reels.length) return

		const currentIndex = getNearestReelIndex(reels)
		const direction = event.key === 'ArrowDown' ? 1 : -1
		const nextIndex = Math.max(0, Math.min(reels.length - 1, currentIndex + direction))
		reels[nextIndex]?.scrollIntoView({ behavior: 'smooth', block: 'start' })

		if (nextIndex >= reels.length - 2) {
			loadFeed()
		}
	})
}

function getNearestReelIndex(reels) {
	const scrollTop = container?.scrollTop || 0
	let nearestIndex = 0
	let smallestDistance = Number.POSITIVE_INFINITY

	reels.forEach((reel, index) => {
		const distance = Math.abs(reel.offsetTop - scrollTop)
		if (distance < smallestDistance) {
			smallestDistance = distance
			nearestIndex = index
		}
	})

	return nearestIndex
}

/* ── Offline Detection ── */
function initOfflineDetection() {
	window.addEventListener('offline', () => {
		wasOffline = true
		showBanner('📵 Internet nahi hai. Videos load nahi honge.', 'offline-banner')
	})
	window.addEventListener('online', () => {
		if (!wasOffline) return
		hideBanner('offline-banner')
		showToast('✅ Internet wapas aa gaya!')
		wasOffline = false; retryCount = 0
		setTimeout(() => loadFeed(), 500)
	})
}
function showBanner(msg, id) {
	document.getElementById(id)?.remove()
	const d = document.createElement('div')
	d.id = id
	d.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:9999;background:rgba(239,68,68,0.95);color:#fff;text-align:center;padding:10px;font-size:0.85rem;'
	d.textContent = msg
	document.body.prepend(d)
}
function hideBanner(id) { document.getElementById(id)?.remove() }

/* ── IntersectionObserver ── */
const videoObserver = new IntersectionObserver(entries => {
	entries.forEach(entry => {
		const video = entry.target
		if (entry.isIntersecting) {
			document.querySelectorAll('.reel-video').forEach(v => { if (v !== video) v.pause() })
			video.play().catch(() => { })
			preloadNext(parseInt(video.dataset.index || '0'))
			loadLikeCount(video.dataset.post)
		} else {
			video.pause()
		}
	})
}, { threshold: 0.75 })

/* ── Load Feed ── */
async function loadFeed(isInitial = false) {
	if (loading) return
	loading = true
	const first = isInitial || (!cursor && !container.querySelector('.reel'))
	if (first) showSkeletons(3)
	try {
		const posts = await fetchFeedFromAPI()
		if (first) removeSkeletons()
		if (!posts?.length) { loading = false; return }
		retryCount = 0
		renderFeed(posts)
	} catch (e) {
		if (first) removeSkeletons()
		console.error('Feed load error:', e)
		loading = false  // FIX: catch mein bhi reset karo warna stuck rehta hai
		handleFeedError(e)
	}
	loading = false
}

async function fetchFeedFromAPI() {
	if (feedSource === 'mobile') {
		return fetchMobileFeed()
	}

	let url = '/api/hybrid?size=5'
	if (cursor) url += '&cursor=' + cursor
	const res = await fetch(url)
	if (!res.ok) throw new Error('Feed API failed: ' + res.status)
	const data = await res.json()
	const posts = Array.isArray(data.data) ? data.data : []
	cursor = data.nextCursor

	if (posts.length >= 2 || cursor) {
		return posts
	}

	try {
		feedSource = 'mobile'
		const mobilePosts = await fetchMobileFeed()
		return mobilePosts.length ? mobilePosts : posts
	} catch (error) {
		feedSource = 'hybrid'
		return posts
	}
}

async function fetchMobileFeed() {
	let url = '/api/mobile/feed?size=6'
	if (mobileCursor) url += '&cursor=' + encodeURIComponent(mobileCursor)

	const res = await fetch(url, { headers: csrfHeaders() })
	if (!res.ok) throw new Error('Mobile feed API failed: ' + res.status)

	const data = await res.json()
	if (data.success === false) throw new Error(data.error || 'Mobile feed unavailable')

	mobileCursor = data.nextCursor || null
	return (Array.isArray(data.data) ? data.data : []).map(normalizeMobilePost)
}

function normalizeMobilePost(item) {
	return {
		postId: item.id,
		videoUrl: item.videoUrl,
		content: item.caption || '',
		tags: item.tags || '',
		chapters: item.chapters,
		parentPostId: item.parentPostId || null,
		userId: item.author?.id || 0,
		userName: item.author?.name || 'User',
		userAvatar: item.author?.avatar || '',
		createdAt: item.createdAt || '',
		likeCount: item.likes || 0,
		commentCount: item.comments || 0,
		isFollowing: false
	}
}

/* ── Error Recovery ── */
function handleFeedError(error) {
	if (retryCount >= MAX_RETRIES) { showNetworkError('Feed load nahi ho rahi. Internet check karo.'); return }
	const delay = RETRY_DELAYS[retryCount] || 10000
	retryCount++
	showToast(`Network error. ${delay / 1000}s mein retry... (${retryCount}/${MAX_RETRIES})`)
	setTimeout(() => loadFeed(), delay)
}
function showNetworkError(msg) {
	if (document.getElementById('__netError')) return
	const d = document.createElement('div')
	d.id = '__netError'
	d.style.cssText = 'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);background:rgba(17,17,17,0.95);color:#fff;border-radius:12px;padding:16px 24px;text-align:center;z-index:999;max-width:300px;'
	d.innerHTML = `<div style="font-size:1.5rem;margin-bottom:8px">📡</div>
		<div style="font-size:0.85rem;margin-bottom:12px">${msg}</div>
		<button onclick="document.getElementById('__netError').remove();retryCount=0;loadFeed();"
			style="background:#6c63ff;color:#fff;border:none;border-radius:8px;padding:8px 16px;cursor:pointer;font-size:0.8rem">🔄 Retry</button>`
	document.body.appendChild(d)
	setTimeout(() => d.remove(), 30000)
}

/* ── Render Feed ── */
function renderFeed(posts) {
	const existingIds = new Set(
		[...container.querySelectorAll('.reel')]
			.map(el => el.dataset.postId)
			.filter(Boolean)
	)

	const uniquePosts = posts.filter(post => {
		const id = String(post.postId || '')
		return id && !existingIds.has(id)
	})

	uniquePosts.forEach(post => {
		const index = container.querySelectorAll('.reel-video').length
		container.appendChild(buildReel(post, index))
	})
	// DOM bloat: keep max 30
	const all = container.querySelectorAll('.reel')
	if (all.length > MAX_REELS) {
		for (let i = 0; i < all.length - MAX_REELS; i++) {
			videoObserver.unobserve(all[i].querySelector('.reel-video'))
			all[i].remove()
		}
	}
}

/* ── Build Reel ── */
function buildReel(post, index) {
	const div = document.createElement('div')
	div.className = 'reel'
	div.dataset.postId = post.postId

	// Video
	const video = document.createElement('video')
	video.className = 'reel-video'; video.muted = true; video.playsInline = true
	video.loop = true; video.preload = 'metadata'
	video.dataset.post = post.postId; video.dataset.index = index

	if (post.videoUrl?.endsWith('.m3u8')) {
		if (typeof Hls !== 'undefined' && Hls.isSupported()) {
			const hls = new Hls({ autoLevelEnabled: true });
			hls.loadSource(post.videoUrl);
			hls.attachMedia(video)

			// Quality manual picker (AUTO / 360p / 720p / 1080p)
			let qualityMenu = null
			let qualityBtn = null

			function ensureQualityUI() {
				if (qualityMenu && qualityBtn) return
				const levels = (hls.levels || []).filter(l => l && (l.height || 0) > 0)
				if (!levels.length) return

				qualityBtn = document.createElement('button')
				qualityBtn.textContent = 'AUTO'
				qualityBtn.style.cssText = [
					'position:absolute;top:12px;right:12px;z-index:12;',
					'background:rgba(0,0,0,0.55);backdrop-filter:blur(4px);',
					'border:1px solid rgba(255,255,255,0.15);color:#fff;',
					'border-radius:10px;padding:6px 10px;font-size:12px;cursor:pointer;'
				].join('')

				qualityMenu = document.createElement('div')
				qualityMenu.style.cssText = [
					'position:absolute;top:52px;right:12px;z-index:12;',
					'background:rgba(0,0,0,0.85);color:#fff;',
					'border-radius:10px;overflow:hidden;',
					'border:1px solid rgba(255,255,255,0.12);',
					'display:none;min-width:120px;'
				].join('')

				const autoRow = document.createElement('div')
				autoRow.textContent = 'AUTO'
				autoRow.style.cssText = 'padding:10px 12px;cursor:pointer;font-weight:700;'
				autoRow.addEventListener('click', e => {
					e.stopPropagation()
					qualityBtn.textContent = 'AUTO'
					hls.autoLevelEnabled = true
					hls.currentLevel = -1
					qualityMenu.style.display = 'none'
				})

				qualityMenu.appendChild(autoRow)

				// De-dupe by height (some HLS streams can include duplicates)
				const byHeight = new Map()
				levels.forEach((lvl, idx) => {
					if (!byHeight.has(lvl.height)) byHeight.set(lvl.height, idx)
				})
				;[...byHeight.entries()].sort((a, b) => a[0] - b[0]).forEach(([height, levelIndex]) => {
					const row = document.createElement('div')
					row.textContent = height + 'p'
					row.style.cssText = 'padding:10px 12px;cursor:pointer;'
					row.addEventListener('click', e => {
						e.stopPropagation()
						qualityBtn.textContent = height + 'p'
						hls.autoLevelEnabled = false
						hls.currentLevel = levelIndex
						qualityMenu.style.display = 'none'
					})
					qualityMenu.appendChild(row)
				})

				qualityBtn.addEventListener('click', e => {
					e.stopPropagation()
					qualityMenu.style.display = qualityMenu.style.display === 'none' ? 'block' : 'none'
				})

				// Close on outside click
				document.addEventListener('click', () => {
					if (qualityMenu) qualityMenu.style.display = 'none'
				}, { once: true })

				div.style.position = div.style.position || 'relative'
				div.appendChild(qualityBtn)
				div.appendChild(qualityMenu)

				hls.on(Hls.Events.LEVEL_SWITCHED, function(_, data) {
					const lvl = hls.levels?.[data.level]
					if (!lvl || !lvl.height) return
					if (hls.autoLevelEnabled) qualityBtn.textContent = 'AUTO'
					else qualityBtn.textContent = lvl.height + 'p'
				})
			}

			hls.on(Hls.Events.MANIFEST_PARSED, function() {
				ensureQualityUI()
			})
		} else if (video.canPlayType('application/vnd.apple.mpegurl')) {
			video.src = post.videoUrl
		}
	} else {
		const src = document.createElement('source')
		src.src = post.videoUrl || ''; src.type = 'video/mp4'
		video.appendChild(src)
	}
	video.addEventListener('error', () => handleVideoError(video, post.postId))

	// ── Duet / Collab UI (basic side-by-side) ─────────────────────
	// If parentPostId exists, we render a split container:
	// [ parent/original ] [ current/response ]
	let duetWrap = null
	let parentVideo = null
	if (post.parentPostId) {
		duetWrap = document.createElement('div')
		duetWrap.className = 'duet-wrap'
		duetWrap.style.cssText = [
			'position:absolute;inset:0;display:flex;gap:2px;z-index:1;',
			'background:#000;'
		].join('')

		const left = document.createElement('div')
		left.style.cssText = 'flex:1;position:relative;overflow:hidden;background:#000;'
		const right = document.createElement('div')
		right.style.cssText = 'flex:1;position:relative;overflow:hidden;background:#000;'

		// Parent video placeholder (will be loaded async)
		parentVideo = document.createElement('video')
		parentVideo.className = 'reel-video duet-parent-video'
		parentVideo.muted = true
		parentVideo.playsInline = true
		parentVideo.loop = true
		parentVideo.preload = 'metadata'
		parentVideo.style.cssText = 'width:100%;height:100%;object-fit:cover;display:block;'

		// Ensure current response video fills right pane
		video.style.cssText = 'width:100%;height:100%;object-fit:cover;display:block;'

		// Small label
		const badge = document.createElement('div')
		badge.textContent = 'DUET'
		badge.style.cssText = [
			'position:absolute;top:10px;left:10px;z-index:3;',
			'background:rgba(0,0,0,0.55);backdrop-filter:blur(4px);',
			'border:1px solid rgba(255,255,255,0.12);',
			'color:#fff;border-radius:12px;padding:6px 10px;font-size:12px;font-weight:800;'
		].join('')

		left.appendChild(parentVideo)
		right.appendChild(video)
		duetWrap.append(left, right, badge)

		// Load parent post videoUrl async
		fetch(`/api/post/${post.parentPostId}`)
			.then(r => r.ok ? r.json() : null)
			.then(p => {
				if (!p || !p.videoUrl) throw new Error('Parent missing')
				if (p.videoUrl.endsWith('.m3u8')) {
					if (typeof Hls !== 'undefined' && Hls.isSupported()) {
						const h = new Hls({ autoLevelEnabled: true })
						h.loadSource(p.videoUrl)
						h.attachMedia(parentVideo)
					} else if (parentVideo.canPlayType('application/vnd.apple.mpegurl')) {
						parentVideo.src = p.videoUrl
					}
				} else {
					const s = document.createElement('source')
					s.src = p.videoUrl
					s.type = 'video/mp4'
					parentVideo.appendChild(s)
				}
			})
			.catch(() => {
				// If parent fails, just hide left pane (fallback to single video)
				try { duetWrap.remove() } catch { }
			})

		// Keep parent in sync (best-effort)
		video.addEventListener('play', () => { parentVideo?.play?.().catch(() => { }) })
		video.addEventListener('pause', () => { parentVideo?.pause?.() })
		video.addEventListener('seeking', () => {
			if (!parentVideo) return
			try { parentVideo.currentTime = video.currentTime } catch { }
		})
	}

	// Progress bar
	const prog = document.createElement('div')
	prog.className = 'reel-progress'
	// Chapters (from backend JSON string/array)
	let chapterList = []
	try {
		if (post.chapters) {
			if (Array.isArray(post.chapters)) chapterList = post.chapters
			else if (typeof post.chapters === 'string') chapterList = JSON.parse(post.chapters)
		}
	} catch { chapterList = [] }
	chapterList = (chapterList || []).filter(c => c && typeof c.timeSeconds !== 'undefined')

	let chapterMarkers = null
	let chapterLabel = null
	let markerEls = []

	function updateChapterUI() {
		if (!video.duration || !chapterList.length) return
		const cur = video.currentTime || 0
		// Pick last chapter whose timeSeconds <= current time
		let idx = -1
		for (let i = 0; i < chapterList.length; i++) {
			const t = Number(chapterList[i].timeSeconds)
			if (!Number.isFinite(t)) continue
			if (t <= cur + 0.15) idx = i
		}

		// Highlight marker + update label
		markerEls.forEach((m, i) => {
			const isActive = i === idx
			m.el.style.background = isActive ? 'rgba(99,102,241,0.95)' : 'rgba(255,255,255,0.35)'
			m.el.style.height = isActive ? '14px' : '10px'
		})
		if (chapterLabel) {
			const ch = idx >= 0 ? chapterList[idx] : null
			chapterLabel.textContent = ch?.title ? ch.title : ''
			chapterLabel.style.opacity = ch?.title ? '1' : '0'
		}
	}

	// Create marker elements once duration is known
	video.addEventListener('loadedmetadata', () => {
		if (!video.duration || !chapterList.length) return
		const dur = video.duration
		if (!dur || dur <= 0) return

		chapterMarkers = document.createElement('div')
		chapterMarkers.style.cssText = [
			'position:absolute;bottom:0;left:0;right:0;',
			'height:18px;z-index:6;pointer-events:none;'
		].join('')

		chapterLabel = document.createElement('div')
		chapterLabel.style.cssText = [
			'position:absolute;bottom:22px;left:12px;z-index:7;',
			'background:rgba(0,0,0,0.55);backdrop-filter:blur(4px);',
			'border:1px solid rgba(255,255,255,0.12);',
			'color:#fff;border-radius:10px;padding:6px 10px;font-size:12px;',
			'pointer-events:none;opacity:0;white-space:nowrap;max-width:60%;overflow:hidden;text-overflow:ellipsis;'
		].join('')

		// Ensure reel wrapper positioning
		div.style.position = div.style.position || 'relative'
		div.appendChild(chapterMarkers)
		div.appendChild(chapterLabel)

		markerEls = chapterList.map((ch, i) => {
			const t = Number(ch.timeSeconds)
			const pct = (t / dur) * 100
			const marker = document.createElement('div')
			marker.title = ch.title ? String(ch.title) : ('Chapter ' + (i + 1))
			marker.style.cssText = [
				'position:absolute;bottom:5px;',
				'left:' + pct + '%;',
				'transform:translateX(-50%);',
				'width:2px;height:10px;',
				'background:rgba(255,255,255,0.35);',
				'border-radius:2px;',
				'pointer-events:auto;cursor:pointer;'
			].join('')
			// Click to seek
			marker.addEventListener('click', e => {
				e.stopPropagation()
				video.currentTime = Number(ch.timeSeconds) || 0
				video.play().catch(() => { })
			})
			chapterMarkers.appendChild(marker)
			return { el: marker }
		})
	})

	video.addEventListener('timeupdate', () => {
		if (video.duration) prog.style.width = (video.currentTime / video.duration * 100) + '%'
		updateChapterUI()
	})

	// Double-tap to like
	let lastTap = 0
	div.addEventListener('touchend', e => {
		const now = Date.now()
		if (now - lastTap < 300) { e.preventDefault(); doubleTapLike(post.postId, div) }
		lastTap = now
	})

	// Info overlay
	const info = document.createElement('div')
	info.className = 'reel-info'

	const creator = document.createElement('div')
	creator.className = 'reel-creator'

	const avatar = document.createElement('div')
	avatar.className = 'creator-avatar'
	if (post.userAvatar) {
		const img = document.createElement('img')
		img.src = post.userAvatar; img.alt = ''
		img.style.cssText = 'width:38px;height:38px;border-radius:50%;object-fit:cover;border:2px solid #fff;'
		img.onerror = () => { avatar.textContent = '👤' }
		avatar.appendChild(img)
	} else { avatar.textContent = '👤' }

	const name = document.createElement('span')
	name.className = 'creator-name'
	name.textContent = '@' + (post.userName || 'user')
	name.style.cursor = 'pointer'
	name.addEventListener('click', () => { if (post.userId) location.href = '/profile/user/' + post.userId })

	const followBtn = document.createElement('button')
	followBtn.className = 'follow-pill'; followBtn.textContent = '+ Follow'
	followBtn.dataset.followStatus = post.isFollowing ? 'FOLLOWING' : 'NONE'
	if (post.isFollowing) {
		followBtn.classList.add('following')
		followBtn.textContent = '✓ Following'
	}
	followBtn.addEventListener('click', function() { toggleFollow(post.userId, this) })

	creator.append(avatar, name, followBtn)

	const caption = document.createElement('div')
	caption.className = 'reel-caption'
	caption.textContent = post.translations?.[currentLang] || post.content || ''

	if (post.tags) {
		const tags = document.createElement('div')
		tags.style.cssText = 'margin-top:6px;font-size:0.78rem;'
		post.tags.split(',').forEach(tag => {
			const sp = document.createElement('span')
			sp.style.cssText = 'color:#818cf8;margin-right:6px;cursor:pointer;'
			sp.textContent = '#' + tag.trim()
			sp.addEventListener('click', () => { location.href = '/hashtag/' + tag.trim() })
			tags.appendChild(sp)
		})
		caption.appendChild(tags)
	}

	info.append(creator, caption)

	// Actions
	const actions = document.createElement('div')
	actions.className = 'reel-actions'

	const likeBtn = document.createElement('button')
	likeBtn.className = 'action-btn like-btn'; likeBtn.dataset.postid = post.postId; likeBtn.dataset.liked = 'false'
	likeBtn.innerHTML = '<span class="btn-icon">❤️</span><span class="btn-label like-count">0</span>'
	likeBtn.addEventListener('click', function() { toggleLike(post.postId, this) })

	const commentBtn = document.createElement('button')
	commentBtn.className = 'action-btn comment-btn'; commentBtn.dataset.postid = post.postId
	commentBtn.innerHTML = '<span class="btn-icon">💬</span><span class="btn-label">Comment</span>'
	commentBtn.addEventListener('click', () => openComments(post.postId))

	const shareBtn = document.createElement('button')
	shareBtn.className = 'action-btn share-btn'; shareBtn.dataset.postid = post.postId
	shareBtn.innerHTML = '<span class="btn-icon">↗️</span><span class="btn-label">Share</span>'
	shareBtn.addEventListener('click', () => sharePost(post.postId))

	const bookmarkBtn = document.createElement('button')
	bookmarkBtn.className = 'action-btn bookmark-btn'; bookmarkBtn.dataset.postid = post.postId
	bookmarkBtn.innerHTML = '<span class="btn-icon">🔖</span><span class="btn-label">Save</span>'
	bookmarkBtn.addEventListener('click', function() { toggleBookmark(post.postId, this) })

	// 🚨 REPORT BUTTON
	const reportBtn = document.createElement('button')
	reportBtn.className = 'action-btn report-btn'
	reportBtn.innerHTML = '<span class="btn-icon">🚨</span><span class="btn-label">Report</span>'
	reportBtn.addEventListener('click', () => reportPost(post.postId))

	// FINAL ACTIONS
	actions.append(likeBtn, commentBtn, shareBtn, bookmarkBtn, reportBtn)
	if (duetWrap) {
		// When duet UI active, wrapper holds both videos
		div.style.position = div.style.position || 'relative'
		div.append(duetWrap, prog, info, actions)
		// Observe response video for autoplay
		videoObserver.observe(video)
		// Also observe parent (so it pauses when out of view)
		if (parentVideo) videoObserver.observe(parentVideo)
	} else {
		div.append(video, prog, info, actions)
		videoObserver.observe(video)
	}
	trackWatch(video, post.postId)
	return div
}

/* ── Video Helpers ── */
function preloadNext(index) {
	const all = document.querySelectorAll('.reel-video')
	if (all[index + 1]) all[index + 1].load()
}
function handleVideoError(video, postId) {
	if (video._errorHandled) return
	video._errorHandled = true
	const wrapper = video.closest('.reel')
	if (!wrapper) return
	const fb = document.createElement('div')
	fb.style.cssText = 'position:absolute;inset:0;display:flex;align-items:center;justify-content:center;flex-direction:column;background:#111;color:#666;gap:12px;z-index:5;'
	fb.innerHTML = `<div style="font-size:3rem">🎬</div>
		<div style="font-size:0.85rem">Video load nahi hui</div>
		<button style="background:#333;color:#fff;border:none;border-radius:8px;padding:6px 14px;cursor:pointer;font-size:0.75rem"
			onclick="this.closest('.reel').querySelector('video').load();this.closest('[style]').remove();">Retry</button>`
	wrapper.style.position = 'relative'
	wrapper.appendChild(fb)
}

/* ── Like Count Sync ── */
async function loadLikeCount(postId) {
	try {
		const res = await fetch('/api/post/likes?postIds=' + postId)
		const data = await res.json()
		const btn = document.querySelector(`.like-btn[data-postid="${postId}"]`)
		if (!btn) return
		const el = btn.querySelector('.like-count')
		if (el && data.counts) el.textContent = data.counts[postId] || 0
		if (data.likedByMe?.[postId]) { btn.classList.add('liked'); btn.dataset.liked = 'true' }
	} catch (e) { /* silent */ }
}

/* ── Double Tap Like ── */
function doubleTapLike(postId, reelDiv) {
	const heart = document.createElement('div')
	heart.className = 'heart-burst'; heart.textContent = '❤️'
	reelDiv.appendChild(heart)
	setTimeout(() => heart.remove(), 700)
	const btn = reelDiv.querySelector('.like-btn')
	if (btn && btn.dataset.liked !== 'true') toggleLike(postId, btn)
}

/* ── Watch Tracking ── */
function trackWatch(video, postId) {
	let startTime = 0
	video.addEventListener('play', () => { startTime = Date.now() })
	video.addEventListener('pause', () => {
		const secs = Math.floor((Date.now() - startTime) / 1000)
		if (secs > 1) sendEngagement(postId, 'WATCH', { watchTime: secs })
	})
}

/* ── Engagement ── */
// ── Skip detection — user ne < 2 sec dekha ───────────────────
const videoStartTimes = new Map()

function trackVideoStart(postId) {
	videoStartTimes.set(postId, Date.now())
}

function trackVideoEnd(postId) {
	const start = videoStartTimes.get(postId)
	if (!start) return
	const watchMs = Date.now() - start
	videoStartTimes.delete(postId)
	// < 2000ms = skip signal
	if (watchMs < 2000) {
		sendEngagement(postId, 'SKIP').catch(() => { })
	}
}

async function sendEngagement(postId, type, extra = {}) {
	try {
		await fetch('/api/engagement', {
			method: 'POST',
			headers: csrfHeaders({ 'Content-Type': 'application/json' }),
			body: JSON.stringify({ postId, type, ...extra })
		})
	} catch (e) { console.error('Engagement error:', e) }
}
async function toggleLike(postId, btn) {
	const liked = btn.classList.contains('liked')
	const el = btn.querySelector('.like-count')
	const count = parseInt(el?.textContent) || 0
	btn.classList.toggle('liked'); btn.dataset.liked = (!liked).toString()
	if (el) el.textContent = liked ? Math.max(0, count - 1) : count + 1
	await sendEngagement(postId, 'LIKE')
}
async function toggleFollow(userId, btn) {
	const currentStatus = btn.dataset.followStatus || (btn.classList.contains('following') ? 'FOLLOWING' : 'NONE')
	const shouldUnfollow = currentStatus === 'FOLLOWING' || currentStatus === 'REQUESTED'
	btn.disabled = true
	try {
		const res = await fetch(shouldUnfollow ? `/api/unfollow/${userId}` : `/api/follow/${userId}`,
			{ method: 'POST', headers: csrfHeaders() })
		if (!res.ok) throw new Error('Failed')
		const data = await res.json().catch(() => ({}))
		const nextStatus = shouldUnfollow ? 'NONE' : (data.status || 'FOLLOWING')
		btn.dataset.followStatus = nextStatus
		btn.classList.toggle('following', nextStatus === 'FOLLOWING')
		btn.textContent = nextStatus === 'REQUESTED' ? 'Requested' : (nextStatus === 'FOLLOWING' ? '✓ Following' : '+ Follow')
	} catch (e) { showToast('Follow fail. Retry karo.') }
	finally { btn.disabled = false }
}
async function toggleBookmark(postId, btn) {
	const saved = btn.classList.contains('saved')
	btn.classList.toggle('saved')
	const icon = btn.querySelector('.btn-icon'), label = btn.querySelector('.btn-label')
	if (icon) icon.textContent = saved ? '🔖' : '✅'
	if (label) label.textContent = saved ? 'Save' : 'Saved'
	try {
		const res = await fetch(`/api/bookmarks/${postId}`, { method: 'POST', headers: csrfHeaders() })
		if (!res.ok) throw new Error('Bookmark failed')
		const data = await res.json().catch(() => ({}))
		const nowSaved = Boolean(data.saved)
		btn.classList.toggle('saved', nowSaved)
		if (icon) icon.textContent = nowSaved ? '✅' : '🔖'
		if (label) label.textContent = nowSaved ? 'Saved' : 'Save'
		showToast(nowSaved ? '🔖 Saved!' : 'Bookmark remove kiya')
	} catch (e) {
		btn.classList.toggle('saved')
		if (icon) icon.textContent = saved ? '✅' : '🔖'
		if (label) label.textContent = saved ? 'Saved' : 'Save'
	}
}
async function sharePost(postId) {
	await sendEngagement(postId, 'SHARE')
	const url = `${location.origin}/reel/${postId}`
	if (navigator.share) navigator.share({ url }).catch(() => { })
	else navigator.clipboard?.writeText(url)
		.then(() => showToast('Link copy ho gaya! 🔗'))
		.catch(() => showToast('Link: ' + url))
}

/* ── Comments ── */
function openComments(postId) {
	activePostId = postId
	document.getElementById('commentsPanel')?.classList.add('open')
	document.getElementById('backdrop')?.classList.add('open')
	loadComments(postId)
}
function closeComments() {
	document.getElementById('commentsPanel')?.classList.remove('open')
	document.getElementById('backdrop')?.classList.remove('open')
	const ci = document.getElementById('commentInput')
	if (ci) ci.value = ''
	activePostId = null
}
async function loadComments(postId) {
	const list = document.getElementById('commentsList')
	if (!list) return
	list.textContent = ''
	const p = document.createElement('p'); p.className = 'comment-empty'; p.textContent = 'Loading...'
	list.appendChild(p)
	try {
		const res = await fetch(`/api/comments/${postId}`)
		const data = await res.json()
		list.textContent = ''
		if (!data?.length) {
			const np = document.createElement('p'); np.className = 'comment-empty'; np.textContent = 'Koi comment nahi abhi 💬'
			list.appendChild(np); return
		}
		data.forEach(c => list.appendChild(buildCommentEl(c)))
	} catch {
		list.textContent = ''
		const ep = document.createElement('p'); ep.className = 'comment-empty'; ep.style.color = '#ff4444'; ep.textContent = 'Comments load nahi hue.'
		list.appendChild(ep)
	}
}
function buildCommentEl(c) {
	const div = document.createElement('div'); div.className = 'comment-item'
	const av = document.createElement('div'); av.className = 'comment-avatar'
	if (c.userAvatar) {
		const img = document.createElement('img')
		img.src = c.userAvatar; img.style.cssText = 'width:34px;height:34px;border-radius:50%;object-fit:cover;'
		img.onerror = () => { av.textContent = '👤' }; av.appendChild(img)
	} else { av.textContent = '👤' }
	const body = document.createElement('div')
	const nm = document.createElement('div'); nm.className = 'comment-name'; nm.textContent = c.userName || 'User'
	const tx = document.createElement('div'); tx.className = 'comment-text'; tx.textContent = c.text || ''
	const tm = document.createElement('div'); tm.className = 'comment-time'; tm.textContent = formatTime(c.createdAt)
	body.append(nm, tx, tm); div.append(av, body)
	return div
}
async function submitComment() {
	const input = document.getElementById('commentInput')
	const text = input?.value.trim()
	if (!text || !activePostId) return
	const btn = document.getElementById('sendCommentBtn')
	if (btn) { btn.disabled = true; btn.textContent = 'Sending...' }
	try {
		const res = await fetch(`/api/comments/${activePostId}`, {
			method: 'POST',
			headers: csrfHeaders({ 'Content-Type': 'application/json' }),
			body: JSON.stringify({ text })
		})
		if (!res.ok) throw new Error('Comment failed')
		if (input) input.value = ''
		await loadComments(activePostId)
		await sendEngagement(activePostId, 'COMMENT', { commentText: text })
	} catch (e) {
		showToast('Comment send fail. Retry karo.')
	} finally {
		if (btn) { btn.disabled = false; btn.textContent = 'Send' }
	}
}

/* ── Utils ── */
function formatTime(dateStr) {
	if (!dateStr) return ''
	const diff = Date.now() - new Date(dateStr).getTime()
	const mins = Math.floor(diff / 60000)
	if (mins < 1) return 'abhi'
	if (mins < 60) return `${mins}m`
	const hrs = Math.floor(mins / 60)
	if (hrs < 24) return `${hrs}h`
	return `${Math.floor(hrs / 24)}d`
}
function showToast(msg) {
	document.getElementById('__toast')?.remove()
	const t = document.createElement('div'); t.id = '__toast'
	t.textContent = msg
	t.style.cssText = 'position:fixed;bottom:30px;left:50%;transform:translateX(-50%);background:#222;color:#fff;padding:10px 22px;border-radius:8px;font-size:14px;z-index:9999;pointer-events:none;'
	document.body.appendChild(t)
	setTimeout(() => t.remove(), 3000)
}


async function reportPost(postId) {

	const reason = prompt("Reason likho (SPAM / HATE / VIOLENCE / etc)")

	if (!reason) return
	try {
		const res = await fetch(`/api/report/post/${postId}`, {
			method: 'POST',
			headers: csrfHeaders({
				'Content-Type': 'application/json'
			}),
			body: JSON.stringify({
				reason: reason.toUpperCase(),
				description: "Reported from feed"
			})
		})
		const data = await res.json()
		if (data.success) {
			showToast("🚨 Report submitted")
		} else {
			showToast(data.message || "Already reported ⚠️")
		}

	} catch (e) {
		console.error(e)
		showToast("Report failed ❌")
	}
}
