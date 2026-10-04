// Turns the plain code input into six boxes. Without this script the input still works as a normal text field.
(function () {
	'use strict';

	function enhance(field) {
		var input = field.querySelector('input');
		var boxes = field.querySelectorAll('.code-box');
		if (!input || boxes.length === 0) {
			return;
		}

		function render() {
			// Codes are hex; the letter O is a common typo for zero (the server maps it too).
			var clean = input.value.toUpperCase().replace(/O/g, '0').replace(/[^0-9A-F]/g, '').slice(0, boxes.length);
			if (clean !== input.value) {
				input.value = clean;
			}
			var focused = document.activeElement === input;
			var active = Math.min(clean.length, boxes.length - 1);
			for (var i = 0; i < boxes.length; i++) {
				boxes[i].textContent = clean.charAt(i);
				boxes[i].classList.toggle('filled', i < clean.length);
				boxes[i].classList.toggle('active', focused && i === active);
			}
		}

		function caretToEnd() {
			var end = input.value.length;
			input.setSelectionRange(end, end);
		}

		input.addEventListener('input', render);
		input.addEventListener('focus', function () { render(); caretToEnd(); });
		input.addEventListener('blur', render);
		input.addEventListener('click', caretToEnd);
		input.addEventListener('keyup', caretToEnd);
		field.classList.add('enhanced');
		render();
	}

	document.querySelectorAll('.code-field').forEach(enhance);
})();
