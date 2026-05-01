document.addEventListener("DOMContentLoaded", loadAnalytics);

async function loadAnalytics() {
	try {
		const res = await fetch('/api/admin/search-analytics');
		const data = await res.json();

		renderTop(data.topSearches || []);
		renderRecent(data.recentSearches || []);
		renderTrending(data.trendingHashtags || []);

	} catch (e) {
		console.error("Analytics load error:", e);
	}
}

/* ================================
   TOP SEARCH TERMS
================================ */

function renderTop(list) {

	const box = document.getElementById('topSearches');
	box.innerHTML = '';

	if (!list.length) {
		box.innerHTML = '<div class="text-muted">No data</div>';
		return;
	}

	list.forEach(s => {
		box.innerHTML += `
			<div style="display:flex;justify-content:space-between;padding:6px 0;">
				<span>🔥 ${s.term}</span>
				<span style="color:#9ca3af">${s.count}</span>
			</div>
		`;
	});
}

/* ================================
   RECENT SEARCHES
================================ */

function renderRecent(list) {

	const box = document.getElementById('recentSearches');
	box.innerHTML = '';

	if (!list.length) {
		box.innerHTML = '<div class="text-muted">No data</div>';
		return;
	}

	list.forEach(s => {
		box.innerHTML += `
			<div style="padding:5px 0;">🕐 ${s}</div>
		`;
	});
}

/* ================================
   TRENDING HASHTAGS
================================ */

function renderTrending(list) {

	const box = document.getElementById('trendingHashtags');
	box.innerHTML = '';

	if (!list.length) {
		box.innerHTML = '<div class="text-muted">No data</div>';
		return;
	}

	list.forEach(t => {
		box.innerHTML += `
			<div style="display:flex;justify-content:space-between;padding:6px 0;">
				<span>#${t.tag}</span>
				<span style="color:#9ca3af">${t.score}</span>
			</div>
		`;
	});
}