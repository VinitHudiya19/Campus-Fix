(async () => {
    const user = await App.start();

    const filters = document.getElementById('filters');
    const results = document.getElementById('results');
    const pager = document.getElementById('pager');
    let page = 0;

    document.getElementById('page-title').textContent = titleFor(user);
    document.getElementById('page-subtitle').textContent = subtitleFor(user);

    if (user.role === 'STUDENT') {
        document.getElementById('primary-action').innerHTML =
            '<a href="request-new.html" class="btn btn-primary">Report a problem</a>';
    }

    // "Unassigned only" is a queue the department head and admin work through.
    // A student has no use for it, so the control is not shown to them.
    if (user.role === 'DEPARTMENT_HEAD' || user.role === 'ADMIN') {
        document.getElementById('unassigned-wrap').classList.remove('d-none');
    }

    await fillDropdowns();
    load();

    filters.addEventListener('submit', (event) => {
        event.preventDefault();
        page = 0;
        load();
    });

    document.getElementById('reset').addEventListener('click', () => {
        filters.reset();
        document.getElementById('unassignedOnly').checked = false;
        page = 0;
        load();
    });

    document.getElementById('prev').addEventListener('click', () => { page--; load(); });
    document.getElementById('next').addEventListener('click', () => { page++; load(); });

    /*
     * The dropdowns are filled from the server rather than hardcoded in this
     * file. The status and priority lists live in Java enums; repeating them
     * here would mean two lists that drift apart the first time one changes.
     */
    async function fillDropdowns() {
        try {
            const [statuses, priorities, categories] = await Promise.all([
                Api.get('/api/requests/statuses'),
                Api.get('/api/requests/priorities'),
                Api.get('/api/categories?activeOnly=true')
            ]);
            appendOptions('status', statuses.map(s => ({ value: s.value, label: s.label })));
            appendOptions('priority', priorities.map(p => ({ value: p.value, label: p.label })));
            appendOptions('categoryId', categories.map(c => ({ value: c.id, label: c.name })));
        } catch (error) {
            // Filters are a convenience. If they cannot load, the list still can,
            // so this does not stop the page.
            UI.toast('Filter options could not be loaded.', 'warning');
        }
    }

    function appendOptions(id, options) {
        const select = document.getElementById(id);
        options.forEach(option => {
            const node = document.createElement('option');
            node.value = option.value;
            node.textContent = option.label;
            select.appendChild(node);
        });
    }

    async function load() {
        UI.loading(results, 'Loading requests…');
        pager.classList.add('d-none');

        const query = {
            status: filters.status.value,
            categoryId: filters.categoryId.value,
            priority: filters.priority.value,
            page,
            size: 20
        };
        if (document.getElementById('unassignedOnly').checked) {
            query.unassignedOnly = true;
        }

        try {
            const result = await Api.get('/api/requests' + Api.query(query));
            render(result);
        } catch (error) {
            UI.failed(results, error.message, load);
        }
    }

    function render(result) {
        if (result.content.length === 0) {
            UI.empty(results, 'Nothing matches',
                anyFilterSet() ? 'Try clearing the filters.' : emptyHintFor(user));
            return;
        }

        const showAssignee = user.role !== 'STUDENT';
        const rows = result.content.map(row => `
            <tr>
              <td class="text-mono"><a href="request-detail.html?id=${row.id}">${UI.text(row.requestNumber)}</a></td>
              <td>
                <div>${UI.text(row.title)}</div>
                <div class="small text-secondary">${UI.text(row.locationName || 'No location given')}</div>
              </td>
              <td>
                <div>${UI.text(row.categoryName)}</div>
                <div class="small text-secondary">${UI.text(row.departmentName)}</div>
              </td>
              ${showAssignee ? `<td>${row.assignedTechnicianName
                    ? UI.text(row.assignedTechnicianName)
                    : '<span class="text-secondary">Unassigned</span>'}</td>` : ''}
              <td>${UI.priority(row.priority, row.priorityLabel)}</td>
              <td>${UI.statusBadge(row.status, row.statusLabel)}</td>
              <td>
                ${UI.slaBadge(row.slaState, row.slaStateLabel)}
                <div class="small text-secondary">${UI.relative(row.dueAt)}</div>
              </td>
            </tr>`).join('');

        results.innerHTML = `
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr>
                <th>Number</th><th>Problem</th><th>Category</th>
                ${showAssignee ? '<th>Assigned to</th>' : ''}
                <th>Priority</th><th>Status</th><th>Due</th>
              </tr>
            </thead>
            <tbody>${rows}</tbody>
          </table>
        </div>`;

        renderPager(result);
    }

    function renderPager(result) {
        if (result.totalPages <= 1) {
            pager.classList.add('d-none');
            return;
        }
        pager.classList.remove('d-none');
        document.getElementById('pager-info').textContent =
            `Page ${result.page + 1} of ${result.totalPages} · ${result.totalElements} requests`;
        document.getElementById('prev').disabled = result.page === 0;
        document.getElementById('next').disabled = result.page + 1 >= result.totalPages;
    }

    function anyFilterSet() {
        return filters.status.value || filters.categoryId.value || filters.priority.value
            || document.getElementById('unassignedOnly').checked;
    }

    function titleFor(user) {
        if (user.role === 'STUDENT') return 'My requests';
        if (user.role === 'TECHNICIAN') return 'My work';
        return 'Requests';
    }

    function subtitleFor(user) {
        switch (user.role) {
            case 'STUDENT': return 'Everything you have reported.';
            case 'TECHNICIAN': return 'Requests assigned to you.';
            case 'DEPARTMENT_HEAD': return 'Everything for ' + (user.departmentName || 'your department') + '.';
            default: return 'Every request across the campus.';
        }
    }

    function emptyHintFor(user) {
        switch (user.role) {
            case 'STUDENT': return 'Use "Report a problem" when something needs fixing.';
            case 'TECHNICIAN': return 'Your department head assigns work to you here.';
            default: return 'Requests appear here as students report problems.';
        }
    }
})();
