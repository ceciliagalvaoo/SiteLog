/*
 * SiteLog - small progressive enhancements. Every screen works without this file except opening a
 * modal from a button (the server can still render a modal open, see fragments/modal.html).
 *  - modals: [data-modal-open="id"] opens <dialog id>, [data-modal-close] closes it,
 *            dialog[data-open-on-load] opens on page load (validation errors after a POST)
 *  - flash:  [data-flash-close] removes its flash message
 *  - menus:  the notification bell and user menu (<details>) close on Escape and outside click
 *  - incident form: choosing High / Critical marks the investigation section as required at once
 *            ([data-severity-select] around the select, [data-investigation] on the section); the
 *            server renders the same state from the submitted severity and validates the rule (AR2)
 */
(function () {
	'use strict';

	document.addEventListener('click', function (event) {
		var opener = event.target.closest('[data-modal-open]');
		if (opener) {
			var dialog = document.getElementById(opener.getAttribute('data-modal-open'));
			if (dialog && typeof dialog.showModal === 'function') {
				dialog.showModal();
			}
			return;
		}

		var closer = event.target.closest('[data-modal-close]');
		if (closer) {
			var parent = closer.closest('dialog');
			if (parent) {
				parent.close();
			}
			return;
		}

		var flashClose = event.target.closest('[data-flash-close]');
		if (flashClose) {
			var flash = flashClose.closest('[data-flash]');
			if (flash) {
				flash.remove();
			}
			return;
		}

		document.querySelectorAll('details.bell[open], details.user-menu[open]').forEach(function (menu) {
			if (!menu.contains(event.target)) {
				menu.removeAttribute('open');
			}
		});
	});

	document.addEventListener('change', function (event) {
		var severity = event.target.closest('[data-severity-select]');
		if (!severity || event.target.tagName !== 'SELECT') {
			return;
		}
		var section = document.querySelector('[data-investigation]');
		if (section) {
			var value = event.target.value;
			section.classList.toggle('investigation--required', value === 'HIGH' || value === 'CRITICAL');
		}
	});

	document.addEventListener('keydown', function (event) {
		if (event.key === 'Escape') {
			document.querySelectorAll('details.bell[open], details.user-menu[open]').forEach(function (menu) {
				menu.removeAttribute('open');
				var summary = menu.querySelector('summary');
				if (summary) {
					summary.focus();
				}
			});
		}
	});

	document.addEventListener('DOMContentLoaded', function () {
		document.querySelectorAll('dialog[data-open-on-load]').forEach(function (dialog) {
			if (typeof dialog.showModal === 'function') {
				dialog.showModal();
			}
		});
	});
})();
