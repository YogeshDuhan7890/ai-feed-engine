function getCsrfToken() {
	return document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || ''
}

function getCsrfHeader() {
	return document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN'
}

function csrfHeaders(extra = {}) {
	const token = getCsrfToken()
	const header = getCsrfHeader()
	return token ? { [header]: token, ...extra } : extra
}

let storyData = []
let currentUserIdx = 0
let currentStoryIdx = 0
let storyTimer = null
let progressTimer = null
let progressStart = 0
let isPaused = false
const STORY_DURATION = 5000

document.addEventListener('DOMContentLoaded', () => {
	loadStories()

	document.getElementById('addStoryBtn')?.addEventListener('click', () => {
		document.getElementById('storyFileInput')?.click()
	})
	document.getElementById('storyFileInput')?.addEventListener('change', uploadStory)
	document.getElementById('storyViewerClose')?.addEventListener('click', closeViewer)
	document.getElementById('storyViewerBackdrop')?.addEventListener('click', closeViewer)
	document.getElementById('storyPrevBtn')?.addEventListener('click', prevStory)
	document.getElementById('storyNextBtn')?.addEventListener('click', nextStory)

	document.addEventListener('keydown', event => {
		if (!document.getElementById('storyViewer')?.classList.contains('open')) return
		if (event.key === 'ArrowRight') nextStory()
		if (event.key === 'ArrowLeft') prevStory()
		if (event.key === 'Escape') closeViewer()
	})

	const viewer = document.getElementById('storyViewer')
	if (viewer) {
		viewer.addEventListener('mousedown', pauseStory)
		viewer.addEventListener('mouseup', resumeStory)
		viewer.addEventListener('touchstart', pauseStory, { passive: true })
		viewer.addEventListener('touchend', resumeStory, { passive: true })
	}
})

async function loadStories() {
	const list = document.getElementById('storyRingsList')
	if (!list) return

	try {
		const res = await fetch('/api/stories/feed')
		if (!res.ok) throw new Error('Feed load failed')
		storyData = await res.json()
		renderStoryRings()
	} catch (error) {
		console.error('Stories load error:', error)
	}
}

function renderStoryRings() {
	const list = document.getElementById('storyRingsList')
	if (!list) return

	list.innerHTML = ''
	list.appendChild(buildAddStoryRing())

	storyData.forEach((group, index) => {
		if (!group?.stories?.length) return

		const ring = document.createElement('div')
		ring.className = 'story-ring ' + (group.hasUnread ? 'unread' : 'seen')
		ring.dataset.unread = group.hasUnread ? 'true' : 'false'
		ring.style.cursor = 'pointer'

		const avatarWrap = document.createElement('div')
		avatarWrap.className = 'story-ring-avatar'

		if (group.userAvatar) {
			const img = document.createElement('img')
			img.src = group.userAvatar
			img.alt = group.userName || 'Story user'
			img.onerror = () => {
				avatarWrap.innerHTML = '<span style="font-size:1.1rem;color:#fff">U</span>'
			}
			avatarWrap.appendChild(img)
		} else {
			avatarWrap.style.display = 'flex'
			avatarWrap.style.alignItems = 'center'
			avatarWrap.style.justifyContent = 'center'
			avatarWrap.innerHTML = '<span style="font-size:1.1rem;color:#fff">U</span>'
		}

		const name = document.createElement('div')
		name.className = 'story-ring-name'
		name.textContent = truncateName(group.isMe ? 'Your Story' : (group.userName || 'User'))

		ring.append(avatarWrap, name)
		ring.addEventListener('click', () => openViewer(index))
		list.appendChild(ring)
	})
}

function buildAddStoryRing() {
	const ring = document.createElement('div')
	ring.className = 'story-ring add-story-ring'
	ring.id = 'addStoryBtn'
	ring.style.cursor = 'pointer'
	ring.innerHTML = `
		<div class="story-ring-avatar">
			<div class="plus-icon">+</div>
		</div>
		<div class="story-ring-name">Add</div>
	`
	ring.addEventListener('click', () => document.getElementById('storyFileInput')?.click())
	return ring
}

function openViewer(userIdx) {
	currentUserIdx = userIdx
	currentStoryIdx = 0
	document.getElementById('storyViewer')?.classList.add('open')
	document.getElementById('storyViewerBackdrop')?.classList.add('open')
	document.body.style.overflow = 'hidden'
	showStory()
}

function closeViewer() {
	clearTimeout(storyTimer)
	cancelAnimationFrame(progressTimer)
	document.getElementById('storyViewer')?.classList.remove('open')
	document.getElementById('storyViewerBackdrop')?.classList.remove('open')
	document.body.style.overflow = ''

	const video = document.getElementById('storyVideo')
	if (video) {
		video.pause()
		video.src = ''
	}
}

function showStory() {
	clearTimeout(storyTimer)
	cancelAnimationFrame(progressTimer)

	const userGroup = storyData[currentUserIdx]
	if (!userGroup) {
		closeViewer()
		return
	}

	const story = userGroup.stories?.[currentStoryIdx]
	if (!story) {
		closeViewer()
		return
	}

	setText('storyUserName', userGroup.userName || 'User')
	setText('storyTime', formatStoryTime(story.createdAt))
	setText('storyCaption', story.storyType === 'TEXT' ? '' : (story.caption || ''))
	setText('storyViewCount', story.viewCount > 0 ? `${story.viewCount} views` : '')

	const avatar = document.getElementById('storyUserAvatar')
	if (avatar) {
		avatar.src = userGroup.userAvatar || ''
		avatar.style.visibility = userGroup.userAvatar ? 'visible' : 'hidden'
	}

	const deleteBtn = document.getElementById('storyDeleteBtn')
	if (deleteBtn) {
		deleteBtn.style.display = userGroup.isMe ? 'inline-block' : 'none'
	}

	renderProgressBars(userGroup.stories.length)
	renderStoryMedia(story)
	markStoryViewed(story.id)
	startProgress()
	storyTimer = setTimeout(nextStory, STORY_DURATION)
}

function renderStoryMedia(story) {
	const image = document.getElementById('storyImage')
	const video = document.getElementById('storyVideo')
	const textOverlay = document.getElementById('storyTextOverlay')

	if (textOverlay) {
		textOverlay.style.display = 'none'
	}
	if (image) {
		image.style.display = 'none'
		image.src = ''
	}
	if (video) {
		video.style.display = 'none'
		video.pause()
		video.src = ''
	}

	if ((story.storyType || '').toUpperCase() === 'TEXT') {
		if (!textOverlay) return
		textOverlay.style.display = 'flex'
		textOverlay.style.background = story.bgColor || 'linear-gradient(135deg,#6366f1,#8b5cf6)'
		const content = textOverlay.querySelector('.story-text-content')
		if (content) {
			content.textContent = story.textContent || story.caption || ''
			content.style.color = story.textColor || '#ffffff'
		}
		return
	}

	const mediaUrl = story.mediaUrl || ''
	const isVideo = (story.mediaType || '').toUpperCase() === 'VIDEO' || /\.(mp4|webm|mov|ogg)$/i.test(mediaUrl)

	if (isVideo && video) {
		video.style.display = 'block'
		video.src = mediaUrl
		video.currentTime = 0
		video.play().catch(() => {})
		return
	}

	if (image) {
		image.style.display = 'block'
		image.src = mediaUrl
	}
}

function renderProgressBars(count) {
	const wrap = document.getElementById('storyProgress')
	if (!wrap) return
	wrap.innerHTML = ''

	for (let index = 0; index < count; index++) {
		const track = document.createElement('div')
		track.style.cssText = 'flex:1;height:3px;background:#ffffff44;border-radius:2px;overflow:hidden;'

		const fill = document.createElement('div')
		fill.style.cssText = 'height:100%;width:0%;background:#fff;'
		if (index < currentStoryIdx) fill.style.width = '100%'
		if (index === currentStoryIdx) fill.id = 'activeStoryProgress'

		track.appendChild(fill)
		wrap.appendChild(track)
	}
}

function startProgress() {
	const fill = document.getElementById('activeStoryProgress')
	if (!fill) return
	progressStart = Date.now()

	const tick = () => {
		if (isPaused) {
			progressTimer = requestAnimationFrame(tick)
			return
		}

		const elapsed = Date.now() - progressStart
		const percent = Math.min((elapsed / STORY_DURATION) * 100, 100)
		fill.style.width = `${percent}%`
		if (percent < 100) {
			progressTimer = requestAnimationFrame(tick)
		}
	}

	progressTimer = requestAnimationFrame(tick)
}

function pauseStory() {
	isPaused = true
}

function resumeStory() {
	if (!isPaused) return
	isPaused = false
	const elapsed = Date.now() - progressStart
	const remaining = Math.max(0, STORY_DURATION - elapsed)
	clearTimeout(storyTimer)
	storyTimer = setTimeout(nextStory, remaining)
}

function nextStory() {
	const userGroup = storyData[currentUserIdx]
	if (!userGroup) return closeViewer()

	if (currentStoryIdx < userGroup.stories.length - 1) {
		currentStoryIdx += 1
		showStory()
		return
	}

	if (currentUserIdx < storyData.length - 1) {
		currentUserIdx += 1
		currentStoryIdx = 0
		showStory()
		return
	}

	closeViewer()
}

function prevStory() {
	if (currentStoryIdx > 0) {
		currentStoryIdx -= 1
		showStory()
		return
	}

	if (currentUserIdx > 0) {
		currentUserIdx -= 1
		const previousGroup = storyData[currentUserIdx]
		currentStoryIdx = Math.max((previousGroup?.stories?.length || 1) - 1, 0)
		showStory()
	}
}

async function uploadStory(event) {
	const file = event.target.files?.[0]
	if (!file) return

	if (!['image/jpeg', 'image/png', 'video/mp4'].includes(file.type)) {
		showToast('Sirf JPG, PNG, ya MP4 story allowed hai.', 'error')
		event.target.value = ''
		return
	}

	if (file.size > 10 * 1024 * 1024) {
		showToast('Story 10MB se badi nahi ho sakti.', 'error')
		event.target.value = ''
		return
	}

	const privacy = await showPrivacyPicker()
	if (!privacy) {
		event.target.value = ''
		return
	}

	const form = new FormData()
	form.append('file', file)
	form.append('privacy', privacy)

	try {
		showToast('Story upload ho rahi hai...', 'info')
		const res = await fetch('/api/stories/upload', {
			method: 'POST',
			headers: csrfHeaders(),
			body: form
		})
		const data = await res.json()
		if (!res.ok || data.success === false) {
			throw new Error(data.message || 'Upload fail ho gayi')
		}
		await loadStories()
		showToast('Story upload ho gayi.', 'success')
	} catch (error) {
		showToast(error.message || 'Story upload fail ho gayi.', 'error')
	} finally {
		event.target.value = ''
	}
}

function showPrivacyPicker() {
	return new Promise(resolve => {
		const overlay = document.createElement('div')
		overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.72);z-index:9999;display:flex;align-items:center;justify-content:center;padding:16px;'
		overlay.innerHTML = `
			<div style="width:min(320px,92vw);background:#111827;border:1px solid rgba(255,255,255,.08);border-radius:18px;padding:20px;box-shadow:0 24px 80px rgba(0,0,0,.45);">
				<div style="color:#fff;font-size:1rem;font-weight:700;margin-bottom:8px;">Story visibility</div>
				<div style="color:#94a3b8;font-size:.8rem;line-height:1.5;margin-bottom:16px;">Choose karo kaun ye status dekh sakta hai.</div>
				<div style="display:grid;gap:10px;">
					<button type="button" data-value="PUBLIC" style="${privacyButtonStyle('#4f46e5')}">Public - sabko dikhegi</button>
					<button type="button" data-value="FOLLOWERS" style="${privacyButtonStyle('#0f766e')}">Followers - sirf followers ko</button>
					<button type="button" data-value="PRIVATE" style="${privacyButtonStyle('#b45309')}">Only me - private status</button>
				</div>
				<button type="button" data-value="CANCEL" style="margin-top:14px;width:100%;padding:10px 12px;border-radius:12px;border:1px solid rgba(255,255,255,.12);background:transparent;color:#cbd5e1;font-size:.82rem;cursor:pointer;">Cancel</button>
			</div>
		`

		overlay.querySelectorAll('button').forEach(button => {
			button.addEventListener('click', () => {
				const value = button.dataset.value
				overlay.remove()
				resolve(value === 'CANCEL' ? null : value)
			})
		})

		document.body.appendChild(overlay)
	})
}

function privacyButtonStyle(color) {
	return `width:100%;padding:12px 14px;border:none;border-radius:14px;background:${color};color:#fff;font-size:.84rem;font-weight:600;cursor:pointer;text-align:left;`
}

async function deleteCurrentStory() {
	const userGroup = storyData[currentUserIdx]
	const story = userGroup?.stories?.[currentStoryIdx]
	if (!userGroup?.isMe || !story) return
	if (!confirm('Story delete karni hai?')) return

	try {
		const res = await fetch(`/api/stories/${story.id}`, {
			method: 'DELETE',
			headers: csrfHeaders()
		})
		if (!res.ok) throw new Error('Delete fail ho gayi')
		closeViewer()
		await loadStories()
		showToast('Story delete ho gayi.', 'success')
	} catch (error) {
		showToast(error.message || 'Delete fail ho gayi.', 'error')
	}
}

function markStoryViewed(storyId) {
	if (!storyId) return
	const group = storyData[currentUserIdx]
	const story = group?.stories?.find(item => item.id === storyId)
	if (story && !story.viewed) {
		story.viewed = true
		group.hasUnread = group.stories.some(item => !item.viewed)
		renderStoryRings()
	}
	fetch(`/api/stories/${storyId}/view`, { method: 'POST', headers: csrfHeaders() }).catch(() => {})
}

function formatStoryTime(dateStr) {
	if (!dateStr) return ''
	const diffMs = Date.now() - new Date(dateStr).getTime()
	const diffHours = Math.floor(diffMs / 3600000)
	if (diffHours < 1) return 'Just now'
	if (diffHours < 24) return `${diffHours}h ago`
	return `${Math.floor(diffHours / 24)}d ago`
}

function truncateName(name) {
	return String(name || '').length > 12 ? `${String(name).slice(0, 12)}...` : String(name || '')
}

function setText(id, value) {
	const element = document.getElementById(id)
	if (element) element.textContent = value || ''
}

function showToast(message, type = 'success') {
	document.getElementById('__story-toast')?.remove()

	const colors = {
		success: '#059669',
		error: '#be123c',
		info: '#4338ca'
	}

	const toast = document.createElement('div')
	toast.id = '__story-toast'
	toast.textContent = message
	toast.style.cssText = `
		position: fixed;
		bottom: 26px;
		left: 50%;
		transform: translateX(-50%);
		background: ${colors[type] || colors.success};
		color: #fff;
		padding: 11px 18px;
		border-radius: 999px;
		font-size: .82rem;
		font-weight: 600;
		box-shadow: 0 18px 50px rgba(0,0,0,.35);
		z-index: 10001;
	`
	document.body.appendChild(toast)
	setTimeout(() => toast.remove(), 3000)
}
