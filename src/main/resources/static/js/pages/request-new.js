(async () => {
    const user = await App.start();

    const form = document.getElementById('request-form');
    const submit = document.getElementById('submit');
    let categories = [];

    if (user.role !== 'STUDENT') {
        // The server refuses this too. Saying so plainly is friendlier than
        // letting them fill the form and be rejected at the end.
        form.closest('.card').innerHTML = `
            <div class="state-panel">
              <div class="state-title">This form is for students</div>
              <div>Staff act on requests rather than reporting them here.</div>
            </div>`;
        return;
    }

    document.getElementById('description').addEventListener('input', (event) => {
        document.getElementById('chars').textContent = event.target.value.length;
    });

    await Promise.all([loadCategories(), loadPriorities(), loadLocations(), loadSlaTable()]);

    document.getElementById('categoryId').addEventListener('change', showRouting);
    document.getElementById('priority').addEventListener('change', showSlaHint);

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        UI.formError(form, null);
        UI.showFieldErrors(form, {});

        const body = {
            title: form.title.value.trim(),
            description: form.description.value.trim(),
            categoryId: Number(form.categoryId.value) || null,
            locationId: form.locationId.value ? Number(form.locationId.value) : null,
            priority: form.priority.value
        };

        UI.busy(submit, true, 'Sending…');
        try {
            const created = await Api.post('/api/requests', body);
            // Straight to the new request: the student's next question is
            // "what happens now?", and the detail page answers it.
            window.location.href = 'request-detail.html?id=' + created.id + '&created=1';
        } catch (error) {
            if (error.fieldErrors) {
                UI.showFieldErrors(form, error.fieldErrors);
                UI.formError(form, 'Please check the highlighted fields.');
            } else {
                UI.formError(form, error.message);
            }
            window.scrollTo({ top: 0, behavior: 'smooth' });
        } finally {
            UI.busy(submit, false);
        }
    });

    async function loadCategories() {
        const select = document.getElementById('categoryId');
        try {
            categories = await Api.get('/api/categories?activeOnly=true');
            if (categories.length === 0) {
                select.innerHTML = '<option value="">No categories are set up yet</option>';
                select.disabled = true;
                UI.formError(form, 'No problem categories have been set up yet. Contact the administrator.');
                submit.disabled = true;
                return;
            }
            categories.forEach(category => {
                const option = document.createElement('option');
                option.value = category.id;
                option.textContent = category.name;
                select.appendChild(option);
            });
        } catch (error) {
            select.innerHTML = '<option value="">Could not load categories</option>';
            select.disabled = true;
            UI.formError(form, 'The category list could not be loaded. Refresh the page to try again.');
            submit.disabled = true;
        }
    }

    /*
     * Shows which team will receive the request. The student never chooses a
     * department, but telling them where it is going is reassuring and makes an
     * obviously wrong category easy to spot.
     */
    function showRouting() {
        const hint = document.getElementById('routing-hint');
        const chosen = categories.find(c => String(c.id) === document.getElementById('categoryId').value);
        hint.textContent = chosen ? 'This goes to ' + chosen.departmentName : ' ';
    }

    async function loadPriorities() {
        const select = document.getElementById('priority');
        try {
            const priorities = await Api.get('/api/requests/priorities');
            // CRITICAL is filtered out because the server only lets staff set it.
            // Offering a button that always fails would be a broken promise.
            priorities.filter(p => p.studentSelectable).forEach(p => {
                const option = document.createElement('option');
                option.value = p.value;
                option.textContent = p.label;
                option.dataset.hours = p.slaHours;
                if (p.value === 'MEDIUM') {
                    option.selected = true;
                }
                select.appendChild(option);
            });
            showSlaHint();
        } catch (error) {
            select.innerHTML = '<option value="MEDIUM">Medium</option>';
        }
    }

    function showSlaHint() {
        const select = document.getElementById('priority');
        const hours = select.selectedOptions[0] && select.selectedOptions[0].dataset.hours;
        document.getElementById('sla-hint').textContent =
            hours ? `Target response: ${hours} hours` : ' ';
    }

    async function loadLocations() {
        const select = document.getElementById('locationId');
        try {
            const locations = await Api.get('/api/locations?activeOnly=true');
            locations.forEach(location => {
                const option = document.createElement('option');
                option.value = location.id;
                option.textContent = location.displayName;
                select.appendChild(option);
            });
        } catch (error) {
            // Optional field: a failure here must not block the report.
            select.disabled = true;
        }
    }

    async function loadSlaTable() {
        const host = document.getElementById('sla-table');
        UI.loading(host, 'Loading targets…');
        try {
            const configs = await Api.get('/api/sla');
            host.innerHTML = `
                <p class="small text-secondary">
                  These are the targets the college works to. The clock starts when you send the request.
                </p>
                <table class="table table-sm mb-0">
                  <tbody>
                    ${configs.filter(c => c.priority !== 'CRITICAL').map(c => `
                      <tr>
                        <td>${UI.text(c.priorityLabel)}</td>
                        <td class="text-end text-secondary">${c.durationHours} hours</td>
                      </tr>`).join('')}
                  </tbody>
                </table>`;
        } catch (error) {
            UI.failed(host, error.message, loadSlaTable);
        }
    }
})();
