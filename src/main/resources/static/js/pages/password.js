(async () => {
    await App.start();

    const form = document.getElementById('password-form');
    const submit = document.getElementById('submit');

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        UI.formError(form, null);
        UI.showFieldErrors(form, {});

        const newPassword = form.newPassword.value;
        const repeat = document.getElementById('repeatPassword').value;

        // Checked here rather than on the server: "these two do not match" is
        // about this form, not about the account.
        if (newPassword !== repeat) {
            UI.showFieldErrors(form, { repeatPassword: 'The two passwords do not match.' });
            document.getElementById('repeatPassword').classList.add('is-invalid');
            return;
        }

        UI.busy(submit, true, 'Saving…');
        try {
            await Api.put('/api/auth/password', {
                currentPassword: form.currentPassword.value,
                newPassword
            });
            UI.toast('Your password has been changed.');
            form.reset();
        } catch (error) {
            if (error.fieldErrors) {
                UI.showFieldErrors(form, error.fieldErrors);
            }
            UI.formError(form, error.message);
        } finally {
            UI.busy(submit, false);
        }
    });
})();
