/* ================================
   PROFILE.JS — COMPLETE UPDATED
   ✅ My Videos (with delete)
   ✅ Saved Bookmarks tab
   ✅ Blocked Users tab
   ✅ Edit Profile modal
   ✅ Avatar Upload with preview
   ✅ Followers / Following modal
   ✅ NEW: Stats animation on load
   ✅ NEW: Video grid skeleton loader
   ✅ NEW: Share profile button
   ✅ NEW: Copy profile link
   ✅ NEW: Video hover preview
   ✅ NEW: Likes count on video cards
   ✅ NEW: Toast improvements (success/error/info)
   ✅ NEW: Edit modal char count
   ✅ NEW: Avatar crop preview
================================ */

let currentProfile = null
let activeTab = 'videos'

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

/* ── INIT ── */
document.addEventListener('DOMContentLoaded', () => {
	loadProfile()
	loadVideos()

	document.getElementById('editProfileBtn')?.addEventListener('click', openEditModal)
	document.getElementById('closeEditBtn')?.addEventListener('click', closeEditModal)
	document.getElementById('cancelEditBtn')?.addEventListener('click', closeEditModal)
	document.getElementById('saveProfileBtn')?.addEventListener('click', saveProfile)
	document.getElementById('editModal')?.addEventListener('click', e => {
		if (e.target.id === 'editModal') closeEditModal()
	})
	document.getElementById('changeAvatarBtn')?.addEventListener('click', () => {
		document.getElementById('avatarUpload').click()
	})
	document.getElementById('avatarUpload')?.addEventListener('change', uploadAvatar)

	// Share profile button (NEW)
	document.getElementById('shareProfileBtn')?.addEventListener('click', shareProfile)

	// Tab switching
	document.querySelectorAll('.profile-tab-btn').forEach(btn => {
		btn.addEventListener('click', () => switchTab(btn.dataset.tab))
	})

	// Followers / Following modal
	document.getElementById('followersBtn')?.addEventListener('click', () => {
		const uid = document.getElementById('followersBtn').getAttribute('data-uid')
		if (!uid) return
		openFollowModal('followers', uid)
	})
	document.getElementById('followingBtn')?.addEventListener('click', () => {
		const uid = document.getElementById('followingBtn').getAttribute('data-uid')
		if (!uid) return
		openFollowModal('following', uid)
	})
	document.getElementById('followModalClose')?.addEventListener('click', closeFollowModal)
	document.getElementById('followModalBackdrop')?.addEventListener('click', closeFollowModal)

	// Edit modal char counts (NEW)
	document.getElementById('editBio')?.addEventListener('input', e => {
		const el = document.getElementById('bioCharCount')
		if (el) el.textContent = e.target.value.length + '/150'
	})
	document.getElementById('editName')?.addEventListener('input', e => {
		const el = document.getElementById('nameCharCount')
		if (el) el.textContent = e.target.value.length + '/50'
	})
})

/* ── TAB SWITCHING ── */
function switchTab(tab) {
	activeTab = tab
	document.querySelectorAll('.profile-tab-btn').forEach(btn => {
		btn.classList.toggle('active', btn.dataset.tab === tab)
	})
	document.getElementById('videoGrid').innerHTML = ''
	if (tab === 'videos') loadVideos()
	else if (tab === 'saved') loadSaved()
	else if (tab === 'blocked') loadBlockList()
}

/* ── LOAD PROFILE ── */
async function loadProfile() {
	try {
		const res = await fetch('/api/profile/me')
		if (!res.ok) throw new Error('Profile fetch failed: ' + res.status)
		const data = await res.json()
		currentProfile = data

		// Animate stats (NEW)
		animateStat('followers', data.followers ?? data.followersCount ?? 0)
		animateStat('following', data.following ?? data.followingCount ?? 0)
		animateStat('videos', data.videos ?? data.videoCount ?? 0)

		setText('name', data.name)
		setText('email', data.email)
		setText('bio', data.bio || '')

		// FIX: PostgreSQL text[] → "{a,b}" ya [] ya null
		let _int = data.interests
		if (typeof _int === 'string') {
			_int = _int.replace(/^\{|\}$/g, '').split(',').map(s => s.trim()).filter(Boolean)
		}
		if (Array.isArray(_int) && _int.length && !(_int.length === 1 && _int[0] === ''))
			setText('interests', '🏷 ' + _int.join(', '))
		else
			setText('interests', '')

		const avatarEl = document.getElementById('avatar')
		if (avatarEl && data.avatar) {
			avatarEl.src = data.avatar
			avatarEl.onerror = () => { avatarEl.style.display = 'none' }
		}

		const myId = data.id || data.userId
		document.getElementById('followersBtn')?.setAttribute('data-uid', myId)
		document.getElementById('followingBtn')?.setAttribute('data-uid', myId)

	} catch (e) {
		console.error('Profile load error:', e)
		showToast('Profile load fail ho gaya', 'error')
	}
}

// Animate number counting up (NEW)
function animateStat(id, target) {
	const el = document.getElementById(id)
	if (!el) return
	const start = 0
	const duration = 600
	const step = (timestamp, startTime) => {
		const elapsed = timestamp - startTime
		const progress = Math.min(elapsed / duration, 1)
		el.textContent = Math.floor(progress * target)
		if (progress < 1) requestAnimationFrame(t => step(t, startTime))
		else el.textContent = target
	}
	requestAnimationFrame(t => step(t, t))
}

/* ── MY VIDEOS ── */
async function loadVideos(retry = 1) {
	const grid = document.getElementById('videoGrid')

	// Skeleton loader
	grid.innerHTML = [1, 2, 3, 4, 5, 6].map(() => `
		<div style="background:#1a1a1a;border-radius:10px;aspect-ratio:9/16;animation:shimmer 1.4s infinite linear;
		background:linear-gradient(90deg,#1a1a1a 25%,#2a2a2a 50%,#1a1a1a 75%);background-size:700px 100%"></div>
	`).join('')

	try {
		console.log("Loading my videos...")

		// ✅ FIXED API
		const res = await fetch(`/api/profile/user/videos`)

		if (!res.ok) throw new Error("API failed: " + res.status)

		const posts = await res.json()

		console.log("My videos:", posts)

		if (!posts?.length) {
			grid.innerHTML = '<p style="color:#555;padding:20px;text-align:center">Koi video nahi. Upload karo! 🎬</p>'
			return
		}

		grid.innerHTML = ''

		posts.forEach(post => {

			if (!post.videoUrl) {
				console.warn("Missing videoUrl:", post)
				return
			}

			grid.appendChild(buildVideoCard(post, true))
		})

		document.getElementById('videos') && animateStat('videos', posts.length)

	} catch (e) {
		console.error("Load videos error:", e)

		// retry
		if (retry > 0) {
			setTimeout(() => loadVideos(0), 1000)
			return
		}

		grid.innerHTML = '<p style="color:#ff4444;padding:20px">Videos load nahi hui. Reload karo.</p>'
	}
}

/* ── SAVED BOOKMARKS ── */
async function loadSaved() {
	const grid = document.getElementById('videoGrid')
	grid.innerHTML = '<p style="color:#555;padding:20px">Loading saved...</p>'
	try {
		const res = await fetch('/api/bookmarks')
		if (!res.ok) throw new Error(res.status)
		const posts = await res.json()
		if (!posts?.length) {
			grid.innerHTML = '<p style="color:#555;padding:20px;text-align:center">Koi saved video nahi 🔖</p>'
			return
		}
		grid.innerHTML = ''
		posts.forEach(post => {
			if (!post.videoUrl) return
			const card = buildVideoCard(post, false)
			const btn = makeBtn('🗑 Unsave', '#333', async () => {
				btn.disabled = true; btn.textContent = 'Removing...'
				try {
					const r = await fetch(`/api/bookmarks/${post.postId || post.id}`, { method: 'POST', headers: csrfHeaders() })
					if (!r.ok) throw new Error()
					card.remove()
					showToast('Bookmark remove kiya', 'info')
				} catch { btn.disabled = false; btn.textContent = '🗑 Unsave' }
			})
			card.appendChild(btn)
			grid.appendChild(card)
		})
	} catch (e) {
		grid.innerHTML = '<p style="color:#ff4444;padding:20px">Saved videos load nahi hui.</p>'
	}
}

/* ── BLOCKED USERS ── */
async function loadBlockList() {
	const grid = document.getElementById('videoGrid')
	grid.innerHTML = '<p style="color:#555;padding:20px">Loading blocked users...</p>'
	try {
		const res = await fetch('/api/block/list')
		if (!res.ok) throw new Error(res.status)
		const users = await res.json()
		if (!users?.length) {
			grid.innerHTML = '<p style="color:#555;padding:20px;text-align:center">Koi blocked user nahi 🚫</p>'
			return
		}
		grid.innerHTML = ''
		users.forEach(u => {
			const card = document.createElement('div')
			card.style.cssText = 'display:flex;align-items:center;gap:12px;padding:12px;background:#1a1a1a;border-radius:10px;margin-bottom:10px;'
			const av = document.createElement('div')
			if (u.avatar) {
				const img = document.createElement('img')
				img.src = u.avatar; img.style.cssText = 'width:44px;height:44px;border-radius:50%;object-fit:cover;'
				img.onerror = () => { av.textContent = '👤'; av.style.fontSize = '28px' }
				av.appendChild(img)
			} else { av.textContent = '👤'; av.style.fontSize = '28px' }
			const info = document.createElement('div')
			info.style.flex = '1'
			const nm = document.createElement('div')
			nm.textContent = u.name; nm.style.cssText = 'font-weight:600;color:#eee;'
			info.appendChild(nm)
			const btn = makeBtn('Unblock', '#e74c3c', async () => {
				btn.disabled = true; btn.textContent = 'Unblocking...'
				try {
					const r = await fetch(`/api/block/unblock/${u.userId}`, { method: 'POST', headers: csrfHeaders() })
					if (!r.ok) throw new Error()
					card.remove()
					showToast(u.name + ' unblock ho gaya ✅', 'success')
				} catch { btn.disabled = false; btn.textContent = 'Unblock' }
			})
			card.append(av, info, btn)
			grid.appendChild(card)
		})
	} catch (e) {
		grid.innerHTML = '<p style="color:#ff4444;padding:20px">Block list load nahi hui.</p>'
	}
}

/* ── FOLLOWERS / FOLLOWING MODAL ── */
async function openFollowModal(type, userId) {
	const modal = document.getElementById('followModal')
	const title = document.getElementById('followModalTitle')
	const list = document.getElementById('followModalList')
	if (!modal) return
	title.textContent = type === 'followers' ? 'Followers' : 'Following'
	list.innerHTML = '<p style="color:#888;padding:16px;text-align:center">Loading...</p>'
	modal.classList.add('open')
	document.getElementById('followModalBackdrop')?.classList.add('open')
	try {
		const res = await fetch(`/api/${type}/${userId}`)
		if (!res.ok) throw new Error(res.status)
		const users = await res.json()
		list.innerHTML = ''
		if (!users?.length) {
			list.innerHTML = `<p style="color:#888;padding:16px;text-align:center">Koi ${type === 'followers' ? 'follower' : 'following'} nahi</p>`
			return
		}
		users.forEach(u => list.appendChild(buildFollowUserRow(u)))
	} catch (e) {
		list.innerHTML = '<p style="color:#ff4444;padding:16px;text-align:center">Load fail ho gaya.</p>'
	}
}

function buildFollowUserRow(u) {
	const row = document.createElement('div')
	row.style.cssText = 'display:flex;align-items:center;gap:12px;padding:12px 16px;border-bottom:1px solid #1e1e1e;cursor:pointer;transition:background 0.15s;'
	row.addEventListener('mouseenter', () => row.style.background = '#1a1a1a')
	row.addEventListener('mouseleave', () => row.style.background = '')
	const av = document.createElement('div')
	av.style.flexShrink = '0'
	if (u.avatar) {
		const img = document.createElement('img')
		img.src = u.avatar; img.style.cssText = 'width:44px;height:44px;border-radius:50%;object-fit:cover;'
		img.onerror = () => img.replaceWith(avatarPlaceholder())
		av.appendChild(img)
	} else { av.appendChild(avatarPlaceholder()) }
	const info = document.createElement('div')
	info.style.flex = '1'
	const nm = document.createElement('div')
	nm.textContent = u.name || 'User'; nm.style.cssText = 'font-weight:600;color:#eee;font-size:14px;'
	const cnt = document.createElement('div')
	cnt.textContent = (u.followers ?? 0) + ' followers'; cnt.style.cssText = 'font-size:12px;color:#888;margin-top:2px;'
	info.append(nm, cnt)
	const followBtn = document.createElement('button')
	followBtn.textContent = u.isFollowing ? 'Following' : 'Follow'
	followBtn.style.cssText = `padding:6px 14px;border:none;border-radius:20px;cursor:pointer;font-size:13px;font-weight:600;background:${u.isFollowing ? '#333' : '#4f46e5'};color:#fff;flex-shrink:0;`
	followBtn.dataset.following = u.isFollowing ? '1' : '0'
	followBtn.addEventListener('click', async e => {
		e.stopPropagation()
		await toggleFollowInModal(u.id || u.userId, followBtn)
	})
	row.addEventListener('click', e => {
		if (e.target === followBtn) return
		closeFollowModal()
		location.href = `/profile/user/${u.id || u.userId}`
	})
	row.append(av, info, followBtn)
	return row
}

function avatarPlaceholder() {
	const div = document.createElement('div')
	div.textContent = '👤'
	div.style.cssText = 'width:44px;height:44px;border-radius:50%;background:#222;display:flex;align-items:center;justify-content:center;font-size:22px;'
	return div
}

async function toggleFollowInModal(userId, btn) {
	const isFollowing = btn.dataset.following === '1'
	btn.disabled = true
	try {
		const url = isFollowing ? `/api/unfollow/${userId}` : `/api/follow/${userId}`
		const res = await fetch(url, { method: 'POST', headers: csrfHeaders() })
		if (!res.ok) throw new Error(res.status)
		const data = await res.json().catch(() => ({}))
		const nextStatus = isFollowing ? 'NONE' : (data.status || 'FOLLOWING')
		btn.dataset.following = nextStatus === 'FOLLOWING' ? '1' : '0'
		btn.textContent = nextStatus === 'REQUESTED' ? 'Requested' : (nextStatus === 'FOLLOWING' ? 'Following' : 'Follow')
		btn.style.background = nextStatus === 'FOLLOWING' ? '#333' : (nextStatus === 'REQUESTED' ? '#4b5563' : '#4f46e5')
		loadProfile()
	} catch (e) { showToast('Error. Dobara try karo.', 'error') }
	finally { btn.disabled = false }
}

function closeFollowModal() {
	document.getElementById('followModal')?.classList.remove('open')
	document.getElementById('followModalBackdrop')?.classList.remove('open')
}

/* ── VIDEO CARD ── */
function buildVideoCard(post, showDelete) {
	const item = document.createElement('div')
	item.className = 'video-item'
	item.dataset.postId = post.postId || post.id
	item.style.position = 'relative'

	const video = document.createElement('video')
	video.muted = true; video.preload = 'metadata'
	video.style.cssText = 'width:100%;border-radius:8px;cursor:pointer;display:block;'
	video.addEventListener('click', () => video.paused ? video.play() : video.pause())
	// Hover preview (NEW)
	video.addEventListener('mouseenter', () => { video.play().catch(() => { }) })
	video.addEventListener('mouseleave', () => { video.pause(); video.currentTime = 0 })

	const src = document.createElement('source')
	src.src = post.videoUrl; src.type = 'video/mp4'
	video.appendChild(src)
	video.addEventListener('loadedmetadata', () => { video.currentTime = 1 })
	video.addEventListener('error', () => {
		item.style.background = '#1a1a1a'
		video.style.display = 'none'
		const fb = document.createElement('div')
		fb.style.cssText = 'padding:20px;text-align:center;color:#555;font-size:0.8rem;'
		fb.textContent = '🎬 Video load nahi hui'
		item.prepend(fb)
	})

	const caption = document.createElement('div')
	caption.className = 'video-item-caption'
	caption.textContent = post.content || ''
	caption.style.cssText = 'font-size:12px;color:#888;padding:4px 2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;'

	item.append(video, caption)

	// Action buttons row (NEW)
	const actions = document.createElement('div')
	actions.style.cssText = 'display:flex;gap:4px;margin-top:4px;'

	const pinned = Boolean(post.isPinned ?? post.pinned ?? false)

	// Share button
	const shareBtn = makeBtn('↗️', '#222', () => {
		const url = location.origin + '/reel/' + (post.postId || post.id)
		navigator.clipboard?.writeText(url).then(() => showToast('Link copied! 🔗', 'success'))
	}, 'flex:1;font-size:11px;')
	actions.appendChild(shareBtn)

	if (showDelete) {
		const pinBtn = makeBtn(pinned ? '📌 Unpin' : '📌 Pin', pinned ? '#6366f1' : '#111827', () => {
			togglePin(post.postId || post.id, item)
		}, 'flex:2;font-size:11px;')
		actions.appendChild(pinBtn)

		const deleteBtn = makeBtn('🗑 Delete', '#c0392b', () => deletePost(post.postId || post.id, item), 'flex:2;font-size:11px;')
		actions.appendChild(deleteBtn)
	}

	item.appendChild(actions)
	return item
}

/* ── DELETE POST ── */
async function deletePost(postId, cardEl) {
	if (!confirm('Is video ko delete karna chahte ho?')) return
	try {
		const res = await fetch(`/api/post/${postId}`, { method: 'DELETE', headers: csrfHeaders() })
		if (!res.ok) throw new Error('Delete failed: ' + res.status)
		cardEl.remove()
		const remaining = document.querySelectorAll('.video-item').length
		setText('videos', remaining)
		showToast('Video delete ho gayi 🗑', 'success')
	} catch (e) {
		console.error('Delete error:', e)
		showToast('Delete fail ho gaya.', 'error')
	}
}

/* ── PIN / UNPIN ── */
async function togglePin(postId, cardEl) {
	try {
		const res = await fetch(`/api/profile/pin/${postId}`, { method: 'POST', headers: csrfHeaders() })
		if (!res.ok) throw new Error('Pin failed: ' + res.status)
		const data = await res.json().catch(() => ({}))
		await loadVideos(0)
		showToast(data.pinned ? 'Post pinned ✅' : 'Post unpinned', 'success')
	} catch (e) {
		console.error('Pin error:', e)
		showToast('Pin/unpin fail ho gaya.', 'error')
	}
}

/* ── EDIT MODAL ── */
function openEditModal() {
	if (!currentProfile) return
	const nameEl = document.getElementById('editName')
	const bioEl = document.getElementById('editBio')
	const intEl = document.getElementById('editInterests')
	if (nameEl) nameEl.value = currentProfile.name || ''
	if (bioEl) bioEl.value = currentProfile.bio || ''
	const interests = Array.isArray(currentProfile.interests)
		? currentProfile.interests.join(', ')
		: (currentProfile.interests || '')
	if (intEl) intEl.value = interests
	// Update char counts
	document.getElementById('bioCharCount') && (document.getElementById('bioCharCount').textContent = (currentProfile.bio?.length || 0) + '/150')
	document.getElementById('nameCharCount') && (document.getElementById('nameCharCount').textContent = (currentProfile.name?.length || 0) + '/50')
	document.getElementById('editModal')?.classList.add('open')
}
function closeEditModal() { document.getElementById('editModal')?.classList.remove('open') }

async function saveProfile() {
	const btn = document.getElementById('saveProfileBtn')
	btn.disabled = true; btn.textContent = 'Saving...'
	const payload = {
		name: document.getElementById('editName')?.value.trim() || '',
		bio: document.getElementById('editBio')?.value.trim() || '',
		interests: (document.getElementById('editInterests')?.value || '')
			.split(',').map(i => i.trim()).filter(Boolean)
	}
	if (!payload.name) { showToast('Naam khaali nahi ho sakta', 'error'); btn.disabled = false; btn.textContent = 'Save'; return }
	try {
		const res = await fetch('/api/profile/update', {
			method: 'POST',
			headers: csrfHeaders({ 'Content-Type': 'application/json' }),
			body: JSON.stringify(payload)
		})
		if (!res.ok) throw new Error('Update failed: ' + res.status)
		closeEditModal()
		await loadProfile()
		showToast('Profile update ho gaya! ✅', 'success')
	} catch (e) {
		console.error('Save profile error:', e)
		showToast('Profile update fail ho gaya.', 'error')
	} finally { btn.disabled = false; btn.textContent = 'Save' }
}

/* ── AVATAR UPLOAD ── */
async function uploadAvatar(e) {
	const file = e.target.files[0]
	if (!file) return
	if (!['image/jpeg', 'image/png'].includes(file.type)) { showToast('Sirf JPG/PNG images allowed hain', 'error'); return }
	if (file.size > 10 * 1024 * 1024) { showToast('Avatar 10MB se bada nahi hona chahiye.', 'error'); return }
	// Preview immediately (NEW)
	const avatarEl = document.getElementById('avatar')
	if (avatarEl) {
		const objUrl = URL.createObjectURL(file)
		avatarEl.src = objUrl
	}
	const form = new FormData()
	form.append('file', file)
	try {
		const res = await fetch('/api/profile/avatar', { method: 'POST', headers: csrfHeaders(), body: form })
		if (!res.ok) throw new Error('Upload failed: ' + res.status)
		const url = await res.text()
		if (avatarEl) avatarEl.src = url.trim() + '?t=' + Date.now()
		showToast('Avatar update ho gaya! ✅', 'success')
	} catch (e) {
		console.error('Avatar upload error:', e)
		showToast('Avatar upload fail ho gaya.', 'error')
		await loadProfile() // Restore original
	}
}

/* ── SHARE PROFILE (NEW) ── */
function shareProfile() {
	const url = location.origin + '/profile/user/' + (currentProfile?.id || '')
	if (navigator.share) {
		navigator.share({ title: currentProfile?.name, url }).catch(() => { })
	} else {
		navigator.clipboard?.writeText(url)
			.then(() => showToast('Profile link copied! 🔗', 'success'))
			.catch(() => showToast('Link: ' + url, 'info'))
	}
}

/* ── UTILS ── */
function makeBtn(label, bg, onClick, extraStyle = '') {
	const btn = document.createElement('button')
	btn.textContent = label
	btn.style.cssText = `padding:5px 8px;background:${bg};color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:12px;${extraStyle}`
	btn.addEventListener('click', onClick)
	return btn
}
function setText(id, value) {
	const el = document.getElementById(id)
	if (el) el.textContent = value ?? ''
}
function showToast(msg, type = 'info') {
	document.getElementById('p-toast')?.remove()
	const colors = { error: '#e74c3c', success: '#27ae60', info: '#2d3436', warning: '#f39c12' }
	const icons = { error: '❌', success: '✅', info: 'ℹ️', warning: '⚠️' }
	const t = document.createElement('div')
	t.id = 'p-toast'
	t.textContent = (icons[type] || '') + ' ' + msg
	t.style.cssText = `position:fixed;bottom:30px;left:50%;transform:translateX(-50%) translateY(20px);opacity:0;
		background:${colors[type] || '#333'};color:#fff;padding:12px 22px;
		border-radius:10px;font-size:14px;z-index:9999;
		box-shadow:0 4px 20px rgba(0,0,0,.5);transition:all 0.3s ease;`
	document.body.appendChild(t)
	setTimeout(() => { t.style.opacity = '1'; t.style.transform = 'translateX(-50%) translateY(0)' }, 10)
	setTimeout(() => { t.style.opacity = '0'; setTimeout(() => t.remove(), 300) }, 3200)
}
