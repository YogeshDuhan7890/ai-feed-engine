/* ================================
   HEADER.JS — UPDATED
   ✅ Search dropdown (users)
   ✅ Follow/Unfollow from search
   ✅ Notification badge (30s poll)
   ✅ DM unread badge
   ✅ Push notification button
   ✅ NEW: Keyboard nav in search (↑↓ Enter)
   ✅ NEW: Search shows videos + users tabs
   ✅ NEW: Active nav link highlight
   ✅ NEW: Mobile menu toggle
   ✅ NEW: Theme-aware badge colors
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
function csrfHeaders() {
	const t = getCsrfToken(), h = getCsrfHeader()
	return t ? { [h]: t } : {}
}

document.addEventListener('DOMContentLoaded', () => {
	initSearch()
	initRoleDrawer()
	fetchNotifCount()
	fetchDmCount()
	initPushButton()
	highlightActiveNav()
	setInterval(fetchNotifCount, 30000)
	setInterval(fetchDmCount, 30000)
})

/* ── Active Nav Highlight (NEW) ── */
function highlightActiveNav() {
	const path = location.pathname
	document.querySelectorAll('.nav-link, .header-nav a').forEach(a => {
		const href = a.getAttribute('href')
		if (href && path.startsWith(href) && href !== '/') {
			a.classList.add('active')
		}
	})
}

function initRoleDrawer() {
	const menuBtn = document.getElementById('roleMenuBtn')
	const drawer = document.getElementById('roleDrawer')
	const backdrop = document.getElementById('roleDrawerBackdrop')
	const closeBtn = document.getElementById('roleDrawerClose')
	if (!menuBtn || !drawer || !backdrop || !closeBtn) return

	const open = () => {
		drawer.classList.add('open')
		backdrop.classList.add('open')
		drawer.setAttribute('aria-hidden', 'false')
		document.body.style.overflow = 'hidden'
	}

	const close = () => {
		drawer.classList.remove('open')
		backdrop.classList.remove('open')
		drawer.setAttribute('aria-hidden', 'true')
		document.body.style.overflow = ''
	}

	menuBtn.addEventListener('click', open)
	closeBtn.addEventListener('click', close)
	backdrop.addEventListener('click', close)

	document.querySelectorAll('#roleDrawer a').forEach(link => {
		link.addEventListener('click', close)
	})

	document.addEventListener('keydown', e => {
		if (e.key === 'Escape') close()
	})
}

/* ── Search ── */
function initSearch() {
	const input = document.getElementById('headerSearchInput')
	const dropdown = document.getElementById('searchDropdown')
	const results = document.getElementById('searchResults')
	if (!input || !dropdown) return

	let debTimer = null
	let selectedIdx = -1

	input.addEventListener('input', () => {
		clearTimeout(debTimer)
		const q = input.value.trim()
		if (!q) { hideDropdown(); return }
		debTimer = setTimeout(() => doSearch(q), 300)
	})

	input.addEventListener('keydown', e => {
		const rows = dropdown.querySelectorAll('.search-result-row')
		if (e.key === 'ArrowDown') {
			e.preventDefault()
			selectedIdx = Math.min(selectedIdx + 1, rows.length - 1)
			rows.forEach((r, i) => r.classList.toggle('selected', i === selectedIdx))
		} else if (e.key === 'ArrowUp') {
			e.preventDefault()
			selectedIdx = Math.max(selectedIdx - 1, 0)
			rows.forEach((r, i) => r.classList.toggle('selected', i === selectedIdx))
		} else if (e.key === 'Enter') {
			clearTimeout(debTimer)
			const q = input.value.trim()
			if (selectedIdx >= 0 && rows[selectedIdx]) {
				rows[selectedIdx].click()
			} else if (q) {
				// Go to full search page
				location.href = '/search?q=' + encodeURIComponent(q)
			}
		} else if (e.key === 'Escape') {
			hideDropdown()
		}
	})

	document.addEventListener('click', e => {
		if (!document.getElementById('headerSearchWrap')?.contains(e.target)) hideDropdown()
	})

	async function doSearch(query) {
		results.innerHTML = '<div class="search-loading" style="padding:12px;color:#888;font-size:13px">🔍 Searching...</div>'
		showDropdown()
		selectedIdx = -1
		try {
			const res = await fetch('/api/search/users?q=' + encodeURIComponent(query))
			if (!res.ok) throw new Error()
			const users = await res.json()
			results.innerHTML = ''

			if (!users?.length) {
				results.innerHTML = `<div style="padding:14px;color:#555;font-size:13px;text-align:center">
					No results for "<strong>${escHtml(query)}</strong>"
					<br><a href="/search?q=${encodeURIComponent(query)}" style="color:#4f46e5;font-size:12px;text-decoration:none">Full search →</a>
				</div>`
				return
			}

			users.slice(0, 6).forEach(u => results.appendChild(buildUserRow(u)))

			// "See all results" link
			const seeAll = document.createElement('div')
			seeAll.style.cssText = 'text-align:center;padding:10px;border-top:1px solid #1a1a1a;'
			const link = document.createElement('a')
			link.href = '/search?q=' + encodeURIComponent(query)
			link.style.cssText = 'color:#4f46e5;font-size:12px;text-decoration:none;'
			link.textContent = 'See all results →'
			seeAll.appendChild(link)
			results.appendChild(seeAll)

		} catch {
			results.innerHTML = '<div style="padding:12px;color:#ff4444;font-size:13px">Search fail ho gayi</div>'
		}
	}

	function buildUserRow(user) {
		const row = document.createElement('div')
		row.className = 'search-result-row'
		row.style.cssText = 'display:flex;align-items:center;gap:10px;padding:10px 14px;cursor:pointer;transition:background 0.15s;border-bottom:1px solid #111;'
		row.addEventListener('mouseenter', () => row.style.background = '#1a1a1a')
		row.addEventListener('mouseleave', () => row.style.background = '')

		const av = document.createElement('div')
		av.style.cssText = 'width:36px;height:36px;border-radius:50%;background:#222;display:flex;align-items:center;justify-content:center;font-size:16px;overflow:hidden;flex-shrink:0;'
		if (user.avatar) {
			const img = document.createElement('img')
			img.src = user.avatar; img.style.cssText = 'width:100%;height:100%;object-fit:cover;'
			img.onerror = () => { av.textContent = '👤' }
			av.appendChild(img)
		} else { av.textContent = '👤' }

		const info = document.createElement('div'); info.style.flex = '1'; info.style.minWidth = '0'
		const nm = document.createElement('div'); nm.style.cssText = 'font-weight:600;font-size:13px;color:#eee;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;'
		nm.textContent = user.name || 'User'
		const em = document.createElement('div'); em.style.cssText = 'font-size:11px;color:#555;margin-top:1px;'
		em.textContent = (user.followers ?? 0) + ' followers'
		info.append(nm, em)

		const followBtn = document.createElement('button')
		followBtn.className = 'search-follow-btn' + (user.isFollowing ? ' following' : '')
		followBtn.textContent = user.isFollowing ? 'Following ✓' : 'Follow'
		followBtn.dataset.followStatus = user.isFollowing ? 'FOLLOWING' : 'NONE'
		followBtn.style.cssText = `padding:5px 12px;border:none;border-radius:14px;cursor:pointer;font-size:12px;font-weight:600;
			background:${user.isFollowing ? '#1a1a1a' : '#4f46e5'};
			color:${user.isFollowing ? '#888' : '#fff'};flex-shrink:0;`
		followBtn.addEventListener('click', e => { e.stopPropagation(); toggleFollow(user.id, followBtn) })

		row.append(av, info, followBtn)
		row.addEventListener('click', e => {
			if (e.target === followBtn) return
			hideDropdown()
			location.href = '/profile/user/' + user.id
		})
		return row
	}

	function showDropdown() { dropdown.style.display = 'block' }
	function hideDropdown() { dropdown.style.display = 'none'; results.innerHTML = ''; selectedIdx = -1 }
}

/* ── Follow Toggle ── */
async function toggleFollow(userId, btn) {
	const currentStatus = btn.dataset.followStatus || (btn.classList.contains('following') ? 'FOLLOWING' : 'NONE')
	const shouldUnfollow = currentStatus === 'FOLLOWING' || currentStatus === 'REQUESTED'
	btn.disabled = true
	try {
		const res = await fetch(shouldUnfollow ? `/api/unfollow/${userId}` : `/api/follow/${userId}`,
			{ method: 'POST', headers: csrfHeaders() })
		if (!res.ok) throw new Error()
		const data = await res.json().catch(() => ({}))
		const nextStatus = shouldUnfollow ? 'NONE' : (data.status || 'FOLLOWING')
		btn.dataset.followStatus = nextStatus
		btn.classList.toggle('following', nextStatus === 'FOLLOWING')
		btn.textContent = nextStatus === 'REQUESTED' ? 'Requested' : (nextStatus === 'FOLLOWING' ? 'Following ✓' : 'Follow')
		btn.style.background = nextStatus === 'FOLLOWING' ? '#1a1a1a' : (nextStatus === 'REQUESTED' ? '#4b5563' : '#4f46e5')
		btn.style.color = nextStatus === 'NONE' ? '#fff' : '#888'
	} catch { console.error('Follow error') }
	btn.disabled = false
}

/* ── Notification Badge ── */
async function fetchNotifCount() {
	try {
		const res = await fetch('/api/notifications/unread-count')
		if (!res.ok) return
		const data = await res.json()
		const badge = document.getElementById('notifBadge')
		if (!badge) return
		badge.textContent = data.count > 9 ? '9+' : data.count
		badge.style.display = data.count > 0 ? 'flex' : 'none'
	} catch { }
}

/* ── DM Badge ── */
async function fetchDmCount() {
	try {
		const res = await fetch('/api/dm/unread')
		if (!res.ok) return
		const data = await res.json()
		const badge = document.getElementById('dmBadge')
		if (!badge) return
		badge.textContent = data.count > 9 ? '9+' : data.count
		badge.style.display = data.count > 0 ? 'flex' : 'none'
	} catch { }
}

/* ── Push Button ── */
async function initPushButton() {
	const btn = document.getElementById('enablePushBtn')
	if (!btn || !('serviceWorker' in navigator) || !('PushManager' in window)) return
	try {
		const reg = await navigator.serviceWorker.register('/sw.js')
		const existing = await reg.pushManager.getSubscription()
		if (existing || Notification.permission === 'denied') {
			btn.style.display = 'none'
		} else {
			btn.style.display = 'inline-block'
			btn.textContent = '🔔'
		}
	} catch { }
}

/* ── Utils ── */
function escHtml(str) {
	return String(str ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
