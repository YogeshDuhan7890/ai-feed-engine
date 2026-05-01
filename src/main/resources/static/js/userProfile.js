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

function csrfH() {
	const t = getCsrfToken()
	const h = getCsrfHeader()
	return t ? { [h]: t } : {}
}

const userId = window.location.pathname.split('/').pop()
let profileData = null

document.addEventListener('DOMContentLoaded', () => {
	if (!userId || isNaN(userId)) {
		const pc = document.querySelector('.profile-container')
		if (pc) pc.innerHTML = '<p style="color:#ff4444;padding:40px;text-align:center">User nahi mila.</p>'
		return
	}

	loadProfile()

	document.getElementById('followModalClose')?.addEventListener('click', closeFollowModal)
	document.getElementById('followModalBackdrop')?.addEventListener('click', closeFollowModal)
})

async function loadProfile() {
	try {
		const res = await fetch(`/api/profile/user/${userId}`)
		if (!res.ok) throw new Error('Profile not found: ' + res.status)

		const data = await res.json()
		profileData = data

		document.title = `${data.name || 'Profile'} | AI Feed`
		setText('name', data.name || 'User')
		setText('bio', data.bio || '')
		setText('interests', formatInterests(data.interests))
		animateStat('videos', data.videos ?? 0)
		animateStat('followers', data.followers ?? 0)
		animateStat('following', data.following ?? 0)

		const avatarEl = document.getElementById('avatar')
		if (avatarEl) {
			avatarEl.src = data.avatar || '/images/default.png'
			avatarEl.onerror = () => { avatarEl.src = '/images/default.png' }
		}

		setupFollowBtn(data.followStatus || (data.isFollowing ? 'FOLLOWING' : (data.requestPending ? 'REQUESTED' : 'NONE')))
		setupBlockBtn(Boolean(data.isBlocked))
		setupMsgBtn()
		setupShareBtn(data.name || 'Profile')
		loadVideos()

		document.getElementById('followersBtn')?.addEventListener('click', () => openFollowModal('followers'))
		document.getElementById('followingBtn')?.addEventListener('click', () => openFollowModal('following'))
	} catch (e) {
		console.error('Profile load error:', e)
		const pc = document.querySelector('.profile-container')
		if (pc) pc.innerHTML = '<p style="color:#ff4444;padding:40px;text-align:center">Profile load nahi hua.</p>'
	}
}

async function loadVideos() {
	const grid = document.getElementById('videoGrid')
	if (!grid) return

	if (profileData?.blockedByTarget) {
		grid.innerHTML = '<p style="color:#f87171;text-align:center;padding:30px;grid-column:1/-1">Is user ne aapko block kiya hua hai.</p>'
		return
	}

	if (profileData?.isBlocked) {
		grid.innerHTML = '<p style="color:#fbbf24;text-align:center;padding:30px;grid-column:1/-1">Aapne is user ko block kiya hua hai. Unblock karoge to profile aur videos phir se dikh jayengi.</p>'
		return
	}

	if (profileData?.contentLocked) {
		grid.innerHTML = '<p style="color:#9ca3af;text-align:center;padding:30px;grid-column:1/-1">Yeh private account hai. Videos dekhne ke liye follow karo.</p>'
		return
	}

	grid.innerHTML = [1, 2, 3].map(() =>
		'<div style="background:#1a1a1a;border-radius:10px;aspect-ratio:9/16;animation:shimmer 1.4s infinite linear;background:linear-gradient(90deg,#1a1a1a 25%,#2a2a2a 50%,#1a1a1a 75%);background-size:700px 100%"></div>'
	).join('')

	try {
		const res = await fetch(`/api/user/${userId}/videos`)
		if (!res.ok) throw new Error('Video API failed: ' + res.status)

		const posts = await res.json()
		grid.innerHTML = ''

		if (!posts?.length) {
			grid.innerHTML = '<p style="color:#555;text-align:center;padding:30px;grid-column:1/-1">Koi video nahi abhi</p>'
			return
		}

		posts.forEach(post => {
			if (!post.videoUrl) return

			const item = document.createElement('div')
			item.className = 'video-item'
			item.addEventListener('click', () => {
				location.href = '/reel/' + (post.postId || post.id)
			})

			const video = document.createElement('video')
			video.muted = true
			video.preload = 'metadata'
			video.playsInline = true
			video.style.cssText = 'width:100%;height:100%;object-fit:cover;'
			video.addEventListener('mouseenter', () => video.play().catch(() => {}))
			video.addEventListener('mouseleave', () => {
				video.pause()
				video.currentTime = 0
			})

			const src = document.createElement('source')
			src.src = post.videoUrl
			src.type = 'video/mp4'
			video.appendChild(src)

			const overlay = document.createElement('div')
			overlay.className = 'video-item-caption'
			overlay.textContent = post.content?.slice(0, 40) || ''

			item.append(video, overlay)
			grid.appendChild(item)
		})
	} catch (e) {
		console.error('Video load error:', e)
		grid.innerHTML = '<p style="color:#ff4444;padding:20px;grid-column:1/-1">Videos load nahi hui.</p>'
	}
}

function setupFollowBtn(status) {
	const btn = document.getElementById('followBtn')
	if (!btn) return

	if (profileData?.blockedByTarget || profileData?.isBlocked) {
		btn.style.display = 'none'
		btn.onclick = null
		return
	}

	btn.style.display = ''
	btn.dataset.followStatus = status || 'NONE'
	btn.textContent = getFollowBtnLabel(status)
	btn.style.background = getFollowBtnBg(status)

	btn.onclick = async () => {
		const currentStatus = btn.dataset.followStatus || 'NONE'
		const shouldUnfollow = currentStatus === 'FOLLOWING' || currentStatus === 'REQUESTED'
		btn.disabled = true
		try {
			const url = shouldUnfollow ? `/api/unfollow/${userId}` : `/api/follow/${userId}`
			const res = await fetch(url, { method: 'POST', headers: csrfH() })
			if (!res.ok) throw new Error('Follow action failed')
			const data = await res.json().catch(() => ({}))
			const nextStatus = shouldUnfollow ? 'NONE' : (data.status || 'FOLLOWING')

			btn.dataset.followStatus = nextStatus
			btn.textContent = getFollowBtnLabel(nextStatus)
			btn.style.background = getFollowBtnBg(nextStatus)
			if (profileData) {
				profileData.followStatus = nextStatus
				profileData.isFollowing = nextStatus === 'FOLLOWING'
				profileData.requestPending = nextStatus === 'REQUESTED'
				profileData.contentLocked = Boolean(
					profileData.blockedByTarget
					|| profileData.isBlocked
					|| (profileData.privateAccount && !profileData.isFollowing)
				)
			}

			const followersEl = document.getElementById('followers')
			if (followersEl) {
				const current = parseInt(followersEl.textContent || '0', 10)
				const wasFollowing = currentStatus === 'FOLLOWING'
				const nowFollowing = nextStatus === 'FOLLOWING'
				if (wasFollowing && !nowFollowing) {
					followersEl.textContent = String(Math.max(0, current - 1))
				} else if (!wasFollowing && nowFollowing) {
					followersEl.textContent = String(current + 1)
				}
			}
			loadVideos()
			if (data.message) showToast(data.message)
		} catch (e) {
			console.error(e)
			showToast('Follow action fail ho gayi', 'error')
		} finally {
			btn.disabled = false
		}
	}
}

function setupBlockBtn(isBlocked) {
	const btn = document.getElementById('blockBtn')
	if (!btn) return

	if (profileData?.blockedByTarget) {
		btn.style.display = 'none'
		btn.onclick = null
		return
	}

	btn.style.display = ''
	btn.textContent = isBlocked ? 'Unblock' : 'Block'
	btn.style.background = isBlocked ? '#555' : '#1a1a1a'

	btn.onclick = async () => {
		const currentlyBlocked = btn.textContent === 'Unblock'
		if (!currentlyBlocked && !confirm(`${profileData?.name || 'User'} ko block karna hai?`)) return

		btn.disabled = true
		try {
			const url = currentlyBlocked ? `/api/block/unblock/${userId}` : `/api/block/${userId}`
			const res = await fetch(url, { method: 'POST', headers: csrfH() })
			if (!res.ok) throw new Error('Block action failed')

			btn.textContent = currentlyBlocked ? 'Block' : 'Unblock'
			btn.style.background = currentlyBlocked ? '#1a1a1a' : '#555'
			if (profileData) {
				profileData.isBlocked = !currentlyBlocked
				profileData.contentLocked = Boolean(
					profileData.blockedByTarget
					|| profileData.isBlocked
					|| (profileData.privateAccount && !profileData.isFollowing)
				)
			}
			setupFollowBtn(Boolean(profileData?.isFollowing))
			setupMsgBtn()
			loadVideos()
			showToast(currentlyBlocked ? 'User unblock ho gaya' : 'User block ho gaya')
		} catch (e) {
			console.error(e)
			showToast('Block action fail ho gayi', 'error')
		} finally {
			btn.disabled = false
		}
	}
}

function setupMsgBtn() {
	const btn = document.getElementById('msgBtn')
	if (!btn) return

	if (profileData?.blockedByTarget || profileData?.isBlocked) {
		btn.style.display = 'none'
		btn.onclick = null
		return
	}
	btn.style.display = ''
	btn.onclick = () => {
		location.href = '/messages?with=' + userId
	}
}

function setupShareBtn(name) {
	const btn = document.getElementById('shareBtn')
	if (!btn) return
	btn.style.display = ''
	btn.onclick = () => {
		const url = location.origin + '/profile/user/' + userId
		if (navigator.share) {
			navigator.share({ title: name, url }).catch(() => {})
			return
		}

		navigator.clipboard?.writeText(url)
			.then(() => showToast('Profile link copy ho gaya'))
			.catch(() => showToast(url))
	}
}

async function openFollowModal(type) {
	const modal = document.getElementById('followModal')
	const backdrop = document.getElementById('followModalBackdrop')
	const title = document.getElementById('followModalTitle')
	const list = document.getElementById('followModalList')
	if (!modal || !backdrop || !title || !list) return

	title.textContent = type === 'followers' ? 'Followers' : 'Following'
	list.innerHTML = '<p style="color:#888;padding:16px;text-align:center">Loading...</p>'
	backdrop.style.display = 'block'
	modal.style.display = 'flex'

	try {
		const res = await fetch(`/api/${type}/${userId}`)
		if (!res.ok) throw new Error('Follow list failed')
		const users = await res.json()

		list.innerHTML = ''
		if (!users?.length) {
			list.innerHTML = `<p style="color:#888;padding:16px;text-align:center">Koi ${type === 'followers' ? 'follower' : 'following'} nahi</p>`
			return
		}

		users.forEach(u => {
			const row = document.createElement('div')
			row.className = 'follow-row'
			row.addEventListener('click', () => {
				location.href = '/profile/user/' + (u.id || u.userId)
			})

			const av = document.createElement('img')
			av.className = 'follow-avatar'
			av.src = u.avatar || '/images/default.png'
			av.alt = u.name || 'User'
			av.onerror = () => { av.src = '/images/default.png' }

			const nm = document.createElement('div')
			nm.className = 'follow-name'
			nm.textContent = u.name || 'User'

			row.append(av, nm)
			list.appendChild(row)
		})
	} catch (e) {
		console.error(e)
		list.innerHTML = '<p style="color:#ff4444;padding:16px;text-align:center">Load fail ho gaya.</p>'
	}
}

function getFollowBtnLabel(status) {
	if (status === 'FOLLOWING') return 'Following'
	if (status === 'REQUESTED') return 'Requested'
	return 'Follow'
}

function getFollowBtnBg(status) {
	if (status === 'FOLLOWING') return '#333'
	if (status === 'REQUESTED') return '#4b5563'
	return '#4f46e5'
}

function closeFollowModal() {
	document.getElementById('followModalBackdrop')?.style.setProperty('display', 'none')
	document.getElementById('followModal')?.style.setProperty('display', 'none')
}

function formatInterests(interests) {
	if (!interests) return ''
	if (Array.isArray(interests)) return interests.length ? `# ${interests.join(', ')}` : ''
	if (typeof interests === 'string') {
		const cleaned = interests.replace(/^\{|\}$/g, '').trim()
		return cleaned ? `# ${cleaned}` : ''
	}
	return ''
}

function animateStat(id, target) {
	const el = document.getElementById(id)
	if (!el) return

	let start = null
	const duration = 500
	const endValue = Number(target || 0)

	const step = ts => {
		if (!start) start = ts
		const progress = Math.min((ts - start) / duration, 1)
		el.textContent = String(Math.floor(progress * endValue))
		if (progress < 1) requestAnimationFrame(step)
	}

	requestAnimationFrame(step)
}

function setText(id, value) {
	const el = document.getElementById(id)
	if (el) el.textContent = value ?? ''
}

function showToast(msg, type = 'success') {
	document.getElementById('__up-toast')?.remove()
	const t = document.createElement('div')
	t.id = '__up-toast'
	t.textContent = msg
	t.style.cssText = `position:fixed;bottom:30px;left:50%;transform:translateX(-50%);background:${type === 'error' ? '#e74c3c' : '#27ae60'};color:#fff;padding:10px 22px;border-radius:8px;font-size:14px;z-index:9999;`
	document.body.appendChild(t)
	setTimeout(() => t.remove(), 3000)
}
