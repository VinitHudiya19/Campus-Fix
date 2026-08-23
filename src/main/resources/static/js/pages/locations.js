(async () => {
    await App.start();

    Crud.mount({
        host: document.getElementById('table'),
        singular: 'Location',
        emptyTitle: 'No locations yet',
        emptyHint: 'Add buildings and rooms so students can say where a problem is.',
        labelOf: row => row.displayName,

        columns: [
            { label: 'Campus', render: row => UI.text(row.campus) },
            { label: 'Building', render: row => UI.text(row.building) },
            { label: 'Floor', render: row => UI.text(row.floor || '—') },
            { label: 'Room', render: row => UI.text(row.room || '—') },
            {
                label: 'Status',
                render: row => row.active
                    ? '<span class="badge badge-status st-RESOLVED">Active</span>'
                    : '<span class="badge badge-status st-REJECTED">Inactive</span>'
            }
        ],

        fields: [
            { name: 'campus', label: 'Campus', required: true },
            { name: 'building', label: 'Building', required: true },
            { name: 'floor', label: 'Floor', help: 'Leave blank for places without floors, such as a gate.' },
            { name: 'room', label: 'Room', help: 'Leave blank when the whole floor or building is meant.' }
        ],

        load: () => Api.get('/api/locations'),
        toForm: row => ({ campus: row.campus, building: row.building, floor: row.floor, room: row.room }),
        create: values => Api.post('/api/locations', values),
        update: (id, values) => Api.put('/api/locations/' + id, values),
        deactivate: id => Api.del('/api/locations/' + id),
        activate: id => Api.post('/api/locations/' + id + '/activate')
    });
})();
