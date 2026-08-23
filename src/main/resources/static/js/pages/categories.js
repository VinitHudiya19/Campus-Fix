(async () => {
    await App.start();

    Crud.mount({
        host: document.getElementById('table'),
        singular: 'Category',
        emptyTitle: 'No categories yet',
        emptyHint: 'Students cannot report anything until at least one category exists.',

        columns: [
            { label: 'Name', render: row => UI.text(row.name) },
            { label: 'Goes to', render: row => UI.text(row.departmentName) },
            { label: 'Description', render: row => UI.text(row.description || '—') },
            {
                label: 'Status',
                render: row => row.active
                    ? '<span class="badge badge-status st-RESOLVED">Active</span>'
                    : '<span class="badge badge-status st-REJECTED">Inactive</span>'
            }
        ],

        fields: [
            { name: 'name', label: 'Name', required: true, help: 'Unique within its department — "Wiring" may exist under two teams.' },
            { name: 'departmentId', label: 'Department', type: 'select', required: true },
            { name: 'description', label: 'Description', type: 'textarea' }
        ],

        load: () => Api.get('/api/categories'),
        toForm: row => ({ name: row.name, description: row.description, departmentId: row.departmentId }),
        create: values => Api.post('/api/categories', values),
        update: (id, values) => Api.put('/api/categories/' + id, values),
        deactivate: id => Api.del('/api/categories/' + id),
        activate: id => Api.post('/api/categories/' + id + '/activate'),

        /*
         * Only active departments are offered: the server refuses to attach a
         * category to a closed team, so listing them would be offering a choice
         * that always fails.
         */
        onFormOpen: async (form, row) => {
            try {
                const departments = await Api.get('/api/departments?activeOnly=true');
                Crud.options(form, 'departmentId',
                    departments.map(d => ({ value: d.id, label: d.name })), 'Choose a department…');
                if (row) {
                    form.elements.departmentId.value = row.departmentId;
                }
                if (departments.length === 0) {
                    UI.formError(form, 'Add a department first — a category has to belong to one.');
                }
            } catch (error) {
                UI.formError(form, 'The department list could not be loaded.');
            }
        }
    });
})();
