(async () => {
    const user = await App.start();

    const id = UI.param('id');
    const page = document.getElementById('page');

    const actionModal = new bootstrap.Modal(document.getElementById('action-modal'));
    const assignModal = new bootstrap.Modal(document.getElementById('assign-modal'));
    const actionForm = document.getElementById('action-form');
    const assignForm = document.getElementById('assign-form');

    /*
     * The wording for each action. The server decides which of these are
     * *possible*; this only decides how they are described.
     */
    const ACTION_COPY = {
        START:   { title: 'Start work',       explainer: 'This marks the request as being worked on.', button: 'Start work' },
        RESOLVE: { title: 'Mark as resolved', explainer: 'The student will be asked to confirm the problem is fixed.', button: 'Mark resolved', noteLabel: 'What did you do?' },
        REJECT:  { title: 'Reject request',   explainer: 'Use this for duplicates or requests outside your department.', button: 'Reject', noteLabel: 'Why is this being rejected?' },
        CONFIRM: { title: 'Confirm the fix',  explainer: 'This closes the request for good. Only do this if the problem is really gone.', button: 'Yes, it is fixed' },
        REOPEN:  { title: 'Still not fixed',  explainer: 'The request goes back to the team that worked on it.', button: 'Reopen', noteLabel: 'What is still wrong?' }
    };

    if (!id) {
        UI.empty(page, 'No request chosen', 'Open a request from the list.');
        return;
    }

    if (UI.param('created')) {
        UI.toast('Your request has been sent.');
    }

    load();

    async function load() {
        UI.loading(page, 'Loading request…');
        try {
            const request = await Api.get('/api/requests/' + id);
            render(request);
            // These three are extra detail: the page is already usable without
            // them, so they load after and fail on their own.
            loadActions();
            loadTimeline();
            if (canAssign()) {
                loadAssignmentPanel();
            }
        } catch (error) {
            if (error.status === 404) {
                UI.empty(page, 'Request not found',
                    'It may have been removed, or it belongs to someone else.');
                return;
            }
            UI.failed(page, error.message, load);
        }
    }

    function render(request) {
        page.innerHTML = `
        <div class="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-4">
          <div>
            <div class="text-secondary small text-mono mb-1">${UI.text(request.requestNumber)}</div>
            <h1 class="h4 mb-2">${UI.text(request.title)}</h1>
            <div class="d-flex gap-2 flex-wrap">
              ${UI.statusBadge(request.status, request.statusLabel)}
              ${UI.slaBadge(request.slaState, request.slaStateLabel)}
              <span class="badge badge-status st-OPEN">${UI.text(request.priorityLabel)} priority</span>
            </div>
          </div>
          <a href="requests.html" class="btn btn-sm btn-outline-secondary">Back to list</a>
        </div>

        <div class="row g-3">
          <div class="col-lg-8">
            <div class="card mb-3">
              <div class="card-header">The problem</div>
              <div class="card-body">
                <p class="mb-0" style="white-space: pre-wrap">${UI.text(request.description)}</p>
              </div>
            </div>

            ${request.resolutionNote ? `
            <div class="card mb-3">
              <div class="card-header">What was done</div>
              <div class="card-body">
                <p class="mb-2" style="white-space: pre-wrap">${UI.text(request.resolutionNote)}</p>
                <div class="small text-secondary">Resolved ${UI.dateTime(request.resolvedAt)}</div>
              </div>
            </div>` : ''}

            ${request.rejectionReason ? `
            <div class="card mb-3 border-warning-subtle">
              <div class="card-header">Why this was rejected</div>
              <div class="card-body">
                <p class="mb-0" style="white-space: pre-wrap">${UI.text(request.rejectionReason)}</p>
              </div>
            </div>` : ''}

            <div class="card">
              <div class="card-header">History</div>
              <div class="card-body" id="timeline"></div>
            </div>
          </div>

          <div class="col-lg-4">
            <div class="card mb-3">
              <div class="card-header">What can you do?</div>
              <div class="card-body" id="actions"></div>
            </div>

            <div class="card mb-3">
              <div class="card-header">Details</div>
              <div class="card-body">
                <dl class="mb-0">
                  ${row('Reported by', UI.text(request.studentName))}
                  ${row('Reported', UI.dateTime(request.createdAt))}
                  ${row('Category', UI.text(request.categoryName))}
                  ${row('Team', UI.text(request.departmentName))}
                  ${row('Location', UI.text(request.locationName || 'Not given'))}
                  ${row('Assigned to', request.assignedTechnicianName
                        ? UI.text(request.assignedTechnicianName)
                        : '<span class="text-secondary">Nobody yet</span>')}
                  ${row('Due', `${UI.dateTime(request.dueAt)}<div class="small text-secondary">${UI.relative(request.dueAt)}</div>`)}
                  ${request.closedAt ? row('Closed', UI.dateTime(request.closedAt)) : ''}
                </dl>
              </div>
            </div>

            ${canAssign() ? `
            <div class="card">
              <div class="card-header">Assignment</div>
              <div class="card-body" id="assignment-panel"></div>
            </div>` : ''}
          </div>
        </div>`;
    }

    function row(label, value) {
        return `<div class="definition-row"><dt>${UI.text(label)}</dt><dd>${value}</dd></div>`;
    }

    /*
     * The buttons come from the server. This page never works out for itself
     * whether someone may resolve a request — that rule lives in Java, and
     * copying it here would mean two versions that eventually disagree.
     */
    async function loadActions() {
        const host = document.getElementById('actions');
        UI.loading(host, 'Checking…');
        try {
            const actions = await Api.get(`/api/requests/${id}/available-actions`);
            if (actions.length === 0) {
                UI.empty(host, 'Nothing to do right now',
                    'There is no action for you at this stage.');
                return;
            }
            host.innerHTML = '<div class="d-grid gap-2">' + actions.map(action => `
                <button type="button" class="btn ${action.action === 'REJECT' ? 'btn-outline-danger' : 'btn-primary'}"
                        data-action="${UI.text(action.action)}"
                        data-note-required="${action.noteRequired}">
                  ${UI.text(action.label)}
                </button>`).join('') + '</div>';

            host.querySelectorAll('[data-action]').forEach(button => {
                button.addEventListener('click', () => openAction(button.dataset.action,
                    button.dataset.noteRequired === 'true'));
            });
        } catch (error) {
            UI.failed(host, error.message, loadActions);
        }
    }

    function openAction(action, noteRequired) {
        const copy = ACTION_COPY[action] || { title: action, explainer: '', button: 'Confirm' };

        actionForm.dataset.action = action;
        actionForm.dataset.noteRequired = String(noteRequired);
        document.getElementById('action-title').textContent = copy.title;
        document.getElementById('action-explainer').textContent = copy.explainer;
        document.getElementById('action-submit').textContent = copy.button;
        document.getElementById('note-label').textContent = copy.noteLabel || 'Note (optional)';
        document.getElementById('note-wrap').classList.toggle('d-none', !noteRequired && !copy.noteLabel);
        document.getElementById('note').value = '';

        UI.formError(actionForm, null);
        UI.showFieldErrors(actionForm, {});
        actionModal.show();
    }

    actionForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        const action = actionForm.dataset.action;
        const noteRequired = actionForm.dataset.noteRequired === 'true';
        const note = document.getElementById('note').value.trim();
        const submit = document.getElementById('action-submit');

        if (noteRequired && !note) {
            UI.formError(actionForm, 'Please fill this in before continuing.');
            return;
        }

        UI.busy(submit, true, 'Saving…');
        try {
            await Api.post(`/api/requests/${id}/${action.toLowerCase()}`, { note: note || null });
            actionModal.hide();
            UI.toast('Request updated.');
            load();
        } catch (error) {
            UI.formError(actionForm, error.message);
        } finally {
            UI.busy(submit, false);
        }
    });

    async function loadTimeline() {
        const host = document.getElementById('timeline');
        UI.loading(host, 'Loading history…');
        try {
            const entries = await Api.get(`/api/requests/${id}/timeline`);
            if (entries.length === 0) {
                UI.empty(host, 'Nothing has happened yet', 'Activity will show up here.');
                return;
            }
            host.innerHTML = '<ul class="timeline">' + entries.map(entry => `
                <li class="${entry.actorName === 'System' ? 'system' : ''}">
                  <div>${UI.text(entry.message)}</div>
                  <div class="tl-meta">${UI.text(entry.actorName)} · ${UI.dateTime(entry.createdAt)}</div>
                </li>`).join('') + '</ul>';
        } catch (error) {
            UI.failed(host, error.message, loadTimeline);
        }
    }

    async function loadAssignmentPanel() {
        const host = document.getElementById('assignment-panel');
        if (!host) {
            return;
        }
        UI.loading(host, 'Loading…');
        try {
            const history = await Api.get(`/api/requests/${id}/assignments`);
            const active = history.find(entry => entry.active);

            const historyMarkup = history.length === 0
                ? '<p class="small text-secondary mb-3">Nobody has been assigned yet.</p>'
                : '<ul class="list-unstyled small mb-3">' + history.map(entry => `
                    <li class="mb-2">
                      <div>${UI.text(entry.technicianName)}${entry.active ? ' <span class="text-success">· current</span>' : ''}</div>
                      <div class="text-secondary">
                        by ${UI.text(entry.assignedByName)} · ${UI.dateTime(entry.assignedAt)}
                      </div>
                      ${entry.note ? `<div class="text-secondary fst-italic">${UI.text(entry.note)}</div>` : ''}
                    </li>`).join('') + '</ul>';

            host.innerHTML = historyMarkup + `
                <div class="d-grid gap-2">
                  <button class="btn btn-sm btn-primary" type="button" id="assign-open">
                    ${active ? 'Reassign' : 'Assign a technician'}
                  </button>
                  ${active ? '<button class="btn btn-sm btn-outline-secondary" type="button" id="unassign">Remove assignment</button>' : ''}
                </div>`;

            document.getElementById('assign-open').addEventListener('click', openAssign);
            const unassign = document.getElementById('unassign');
            if (unassign) {
                unassign.addEventListener('click', doUnassign);
            }
        } catch (error) {
            UI.failed(host, error.message, loadAssignmentPanel);
        }
    }

    async function openAssign() {
        const select = document.getElementById('technicianId');
        select.innerHTML = '<option value="">Loading…</option>';
        UI.formError(assignForm, null);
        document.getElementById('assign-note').value = '';
        assignModal.show();

        try {
            const technicians = await Api.get(`/api/requests/${id}/assignable-technicians`);
            if (technicians.length === 0) {
                select.innerHTML = '<option value="">No technicians in this department</option>';
                UI.formError(assignForm, 'This department has no active technicians. Add one first.');
                document.getElementById('assign-submit').disabled = true;
                return;
            }
            document.getElementById('assign-submit').disabled = false;
            select.innerHTML = '<option value="">Choose…</option>' + technicians.map(t =>
                `<option value="${t.id}">${UI.text(t.fullName)} (${t.openRequests} open)</option>`).join('');
        } catch (error) {
            select.innerHTML = '<option value="">Could not load technicians</option>';
            UI.formError(assignForm, error.message);
        }
    }

    assignForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        const submit = document.getElementById('assign-submit');
        const technicianId = document.getElementById('technicianId').value;

        if (!technicianId) {
            UI.formError(assignForm, 'Choose a technician.');
            return;
        }

        UI.busy(submit, true, 'Assigning…');
        try {
            await Api.post(`/api/requests/${id}/assign`, {
                technicianId: Number(technicianId),
                note: document.getElementById('assign-note').value.trim() || null
            });
            assignModal.hide();
            UI.toast('Request assigned.');
            load();
        } catch (error) {
            UI.formError(assignForm, error.message);
        } finally {
            UI.busy(submit, false);
        }
    });

    async function doUnassign() {
        if (!window.confirm('Send this request back to the unassigned queue?')) {
            return;
        }
        try {
            await Api.del(`/api/requests/${id}/assignment`);
            UI.toast('Assignment removed.');
            load();
        } catch (error) {
            UI.toast(error.message, 'danger');
        }
    }

    function canAssign() {
        return user.role === 'ADMIN' || user.role === 'DEPARTMENT_HEAD';
    }
})();
