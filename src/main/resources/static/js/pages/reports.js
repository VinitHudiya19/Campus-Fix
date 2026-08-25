(async () => {
    await App.start();

    const page = document.getElementById('page');
    const windowSelect = document.getElementById('window');
    let charts = [];

    /*
     * Muted, and deliberately not a rainbow. These charts sit next to tables of
     * numbers; saturated colours would pull the eye away from the figures that
     * actually matter. Red is reserved for "something is wrong".
     */
    const COLOURS = {
        good: '#2f7d4f',
        warn: '#b8860b',
        bad: '#a13b32',
        neutral: '#5a7a9a',
        muted: '#adb5bd'
    };

    load();
    windowSelect.addEventListener('change', load);

    async function load() {
        destroyCharts();
        UI.loading(page, 'Working out the numbers…');
        try {
            const report = await Api.get('/api/reports' + Api.query({ days: windowSelect.value }));
            render(report);
        } catch (error) {
            UI.failed(page, error.message, load);
        }
    }

    /**
     * Chart.js keeps a global registry of live charts. Re-rendering without
     * destroying the old ones leaves them attached to detached canvases, and
     * they slowly eat memory as the window filter is changed.
     */
    function destroyCharts() {
        charts.forEach(chart => chart.destroy());
        charts = [];
    }

    function render(report) {
        document.getElementById('scope-line').textContent =
            report.scope + ' · ' + windowLabel(report.windowDays);

        if (report.totalRequests === 0) {
            UI.empty(page, 'Nothing to report yet',
                'No requests were reported in this period. Try a longer window.');
            return;
        }

        page.innerHTML = `
        ${tiles(report)}
        <div class="row g-3 mb-3">
          <div class="col-lg-7">
            <div class="card h-100">
              <div class="card-header">Meeting the target, by department</div>
              <div class="card-body"><canvas id="chart-compliance" height="150"></canvas></div>
            </div>
          </div>
          <div class="col-lg-5">
            <div class="card h-100">
              <div class="card-header">Where requests are now</div>
              <div class="card-body"><canvas id="chart-status" height="150"></canvas></div>
            </div>
          </div>
        </div>

        <div class="card mb-3">
          <div class="card-header">Department detail</div>
          ${departmentTable(report)}
        </div>

        <div class="row g-3">
          <div class="col-lg-7">
            <div class="card h-100">
              <div class="card-header">What gets reported most</div>
              <div class="card-body"><canvas id="chart-categories" height="180"></canvas></div>
            </div>
          </div>
          <div class="col-lg-5">
            <div class="card h-100">
              <div class="card-header">Reading this page</div>
              <div class="card-body small text-secondary">
                <p><strong>Meeting the target</strong> counts only requests that have actually
                   been fixed, comparing when they were fixed against the deadline they were
                   given when reported.</p>
                <p><strong>Breached now</strong> is different — it counts requests that are late
                   at this moment and still unresolved, however long ago they were reported.
                   That is the number worth acting on today.</p>
                <p><strong>Came back</strong> counts requests a student reopened after being told
                   they were fixed. It is read from the activity history rather than from the
                   current status, so a request that was reopened and then properly fixed is
                   still counted.</p>
                <p class="mb-0">A department with no finished work shows “—” rather than 0%,
                   because there is nothing to judge it on yet.</p>
              </div>
            </div>
          </div>
        </div>`;

        drawCompliance(report);
        drawStatus(report);
        drawCategories(report);
    }

    function tiles(report) {
        const cards = [
            {
                label: 'Requests reported',
                value: report.totalRequests,
                hint: windowLabel(report.windowDays)
            },
            {
                label: 'Still open',
                value: report.openNow,
                hint: 'Not yet resolved'
            },
            {
                label: 'Late right now',
                value: report.breachedNow,
                hint: 'Past the deadline, unresolved',
                danger: report.breachedNow > 0
            },
            {
                label: 'Met the target',
                value: report.slaCompliancePercent === null ? '—' : report.slaCompliancePercent + '%',
                hint: report.averageResolutionHours === null
                    ? 'Nothing finished yet'
                    : 'Average ' + report.averageResolutionHours + ' hours to fix'
            }
        ];

        return '<div class="row g-3 mb-3">' + cards.map(card => `
            <div class="col-6 col-lg-3">
              <div class="card h-100">
                <div class="card-body">
                  <div class="text-secondary small mb-1">${UI.text(card.label)}</div>
                  <div class="h3 mb-0 ${card.danger ? 'text-danger' : ''}">${UI.text(card.value)}</div>
                  <div class="small text-secondary mt-1">${UI.text(card.hint)}</div>
                </div>
              </div>
            </div>`).join('') + '</div>';
    }

    function departmentTable(report) {
        const rows = report.departments.map(d => `
            <tr>
              <td>${UI.text(d.departmentName)}</td>
              <td class="text-end">${d.total}</td>
              <td class="text-end">${d.open}</td>
              <td class="text-end">${d.inProgress}</td>
              <td class="text-end ${d.breachedNow > 0 ? 'text-danger fw-semibold' : ''}">${d.breachedNow}</td>
              <td class="text-end">${complianceCell(d)}</td>
              <td class="text-end">${d.averageResolutionHours === null ? '—' : d.averageResolutionHours + ' h'}</td>
              <td class="text-end ${d.reopened > 0 ? 'text-warning-emphasis' : ''}">${d.reopened}</td>
            </tr>`).join('');

        return `
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr>
                <th>Department</th>
                <th class="text-end">Reported</th>
                <th class="text-end">Open</th>
                <th class="text-end">Working on</th>
                <th class="text-end">Late now</th>
                <th class="text-end">Met target</th>
                <th class="text-end">Avg to fix</th>
                <th class="text-end">Came back</th>
              </tr>
            </thead>
            <tbody>${rows}</tbody>
          </table>
        </div>`;
    }

    function complianceCell(department) {
        if (department.compliancePercent === null) {
            return '<span class="text-secondary" title="Nothing finished yet">—</span>';
        }
        const colour = department.compliancePercent >= 90 ? 'text-success'
            : department.compliancePercent >= 70 ? 'text-warning-emphasis' : 'text-danger';
        return `<span class="${colour} fw-semibold">${department.compliancePercent}%</span>`
            + `<div class="small text-secondary">${department.metTarget} of ${department.metTarget + department.missedTarget}</div>`;
    }

    function drawCompliance(report) {
        // Departments with nothing finished are left out rather than drawn as a
        // zero bar, which would read as total failure.
        const measured = report.departments.filter(d => d.compliancePercent !== null);
        const host = document.getElementById('chart-compliance');

        if (measured.length === 0) {
            host.parentElement.innerHTML =
                '<div class="state-panel"><div class="state-title">Nothing finished yet</div>'
                + '<div>Compliance can be measured once requests start being resolved.</div></div>';
            return;
        }

        charts.push(new Chart(host, {
            type: 'bar',
            data: {
                labels: measured.map(d => d.departmentName),
                datasets: [{
                    label: '% met on time',
                    data: measured.map(d => d.compliancePercent),
                    backgroundColor: measured.map(d => d.compliancePercent >= 90 ? COLOURS.good
                        : d.compliancePercent >= 70 ? COLOURS.warn : COLOURS.bad),
                    borderWidth: 0
                }]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    // Fixed 0–100. An auto-scaled axis makes 62% and 68% look
                    // dramatically different, which is a chart lying politely.
                    x: { min: 0, max: 100, ticks: { callback: value => value + '%' } }
                }
            }
        }));
    }

    function drawStatus(report) {
        const entries = Object.entries(report.byStatus).filter(([, count]) => count > 0);

        charts.push(new Chart(document.getElementById('chart-status'), {
            type: 'doughnut',
            data: {
                labels: entries.map(([label]) => label),
                datasets: [{
                    data: entries.map(([, count]) => count),
                    backgroundColor: entries.map(([label]) => statusColour(label)),
                    borderWidth: 1,
                    borderColor: '#fff'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { position: 'right', labels: { boxWidth: 12, font: { size: 11 } } } }
            }
        }));
    }

    function statusColour(label) {
        switch (label) {
            case 'Open': return COLOURS.neutral;
            case 'Assigned': return '#7a9cc6';
            case 'In Progress': return COLOURS.warn;
            case 'Resolved': return COLOURS.good;
            case 'Closed': return COLOURS.muted;
            case 'Reopened': return COLOURS.bad;
            default: return '#ced4da';
        }
    }

    function drawCategories(report) {
        charts.push(new Chart(document.getElementById('chart-categories'), {
            type: 'bar',
            data: {
                labels: report.topCategories.map(c => c.categoryName),
                datasets: [{
                    label: 'Requests',
                    data: report.topCategories.map(c => c.count),
                    backgroundColor: COLOURS.neutral,
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            // The department matters here: "Wi-Fi" alone does not
                            // say who is responsible for fixing it.
                            afterLabel: item => report.topCategories[item.dataIndex].departmentName
                        }
                    }
                },
                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        }));
    }

    function windowLabel(days) {
        if (days === 0) {
            return 'everything on record';
        }
        return 'last ' + days + ' days';
    }
})();
