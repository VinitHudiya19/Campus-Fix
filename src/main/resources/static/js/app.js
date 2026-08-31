/*
 * Session handling and the navigation bar.
 *
 * Every page except the login page calls App.start(). It checks there is a
 * session, draws the menu for that person's role, and hands the page its user.
 */
const App = (() => {

    /*
     * The menu each role sees. This mirrors what the server allows rather than
     * deciding it: hiding a link is a convenience, not a security measure —
     * anyone can type the URL, and the API refuses them regardless.
     */
    const MENUS = {
        STUDENT: [
            { href: 'index.html', label: 'Dashboard' },
            { href: 'requests.html', label: 'My requests' },
            { href: 'request-new.html', label: 'Report a problem' }
        ],
        TECHNICIAN: [
            { href: 'index.html', label: 'Dashboard' },
            { href: 'requests.html', label: 'My work' }
        ],
        DEPARTMENT_HEAD: [
            { href: 'index.html', label: 'Dashboard' },
            { href: 'requests.html', label: 'Requests' },
            { href: 'reports.html', label: 'Reports' }
        ],
        ADMIN: [
            { href: 'index.html', label: 'Dashboard' },
            { href: 'requests.html', label: 'Requests' },
            { href: 'reports.html', label: 'Reports' },
            { href: 'departments.html', label: 'Departments' },
            { href: 'categories.html', label: 'Categories' },
            { href: 'locations.html', label: 'Locations' },
            { href: 'users.html', label: 'Users' },
            { href: 'sla.html', label: 'SLA targets' }
        ]
    };

    /**
     * @returns the signed-in user, or redirects to the login page and never
     *          resolves. Pages can assume a user exists after awaiting this.
     */
    async function start() {
        if (!Api.token()) {
            redirectToLogin();
            return new Promise(() => {});
        }

        // Drawn from the stored copy first so the page does not flash an empty
        // bar, then refreshed from the server in case the role changed.
        let user = Api.storedUser();
        if (user) {
            renderNav(user);
        }

        try {
            user = await Api.get('/api/auth/me');
            sessionStorage.setItem('campusfix.user', JSON.stringify(user));
            renderNav(user);
            return user;
        } catch (error) {
            if (error.status === 401) {
                return new Promise(() => {});   // Api already redirected
            }
            if (user) {
                return user;                    // offline: carry on with what we have
            }
            redirectToLogin();
            return new Promise(() => {});
        }
    }

    function redirectToLogin() {
        window.location.href = 'login.html';
    }

    function renderNav(user) {
        const host = document.getElementById('app-nav');
        if (!host) {
            return;
        }

        const current = window.location.pathname.split('/').pop() || 'index.html';
        const links = (MENUS[user.role] || []).map(item => `
            <li class="nav-item">
                <a class="nav-link ${item.href === current ? 'active fw-semibold' : ''}"
                   href="${item.href}">${UI.text(item.label)}</a>
            </li>`).join('');

        host.innerHTML = `
        <nav class="navbar navbar-expand-lg bg-white border-bottom mb-4">
          <div class="container">
            <a class="navbar-brand" href="index.html">CampusFix</a>
            <button class="navbar-toggler" type="button" data-bs-toggle="collapse"
                    data-bs-target="#cf-nav" aria-controls="cf-nav" aria-expanded="false"
                    aria-label="Toggle navigation">
              <span class="navbar-toggler-icon"></span>
            </button>
            <div class="collapse navbar-collapse" id="cf-nav">
              <ul class="navbar-nav me-auto">${links}</ul>

              <div class="dropdown me-2">
                <button class="btn btn-sm btn-outline-secondary position-relative" type="button"
                        id="cf-bell" data-bs-toggle="dropdown" data-bs-auto-close="outside"
                        aria-expanded="false" aria-label="Notifications">
                  Notifications
                  <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger d-none"
                        id="cf-bell-count">0</span>
                </button>
                <div class="dropdown-menu dropdown-menu-end p-0 cf-notifications">
                  <div class="d-flex justify-content-between align-items-center px-3 py-2 border-bottom">
                    <strong class="small">Notifications</strong>
                    <button class="btn btn-link btn-sm p-0 text-decoration-none d-none"
                            type="button" id="cf-mark-all">Mark all read</button>
                  </div>
                  <div id="cf-bell-list"></div>
                </div>
              </div>

              <div class="dropdown">
                <button class="btn btn-sm btn-outline-secondary dropdown-toggle"
                        type="button" data-bs-toggle="dropdown" aria-expanded="false">
                  ${UI.text(user.fullName)}
                </button>
                <ul class="dropdown-menu dropdown-menu-end">
                  <li class="dropdown-header">
                    ${UI.text(user.roleLabel)}${user.departmentName ? ' · ' + UI.text(user.departmentName) : ''}
                  </li>
                  <li><hr class="dropdown-divider"></li>
                  <li><a class="dropdown-item" href="password.html">Change password</a></li>
                  <li><button class="dropdown-item" type="button" id="cf-signout">Sign out</button></li>
                </ul>
              </div>
            </div>
          </div>
        </nav>`;

        startNotifications();

        document.getElementById('cf-signout').addEventListener('click', () => {
            // Nothing to tell the server: a JWT is not stored anywhere on it, so
            // signing out is simply forgetting the token on this machine.
            Api.clearSession();
            redirectToLogin();
        });
    }

    /*
     * The unread count refreshes on a timer. Polling rather than server-sent
     * events or websockets: this is one small number, and a persistent
     * connection per signed-in user is a real cost on a free-tier instance for
     * a badge nobody watches continuously.
     *
     * Sixty seconds is deliberately slow. Anything faster would keep the
     * container awake and burn the free plan's hours for no benefit.
     */
    const UNREAD_POLL_MS = 60000;

    function startNotifications() {
        refreshUnread();
        setInterval(refreshUnread, UNREAD_POLL_MS);

        // The list is only fetched when the dropdown is actually opened, so a
        // page nobody interacts with costs exactly one count request a minute.
        document.getElementById('cf-bell').addEventListener('click', loadNotifications);
        document.getElementById('cf-mark-all').addEventListener('click', markAllRead);
    }

    async function refreshUnread() {
        try {
            const result = await Api.get('/api/notifications/unread-count');
            const badge = document.getElementById('cf-bell-count');
            if (!badge) {
                return;
            }
            badge.textContent = result.unread > 9 ? '9+' : result.unread;
            badge.classList.toggle('d-none', result.unread === 0);
            document.getElementById('cf-mark-all').classList.toggle('d-none', result.unread === 0);
        } catch (error) {
            // A failed poll is not worth telling the user about. The next one
            // is a minute away, and an error toast every minute would be worse
            // than a stale badge.
        }
    }

    async function loadNotifications() {
        const host = document.getElementById('cf-bell-list');
        UI.loading(host, 'Loading…');
        try {
            const items = await Api.get('/api/notifications');
            if (items.length === 0) {
                UI.empty(host, 'Nothing yet', 'You will be told when something needs you.');
                return;
            }
            host.innerHTML = items.map(item => `
                <a class="d-block px-3 py-2 border-bottom text-decoration-none cf-note ${item.read ? '' : 'unread'}"
                   href="request-detail.html?id=${item.requestId}" data-note="${item.id}">
                  <div class="small ${item.read ? 'text-secondary' : 'fw-semibold'}">${UI.text(item.message)}</div>
                  <div class="tl-meta">${UI.text(item.requestNumber)} · ${UI.dateTime(item.createdAt)}</div>
                </a>`).join('');

            // Marking read is fire-and-forget: the click is already navigating
            // to the request, and blocking that on a PUT would make the app
            // feel slow for no gain.
            host.querySelectorAll('[data-note]').forEach(link =>
                link.addEventListener('click', () => {
                    Api.put(`/api/notifications/${link.dataset.note}/read`, {}).catch(() => {});
                }));
        } catch (error) {
            UI.failed(host, error.message, loadNotifications);
        }
    }

    async function markAllRead() {
        try {
            await Api.put('/api/notifications/read-all', {});
            await refreshUnread();
            await loadNotifications();
        } catch (error) {
            UI.toast(error.message, 'danger');
        }
    }

    // MENUS stays private: the navigation is drawn here and nowhere else, so
    // no page has a reason to read it.
    return { start };
})();
