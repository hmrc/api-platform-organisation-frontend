(function () {
  'use strict';

  const container = document.getElementById('file-processing');

  if (!container) {
    return;
  }

  const pageTitle = container.getAttribute('data-page-title');
  if (pageTitle) {
    document.title = pageTitle;
  }

  const statusUrl = container.getAttribute('data-status-url');
  const pollIntervalMs = Number(container.getAttribute('data-poll-interval-millis'));
  const maxConsecutiveErrors = 5;
  const maxWaitMinutes = Number(container.getAttribute('data-max-wait-minutes'));
  const maxWaitMs = maxWaitMinutes * 60 * 1000;
  const startedAt = Date.now();
  let consecutiveErrors = 0;

  const pendingStatuses = ['CREATED', 'UPSCAN_SUCCESS'];

  const destinationByStatus = {
    UPSCAN_QUARANTINE: container.getAttribute('data-virus-url'),
    UPSCAN_REJECTED: container.getAttribute('data-rejected-url'),
    UPSCAN_UNKNOWN: container.getAttribute('data-failed-url'),
    UPSCAN_EXPIRED: container.getAttribute('data-failed-url'),
    DUPLICATE: container.getAttribute('data-duplicate-url'),
    VALIDATION_SUCCESS: container.getAttribute('data-success-url'),
    VALIDATION_FAILURE: container.getAttribute('data-validation-errors-url')
  };

  const destinationByInvalidFileReason = {
    InvalidHeader: container.getAttribute('data-problem-with-file-url'),
    InvalidWorkbook: container.getAttribute('data-problem-with-file-url'),
    InvalidFile: container.getAttribute('data-problem-with-file-url'),
    NoDataRows: container.getAttribute('data-empty-file-url'),
    UnsupportedFileType: container.getAttribute('data-rejected-url')
  };

  function poll() {
    window
      .fetch(statusUrl)
      .then(function (response) {
        if (!response.ok) {
          throw new Error('Unexpected status: ' + response.status);
        }

        return response.json();
      })
      .then(function (body) {
        consecutiveErrors = 0;

        const status = body.status;

        if (pendingStatuses.includes(status)) {
          scheduleNextPoll();
          return;
        }

        if (body.validationStatus === 'InvalidFile') {
          window.location.href =
            destinationByInvalidFileReason[body.invalidFileReason] || container.getAttribute('data-failed-url');
          return;
        }

        if (body.passwordProtected) {
          window.location.href = container.getAttribute('data-password-protected-url');
          return;
        }

        window.location.href = destinationByStatus[status] || container.getAttribute('data-failed-url');
      })
      .catch(handleError);
  }

  function handleError() {
    consecutiveErrors += 1;

    if (consecutiveErrors >= maxConsecutiveErrors) {
      window.location.href = container.getAttribute('data-failed-url');
      return;
    }

    scheduleNextPoll();
  }

  function scheduleNextPoll() {
    if (Date.now() - startedAt >= maxWaitMs) {
      window.location.href = container.getAttribute('data-failed-url');
      return;
    }

    window.setTimeout(poll, pollIntervalMs);
  }

  poll();
})();
