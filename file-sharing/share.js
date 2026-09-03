(function () {
  'use strict';

  var copyBtn = document.getElementById('copy-link');
  if (copyBtn) {
    copyBtn.addEventListener('click', function () {
      navigator.clipboard.writeText(window.location.href).then(function () {
        var original = copyBtn.textContent;
        copyBtn.textContent = '✅ Скопировано';
        setTimeout(function () { copyBtn.textContent = original; }, 1500);
      }).catch(function () {
        prompt('Скопируйте ссылку:', window.location.href);
      });
    });
  }

  // Текстовый предпросмотр: контент выводится через textContent, а не innerHTML,
  // так что даже если файл содержит HTML/скрипты, он останется просто текстом.
  document.querySelectorAll('.js-preview-toggle').forEach(function (btn) {
    var card = btn.closest('.file-card');
    var pre = card ? card.querySelector('.text-preview') : null;
    var loaded = false;

    btn.addEventListener('click', function () {
      if (!pre) return;
      if (!pre.hidden) {
        pre.hidden = true;
        btn.textContent = '👁 Просмотр';
        return;
      }
      if (loaded) {
        pre.hidden = false;
        btn.textContent = '🙈 Скрыть';
        return;
      }
      btn.disabled = true;
      btn.textContent = 'Загрузка…';
      fetch(btn.dataset.rawUrl)
        .then(function (res) {
          if (!res.ok) throw new Error('preview failed');
          return res.text();
        })
        .then(function (text) {
          pre.textContent = text;
          pre.hidden = false;
          loaded = true;
          btn.textContent = '🙈 Скрыть';
        })
        .catch(function () {
          btn.textContent = 'Не удалось загрузить';
        })
        .finally(function () {
          btn.disabled = false;
        });
    });
  });

  // Лайтбокс для изображений
  var lightbox = document.getElementById('lightbox');
  var lightboxImg = document.getElementById('lightbox-img');
  var lightboxClose = document.getElementById('lightbox-close');

  document.querySelectorAll('.js-image-open').forEach(function (link) {
    link.addEventListener('click', function (e) {
      e.preventDefault();
      lightboxImg.src = link.getAttribute('href');
      lightboxImg.alt = link.querySelector('img') ? link.querySelector('img').alt : '';
      lightbox.hidden = false;
    });
  });

  function closeLightbox() {
    lightbox.hidden = true;
    lightboxImg.src = '';
  }
  if (lightboxClose) lightboxClose.addEventListener('click', closeLightbox);
  if (lightbox) {
    lightbox.addEventListener('click', function (e) {
      if (e.target === lightbox) closeLightbox();
    });
  }
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && lightbox && !lightbox.hidden) closeLightbox();
  });
})();
