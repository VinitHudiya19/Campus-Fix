(async () => {
    await App.start();

    const host = document.getElementById('table');
    const form = document.getElementById('edit-form');
    const dialog = new bootstrap.Modal(document.getElementById('edit-modal'));
    let configs = [];

    load();

    document.getElementById('check-now').addEventListener('click', runCheck);
    form.addEventListener('submit', save);
    ['durationHours', 'warningPercentage'].forEach(name =>
        form.elements[name].addEventListener('input', showPreview));

    async function load() {
        UI.loading(host, 'Loading targets…');
        try {
            configs = await Api.get('/api/sla');
            render();
        } catch (error) {
            UI.failed(host, error.message, load);
        }
    }

    function render() {
        if (configs.length === 0) {
            UI.empty(host, 'No targets configured', 'Restart the application to seed the defaults.');
            return;
        }

        host.innerHTML = `
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0">
            <thead>
              <tr><th>Priority</th><th>Target</th><th>Warns at</th><th class="text-end">Actions</th></tr>
            </thead>
            <tbody>
              ${configs.map((config, index) => `
                <tr>
                  <td>${UI.priority(config.priority, config.priorityLabel)}</td>
                  <td>${config.durationHours} hours</td>
                  <td>
                    ${config.warningPercentage}%
                    <span class="text-secondary small">
                      (after ${hoursInto(config)} hours)
                    </span>
                  </td>
                  <td class="text-end">
                    <button class="btn btn-sm btn-outline-secondary" type="button" data-edit="${index}">Edit</button>
                  </td>
                </tr>`).join('')}
            </tbody>
          </table>
        </div>`;

        host.querySelectorAll('[data-edit]').forEach(button =>
            button.addEventListener('click', () => openEdit(configs[Number(button.dataset.edit)])));
    }

    function hoursInto(config) {
        return Math.round(config.durationHours * config.warningPercentage / 100 * 10) / 10;
    }

    function openEdit(config) {
        form.dataset.priority = config.priority;
        document.getElementById('edit-title').textContent = config.priorityLabel + ' priority';
        form.elements.durationHours.value = config.durationHours;
        form.elements.warningPercentage.value = config.warningPercentage;

        UI.formError(form, null);
        UI.showFieldErrors(form, {});
        showPreview();
        dialog.show();
    }

    /*
     * Turns the percentage back into hours as it is typed. "75%" is abstract;
     * "warns after 18 hours" is the number the admin is actually deciding.
     */
    function showPreview() {
        const hours = Number(form.elements.durationHours.value);
        const percentage = Number(form.elements.warningPercentage.value);
        const preview = document.getElementById('warning-preview');

        if (!hours || !percentage) {
            preview.textContent = '';
            return;
        }
        const at = Math.round(hours * percentage / 100 * 10) / 10;
        preview.textContent = `A request starts showing as "due soon" after ${at} hours.`;
    }

    async function save(event) {
        event.preventDefault();
        const submit = document.getElementById('edit-submit');

        UI.formError(form, null);
        UI.showFieldErrors(form, {});

        UI.busy(submit, true, 'Saving…');
        try {
            await Api.put('/api/sla/' + form.dataset.priority, {
                durationHours: Number(form.elements.durationHours.value),
                warningPercentage: Number(form.elements.warningPercentage.value)
            });
            dialog.hide();
            UI.toast('Target updated. It applies to new requests.');
            load();
        } catch (error) {
            if (error.fieldErrors) {
                UI.showFieldErrors(form, error.fieldErrors);
                UI.formError(form, 'Please check the highlighted fields.');
            } else {
                UI.formError(form, error.message);
            }
        } finally {
            UI.busy(submit, false);
        }
    }

    async function runCheck() {
        const button = document.getElementById('check-now');
        UI.busy(button, true, 'Checking…');
        try {
            const result = await Api.post('/api/sla/check-now');
            UI.toast(result.escalated === 0
                ? 'Nothing is overdue that has not already been escalated.'
                : `${result.escalated} escalation(s) recorded.`);
        } catch (error) {
            UI.toast(error.message, 'danger');
        } finally {
            UI.busy(button, false);
        }
    }
})();
