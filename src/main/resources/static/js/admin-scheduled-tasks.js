document.addEventListener("DOMContentLoaded", loadTasks);

async function loadTasks() {
	const res = await fetch('/api/admin/tasks');
	const data = await res.json();

	const tbody = document.getElementById('taskTable');
	tbody.innerHTML = '';

	data.forEach(t => {
		tbody.innerHTML += `
	<tr>
		<td>${t.name}</td>
		<td>${t.description}</td>
		<td>
			<span class="${t.status === 'completed' ? 'text-success' : 'text-warning'}">
				${t.status}
			</span>
		</td>
		<td>${t.lastRun}</td>
		<td>
			<button class="btn btn-sm btn-success"
				onclick="runTask('${t.id}')">▶ Run</button>
		</td>
	</tr>
	`;
	});
}


function openCreate() {

	const id = prompt("Task ID (e.g. custom:job)");
	const name = prompt("Task Name");
	const desc = prompt("Description");
	const cron = prompt("Cron (optional)");

	if (!id) return;

	fetch('/api/admin/tasks', {
		method: 'POST',
		headers: {
			'Content-Type': 'application/json'
		},
		body: JSON.stringify({
			id, name, description: desc, cron
		})
	})
		.then(r => r.json())
		.then(res => {
			if (res.success) {
				alert("Task added");
				loadTasks();
			} else {
				alert(res.message);
			}
		});
}


async function runTask(name) {
	await fetch(`/api/admin/tasks/run/${name}`, { method: 'POST' });
	loadTasks();
}

async function toggleTask(name) {
	await fetch(`/api/admin/tasks/toggle/${name}`, { method: 'POST' });
	loadTasks();
}

async function deleteTask(name) {
	await fetch(`/api/admin/tasks/${name}`, { method: 'DELETE' });
	loadTasks();
}