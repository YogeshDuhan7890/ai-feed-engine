/* ================================
   admin-ip-ban.js (REDIS FINAL)
================================ */

let allIPs = [];

document.addEventListener("DOMContentLoaded", loadIPs);

/* ================================
   LOAD BANNED IPS
================================ */

async function loadIPs() {
	try {
		const res = await fetch('/api/admin/ip-ban');
		const data = await res.json();

		allIPs = data;
		renderIPs(data);

		document.getElementById('banCount').innerText = data.length + ' Banned IPs';

	} catch (e) {
		console.error(e);
	}
}

/* ================================
   RENDER LIST
================================ */

function renderIPs(list) {

	const box = document.getElementById('ipList');
	box.innerHTML = '';

	if (!list.length) {
		box.innerHTML = '<div class="text-muted text-center py-3">No banned IPs</div>';
		return;
	}

	list.forEach(ip => {

		const div = document.createElement('div');

		div.style.cssText = `
			display:flex;
			justify-content:space-between;
			align-items:center;
			padding:10px;
			border-bottom:1px solid rgba(255,255,255,0.08);
		`;

		div.innerHTML = `
			<div>
				<b>${ip.ip}</b><br>
				<span style="font-size:12px;color:#9ca3af">${ip.reason || '-'}</span>
			</div>

			<button class="btn btn-sm btn-outline-danger"
				onclick="unbanIP('${ip.ip}')">
				Unban
			</button>
		`;

		box.appendChild(div);
	});
}

/* ================================
   BAN IP
================================ */

async function banIP() {

	const ip = document.getElementById('newIP').value.trim();
	const reason = document.getElementById('banReason').value.trim();

	if (!ip) return alert("IP enter karo");

	try {
		await fetch('/api/admin/ip-ban', {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
				[getCsrfHeader()]: getCsrfToken()
			},
			body: JSON.stringify({ ip, reason })
		});

		document.getElementById('newIP').value = '';
		document.getElementById('banReason').value = '';

		loadIPs();

	} catch (e) {
		console.error(e);
	}
}

/* ================================
   UNBAN
================================ */

async function unbanIP(ip) {

	if (!confirm("Unban karna hai?")) return;

	await fetch(`/api/admin/ip-ban/${encodeURIComponent(ip)}`, {
		method: 'DELETE',
		headers: {
			[getCsrfHeader()]: getCsrfToken()
		}
	});

	loadIPs();
}

/* ================================
   SEARCH
================================ */

function filterIPs(q) {

	q = q.toLowerCase();

	renderIPs(allIPs.filter(i =>
		i.ip.toLowerCase().includes(q) ||
		(i.reason || '').toLowerCase().includes(q)
	));
}

/* ================================
   CSRF
================================ */

function getCsrfToken() {
	return document.querySelector('meta[name="_csrf"]').content;
}

function getCsrfHeader() {
	return document.querySelector('meta[name="_csrf_header"]').content;
}