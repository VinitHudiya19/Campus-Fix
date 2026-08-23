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
            { href: 'requests.html', label: 'Requests' }
        ],
        ADMIN: [
            { href: 'index.html', label: 'Dashboard' },
            { href: 'requests.html', label: 'Requests' },
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

        document.getElementById('cf-signout').addEventListener('click', () => {
            // Nothing to tell the server: a JWT is not stored anywhere on it, so
            // signing out is simply forgetting the token on this machine.
            Api.clearSession();
            redirectToLogin();
        });
    }

    return { start, MENUS };
})();
