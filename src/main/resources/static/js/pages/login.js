(() => {
    const form = document.getElementById('login-form');
    const submit = document.getElementById('submit');

    // Already signed in? Skip the form.
    if (Api.token()) {
        window.location.href = 'index.html';
        return;
    }

    if (UI.param('expired')) {
        document.getElementById('session-notice').classList.remove('d-none');
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        UI.formError(form, null);
        UI.showFieldErrors(form, {});

        const email = form.email.value.trim();
        const password = form.password.value;

        if (!email || !password) {
            UI.formError(form, 'Enter your email and password.');
            return;
        }

        UI.busy(submit, true, 'Signing in…');
        try {
            const session = await Api.post('/api/auth/login', { email, password });
            Api.saveSession(session);
            window.location.href = 'index.html';
        } catch (error) {
            if (error.fieldErrors) {
                UI.showFieldErrors(form, error.fieldErrors);
            }
            // The server answers the same way for an unknown email and a wrong
            // password, on purpose. The page repeats that message as-is rather
            // than guessing which one it was.
            UI.formError(form, error.message);
            form.password.value = '';
            form.password.focus();
        } finally {
            UI.busy(submit, false);
        }
    });
})();
