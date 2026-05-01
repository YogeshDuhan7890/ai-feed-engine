/* ================================
   admin-reports.js (FINAL PRO)
================================ */

var currentStatus = 'pending';
var currentPage = 0;

let reportsChart;
let pendingCount = 0;
let resolvedCount = 0;

document.addEventListener("DOMContentLoaded", () => {
	loadReports('pending');
});

/* ================================
   LOAD REPORTS
================================ */

async function loadReports(status = 'pending', btn = null) {

	currentStatus = status;

	const container = document.getElementById('reportsContainer');
	const loading = document.getElementById('reportsLoading');
	const empty = document.getElementById('reportsEmpty');

	if (!container) return;

	// UI reset
	container.innerHTML = '';
	empty.style.display = 'none';
	loading.style.display = 'block';

	// active tab
	document.querySelectorAll('.active-tab').forEach(b => b.classList.remove('active-tab'));
	if (btn) btn.classList.add('active-tab');

	try {
		const res = await fetch(`/api/report/${status}?page=${currentPage}`);
		const data = await res.json();

		loading.style.display = 'none';

		if (!data.length) {
			empty.style.display = 'block';
			return;
		}

		renderReports(data);

		// counts
		if (status === 'pending') pendingCount = data.length;
		if (status === 'resolved') resolvedCount = data.length;

		renderChart();

	} catch (e) {
		console.error(e);
		container.innerHTML = '<div style="color:#f43f5e;padding:20px">Load failed</div>';
	}
}

/* ================================
   RENDER REPORTS
================================ */

function renderReports(list) {

	const container = document.getElementById('reportsContainer');
	container.innerHTML = '';

	list.forEach(r => {
		container.appendChild(buildReportCard(r));
	});
}

/* ================================
   BUILD CARD
================================ */

function buildReportCard(r) {

	const isResolved = r.status === 'RESOLVED' || r.status === 'DISMISSED';

	const isHigh =
		r.reason === 'VIOLENCE' ||
		r.reason === 'HATE' ||
		r.reason === 'HARASSMENT';

	const card = document.createElement('div');
	card.className = 'report-card';

	card.innerHTML = `
		<div class="report-left">

			<div class="report-tags">
				<span class="tag">${r.targetType}</span>
				<span class="tag">${r.reason}</span>

				${isHigh ? '<span class="tag high">HIGH</span>' : ''}

				<span class="tag ${isResolved ? 'resolved' : ''}">
					${r.status}
				</span>
			</div>

			<div style="font-size:13px;color:#cbd5e1">
				${esc(r.description || 'No description')}
			</div>

			<div style="font-size:11px;color:#64748b">
				${fmtTime(r.createdAt)}
			</div>

		</div>

		<div class="report-actions">

			<a href="${r.targetType === 'POST' ? '/feed#post-' + r.targetId : '/profile/user/' + r.targetId}"
			   target="_blank"
			   class="btn btn-outline-light btn-sm">
			   View
			</a>

			${!isResolved ? `
				<button class="btn btn-success btn-sm"
					onclick="resolveReport(${r.id}, 'RESOLVED', this)">
					✓
				</button>

				<button class="btn btn-outline-danger btn-sm"
					onclick="resolveReport(${r.id}, 'DISMISSED', this)">
					✕
				</button>
			` : ''}

		</div>
	`;

	return card;
}

/* ================================
   RESOLVE / DISMISS
================================ */

async function resolveReport(id, status, btn) {

	btn.disabled = true;

	try {
		const csrf = document.querySelector('meta[name="_csrf"]').content;
		const header = document.querySelector('meta[name="_csrf_header"]').content;

		await fetch(`/api/report/${id}/resolve`, {
			method: 'POST',
			headers: { [header]: csrf },
			body: JSON.stringify({ status })
		});

		btn.closest('div').remove();

		toast("Updated ✓");

	} catch (e) {
		toast("Error ❌");
	}

	btn.disabled = false;
}

/* ================================
   CHART
================================ */

function renderChart() {

	const ctx = document.getElementById('reportsChart');
	if (!ctx) return;

	if (reportsChart) reportsChart.destroy();

	reportsChart = new Chart(ctx, {
		type: 'doughnut',
		data: {
			labels: ['Pending', 'Resolved'],
			datasets: [{
				data: [pendingCount, resolvedCount],
				backgroundColor: ['#ef4444', '#10b981']
			}]
		},
		options: {
			responsive: true,
			maintainAspectRatio: false
		}
	});
}

/* ================================
   HELPERS
================================ */

function fmtTime(t) {
	if (!t) return '';
	return t.replace('T', ' ').substring(0, 16);
}

function esc(s) {
	return String(s || '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;');
}

function toast(msg) {
	const t = document.getElementById('adminToast');
	if (!t) return;
	t.textContent = msg;
	t.style.display = 'block';
	setTimeout(() => t.style.display = 'none', 2000);
}