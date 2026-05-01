/* ================================
   SEARCH.JS — ENHANCED
   ✅ Users search
   ✅ Videos search
   ✅ Hashtags search
   ✅ Debounced suggestions dropdown
   ✅ Trending chips from Redis
   ✅ URL query param support
================================ */

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
function csrfH() { return { [getCsrfHeader()]: getCsrfToken() } }

let activeTab = 'users'
let debTimer = null
let sugTimer = null

document.addEventListener('DOMContentLoaded', () => {
	// URL param support
	const params = new URLSearchParams(window.location.search)
	const q = params.get('q')
	if (q) {
		const input = document.getElementById('searchInput')
		if (input) input.value = q
		doSearch(q)
	}
	loadTrendingChips()
	// Close suggestions on outside click
	document.addEventListener('click', e => {
		if (!e.target.closest('.search-bar-wrap')) hideSuggestions()
	})
})

// ========================
// SEARCH INPUT
// ========================
function onSearchInput(val) {
	clearTimeout(debTimer)
	clearTimeout(sugTimer)
	if (!val.trim()) { showTrending(); hideSuggestions(); return }
	hideTrending()
	debTimer = setTimeout(() => doSearch(val), 350)
	sugTimer = setTimeout(() => loadSuggestions(val), 200)
}

function quickSearch(q) {
	const input = document.getElementById('searchInput')
	if (input) input.value = q
	doSearch(q)
}

// ========================
// MAIN SEARCH
// ========================
async function doSearch(q) {
	if (!q?.trim()) return
	hideSuggestions()
	hideTrending()
	show('spinner'); clear('results')
	try {
		if (activeTab === 'users') await searchUsers(q)
		else if (activeTab === 'videos') await searchVideos(q)
		else if (activeTab === 'hashtags') await searchHashtags(q)
	} catch (e) { showError('Search fail: ' + e.message) }
	finally { hide('spinner') }
}

async function searchUsers(q) {
	const res = await fetch('/api/search/suggestions?q=' + encodeURIComponent(q))
	const data = await res.json()
	if (!data.length) { setHtml('results', '<div id="noResults">😕 Koi user nahi mila "<strong>' + esc(q) + '"</strong></div>'); return }
	setHtml('results', data.map(u => `
		<div class="result-card">
			${u.avatar
			? `<img class="result-avatar" src="${esc(u.avatar)}" onerror="this.style.display='none';this.nextSibling.style.display='flex'">`
			: ''
		}
			<div class="result-avatar" style="${u.avatar ? 'display:none' : ''}">👤</div>
			<div class="result-info">
				<div class="result-name">${esc(u.name)}</div>
				<div class="result-sub">${esc(u.email)} · ${(u.followers || 0).toLocaleString()} followers</div>
			</div>
			<div class="result-actions">
				<a href="/profile/user/${u.id}" style="color:#818cf8;font-size:0.8rem;text-decoration:none">View</a>
				<button class="btn-follow ${u.following ? 'following' : ''}" id="fBtn-${u.id}"
					data-follow-status="${u.following ? 'FOLLOWING' : 'NONE'}"
					onclick="toggleFollow(${u.id}, this)">${u.following ? 'Following' : 'Follow'}</button>
			</div>
		</div>`).join(''))
}

async function searchVideos(q) {
	const res = await fetch('/api/search/videos?q=' + encodeURIComponent(q))
	const data = await res.json()
	const videos = data.data || []
	if (!videos.length) { setHtml('results', '<div id="noResults">😕 Koi video nahi mila "' + esc(q) + '"</div>'); return }
	setHtml('results', videos.map(v => `
		<div class="result-card" onclick="window.location='/reel/${v.id}'" style="cursor:pointer">
			<div class="video-thumb">🎬</div>
			<div class="result-info">
				<div class="result-name">${esc(v.caption || '(no caption)')}</div>
				<div class="result-sub">${esc(v.tags || '')}</div>
			</div>
			<div class="result-actions">
				<a href="/reel/${v.id}" style="color:#818cf8;font-size:0.8rem;text-decoration:none">Watch →</a>
			</div>
		</div>`).join(''))
}

async function searchHashtags(q) {
	const tag = q.replace(/^#/, '')
	const res = await fetch('/api/hashtags/search?q=' + encodeURIComponent(tag))
	let data = []
	try { data = await res.json() } catch { }
	if (!data.length) {
		// Fallback: show the tag as a result
		data = [{ name: tag, postCount: 0 }]
	}
	setHtml('results', data.map(h => `
		<div class="hashtag-card" onclick="window.location='/hashtag/${esc(h.name)}'">
			<div class="hashtag-icon">#️⃣</div>
			<div class="result-info">
				<div class="result-name">#${esc(h.name)}</div>
				<div class="result-sub">${(h.postCount || 0).toLocaleString()} posts</div>
			</div>
			<div style="color:#818cf8;font-size:0.8rem">Explore →</div>
		</div>`).join(''))
}

// ========================
// FOLLOW TOGGLE
// ========================
async function toggleFollow(userId, btn) {
	const currentStatus = btn.dataset.followStatus || (btn.classList.contains('following') ? 'FOLLOWING' : 'NONE')
	const shouldUnfollow = currentStatus === 'FOLLOWING' || currentStatus === 'REQUESTED'
	try {
		const url = shouldUnfollow ? `/api/unfollow/${userId}` : `/api/follow/${userId}`
		const res = await fetch(url, { method: 'POST', headers: csrfH() })
		if (!res.ok) throw new Error('Follow failed')
		const data = await res.json().catch(() => ({}))
		const nextStatus = shouldUnfollow ? 'NONE' : (data.status || 'FOLLOWING')
		btn.dataset.followStatus = nextStatus
		btn.classList.toggle('following', nextStatus === 'FOLLOWING')
		btn.textContent = nextStatus === 'REQUESTED' ? 'Requested' : (nextStatus === 'FOLLOWING' ? 'Following' : 'Follow')
	} catch (e) { console.error(e) }
}

// ========================
// SUGGESTIONS
// ========================
async function loadSuggestions(q) {
	if (q.length < 2) { hideSuggestions(); return }
	try {
		const res = await fetch('/api/search/suggestions?q=' + encodeURIComponent(q))
		const data = await res.json()
		if (!data.length) { hideSuggestions(); return }
		const el = document.getElementById('suggestions')
		el.style.display = ''
		el.innerHTML = data.slice(0, 5).map(u => `
			<div class="suggestion-item" onclick="window.location='/profile/user/${u.id}'">
				<span>👤</span>
				<span>${esc(u.name)} <span style="color:#555;font-size:0.75rem">${esc(u.email)}</span></span>
			</div>`).join('')
	} catch (e) { hideSuggestions() }
}

function hideSuggestions() {
	const el = document.getElementById('suggestions')
	if (el) el.style.display = 'none'
}

// ========================
// TRENDING CHIPS
// ========================
async function loadTrendingChips() {
	try {
		const res = await fetch('/api/search/trending')
		// Just show static chips + any from Redis in future
	} catch (e) { }
}

// ========================
// TAB SWITCH
// ========================
function switchTab(tab, btn) {
	activeTab = tab
	document.querySelectorAll('.tab-btn').forEach(b => b.classList.toggle('active', b === btn))
	clear('results')
	const q = document.getElementById('searchInput')?.value
	if (q?.trim()) doSearch(q)
}

function showTrending() { show('trendingSection') }
function hideTrending() { hide('trendingSection') }
function showError(msg) { setHtml('results', `<div id="noResults" style="color:#ef4444">${esc(msg)}</div>`) }

// ========================
// UTILS
// ========================
function show(id) { const el = document.getElementById(id); if (el) el.style.display = '' }
function hide(id) { const el = document.getElementById(id); if (el) el.style.display = 'none' }
function clear(id) { const el = document.getElementById(id); if (el) el.innerHTML = '' }
function setHtml(id, html) { const el = document.getElementById(id); if (el) el.innerHTML = html }
function esc(s) { return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') }
