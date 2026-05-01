/* ════════════════════════════════════════════════════════
   admin-monitor.js — Real-time System Health Monitor
   FIX: Actuator quick links now preview JSON in modal
   FIX: CSRF headers added to all fetch calls
   FIX: Link status (UP/DOWN) auto-checked on load
════════════════════════════════════════════════════════ */

const MAX_POINTS = 30
const heapHistory = []
const cpuHistory = []
const timeLabels = []

let heapChart = null
let cpuChart = null
let intervalId = null
let refreshMs = 5000
let currentActuatorUrl = ''

// ── Init ──────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
	initCharts()
	refresh()
	startInterval()
	checkActuatorLinks()   // FIX: check all links on page load
})

function startInterval() {
	clearInterval(intervalId)
	if (refreshMs > 0) intervalId = setInterval(refresh, refreshMs)
}

function changeInterval() {
	refreshMs = parseInt(document.getElementById('refreshInterval')?.value || 5000)
	startInterval()
}

// ── Charts Init ───────────────────────────────────────────────────────────────
function initCharts() {
	const base = {
		responsive: true,
		animation: { duration: 300 },
		plugins: { legend: { display: false } },
		scales: {
			y: { ticks: { color: '#6b7280', font: { size: 10 } }, grid: { color: '#1f2937' } },
			x: { ticks: { color: '#6b7280', font: { size: 9 }, maxRotation: 0 }, grid: { color: '#1f2937' } }
		}
	}

	const hCtx = document.getElementById('heapChart')?.getContext('2d')
	if (hCtx) heapChart = new Chart(hCtx, {
		type: 'line',
		data: {
			labels: timeLabels, datasets: [{
				label: 'Heap (MB)', data: heapHistory,
				borderColor: '#6c63ff', backgroundColor: 'rgba(108,99,255,.1)',
				borderWidth: 2, pointRadius: 0, fill: true, tension: .4
			}]
		},
		options: { ...base }
	})

	const cCtx = document.getElementById('cpuChart')?.getContext('2d')
	if (cCtx) cpuChart = new Chart(cCtx, {
		type: 'line',
		data: {
			labels: timeLabels, datasets: [{
				label: 'CPU %', data: cpuHistory,
				borderColor: '#f59e0b', backgroundColor: 'rgba(245,158,11,.1)',
				borderWidth: 2, pointRadius: 0, fill: true, tension: .4
			}]
		},
		options: { ...base, scales: { ...base.scales, y: { ...base.scales.y, min: 0, max: 100 } } }
	})
}

// ── Main Refresh ──────────────────────────────────────────────────────────────
async function refresh() {
	try {
		const [sysRes, diskRes] = await Promise.all([
			fetch('/api/admin/system', { headers: { [CSRF_HEADER]: CSRF_TOKEN } }),
			fetch('/api/admin/system/disk', { headers: { [CSRF_HEADER]: CSRF_TOKEN } })
		])
		const sys = await sysRes.json()
		const disk = await diskRes.json()

		updateMetrics(sys, disk)
		updateCharts(sys)
		updateAlerts(sys, disk)
		setText('lastUpdated', '🟢 Updated: ' + sys.timestamp)
	} catch (e) {
		console.error('Monitor refresh fail:', e)
		setText('lastUpdated', '🔴 Error: ' + e.message)
	}
}

// ── Update Metrics ────────────────────────────────────────────────────────────
function updateMetrics(sys, disk) {
	setText('uptimeVal', sys.uptimeFormatted || '—')
	setText('uptimeSub', (sys.uptimeMinutes || 0) + ' minutes total')

	const cpuPct = sys.cpuLoadPercent >= 0 ? sys.cpuLoadPercent + '%' : 'N/A'
	setText('cpuVal', cpuPct)
	setText('cpuSub', (sys.availableProcessors || '?') + ' cores • avg: ' + (sys.cpuLoad >= 0 ? sys.cpuLoad : 'N/A'))

	setText('threadVal', sys.threadCount || '—')
	setText('threadSub', 'peak: ' + (sys.peakThreadCount || '—'))

	setText('redisVal', sys.redisKeys >= 0 ? sys.redisKeys : 'ERR')
	setText('redisSub', sys.redisStatus === 'UP' ? '● Connected' : '● Disconnected')

	// Heap
	const hp = sys.heapPercent || 0
	setText('heapUsedLbl', (sys.heapUsedMB || 0) + ' MB (' + hp + '%)')
	setText('heapMaxLbl', 'Max: ' + (sys.heapMaxMB || 0) + ' MB')
	setBar('heapBar', hp)

	const nhp = sys.heapMaxMB > 0 ? Math.round((sys.nonHeapUsedMB || 0) / sys.heapMaxMB * 100) : 40
	setText('nonHeapLbl', (sys.nonHeapUsedMB || 0) + ' MB')
	setBar2('nonHeapBar', Math.min(nhp, 100))

	// System memory
	if (sys.systemTotalMemMB) {
		const sp = sys.systemMemPercent || 0
		setText('sysMemLbl', ((sys.systemTotalMemMB || 0) - (sys.systemFreeMemMB || 0)) + ' / ' + (sys.systemTotalMemMB || 0) + ' MB (' + sp + '%)')
		setBar('sysMemBar', sp)
	} else {
		setText('sysMemLbl', 'N/A (Windows/restricted)')
	}

	// Disk
	if (disk && !disk.error) {
		const dp = disk.usedPercent || 0
		setText('diskLbl', (disk.usedGB || 0) + ' / ' + (disk.totalGB || 0) + ' GB (' + dp + '%)')
		setText('diskMaxLbl', 'Total: ' + (disk.totalGB || 0) + ' GB')
		setBar('diskBar', dp)
	} else {
		setText('diskLbl', 'N/A')
	}

	// Service health
	const dbUp = sys.dbStatus === 'UP'
	setHtml('dbStatus', `<span class="health-dot ${dbUp ? 'dot-up' : 'dot-down'}"></span><span class="stat-val">${sys.dbStatus || '—'}</span>`)

	const ping = sys.dbPingMs
	let pingHtml = '—'
	if (ping != null && ping >= 0) {
		const cls = ping < 20 ? 'ping-ok' : ping < 100 ? 'ping-slow' : 'ping-bad'
		pingHtml = `<span class="ping-badge ${cls}">${ping}ms</span>`
	}
	setHtml('dbPing', pingHtml)

	const redisUp = sys.redisStatus === 'UP'
	setHtml('redisStatus', `<span class="health-dot ${redisUp ? 'dot-up' : 'dot-down'}"></span><span class="stat-val">${sys.redisStatus || '—'}</span>`)

	setText('gcCount', sys.gcCount ?? '—')
	setText('gcTime', sys.gcTimeMs != null ? sys.gcTimeMs + ' ms' : '—')
	setText('osName', sys.osName || '—')
	setText('processCpu', sys.processCpuPercent != null ? sys.processCpuPercent + '%' : 'N/A')

	// App stats
	setText('appUsers', sys.totalUsers?.toLocaleString() || '—')
	setText('appPosts', sys.totalPosts?.toLocaleString() || '—')
	setText('appEngage', sys.totalEngagements?.toLocaleString() || '—')
	setText('appRedis', sys.redisKeys >= 0 ? sys.redisKeys : '—')
	setText('dbProduct', sys.dbProduct || '—')

	// Threads
	setText('threadActive', sys.threadCount || '—')
	setText('threadPeak', sys.peakThreadCount || '—')
	setText('threadDaemon', sys.daemonThreadCount || '—')
	setText('threadTotal', sys.totalStartedThreads?.toLocaleString() || '—')
}

// ── Charts Update ─────────────────────────────────────────────────────────────
function updateCharts(sys) {
	const now = sys.timestamp || new Date().toLocaleTimeString()
	timeLabels.push(now)
	heapHistory.push(sys.heapUsedMB || 0)
	cpuHistory.push(sys.cpuLoadPercent >= 0 ? sys.cpuLoadPercent : (sys.processCpuPercent || 0))
	if (timeLabels.length > MAX_POINTS) { timeLabels.shift(); heapHistory.shift(); cpuHistory.shift() }
	heapChart?.update('none')
	cpuChart?.update('none')
}

// ── Alerts ────────────────────────────────────────────────────────────────────
function updateAlerts(sys, disk) {
	const alerts = []
	if (sys.heapPercent >= 90) alerts.push({ t: 'crit', m: '🔴 CRITICAL: Heap ' + sys.heapPercent + '% — OOM risk!' })
	else if (sys.heapPercent >= 75) alerts.push({ t: 'warn', m: '⚠️ Heap memory high: ' + sys.heapPercent + '%' })
	if (sys.dbStatus !== 'UP') alerts.push({ t: 'crit', m: '🔴 CRITICAL: Database DOWN!' })
	else if (sys.dbPingMs > 200) alerts.push({ t: 'warn', m: '⚠️ DB slow: ' + sys.dbPingMs + 'ms' })
	if (sys.redisStatus !== 'UP') alerts.push({ t: 'warn', m: '⚠️ Redis connection failed' })
	if (disk && !disk.error && disk.usedPercent >= 90) alerts.push({ t: 'crit', m: '🔴 Disk almost full: ' + disk.usedPercent + '%' })
	if (sys.cpuLoadPercent >= 90) alerts.push({ t: 'warn', m: '⚠️ CPU very high: ' + sys.cpuLoadPercent + '%' })
	if (sys.threadCount > 200) alerts.push({ t: 'warn', m: '⚠️ High thread count: ' + sys.threadCount })

	const banner = document.getElementById('alertsBanner')
	if (!banner) return
	if (!alerts.length) { banner.style.display = 'none'; return }
	banner.style.display = ''
	banner.innerHTML = alerts.map(a => `<div class="alert-item ${a.t === 'warn' ? 'warn' : ''}">${a.m}</div>`).join('')
}

// ── Actuator Links — FIXED ────────────────────────────────────────────────────
const ACTUATOR_ENDPOINTS = [
	{ path: '/actuator/health', id: 'health' },
	{ path: '/actuator/metrics', id: 'metrics' },
	{ path: '/actuator/info', id: 'info' },
	{ path: '/actuator/env', id: 'env' },
	{ path: '/actuator/beans', id: 'beans' },
	{ path: '/actuator/threaddump', id: 'threaddump' }
]

// FIX: Check all actuator endpoints on page load — show UP/DOWN badge
async function checkActuatorLinks() {
	for (const ep of ACTUATOR_ENDPOINTS) {
		checkSingleLink(ep.path, ep.id)
	}
}

async function checkSingleLink(path, id) {
	const badge = document.getElementById('status-' + id)
	if (!badge) return
	try {
		const res = await fetch(path, {
			method: 'GET',
			headers: { [CSRF_HEADER]: CSRF_TOKEN }
		})
		if (res.ok) {
			badge.textContent = 'UP'
			badge.className = 'link-status ls-ok'
		} else {
			badge.textContent = res.status
			badge.className = 'link-status ls-fail'
		}
	} catch (e) {
		badge.textContent = 'ERR'
		badge.className = 'link-status ls-fail'
	}
}

// FIX: previewActuator — fetch JSON and show in modal (not new tab)
async function previewActuator(path, title) {
	currentActuatorUrl = path
	const modal = document.getElementById('apiModal')
	const content = document.getElementById('apiModalContent')
	const titleEl = document.getElementById('apiModalTitle')

	titleEl.textContent = title + ' — ' + path
	content.innerHTML = `<div style="text-align:center;padding:40px;color:var(--text-muted)">
		<div style="font-size:2rem;margin-bottom:10px">⏳</div>Loading...
	</div>`
	modal.classList.add('show')

	try {
		const res = await fetch(path, {
			headers: { [CSRF_HEADER]: CSRF_TOKEN }
		})
		if (!res.ok) {
			content.innerHTML = `<div class="fetch-error">
				<strong>HTTP ${res.status} ${res.statusText}</strong><br><br>
				${getActuatorHelp(path, res.status)}
			</div>`
			return
		}
		const data = await res.json()
		content.innerHTML = `<pre class="api-json">${syntaxHighlight(JSON.stringify(data, null, 2))}</pre>`
	} catch (e) {
		content.innerHTML = `<div class="fetch-error">
			<strong>Fetch failed:</strong> ${e.message}<br><br>
			${getActuatorHelp(path, 0)}
		</div>`
	}
}

function getActuatorHelp(path, status) {
	if (status === 404) return `<small>
		<strong>Endpoint not found.</strong> Ye endpoint expose nahi hua.<br><br>
		<code>application.yml</code> mein add karo:<br>
		<code style="color:#86efac">management:<br>
		  endpoints:<br>
		    web:<br>
		      exposure:<br>
		        include: health,metrics,info,env,beans,loggers,threaddump</code>
	</small>`
	if (status === 403) return `<small>
		<strong>Access Denied (403).</strong> SecurityConfig mein allow nahi hai.<br><br>
		<code>SecurityConfig.java</code> mein check karo ki <code>/actuator/**</code> ADMIN role ko allow hai.
	</small>`
	return `<small>Check karo ki Spring Boot Actuator dependency <code>pom.xml</code> mein hai:<br>
		<code style="color:#86efac">&lt;dependency&gt;<br>
		  &lt;groupId&gt;org.springframework.boot&lt;/groupId&gt;<br>
		  &lt;artifactId&gt;spring-boot-starter-actuator&lt;/artifactId&gt;<br>
		&lt;/dependency&gt;</code></small>`
}

function openCurrent() {
	if (currentActuatorUrl) window.open(currentActuatorUrl, '_blank')
}

function closeApiModal() {
	document.getElementById('apiModal').classList.remove('show')
}

// ── Syntax highlight JSON ──────────────────────────────────────────────────────
function syntaxHighlight(json) {
	return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, match => {
		let cls = 'json-num'
		if (/^"/.test(match)) {
			cls = /:$/.test(match) ? 'json-key' : 'json-str'
		} else if (/true|false/.test(match)) {
			cls = 'json-bool'
		} else if (/null/.test(match)) {
			cls = 'json-null'
		}
		return `<span class="${cls}">${match}</span>`
	})
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function setBar(id, pct) {
	const el = document.getElementById(id)
	if (!el) return
	el.style.width = Math.min(pct, 100) + '%'
	el.className = 'progress-bar ' + (pct >= 85 ? 'bar-crit' : pct >= 65 ? 'bar-warn' : 'bar-ok')
}
function setBar2(id, pct) {
	const el = document.getElementById(id)
	if (el) el.style.width = pct + '%'
}
function setText(id, val) {
	const el = document.getElementById(id)
	if (el) el.textContent = val
}
function setHtml(id, val) {
	const el = document.getElementById(id)
	if (el) el.innerHTML = val
}
