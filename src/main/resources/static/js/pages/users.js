(async () => {
    await App.start();

    let roles = [];

    Crud.mount({
        host: document.getElementById('table'),
        singular: 'User',
        emptyTitle: 'No users yet',
        emptyHint: 'Add the students and staff who will use CampusFix.',
        labelOf: row => row.fullName,

        columns: [
            { label: 'Name', render: row => UI.text(row.fullName) },
            { label: 'Email', render: row => `<span class="text-mono">${UI.text(row.email)}</span>` },
            { label: 'Role', render: row => UI.text(row.roleLabel) },
            { label: 'Department', render: row => UI.text(row.departmentName || '—') },
            {
                label: 'Status',
                render: row => row.active
                    ? '<span class="badge badge-status st-RESOLVED">Active</span>'
                    : '<span class="badge badge-status st-REJECTED">Inactive</span>'
            }
        ],

        fields: [
            { name: 'fullName', label: 'Full name', required: true },
            {
                name: 'email', label: 'Email', type: 'email', required: true, createOnly: true,
                help: 'This is how they sign in. It cannot be changed afterwards.'
            },
            {
                name: 'password', label: 'Temporary password', type: 'password', required: true, createOnly: true,
                help: 'At least 8 characters. Ask them to change it after their first sign-in.'
            },
            { name: 'role', label: 'Role', type: 'select', required: true },
            {
                name: 'departmentId', label: 'Department', type: 'select',
                help: 'Required for technicians and department heads. Leave blank for students and admins.'
            }
        ],

        load: () => Api.get('/api/users'),
        toForm: row => ({ fullName: row.fullName, role: row.role, departmentId: row.departmentId }),
        create: values => Api.post('/api/users', values),
        // The update endpoint deliberately takes no email or password — a
        // profile edit must not be able to overwrite either by accident.
        update: (id, values) => Api.put('/api/users/' + id, {
            fullName: values.fullName,
            role: values.role,
            departmentId: values.departmentId
        }),
        deactivate: id => Api.del('/api/users/' + id),
        activate: id => Api.post('/api/users/' + id + '/activate'),

        onFormOpen: async (form, row) => {
            try {
                if (roles.length === 0) {
                    roles = await Api.get('/api/users/roles');
                }
                const departments = await Api.get('/api/departments?activeOnly=true');

                Crud.options(form, 'role', roles.map(r => ({ value: r.value, label: r.label })), 'Choose a role…');
                Crud.options(form, 'departmentId',
                    departments.map(d => ({ value: d.id, label: d.name })), 'No department');

                if (row) {
                    form.elements.role.value = row.role;
                    form.elements.departmentId.value = row.departmentId || '';
                }

                /*
                 * The role list carries departmentRequired, so the form can show
                 * or hide the department field using the same rule the server
                 * enforces. Without it, an admin would fill in a department for
                 * a student and only find out it was wrong on submit.
                 */
                const syncDepartment = () => {
                    const chosen = roles.find(r => r.value === form.elements.role.value);
                    const wrap = form.querySelector('[data-field="departmentId"]');
                    const needed = Boolean(chosen && chosen.departmentRequired);
                    wrap.classList.toggle('d-none', !chosen || !needed);
                    form.elements.departmentId.required = needed;
                    if (!needed) {
                        form.elements.departmentId.value = '';
                    }
                };
                form.elements.role.onchange = syncDepartment;
                syncDepartment();
            } catch (error) {
                UI.formError(form, 'Roles or departments could not be loaded.');
            }
        }
    });
})();
