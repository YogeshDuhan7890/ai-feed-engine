/* admin-heatmap.js */
let trendChart = null

document.addEventListener('DOMContentLoaded', () => loadHeatmap(30))

async function loadHeatmap(days) {
	;['7','30','90'].forEach(d => {
		const btn = document.querySelector(`button[onclick="loadHeatmap(${d})"]`)
		if (btn) { btn.className = d == days ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-outline-secondary' }
	})
	try {
		const res  = await fetch('/api/admin/heatmap?days=' + days)
		const data = await res.json()
		if (data.error) { console.error(data.error); return }

		const dailyVals  = data.dailyValues || []
		const total      = dailyVals.reduce((a, b) => a + b, 0)
		const avg        = dailyVals.length ? Math.round(total / dailyVals.length) : 0

		setText('statTotal', total.toLocaleString())
		setText('statPeak',  data.peakHourLabel || '—')
		setText('statAvg',   avg.toLocaleString() + '/day')
		setText('statDays',  days + ' days')

		renderHourlyHeatmap(data.hourlyDist || [], data.peakHour)
		renderTrendChart(data.dailyLabels || [], dailyVals)
		renderActivityGrid(data.dailyLabels || [], dailyVals)
	} catch(e) { console.error('Heatmap load fail:', e) }
}

function renderHourlyHeatmap(dist, peakHour) {
	const container = document.getElementById('hourlyHeatmap')
	const labelEl   = document.getElementById('hourLabels')
	if (!container) return
	const max = Math.max(...dist, 1)
	container.innerHTML = dist.map((val, h) => {
		const pct     = Math.round((val / max) * 100)
		const height  = Math.max(8, pct * 0.72)
		const isPeak  = h === peakHour
		const color   = isPeak ? '#6c63ff' : pct > 70 ? '#22c55e' : pct > 40 ? '#86efac' : pct > 15 ? '#bbf7d0' : '#e5e7eb'
		return `<div title="${String(h).padStart(2,'0')}:00 — ${val} engagements"
			style="flex:1;height:${height}px;background:${color};border-radius:3px 3px 0 0;transition:height 0.4s ease;cursor:pointer"
			onmouseover="this.style.opacity=0.8" onmouseout="this.style.opacity=1"></div>`
	}).join('')
	if (labelEl) {
		labelEl.innerHTML = dist.map((_, h) =>
			`<div style="flex:1;text-align:center">${h % 3 === 0 ? String(h).padStart(2,'0') : ''}</div>`
		).join('')
	}
}

function renderTrendChart(labels, values) {
	if (trendChart) { trendChart.destroy(); trendChart = null }
	const ctx = document.getElementById('trendChart')?.getContext('2d')
	if (!ctx) return
	trendChart = new Chart(ctx, {
		type: 'line',
		data: {
			labels: labels,
			datasets: [{
				label: 'Engagements',
				data: values,
				borderColor: '#6c63ff',
				backgroundColor: 'rgba(108,99,255,0.1)',
				borderWidth: 2,
				pointRadius: 3,
				fill: true,
				tension: 0.4
			}]
		},
		options: {
			responsive: true,
			plugins: { legend: { display: false } },
			scales: {
				x: { grid: { display: false } },
				y: { beginAtZero: true, grid: { color: '#f0f0f0' } }
			}
		}
	})
}

function renderActivityGrid(labels, values) {
	const el = document.getElementById('activityGrid')
	if (!el || !labels.length) { if(el) el.innerHTML = '<div class="text-muted small">Data available nahi hai</div>'; return }
	const max = Math.max(...values, 1)
	const getColor = v => {
		const pct = v / max
		if (pct === 0)   return '#ebedf0'
		if (pct < 0.25)  return '#9be9a8'
		if (pct < 0.50)  return '#40c463'
		if (pct < 0.75)  return '#30a14e'
		return '#216e39'
	}
	// Render as horizontal timeline
	el.innerHTML = `<div style="display:flex;flex-wrap:wrap;gap:3px">` +
		labels.map((label, i) => `
			<div style="width:14px;height:14px;background:${getColor(values[i]||0)};border-radius:2px;cursor:pointer"
				title="${label}: ${values[i]||0} engagements"></div>`
		).join('') +
		'</div>'
}

function setText(id, v) { const el = document.getElementById(id); if (el) el.textContent = v }