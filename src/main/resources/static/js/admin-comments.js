/* ================================
   admin-comments.js
================================ */

let allComments = []
const userProfile = {}

document.addEventListener('DOMContentLoaded', () => {
	loadComments()
})

async function loadComments() {
	try {
		const res = await fetch('/api/admin/comments').then(r => r.json())
		allComments = res.comments || res.data || res || []

		document.getElementById('commentsLoading').style.display = 'none'
		updateAnalytics(allComments)
		renderTree(buildTree(allComments))
		renderChart(allComments)
		document.getElementById('commentsTableWrap').style.display = ''
	} catch (e) {
		console.error(e)
		document.getElementById('commentsLoading').textContent = 'Load fail ho gaya.'
	}
}

function buildTree(comments) {
	const map = {}
	const roots = []

	comments.forEach(c => {
		c.children = []
		map[c.id] = c
	})

	comments.forEach(c => {
		if (c.parentId && map[c.parentId]) {
			map[c.parentId].children.push(c)
		} else {
			roots.push(c)
		}
	})

	return roots
}

function renderTree(comments, level = 0) {
	const tbody = document.getElementById('commentsBody')
	if (level === 0) tbody.innerHTML = ''

	comments.forEach(c => {
		const toxicity = getToxicityScore(c.text)
		updateUserProfile(c, toxicity)

		const action = decideAction(c, toxicity)
		if (isShadowBanned(c.userId)) return
		if (action === 'hide') return
		if (action === 'ban') {
			autoBanUser(c.userId)
			return
		}

		const tr = document.createElement('tr')
		tr.id = `comment-row-${c.id}`

		if (action === 'flag') tr.className = 'medium-row'
		if (toxicity > 60) tr.className = 'toxic-row'

		const createdAt = c.createdAt ? c.createdAt.substring(0, 16).replace('T', ' ') : '-'
		const query = document.getElementById('commentSearch')?.value || ''

		tr.innerHTML = `
			<td><code>${c.id}</code></td>
			<td style="padding-left:${level * 20}px" class="comment-user">${esc(c.userName)}</td>
			<td><code>#${c.postId}</code></td>
			<td class="comment-text" onclick="toggleComment(this)">
				${highlight(esc(c.text), query)}
			</td>
			<td>
				<div class="tox-bar">
					<div class="tox-fill" style="width:${toxicity}%"></div>
				</div>
				<span>${toxicity}%</span>
			</td>
			<td class="comment-time">${createdAt}</td>
			<td>
				<button class="btn-delete" onclick="deleteComment(${c.id}, this)">Delete</button>
				<button class="btn-ban" onclick="banUser(${c.userId})">Ban</button>
			</td>
		`

		tbody.appendChild(tr)

		if (c.children?.length) {
			renderTree(c.children, level + 1)
		}
	})
}

function getToxicityScore(text) {
	if (!text) return 0

	text = text.toLowerCase()
	let score = 0

	const severe = ['kill', 'rape', 'terrorist']
	const medium = ['hate', 'abuse', 'stupid', 'idiot']
	const spam = ['buy now', 'click here', 'free money']

	severe.forEach(w => { if (text.includes(w)) score += 40 })
	medium.forEach(w => { if (text.includes(w)) score += 20 })
	spam.forEach(w => { if (text.includes(w)) score += 15 })

	if (text.includes('!!!')) score += 10
	if (text.length < 3) score += 10
	if (text.split(' ').length > 30) score += 10

	return Math.min(score, 100)
}

function updateUserProfile(c, toxicity) {
	if (!userProfile[c.userId]) {
		userProfile[c.userId] = {
			score: 0,
			comments: 0,
			toxic: 0,
			lastComments: []
		}
	}

	const u = userProfile[c.userId]
	u.comments++
	u.lastComments.push(c.text)

	if (u.lastComments.length > 5) u.lastComments.shift()

	if (toxicity > 40) {
		u.toxic++
		u.score += toxicity
	} else {
		u.score -= 5
	}

	u.score = Math.max(0, u.score)
}

function isSpam(c, u) {
	const repeated = u.lastComments.filter(t => t === c.text).length >= 2
	const spamWords = ['buy now', 'free money', 'click here']
	const spamHit = spamWords.some(w => c.text.toLowerCase().includes(w))
	return repeated || spamHit
}

function decideAction(c, toxicity) {
	const u = userProfile[c.userId]

	if (isSpam(c, u)) return 'hide'
	if (toxicity > 80) return 'hide'
	if (u.toxic >= 3 && u.score > 100) return 'ban'
	if (toxicity > 50) return 'flag'

	return 'allow'
}

function isShadowBanned(userId) {
	const u = userProfile[userId]
	return u && u.score > 120
}

async function autoBanUser(userId) {
	console.warn('Auto banning:', userId)

	const csrf = document.querySelector('meta[name="_csrf"]').content
	const header = document.querySelector('meta[name="_csrf_header"]').content

	await fetch(`/admin/users/ban/${userId}`, {
		method: 'POST',
		headers: { [header]: csrf }
	})

	toast('User auto-banned')
}

function updateAnalytics(comments) {
	const total = comments.length
	const toxic = comments.filter(c => getToxicityScore(c.text) > 40).length
	const users = new Set(comments.map(c => c.userId)).size

	document.getElementById('totalComments').textContent = total
	document.getElementById('toxicCount').textContent = toxic
	document.getElementById('uniqueUsers').textContent = users
}

function filterComments(q) {
	q = q.toLowerCase()

	const filtered = allComments.filter(c =>
		(c.text || '').toLowerCase().includes(q) ||
		(c.userName || '').toLowerCase().includes(q) ||
		String(c.postId).includes(q)
	)

	renderTree(buildTree(filtered))
}

async function deleteComment(commentId, btn) {
	if (!confirm(`Delete comment #${commentId}?`)) return

	btn.disabled = true
	btn.textContent = '...'

	try {
		const csrf = document.querySelector('meta[name="_csrf"]').content
		const header = document.querySelector('meta[name="_csrf_header"]').content

		const res = await fetch(`/admin/comments/delete/${commentId}`, {
			method: 'POST',
			headers: { [header]: csrf }
		})

		const data = await res.json()

		if (data.success) {
			document.getElementById(`comment-row-${commentId}`)?.remove()
			allComments = allComments.filter(c => c.id != commentId)
			updateAnalytics(allComments)
			toast('Deleted successfully')
		} else {
			toast('Delete fail', true)
		}
	} catch {
		toast('Error', true)
	}

	btn.disabled = false
	btn.textContent = 'Delete'
}

async function banUser(userId) {
	if (!confirm('User ban karna hai?')) return

	const csrf = document.querySelector('meta[name="_csrf"]').content
	const header = document.querySelector('meta[name="_csrf_header"]').content

	await fetch(`/admin/users/ban/${userId}`, {
		method: 'POST',
		headers: { [header]: csrf }
	})

	toast('User banned')
}

function toggleComment(el) {
	el.classList.toggle('expanded')
}

function highlight(text, q) {
	if (!q) return text
	const re = new RegExp(`(${q})`, 'gi')
	return text.replace(re, '<mark>$1</mark>')
}

function esc(s) {
	return String(s || '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
}

function toast(msg, err = false) {
	const t = document.getElementById('adminToast')
	if (!t) return

	t.textContent = msg
	t.style.background = err ? '#c0392b' : '#1f2937'
	t.style.display = 'block'

	setTimeout(() => {
		t.style.display = 'none'
	}, 3000)
}

let chart

function renderChart(comments) {
	const ctx = document.getElementById('commentsChart')
	if (!ctx) return

	const toxic = comments.filter(c => getToxicityScore(c.text) > 40).length
	const safe = comments.length - toxic

	if (chart) chart.destroy()

	chart = new Chart(ctx, {
		type: 'doughnut',
		data: {
			labels: ['Safe', 'Toxic'],
			datasets: [{
				data: [safe, toxic],
				backgroundColor: ['#22c55e', '#ef4444']
			}]
		},
		options: {
			responsive: true,
			maintainAspectRatio: false,
			plugins: {
				legend: {
					labels: { color: '#fff' }
				}
			}
		}
	})
}
