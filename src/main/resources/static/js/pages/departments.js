(async () => {
    await App.start();

    Crud.mount({
        host: document.getElementById('table'),
        singular: 'Department',
        emptyTitle: 'No departments yet',
        emptyHint: 'Add the teams that handle problems — IT Support, Electrical, Facilities.',

        columns: [
            { label: 'Name', render: row => UI.text(row.name) },
            { label: 'Description', render: row => UI.text(row.description || '—') },
            {
                label: 'Status',
                render: row => row.active
                    ? '<span class="badge badge-status st-RESOLVED">Active</span>'
                    : '<span class="badge badge-status st-REJECTED">Inactive</span>'
            }
        ],

        fields: [
            { name: 'name', label: 'Name', required: true, help: 'Must be unique across the college.' },
            { name: 'description', label: 'Description', type: 'textarea' }
        ],

        load: () => Api.get('/api/departments'),
        toForm: row => ({ name: row.name, description: row.description }),
        create: values => Api.post('/api/departments', values),
        update: (id, values) => Api.put('/api/departments/' + id, values),
        deactivate: id => Api.del('/api/departments/' + id),
        activate: id => Api.post('/api/departments/' + id + '/activate')
    });
})();
