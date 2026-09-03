(function () {
  'use strict';

  var dropzone = document.getElementById('dropzone');
  var fileInput = document.getElementById('file-input');
  var selectedList = document.getElementById('selected-list');
  var uploadBtn = document.getElementById('upload-btn');
  var expirySelect = document.getElementById('expiry-select');
  var progressWrap = document.getElementById('progress-wrap');
  var progressFill = document.getElementById('progress-fill');
  var progressText = document.getElementById('progress-text');
  var resultBox = document.getElementById('result');
  var resultLink = document.getElementById('result-link');
  var resultOpen = document.getElementById('result-open');
  var resultCopy = document.getElementById('result-copy');
  var errorBox = document.getElementById('error');

  var selectedFiles = [];

  function humanSize(bytes) {
    var units = ['Б', 'КБ', 'МБ', 'ГБ'];
    var i = 0;
    var size = bytes;
    while (size >= 1024 && i < units.length - 1) {
      size /= 1024;
      i++;
    }
    return (i === 0 ? bytes : size.toFixed(1)) + ' ' + units[i];
  }

  function renderSelected() {
    selectedList.innerHTML = '';
    selectedFiles.forEach(function (file, index) {
      var row = document.createElement('div');
      row.className = 'selected-item';

      var name = document.createElement('span');
      name.className = 'name';
      name.textContent = file.name + ' (' + humanSize(file.size) + ')';

      var remove = document.createElement('button');
      remove.className = 'remove';
      remove.type = 'button';
      remove.setAttribute('aria-label', 'Убрать файл');
      remove.textContent = '✕';
      remove.addEventListener('click', function () {
        selectedFiles.splice(index, 1);
        renderSelected();
      });

      row.appendChild(name);
      row.appendChild(remove);
      selectedList.appendChild(row);
    });
    uploadBtn.disabled = selectedFiles.length === 0;
  }

  function addFiles(fileList) {
    for (var i = 0; i < fileList.length; i++) {
      selectedFiles.push(fileList[i]);
    }
    renderSelected();
  }

  dropzone.addEventListener('click', function () {
    fileInput.click();
  });
  fileInput.addEventListener('change', function () {
    addFiles(fileInput.files);
    fileInput.value = '';
  });

  ['dragenter', 'dragover'].forEach(function (evt) {
    dropzone.addEventListener(evt, function (e) {
      e.preventDefault();
      dropzone.classList.add('dragover');
    });
  });
  ['dragleave', 'drop'].forEach(function (evt) {
    dropzone.addEventListener(evt, function (e) {
      e.preventDefault();
      dropzone.classList.remove('dragover');
    });
  });
  dropzone.addEventListener('drop', function (e) {
    if (e.dataTransfer && e.dataTransfer.files) {
      addFiles(e.dataTransfer.files);
    }
  });

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  resultCopy.addEventListener('click', function () {
    navigator.clipboard.writeText(resultLink.value).then(function () {
      resultCopy.textContent = 'Скопировано!';
      setTimeout(function () { resultCopy.textContent = 'Скопировать'; }, 1500);
    }).catch(function () {
      resultLink.select();
    });
  });

  uploadBtn.addEventListener('click', function () {
    if (selectedFiles.length === 0) {
      return;
    }
    errorBox.hidden = true;
    resultBox.hidden = true;

    var formData = new FormData();
    selectedFiles.forEach(function (file) {
      formData.append('files[]', file);
    });
    formData.append('expiry', expirySelect.value);

    uploadBtn.disabled = true;
    progressWrap.hidden = false;
    progressFill.style.width = '0%';
    progressText.textContent = '0%';

    var xhr = new XMLHttpRequest();
    xhr.open('POST', 'upload.php');

    xhr.upload.addEventListener('progress', function (e) {
      if (e.lengthComputable) {
        var percent = Math.round((e.loaded / e.total) * 100);
        progressFill.style.width = percent + '%';
        progressText.textContent = percent + '%';
      }
    });

    xhr.addEventListener('load', function () {
      progressWrap.hidden = true;
      uploadBtn.disabled = false;

      var data = null;
      try {
        data = JSON.parse(xhr.responseText);
      } catch (err) {
        data = null;
      }

      if (xhr.status >= 200 && xhr.status < 300 && data && data.ok) {
        var fullUrl = new URL(data.url, window.location.href).href;
        resultLink.value = fullUrl;
        resultOpen.href = fullUrl;
        resultBox.hidden = false;
        selectedFiles = [];
        renderSelected();
      } else {
        showError((data && data.error) || 'Ошибка загрузки. Попробуйте снова.');
      }
    });

    xhr.addEventListener('error', function () {
      progressWrap.hidden = true;
      uploadBtn.disabled = false;
      showError('Ошибка сети при загрузке файлов.');
    });

    xhr.send(formData);
  });
})();
