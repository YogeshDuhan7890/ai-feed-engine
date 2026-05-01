let tickets = [];
let activeTicketId = null;

// 🚀 Init
document.addEventListener("DOMContentLoaded", () => {
	loadTickets();
	setInterval(loadTickets, 5000); 
});

// 📡 Load tickets
function loadTickets() {
	fetch("/api/admin/support/tickets")
		.then(res => res.json())
		.then(data => {
			tickets = data;
			renderTicketList(data);
			document.getElementById("ticketCount").innerText = data.length;
		})
		.catch(err => console.error("Load tickets error", err));
}

// 🧾 Render ticket list
function renderTicketList(data) {
	const container = document.getElementById("ticketList");

	if (!data.length) {
		container.innerHTML = `<div class="text-muted text-center py-4">No tickets yet</div>`;
		return;
	}

	let html = "";

	data.forEach(t => {
		let parsed = {};
		try { parsed = JSON.parse(t.data); } catch { }

		html += `
            <div class="p-2 border-bottom ticket-item"
                 onclick="openTicket('${t.id}')"
                 style="cursor:pointer">
                <div class="fw-semibold small">${parsed.subject || t.id}</div>
                <div class="text-muted small">
                    ${parsed.status || 'open'} • ${t.messageCount} msgs
                </div>
            </div>
        `;
	});

	container.innerHTML = html;
}

// 📂 Open ticket
function openTicket(ticketId) {
	activeTicketId = ticketId;

	const ticket = tickets.find(t => t.id === ticketId);
	let parsed = {};
	try { parsed = JSON.parse(ticket.data); } catch { }

	document.getElementById("activeTicketTitle").innerText =
		`${parsed.subject || ticketId} (${parsed.status})`;

	document.getElementById("replyArea").style.display = "flex";
	document.getElementById("closeTicketBtn").style.display =
		parsed.status === "open" ? "block" : "none";

	loadMessages(ticketId);
}

// 💬 Load messages
function loadMessages(ticketId) {
	fetch(`/api/admin/support/messages/${ticketId}`)
		.then(res => res.json())
		.then(data => renderMessages(data))
		.catch(err => console.error(err));
}

// 🧾 Render messages
function renderMessages(messages) {
	const area = document.getElementById("messageArea");

	if (!messages.length) {
		area.innerHTML = `<div class="text-muted text-center">No messages</div>`;
		return;
	}

	let html = "";

	messages.forEach(m => {
		let msg = {};
		try { msg = JSON.parse(m); } catch { }

		const isAdmin = msg.from === "admin";

		html += `
            <div class="d-flex ${isAdmin ? 'justify-content-end' : 'justify-content-start'} mb-2">
                <div style="
                    max-width:70%;
                    padding:8px 12px;
                    border-radius:10px;
                    background:${isAdmin ? '#d1e7ff' : '#f1f1f1'};
                    font-size:13px;">
                    
                    <div>${msg.text || ''}</div>
                    <div class="text-muted small text-end">${msg.time || ''}</div>
                </div>
            </div>
        `;
	});

	area.innerHTML = html;
	area.scrollTop = area.scrollHeight; // auto scroll
}

// ✉️ Send reply
function sendReply() {
	const input = document.getElementById("replyInput");
	const text = input.value.trim();

	if (!text || !activeTicketId) return;

	fetch(`/api/admin/support/reply/${activeTicketId}`, {
		method: "POST",
		headers: {
			"Content-Type": "application/json",
			[CSRF_HEADER]: CSRF_TOKEN
		},
		body: JSON.stringify({ text })
	})
		.then(res => res.json())
		.then(res => {
			if (res.success) {
				input.value = "";
				loadMessages(activeTicketId);
			} else {
				alert(res.message);
			}
		})
		.catch(err => console.error(err));
}

// ✅ Close ticket
function closeTicket() {
	if (!activeTicketId) return;

	fetch(`/api/admin/support/close/${activeTicketId}`, {
		method: "POST",
		headers: {
			[CSRF_HEADER]: CSRF_TOKEN
		}
	})
		.then(res => res.json())
		.then(() => {
			loadTickets();
			openTicket(activeTicketId);
		})
		.catch(err => console.error(err));
}

// 🧪 Create test ticket
function createTestTicket() {
	fetch("/api/admin/support/ticket", {
		method: "POST",
		headers: {
			"Content-Type": "application/json",
			[CSRF_HEADER]: CSRF_TOKEN
		},
		body: JSON.stringify({
			userId: "101",
			subject: "Test Issue",
			message: "This is a test support message"
		})
	})
		.then(res => res.json())
		.then(() => loadTickets())
		.catch(err => console.error(err));
}