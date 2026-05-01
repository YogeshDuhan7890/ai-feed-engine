const DISMISSED_SUGGESTIONS_KEY = 'ai-feed-dismissed-suggestions'

document.addEventListener('DOMContentLoaded', () => {
	loadSuggestions()
})

function getCsrfToken() {
	const meta = document.querySelector('meta[name="_csrf"]')
	if (meta) return meta.getAttribute('content')
	const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
	return match ? decodeURIComponent(match[1]) : ''
}

function getCsrfHeader() {
	const meta = document.querySelector('meta[name="_csrf_header"]')
	return meta ? meta.getAttribute('content') : 'X-CSRF-TOKEN'
}

function csrfHeaders(extra = {}) {
	const token = getCsrfToken()
	const header = getCsrfHeader()
	return token ? { [header]: token, ...extra } : { ...extra }
}

function readDismissedSuggestions() {
	try {
		return JSON.parse(localStorage.getItem(DISMISSED_SUGGESTIONS_KEY) || '[]')
	} catch {
		return []
	}
}

function writeDismissedSuggestions(ids) {
	localStorage.setItem(DISMISSED_SUGGESTIONS_KEY, JSON.stringify(ids))
}

function dismissSuggestion(userId) {
	const current = new Set(readDismissedSuggestions())
	current.add(Number(userId))
	writeDismissedSuggestions([...current])
}

function resetDismissedSuggestions() {
	localStorage.removeItem(DISMISSED_SUGGESTIONS_KEY)
	loadSuggestions()
}

async function loadSuggestions() {
	const container = document.getElementById('suggestedUsers')
	if (!container) return

	try {
		const response = await fetch('/api/suggestions?limit=10')
		if (!response.ok) throw new Error('Failed to load suggestions')

		const dismissed = new Set(readDismissedSuggestions().map(Number))
		const users = (await response.json()).filter(user => !dismissed.has(Number(user.userId)))

		renderSuggestions(container, users, dismissed.size > 0)
	} catch (error) {
		console.error('Suggestions load error:', error)
		container.style.display = 'none'
	}
}

function renderSuggestions(container, users, hasDismissed) {
	container.style.display = ''
	container.innerHTML = ''

	const head = document.createElement('div')
	head.className = 'suggestions-head'
	head.innerHTML = `
		<div class="suggestions-title">Suggested for you</div>
		<button type="button" class="suggestions-reset${hasDismissed ? '' : ' is-disabled'}" onclick="resetDismissedSuggestions()" ${hasDismissed ? '' : 'disabled'}>Reset hidden</button>
	`
	container.appendChild(head)

	if (!users.length) {
		const empty = document.createElement('div')
		empty.className = 'suggestion-empty'
		empty.textContent = hasDismissed ? 'Hidden suggestions removed from this list.' : 'No suggestions available right now.'
		container.appendChild(empty)
		return
	}

	users.slice(0, 6).forEach(user => {
		container.appendChild(buildSuggestionRow(user))
	})
}

function buildSuggestionRow(user) {
	const row = document.createElement('div')
	row.className = 'suggestion-row'

	const avatar = document.createElement('div')
	avatar.className = 'suggestion-avatar'
	avatar.addEventListener('click', () => {
		window.location.href = `/profile/user/${user.userId}`
	})

	if (user.avatar) {
		const image = document.createElement('img')
		image.src = user.avatar
		image.alt = user.name || 'User'
		avatar.appendChild(image)
	} else {
		avatar.textContent = 'U'
	}

	const info = document.createElement('div')
	info.className = 'suggestion-info'
	info.addEventListener('click', () => {
		window.location.href = `/profile/user/${user.userId}`
	})

	const name = document.createElement('div')
	name.className = 'suggestion-name'
	name.textContent = user.name || 'User'

	const meta = document.createElement('div')
	meta.className = 'suggestion-meta'
	meta.textContent = `${formatNum(user.followers)} followers`

	info.append(name, meta)

	const actions = document.createElement('div')
	actions.className = 'suggestion-actions'

	const followBtn = document.createElement('button')
	followBtn.type = 'button'
	followBtn.className = 'suggestion-follow-btn'
	followBtn.textContent = 'Follow'
	followBtn.addEventListener('click', async event => {
		event.stopPropagation()
		followBtn.disabled = true
		followBtn.textContent = '...'
		try {
			const response = await fetch(`/api/follow/${user.userId}`, {
				method: 'POST',
				headers: csrfHeaders()
			})
			if (!response.ok) throw new Error('Follow failed')
			const data = await response.json().catch(() => ({}))
			const nextStatus = data.status || 'FOLLOWING'
			followBtn.textContent = nextStatus === 'REQUESTED' ? 'Requested' : 'Following'
			dismissSuggestion(user.userId)
			setTimeout(() => row.remove(), 600)
		} catch (error) {
			console.error(error)
			followBtn.disabled = false
			followBtn.textContent = 'Follow'
		}
	})

	const hideBtn = document.createElement('button')
	hideBtn.type = 'button'
	hideBtn.className = 'suggestion-hide-btn'
	hideBtn.textContent = 'Remove'
	hideBtn.addEventListener('click', event => {
		event.stopPropagation()
		dismissSuggestion(user.userId)
		row.remove()
		if (!row.parentElement?.querySelector('.suggestion-row')) {
			loadSuggestions()
		}
	})

	actions.append(followBtn, hideBtn)
	row.append(avatar, info, actions)
	return row
}

function formatNum(value) {
	const number = Number(value || 0)
	if (number >= 1000000) return `${(number / 1000000).toFixed(1)}M`
	if (number >= 1000) return `${(number / 1000).toFixed(1)}K`
	return String(number)
}
