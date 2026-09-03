<?php
declare(strict_types=1);

require __DIR__ . '/lib.php';

$id = (string) ($_GET['id'] ?? '');
$meta = fs_load_meta_fresh($id);
if ($meta === null) {
    http_response_code(404);
}

function h(string $s): string
{
    return htmlspecialchars($s, ENT_QUOTES, 'UTF-8');
}
?><!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title><?= $meta ? 'Файлы по ссылке — Файлообменник' : 'Ссылка не найдена — Файлообменник' ?></title>
<link rel="stylesheet" href="style.css">
</head>
<body>
<div class="page">
  <header class="topbar">
    <a href="index.html" class="brand">📁 Файлообменник</a>
  </header>

<?php if ($meta === null): ?>
  <main class="card empty-state">
    <h1>Ссылка недоступна</h1>
    <p>Файлы не найдены — ссылка неверна или срок её действия истёк.</p>
    <a class="btn" href="index.html">Загрузить свои файлы</a>
  </main>
<?php else:
    $totalSize = array_sum(array_column($meta['files'], 'size'));
    $fileCount = count($meta['files']);
    $expiresText = $meta['expires_at']
        ? 'до ' . date('d.m.Y H:i', $meta['expires_at'])
        : 'без ограничения по времени';
?>
  <main class="card">
    <div class="share-head">
      <div>
        <h1><?= $fileCount ?> файл<?= $fileCount === 1 ? '' : ($fileCount < 5 ? 'а' : 'ов') ?></h1>
        <p class="muted">Общий размер: <?= h(fs_human_size((int) $totalSize)) ?> · Ссылка действует <?= h($expiresText) ?></p>
      </div>
      <div class="share-actions">
        <button class="btn" id="copy-link">🔗 Скопировать ссылку</button>
        <?php if ($fileCount > 1): ?>
          <a class="btn btn-primary" href="zip.php?id=<?= h($id) ?>">⬇ Скачать всё (ZIP)</a>
        <?php endif; ?>
      </div>
    </div>

    <div class="file-list">
      <?php foreach ($meta['files'] as $f):
          $ext = fs_safe_ext($f['name']);
          $cat = fs_file_category($f['mime'], $ext);
          $downloadUrl = 'file.php?id=' . h($id) . '&idx=' . (int) $f['idx'] . '&mode=attachment';
          $inlineUrl = 'file.php?id=' . h($id) . '&idx=' . (int) $f['idx'] . '&mode=inline';
          $rawUrl = 'file.php?id=' . h($id) . '&idx=' . (int) $f['idx'] . '&mode=raw';
      ?>
      <div class="file-card" data-category="<?= h($cat) ?>">
        <div class="file-row">
          <span class="file-icon"><?= match ($cat) {
              'image' => '🖼️', 'video' => '🎬', 'audio' => '🎵', 'pdf' => '📕', 'text' => '📄', default => '📦',
          } ?></span>
          <div class="file-info">
            <div class="file-name"><?= h($f['name']) ?></div>
            <div class="file-size muted"><?= h(fs_human_size((int) $f['size'])) ?></div>
          </div>
          <div class="file-buttons">
            <?php if ($cat === 'text' && $f['size'] <= fs_config()['preview_max_bytes']): ?>
              <button class="btn btn-sm js-preview-toggle" data-raw-url="<?= h($rawUrl) ?>">👁 Просмотр</button>
            <?php endif; ?>
            <a class="btn btn-sm" href="<?= $downloadUrl ?>">⬇ Скачать</a>
          </div>
        </div>

        <?php if ($cat === 'image'): ?>
          <a class="preview-open js-image-open" href="<?= $inlineUrl ?>">
            <img class="preview-thumb" src="<?= $inlineUrl ?>" alt="<?= h($f['name']) ?>" loading="lazy">
          </a>
        <?php elseif ($cat === 'video'): ?>
          <video class="preview-media" controls preload="metadata">
            <source src="<?= $inlineUrl ?>" type="<?= h($f['mime']) ?>">
          </video>
        <?php elseif ($cat === 'audio'): ?>
          <audio class="preview-audio" controls preload="metadata">
            <source src="<?= $inlineUrl ?>" type="<?= h($f['mime']) ?>">
          </audio>
        <?php elseif ($cat === 'pdf'): ?>
          <details class="pdf-details">
            <summary>📕 Открыть PDF предпросмотр</summary>
            <iframe class="preview-pdf" src="<?= $inlineUrl ?>" loading="lazy"></iframe>
          </details>
        <?php endif; ?>

        <pre class="text-preview" hidden></pre>
      </div>
      <?php endforeach; ?>
    </div>
  </main>
<?php endif; ?>

  <footer class="footer muted">Файлы хранятся временно и удаляются автоматически по истечении срока ссылки.</footer>
</div>

<?php if ($meta !== null): ?>
<div class="lightbox" id="lightbox" hidden>
  <button class="lightbox-close" id="lightbox-close" aria-label="Закрыть">✕</button>
  <img id="lightbox-img" alt="">
</div>
<script src="share.js"></script>
<?php endif; ?>
</body>
</html>
