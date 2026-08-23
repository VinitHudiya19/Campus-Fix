/*
 * A reusable table-plus-form for the admin screens.
 *
 * Departments, categories, locations and users are the same screen four times:
 * list the rows, open a dialog to add or edit one, deactivate it, bring it back.
 * Written out four times, they would drift — one would lose its empty state,
 * another would forget to show field errors.
 *
 * Each page supplies a small configuration and nothing else.
 */
const Crud = (() => {

    function mount(config) {
        const host = config.host;
        let rows = [];
        let showInactive = false;

        const modal = buildModal(config);
        const dialog = new bootstrap.Modal(modal);
        const form = modal.querySelector('form');

        const toggle = document.getElementById('show-inactive');
        if (toggle) {
            toggle.addEventListener('change', () => {
                showInactive = toggle.checked;
                render();
            });
        }

        const addButton = document.getElementById('add-button');
        if (addButton) {
            addButton.addEventListener('click', () => openForm(null));
        }

        form.addEventListener('submit', onSubmit);

        load();

        async function load() {
            UI.loading(host, 'Loading…');
            try {
                rows = await config.load();
                render();
            } catch (error) {
                UI.failed(host, error.message, load);
            }
        }

        function visibleRows() {
            return showInactive ? rows : rows.filter(row => row.active !== false);
        }

        function render() {
            const data = visibleRows();
            if (data.length === 0) {
                UI.empty(host,
                    rows.length === 0 ? config.emptyTitle : 'Nothing to show',
                    rows.length === 0 ? config.emptyHint : 'All of these are inactive. Tick "show inactive" to see them.');
                return;
            }

            const header = config.columns.map(column => `<th>${UI.text(column.label)}</th>`).join('');
            const body = data.map((row, index) => `
                <tr class="${row.active === false ? 'text-secondary' : ''}">
                  ${config.columns.map(column => `<td>${column.render(row)}</td>`).join('')}
                  <td class="text-end text-nowrap">
                    <button class="btn btn-sm btn-outline-secondary" type="button" data-edit="${index}">Edit</button>
                    ${row.active === false
                        ? `<button class="btn btn-sm btn-outline-success ms-1" type="button" data-activate="${index}">Restore</button>`
                        : `<button class="btn btn-sm btn-outline-danger ms-1" type="button" data-deactivate="${index}">Deactivate</button>`}
                  </td>
                </tr>`).join('');

            host.innerHTML = `
                <div class="table-responsive">
                  <table class="table table-hover align-middle mb-0">
                    <thead><tr>${header}<th class="text-end">Actions</th></tr></thead>
                    <tbody>${body}</tbody>
                  </table>
                </div>`;

            host.querySelectorAll('[data-edit]').forEach(button =>
                button.addEventListener('click', () => openForm(data[Number(button.dataset.edit)])));
            host.querySelectorAll('[data-deactivate]').forEach(button =>
                button.addEventListener('click', () => changeActive(data[Number(button.dataset.deactivate)], false)));
            host.querySelectorAll('[data-activate]').forEach(button =>
                button.addEventListener('click', () => changeActive(data[Number(button.dataset.activate)], true)));
        }

        function openForm(row) {
            form.dataset.id = row ? row.id : '';
            modal.querySelector('.modal-title').textContent =
                (row ? 'Edit ' : 'Add ') + config.singular;

            UI.formError(form, null);
            UI.showFieldErrors(form, {});

            const values = row ? config.toForm(row) : {};
            config.fields.forEach(field => {
                const input = form.elements[field.name];
                if (input) {
                    input.value = values[field.name] === null || values[field.name] === undefined
                        ? '' : values[field.name];
                    // Some fields only make sense when creating — a password, for
                    // instance, which is changed through its own screen.
                    const wrap = form.querySelector(`[data-field="${field.name}"]`);
                    if (wrap && field.createOnly) {
                        wrap.classList.toggle('d-none', Boolean(row));
                        input.required = !row && Boolean(field.required);
                    }
                }
            });

            if (config.onFormOpen) {
                config.onFormOpen(form, row);
            }
            dialog.show();
        }

        async function onSubmit(event) {
            event.preventDefault();
            const submit = form.querySelector('[type="submit"]');
            const id = form.dataset.id;

            UI.formError(form, null);
            UI.showFieldErrors(form, {});

            const values = {};
            config.fields.forEach(field => {
                const input = form.elements[field.name];
                if (!input) {
                    return;
                }
                let value = input.value.trim();
                if (field.createOnly && id) {
                    return;
                }
                if (field.type === 'number') {
                    values[field.name] = value === '' ? null : Number(value);
                } else {
                    values[field.name] = value === '' ? null : value;
                }
            });

            UI.busy(submit, true, 'Saving…');
            try {
                if (id) {
                    await config.update(id, values);
                } else {
                    await config.create(values);
                }
                dialog.hide();
                UI.toast(config.singular + (id ? ' updated.' : ' added.'));
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

        async function changeActive(row, activate) {
            const label = config.labelOf ? config.labelOf(row) : row.name;
            if (!activate && !window.confirm(
                `Deactivate "${label}"?\n\nIt stays in the records and on past requests, but stops appearing in new ones.`)) {
                return;
            }
            try {
                await (activate ? config.activate(row.id) : config.deactivate(row.id));
                UI.toast(activate ? 'Restored.' : 'Deactivated.');
                load();
            } catch (error) {
                // 422 here is a real rule, such as a department that still has
                // categories. The server's message says exactly what to do first.
                UI.toast(error.message, 'danger');
            }
        }

        return { reload: load };
    }

    function buildModal(config) {
        const fields = config.fields.map(field => {
            const control = field.type === 'select'
                ? `<select class="form-select" id="f-${field.name}" name="${field.name}" ${field.required ? 'required' : ''}></select>`
                : field.type === 'textarea'
                    ? `<textarea class="form-control" id="f-${field.name}" name="${field.name}" rows="2" ${field.required ? 'required' : ''}></textarea>`
                    : `<input type="${field.type || 'text'}" class="form-control" id="f-${field.name}"
                              name="${field.name}" ${field.required ? 'required' : ''}
                              ${field.min !== undefined ? `min="${field.min}"` : ''}
                              ${field.max !== undefined ? `max="${field.max}"` : ''}>`;

            return `
                <div class="mb-3" data-field="${field.name}">
                  <label for="f-${field.name}" class="form-label">
                    ${UI.text(field.label)}
                    ${field.required ? '' : '<span class="text-secondary fw-normal">(optional)</span>'}
                  </label>
                  ${control}
                  ${field.help ? `<div class="form-text">${UI.text(field.help)}</div>` : ''}
                  <div class="invalid-feedback" data-error-for="${field.name}"></div>
                </div>`;
        }).join('');

        const node = document.createElement('div');
        node.className = 'modal fade';
        node.tabIndex = -1;
        node.innerHTML = `
            <div class="modal-dialog">
              <div class="modal-content">
                <form novalidate>
                  <div class="modal-header">
                    <h5 class="modal-title">Add</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                  </div>
                  <div class="modal-body">
                    ${fields}
                    <div class="alert alert-danger py-2 px-3 small d-none" data-form-error role="alert"></div>
                  </div>
                  <div class="modal-footer">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
                    <button type="submit" class="btn btn-primary">Save</button>
                  </div>
                </form>
              </div>
            </div>`;
        document.body.appendChild(node);
        return node;
    }

    /** Fills a select inside the CRUD form. */
    function options(form, name, items, placeholder) {
        const select = form.elements[name];
        select.innerHTML = (placeholder ? `<option value="">${UI.text(placeholder)}</option>` : '')
            + items.map(item => `<option value="${UI.text(item.value)}">${UI.text(item.label)}</option>`).join('');
    }

    return { mount, options };
})();
