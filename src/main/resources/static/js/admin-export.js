let exportLog = []
let currentPreviewType = ''

// ========================
// CSV DOWNLOAD
// ========================
async function downloadCSV(type) {
	const days = document.getElementById('exportDays').value
	let url = `/api/admin/export/${type}`
	if (type === 'engagements') url += `?days=${days}`

	logExport(`CSV: ${type}`, 'downloading...')
	try {
		const res = await fetch(url)
		if (!res.ok) throw new Error('Server error ' + res.status)
		const blob = await res.blob()
		const filename = getFilename(res) || `${type}_export.csv`
		triggerDownload(blob, filename)
		logExport(`CSV: ${type}`, '✅ Downloaded: ' + filename)
	} catch (e) {
		logExport(`CSV: ${type}`, '❌ Failed: ' + e.message)
		alert('Export fail: ' + e.message)
	}
}

// ========================
// CSV PREVIEW
// ========================
async function previewCSV(type) {
	const days = document.getElementById('exportDays').value
	let url = `/api/admin/export/${type}`
	if (type === 'engagements') url += `?days=${days}`

	currentPreviewType = type
	document.getElementById('previewTitle').textContent = `Preview: ${type}.csv`
	document.getElementById('previewContent').textContent = 'Loading...'
	document.getElementById('previewDownloadBtn').onclick = () => downloadCSV(type)

	const modal = new bootstrap.Modal(document.getElementById('previewModal'))
	modal.show()

	try {
		const res = await fetch(url)
		const text = await res.text()
		const lines = text.split('\n').slice(0, 20)
		document.getElementById('previewContent').textContent =
			lines.join('\n') + (text.split('\n').length > 20 ? '\n...(truncated)' : '')
	} catch (e) {
		document.getElementById('previewContent').textContent = 'Error: ' + e.message
	}
}

// ========================
// PDF: FULL SUMMARY
// ========================
async function generateSummaryPDF() {
	const btn = document.getElementById('btnSummaryPDF')
	btn.disabled = true; btn.textContent = '⏳ Generating...'

	const days = document.getElementById('exportDays').value
	logExport('PDF: Summary', 'generating...')

	try {
		const res = await fetch(`/api/admin/export/report?days=${days}`)
		const data = await res.json()

		const { jsPDF } = window.jspdf
		const doc = new jsPDF()

		// Header
		doc.setFillColor(108, 99, 255)
		doc.rect(0, 0, 210, 30, 'F')
		doc.setTextColor(255, 255, 255)
		doc.setFontSize(18)
		doc.setFont('helvetica', 'bold')
		doc.text('AI Feed Engine — Admin Report', 14, 18)
		doc.setFontSize(9)
		doc.text(`Generated: ${data.generatedAt}   |   Period: Last ${data.periodDays} days`, 14, 25)

		// Summary Stats
		doc.setTextColor(40, 40, 40)
		doc.setFontSize(13)
		doc.setFont('helvetica', 'bold')
		doc.text('Platform Summary', 14, 42)

		const stats = [
			['Total Users', data.totalUsers],
			['Total Videos', data.totalPosts],
			['Total Engagements', data.totalEngagements],
			['Blocked Users', data.blockedUsers],
			['Hidden Posts', data.hiddenPosts],
			['Pending Reports', data.pendingReports],
		]

		doc.autoTable({
			startY: 46,
			head: [['Metric', 'Value']],
			body: stats,
			theme: 'striped',
			headStyles: { fillColor: [108, 99, 255] },
			columnStyles: { 1: { halign: 'right', fontStyle: 'bold' } },
			margin: { left: 14, right: 14 },
		})

		// Engagement by Type
		const typeData = data.engagementByType || {}
		if (Object.keys(typeData).length > 0) {
			doc.setFontSize(13)
			doc.setFont('helvetica', 'bold')
			doc.text('Engagement by Type', 14, doc.lastAutoTable.finalY + 14)
			doc.autoTable({
				startY: doc.lastAutoTable.finalY + 18,
				head: [['Type', 'Count']],
				body: Object.entries(typeData),
				theme: 'striped',
				headStyles: { fillColor: [34, 197, 94] },
				columnStyles: { 1: { halign: 'right' } },
				margin: { left: 14, right: 14 },
			})
		}

		// Top Posts
		const topPosts = data.topPosts || []
		if (topPosts.length > 0) {
			doc.setFontSize(13)
			doc.setFont('helvetica', 'bold')
			doc.text('Top Posts by Engagement', 14, doc.lastAutoTable.finalY + 14)
			doc.autoTable({
				startY: doc.lastAutoTable.finalY + 18,
				head: [['Post ID', 'Author', 'Content', 'Engagements']],
				body: topPosts.map(p => [
					p.postId,
					p.author || '—',
					(p.content || '').substring(0, 35) + (p.content?.length > 35 ? '...' : ''),
					p.engagements
				]),
				theme: 'striped',
				headStyles: { fillColor: [239, 68, 68] },
				columnStyles: { 3: { halign: 'right' } },
				margin: { left: 14, right: 14 },
			})
		}

		// Footer
		const pageCount = doc.internal.getNumberOfPages()
		for (let i = 1; i <= pageCount; i++) {
			doc.setPage(i)
			doc.setFontSize(8)
			doc.setTextColor(150)
			doc.text(`AI Feed Admin — Page ${i} of ${pageCount}`, 14, 290)
		}

		const filename = `summary_report_${days}days_${now()}.pdf`
		doc.save(filename)
		logExport('PDF: Summary', '✅ Downloaded: ' + filename)
	} catch (e) {
		logExport('PDF: Summary', '❌ Failed: ' + e.message)
		alert('PDF generation fail: ' + e.message)
		console.error(e)
	} finally {
		btn.disabled = false; btn.textContent = '⬇ Generate PDF'
	}
}

// ========================
// PDF: USERS TABLE
// ========================
async function generateUsersPDF() {
	const btn = document.getElementById('btnUsersPDF')
	btn.disabled = true; btn.textContent = '⏳ Generating...'
	logExport('PDF: Users', 'generating...')

	try {
		const res = await fetch('/api/admin/export/users')
		const text = await res.text()
		const rows = text.trim().split('\n').slice(1)
			.map(line => parseCSVLine(line))

		const { jsPDF } = window.jspdf
		const doc = new jsPDF('l') // landscape

		doc.setFillColor(108, 99, 255)
		doc.rect(0, 0, 297, 28, 'F')
		doc.setTextColor(255, 255, 255)
		doc.setFontSize(16)
		doc.setFont('helvetica', 'bold')
		doc.text('AI Feed — Users Report', 14, 17)
		doc.setFontSize(9)
		doc.text(`Total: ${rows.length} users   |   ${new Date().toLocaleString()}`, 14, 24)

		doc.autoTable({
			startY: 32,
			head: [['ID', 'Name', 'Email', 'Role', 'Status', 'Posts']],
			body: rows,
			theme: 'striped',
			headStyles: { fillColor: [108, 99, 255] },
			styles: { fontSize: 9, cellPadding: 3 },
			columnStyles: {
				3: { halign: 'center' },
				4: { halign: 'center' },
				5: { halign: 'right' }
			},
			didParseCell: (data) => {
				if (data.column.index === 4) {
					data.cell.styles.textColor = data.cell.raw === 'Active' ? [22, 163, 74] : [220, 38, 38]
					data.cell.styles.fontStyle = 'bold'
				}
			},
			margin: { left: 14, right: 14 },
		})

		const filename = `users_report_${now()}.pdf`
		doc.save(filename)
		logExport('PDF: Users', '✅ Downloaded: ' + filename)
	} catch (e) {
		logExport('PDF: Users', '❌ Failed: ' + e.message)
		alert('PDF fail: ' + e.message)
	} finally {
		btn.disabled = false; btn.textContent = '⬇ Generate PDF'
	}
}

// ========================
// PDF: ANALYTICS
// ========================
async function generateAnalyticsPDF() {
	const btn = document.getElementById('btnAnalyticsPDF')
	btn.disabled = true; btn.textContent = '⏳ Generating...'

	const days = document.getElementById('exportDays').value
	logExport('PDF: Analytics', 'generating...')

	try {
		const res = await fetch(`/api/admin/analytics?days=${days}`)
		const data = await res.json()

		const { jsPDF } = window.jspdf
		const doc = new jsPDF()

		// Header
		doc.setFillColor(34, 197, 94)
		doc.rect(0, 0, 210, 30, 'F')
		doc.setTextColor(255, 255, 255)
		doc.setFontSize(18)
		doc.setFont('helvetica', 'bold')
		doc.text('AI Feed — Analytics Report', 14, 18)
		doc.setFontSize(9)
		doc.text(`Period: Last ${days} days   |   ${new Date().toLocaleString()}`, 14, 25)

		// Summary
		doc.setTextColor(40, 40, 40)
		doc.setFontSize(13)
		doc.setFont('helvetica', 'bold')
		doc.text('Summary', 14, 42)

		const dailyVals = data.dailyValues || []
		const totalInPeriod = dailyVals.reduce((a, b) => a + b, 0)
		const avgDaily = dailyVals.length ? Math.round(totalInPeriod / dailyVals.length) : 0

		doc.autoTable({
			startY: 46,
			head: [['Metric', 'Value']],
			body: [
				['Total Engagements (all time)', data.totalEngagements],
				[`Engagements (last ${days} days)`, totalInPeriod],
				['Avg Daily Engagements', avgDaily],
				['Total Users', data.totalUsers],
				['Total Videos', data.totalPosts],
			],
			theme: 'striped',
			headStyles: { fillColor: [34, 197, 94] },
			columnStyles: { 1: { halign: 'right', fontStyle: 'bold' } },
			margin: { left: 14, right: 14 },
		})

		// Engagement by Type
		const typeMap = data.engagementByType || {}
		if (Object.keys(typeMap).length > 0) {
			doc.setFontSize(13)
			doc.setFont('helvetica', 'bold')
			doc.text('Engagement by Type', 14, doc.lastAutoTable.finalY + 14)
			doc.autoTable({
				startY: doc.lastAutoTable.finalY + 18,
				head: [['Type', 'Count', '% Share']],
				body: Object.entries(typeMap).map(([k, v]) => [
					k, v, totalInPeriod ? (v / totalInPeriod * 100).toFixed(1) + '%' : '—'
				]),
				theme: 'striped',
				headStyles: { fillColor: [108, 99, 255] },
				columnStyles: { 1: { halign: 'right' }, 2: { halign: 'right' } },
				margin: { left: 14, right: 14 },
			})
		}

		// Daily trend table
		const dailyLabels = data.dailyLabels || []
		if (dailyLabels.length > 0) {
			doc.setFontSize(13)
			doc.setFont('helvetica', 'bold')
			doc.text('Daily Engagement Trend', 14, doc.lastAutoTable.finalY + 14)
			doc.autoTable({
				startY: doc.lastAutoTable.finalY + 18,
				head: [['Date', 'Engagements']],
				body: dailyLabels.map((l, i) => [l, dailyVals[i] || 0]),
				theme: 'striped',
				headStyles: { fillColor: [245, 158, 11] },
				columnStyles: { 1: { halign: 'right' } },
				margin: { left: 14, right: 14 },
			})
		}

		// Top posts
		const topPosts = data.topPosts || []
		if (topPosts.length > 0) {
			doc.setFontSize(13)
			doc.setFont('helvetica', 'bold')
			doc.text('Top Posts', 14, doc.lastAutoTable.finalY + 14)
			doc.autoTable({
				startY: doc.lastAutoTable.finalY + 18,
				head: [['#', 'Post ID', 'Author', 'Content Preview', 'Engagements']],
				body: topPosts.map((p, i) => [
					i + 1, p.postId, p.userName || '—',
					(p.content || '').substring(0, 30),
					p.engagements
				]),
				theme: 'striped',
				headStyles: { fillColor: [239, 68, 68] },
				columnStyles: { 4: { halign: 'right' } },
				margin: { left: 14, right: 14 },
			})
		}

		const filename = `analytics_report_${days}days_${now()}.pdf`
		doc.save(filename)
		logExport('PDF: Analytics', '✅ Downloaded: ' + filename)
	} catch (e) {
		logExport('PDF: Analytics', '❌ Failed: ' + e.message)
		alert('PDF fail: ' + e.message)
		console.error(e)
	} finally {
		btn.disabled = false; btn.textContent = '⬇ Generate PDF'
	}
}

// ========================
// UTILS
// ========================
function triggerDownload(blob, filename) {
	const url = URL.createObjectURL(blob)
	const a = document.createElement('a')
	a.href = url; a.download = filename; a.click()
	URL.revokeObjectURL(url)
}

function getFilename(res) {
	const cd = res.headers.get('Content-Disposition') || ''
	const m = cd.match(/filename="?([^"]+)"?/)
	return m ? m[1] : null
}

function now() {
	return new Date().toISOString().slice(0, 16).replace('T', '_').replace(':', '')
}

function logExport(type, status) {
	const time = new Date().toLocaleTimeString()
	exportLog.unshift({ time, type, status })
	if (exportLog.length > 20) exportLog.pop()

	const el = document.getElementById('exportLog')
	if (!el) return
	el.innerHTML = exportLog.map(e =>
		`<div class="d-flex gap-3 py-1 border-bottom">
			<span class="text-muted" style="min-width:70px">${e.time}</span>
			<span class="fw-semibold" style="min-width:140px">${e.type}</span>
			<span>${e.status}</span>
		</div>`
	).join('')
}

function parseCSVLine(line) {
	const result = []
	let current = ''
	let inQuotes = false
	for (let i = 0; i < line.length; i++) {
		const ch = line[i]
		if (ch === '"') { inQuotes = !inQuotes }
		else if (ch === ',' && !inQuotes) { result.push(current.trim()); current = '' }
		else { current += ch }
	}
	result.push(current.trim())
	return result
}