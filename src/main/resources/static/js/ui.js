/*
 * Shared rendering helpers.
 *
 * The important one is `text()`. Everything a user typed — a request title, a
 * technician's note — passes through it before reaching the page, so a
 * description containing <script> is displayed as characters and never runs.
 */
const UI = (() => {

    function text(value) {
        if (value === null || value === undefined) {
            return '';
        }
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /** "23 Aug 2026, 14:05" in the reader's own timezone. Times are sent as UTC. */
    function dateTime(value) {
        if (!value) {
            return '—';
        }
        return new Date(value).toLocaleString(undefined, {
            day: '2-digit', month: 'short', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    }

    /**
     * "in 6 hours" / "3 days ago". A deadline is much easier to judge as a
     * distance than as a date the reader has to compare against now.
     */
    function relative(value) {
        if (!value) {
            return '—';
        }
        const diffMs = new Date(value).getTime() - Date.now();
        const units = [
            ['day', 86400000],
            ['hour', 3600000],
            ['minute', 60000]
        ];
        const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
        for (const [unit, ms] of units) {
            if (Math.abs(diffMs) >= ms || unit === 'minute') {
                return formatter.format(Math.round(diffMs / ms), unit);
            }
        }
        return '';
    }

    function statusBadge(status, label) {
        return `<span class="badge badge-status st-${text(status)}">${text(label || status)}</span>`;
    }

    function slaBadge(state, label) {
        if (!state) {
            return '';
        }
        return `<span class="badge badge-status sla-${text(state)}">${text(label || state)}</span>`;
    }

    function priority(value, label) {
        return `<span class="priority-${text(value)}">${text(label || value)}</span>`;
    }

    /*
     * The three states every list has to handle. Skipping these is what makes a
     * project look unfinished: a table that is blank while loading, and blank
     * again when there is nothing to show, tells the user nothing.
     */
    function loading(container, message) {
        container.innerHTML = `
            <div class="state-panel">
                <div class="spinner-border spinner-border-sm text-secondary mb-2" role="status"></div>
                <div>${text(message || 'Loading…')}</div>
            </div>`;
    }

    function empty(container, title, hint) {
        container.innerHTML = `
            <div class="state-panel">
                <div class="state-title">${text(title)}</div>
                <div>${text(hint || '')}</div>
            </div>`;
    }

    function failed(container, message, onRetry) {
        container.innerHTML = `
            <div class="state-panel">
                <div class="state-title">Could not load this</div>
                <div class="mb-3">${text(message)}</div>
                <button class="btn btn-sm btn-outline-secondary" type="button" data-retry>Try again</button>
            </div>`;
        if (onRetry) {
            container.querySelector('[data-retry]').addEventListener('click', onRetry);
        }
    }

    /** A short confirmation or failure notice, top right, gone after a few seconds. */
    function toast(message, variant) {
        let host = document.getElementById('cf-toasts');
        if (!host) {
            host = document.createElement('div');
            host.id = 'cf-toasts';
            host.className = 'position-fixed top-0 end-0 p-3';
            host.style.zIndex = '1080';
            document.body.appendChild(host);
        }

        const node = document.createElement('div');
        node.className = `alert alert-${variant || 'success'} shadow-sm py-2 px-3 mb-2`;
        node.setAttribute('role', 'status');
        node.textContent = message;
        host.appendChild(node);

        setTimeout(() => node.remove(), 4500);
    }

    /** Puts server-side validation messages next to the fields they belong to. */
    function showFieldErrors(form, fieldErrors) {
        form.querySelectorAll('.is-invalid').forEach(el => el.classList.remove('is-invalid'));
        form.querySelectorAll('[data-error-for]').forEach(el => { el.textContent = ''; });

        Object.entries(fieldErrors || {}).forEach(([field, message]) => {
            const input = form.querySelector(`[name="${field}"]`);
            if (input) {
                input.classList.add('is-invalid');
            }
            const slot = form.querySelector(`[data-error-for="${field}"]`);
            if (slot) {
                slot.textContent = message;
            }
        });
    }

    function formError(form, message) {
        const slot = form.querySelector('[data-form-error]');
        if (slot) {
            slot.textContent = message || '';
            slot.classList.toggle('d-none', !message);
        }
    }

    /** Disables a submit button while a request is in flight, so it cannot be double-sent. */
    function busy(button, isBusy, busyLabel) {
        if (isBusy) {
            button.dataset.originalLabel = button.innerHTML;
            button.disabled = true;
            button.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>${text(busyLabel || 'Working…')}`;
        } else {
            button.disabled = false;
            button.innerHTML = button.dataset.originalLabel || button.innerHTML;
        }
    }

    function param(name) {
        return new URLSearchParams(window.location.search).get(name);
    }

    return {
        text, dateTime, relative,
        statusBadge, slaBadge, priority,
        loading, empty, failed, toast,
        showFieldErrors, formError, busy, param
    };
})();
