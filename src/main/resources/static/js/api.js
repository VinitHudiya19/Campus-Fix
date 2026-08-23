/*
 * Every call to the backend goes through here.
 *
 * One place that attaches the token, one place that turns a failed response into
 * a readable message, one place that reacts to an expired session. Without it,
 * each page would repeat the same fetch boilerplate and they would slowly stop
 * agreeing about what an error looks like.
 */
const Api = (() => {

    /*
     * sessionStorage, not localStorage: the token disappears when the tab is
     * closed, which limits the damage on a shared library computer where nobody
     * remembers to sign out.
     *
     * Honest limitation: JavaScript can read it either way, so a cross-site
     * scripting hole would expose it. The alternative — an HttpOnly cookie — is
     * safer against that but brings CSRF back, which would mean tokens the API
     * was not built for. Recorded in the phase notes rather than glossed over.
     */
    const TOKEN_KEY = 'campusfix.token';
    const USER_KEY = 'campusfix.user';

    function token() {
        return sessionStorage.getItem(TOKEN_KEY);
    }

    function storedUser() {
        const raw = sessionStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
    }

    function saveSession(loginResponse) {
        sessionStorage.setItem(TOKEN_KEY, loginResponse.token);
        sessionStorage.setItem(USER_KEY, JSON.stringify(loginResponse.user));
    }

    function clearSession() {
        sessionStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(USER_KEY);
    }

    /** Thrown for any non-2xx response, carrying the parts a page might show. */
    class ApiError extends Error {
        constructor(status, message, fieldErrors) {
            super(message);
            this.status = status;
            this.fieldErrors = fieldErrors || null;
        }
    }

    async function request(method, path, body) {
        const headers = {};
        if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
        }
        const current = token();
        if (current) {
            headers['Authorization'] = 'Bearer ' + current;
        }

        let response;
        try {
            response = await fetch(path, {
                method,
                headers,
                body: body === undefined ? undefined : JSON.stringify(body)
            });
        } catch (networkFailure) {
            // fetch only rejects when the request never completed, so this is
            // genuinely "no connection", not "the server said no".
            throw new ApiError(0, 'Could not reach the server. Check your connection and try again.');
        }

        if (response.status === 204) {
            return null;
        }

        const payload = await readBody(response);

        if (response.ok) {
            return payload;
        }

        // An expired or missing token means the session is over. Bounce to the
        // login page rather than showing a confusing error on every panel.
        if (response.status === 401 && !path.endsWith('/auth/login')) {
            clearSession();
            window.location.href = 'login.html?expired=1';
            throw new ApiError(401, 'Your session has ended. Please sign in again.');
        }

        const message = (payload && payload.message) || 'Something went wrong. Please try again.';
        throw new ApiError(response.status, message, payload && payload.fieldErrors);
    }

    async function readBody(response) {
        const type = response.headers.get('content-type') || '';
        if (!type.includes('application/json')) {
            return null;
        }
        try {
            return await response.json();
        } catch (e) {
            return null;
        }
    }

    /** Builds a query string, leaving out anything blank so filters stay optional. */
    function query(params) {
        const search = new URLSearchParams();
        Object.entries(params || {}).forEach(([key, value]) => {
            if (value !== null && value !== undefined && value !== '') {
                search.append(key, value);
            }
        });
        const text = search.toString();
        return text ? '?' + text : '';
    }

    return {
        ApiError,
        token,
        storedUser,
        saveSession,
        clearSession,
        query,
        get: (path) => request('GET', path),
        post: (path, body) => request('POST', path, body === undefined ? {} : body),
        put: (path, body) => request('PUT', path, body),
        del: (path) => request('DELETE', path)
    };
})();
