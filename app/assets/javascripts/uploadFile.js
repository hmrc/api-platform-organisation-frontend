(function () {
  'use strict';

  const form = document.getElementById('file-upload-form');

  if (!form) {
    return;
  }

  const contentSection = document.getElementById('upload-file-content');
  const progressSection = document.getElementById('upload-in-progress');
  const progressHeading = document.getElementById('upload-in-progress-heading');
  const liveRegion = document.getElementById('upload-live-region');
  const errorRedirectUrl = form.getAttribute('data-error-redirect');
  const emptyFileUrl = form.getAttribute('data-empty-file-url');
  const minFileSize = Number(form.getAttribute('data-min-file-size'));
  const maxFileSize = Number(form.getAttribute('data-max-file-size'));
  const fileInput = form.querySelector('input[type="file"]');

  fileInput.removeAttribute('required');
  form.setAttribute('novalidate', 'novalidate');

  const acceptedMimeTypes = (fileInput.getAttribute('accept') || '')
    .split(',')
    .map(function (type) {
      return type.trim();
    })
    .filter(function (type) {
      return type.length > 0;
    });

  const acceptedExtensions = (form.getAttribute('data-accepted-extensions') || '')
    .split(',')
    .map(function (extension) {
      return extension.trim().toLowerCase();
    })
    .filter(function (extension) {
      return extension.length > 0;
    });

  function fileExtension(filename) {
    const lastDot = filename.lastIndexOf('.');

    return lastDot === -1 ? '' : filename.slice(lastDot).toLowerCase();
  }

  function validationErrorCode() {
    const files = fileInput.files;

    if (!files || files.length === 0) {
      return 'InvalidArgument';
    }

    const file = files[0];

    if (acceptedMimeTypes.length > 0 || acceptedExtensions.length > 0) {
      const matchesMimeType = acceptedMimeTypes.includes(file.type);
      const matchesExtension = acceptedExtensions.includes(fileExtension(file.name));

      if (!matchesMimeType && !matchesExtension) {
        return 'UnexpectedContent';
      }
    }

    if (file.size > maxFileSize) {
      return 'EntityTooLarge';
    }

    if (file.size < minFileSize) {
      return 'EntityTooSmall';
    }

    return null;
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();

    const errorCode = validationErrorCode();

    if (errorCode === 'EntityTooSmall') {
      window.location.href = emptyFileUrl;
      return;
    }

    if (errorCode) {
      window.location.href = errorRedirectUrl + '?errorCode=' + encodeURIComponent(errorCode);
      return;
    }

    contentSection.setAttribute('hidden', 'hidden');
    progressSection.removeAttribute('hidden');
    progressHeading.setAttribute('tabindex', '-1');
    progressHeading.focus();

    window.setTimeout(function () {
      liveRegion.textContent = progressSection.textContent;
    }, 100);

    window
      .fetch(form.action, {
        method: 'POST',
        body: new FormData(form),
        credentials: 'include'
      })
      .then(function (response) {
        if (!response.ok) {
          throw new Error('Unexpected status: ' + response.status);
        }
        window.location.href = response.url;
      })
      .catch(function () {
        window.location.href = errorRedirectUrl;
      });
  });
})();
