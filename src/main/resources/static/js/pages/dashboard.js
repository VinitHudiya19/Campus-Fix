(async () => {
    const user = await App.start();

    document.getElementById('greeting').textContent = firstNameOf(user.fullName) + ', welcome back';
    document.getElementById('subtitle').textContent = subtitleFor(user);

    if (user.role === 'STUDENT') {
        document.getElementById('primary-action').innerHTML =
            '<a href="request-new.html" class="btn btn-primary">Report a problem</a>';
    }

    renderStats(user);
    renderRecent(user);

    /*
     * Each tile is a count, and a count is just a filtered search with one row
     * asked for — the API already returns totalElements, so no separate
     * statistics endpoint is needed for four numbers.
     */
    async function renderStats(user) {
        const host = document.getElementById('stats');
        const tiles = tilesFor(user);

        host.innerHTML = tiles.map((tile, index) => `
            <div class="col-6 col-lg-3">
              <div class="card h-100">
                <div class="card-body">
                  <div class="text-secondary small mb-1">${UI.text(tile.label)}</div>
                  <div class="h3 mb-0" id="stat-${index}">
                    <span class="placeholder col-4 bg-secondary-subtle"></span>
                  </div>
                  <div class="small text-secondary mt-1">${UI.text(tile.hint)}</div>
                </div>
              </div>
            </div>`).join('');

        await Promise.all(tiles.map(async (tile, index) => {
            const slot = document.getElementById('stat-' + index);
            try {
                const page = await Api.get('/api/requests' + Api.query({ ...tile.filter, size: 1 }));
                slot.textContent = page.totalElements;
            } catch (error) {
                // A tile that cannot load says so rather than showing a stale or
                // invented zero.
                slot.innerHTML = '<span class="text-secondary fs-6">unavailable</span>';
            }
        }));
    }

    function tilesFor(user) {
        switch (user.role) {
            case 'STUDENT':
                return [
                    { label: 'Open', filter: { status: 'OPEN' }, hint: 'Waiting to be picked up' },
                    { label: 'Being worked on', filter: { status: 'IN_PROGRESS' }, hint: 'Someone is on it' },
                    { label: 'Needs your answer', filter: { status: 'RESOLVED' }, hint: 'Confirm or reopen' },
                    { label: 'Closed', filter: { status: 'CLOSED' }, hint: 'Finished' }
                ];
            case 'TECHNICIAN':
                return [
                    { label: 'Assigned to you', filter: { status: 'ASSIGNED' }, hint: 'Not started yet' },
                    { label: 'In progress', filter: { status: 'IN_PROGRESS' }, hint: 'Started' },
                    { label: 'Reopened', filter: { status: 'REOPENED' }, hint: 'Came back to you' },
                    { label: 'Awaiting confirmation', filter: { status: 'RESOLVED' }, hint: 'With the student' }
                ];
            case 'DEPARTMENT_HEAD':
                return [
                    { label: 'Unassigned', filter: { unassignedOnly: true }, hint: 'Needs a technician' },
                    { label: 'In progress', filter: { status: 'IN_PROGRESS' }, hint: 'Being worked on' },
                    { label: 'Reopened', filter: { status: 'REOPENED' }, hint: 'Not fixed first time' },
                    { label: 'Awaiting confirmation', filter: { status: 'RESOLVED' }, hint: 'With the student' }
                ];
            default:
                return [
                    { label: 'Open', filter: { status: 'OPEN' }, hint: 'Across the campus' },
                    { label: 'Unassigned', filter: { unassignedOnly: true }, hint: 'Needs a technician' },
                    { label: 'In progress', filter: { status: 'IN_PROGRESS' }, hint: 'Being worked on' },
                    { label: 'Closed', filter: { status: 'CLOSED' }, hint: 'Finished' }
                ];
        }
    }

    async function renderRecent(user) {
        const host = document.getElementById('recent');
        document.getElementById('recent-title').textContent =
            user.role === 'STUDENT' ? 'Your recent requests' : 'Most recent';

        UI.loading(host, 'Loading requests…');
        try {
            const page = await Api.get('/api/requests' + Api.query({ size: 5 }));
            if (page.content.length === 0) {
                UI.empty(host, emptyTitleFor(user), emptyHintFor(user));
                return;
            }
            host.innerHTML = tableOf(page.content);
        } catch (error) {
            UI.failed(host, error.message, () => renderRecent(user));
        }
    }

    function tableOf(rows) {
        const body = rows.map(row => `
            <tr>
              <td class="text-mono"><a href="request-detail.html?id=${row.id}">${UI.text(row.requestNumber)}</a></td>
              <td>${UI.text(row.title)}</td>
              <td>${UI.text(row.categoryName)}</td>
              <td>${UI.statusBadge(row.status, row.statusLabel)}</td>
              <td>${UI.slaBadge(row.slaState, row.slaStateLabel)}</td>
              <td class="text-secondary small">${UI.dateTime(row.createdAt)}</td>
            </tr>`).join('');

        return `
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr>
                <th>Number</th><th>Title</th><th>Category</th>
                <th>Status</th><th>SLA</th><th>Reported</th>
              </tr>
            </thead>
            <tbody>${body}</tbody>
          </table>
        </div>`;
    }

    function subtitleFor(user) {
        switch (user.role) {
            case 'STUDENT': return 'Everything you have reported, and where it has got to.';
            case 'TECHNICIAN': return 'The work assigned to you in ' + (user.departmentName || 'your department') + '.';
            case 'DEPARTMENT_HEAD': return 'The queue for ' + (user.departmentName || 'your department') + '.';
            default: return 'Every request across the campus.';
        }
    }

    function emptyTitleFor(user) {
        switch (user.role) {
            case 'STUDENT': return 'You have not reported anything yet';
            case 'TECHNICIAN': return 'Nothing is assigned to you';
            case 'DEPARTMENT_HEAD': return 'No requests for your department';
            default: return 'No requests have been reported yet';
        }
    }

    function emptyHintFor(user) {
        switch (user.role) {
            case 'STUDENT': return 'Use "Report a problem" when something needs fixing.';
            case 'TECHNICIAN': return 'Your department head assigns work here.';
            default: return 'They will appear here as students report problems.';
        }
    }

    function firstNameOf(fullName) {
        return (fullName || '').trim().split(/\s+/)[0] || 'Hello';
    }
})();
