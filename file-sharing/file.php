<?php
declare(strict_types=1);

require __DIR__ . '/lib.php';

$id = (string) ($_GET['id'] ?? '');
$idx = isset($_GET['idx']) ? (int) $_GET['idx'] : -1;
$mode = (string) ($_GET['mode'] ?? 'attachment');
if (!in_array($mode, ['inline', 'attachment', 'raw'], true)) {
    $mode = 'attachment';
}

$meta = fs_load_meta_fresh($id);
if ($meta === null) {
    http_response_code(410);
    header('Content-Type: text/plain; charset=utf-8');
    echo 'Ссылка не найдена или срок её действия истёк.';
    exit;
}

$entry = null;
foreach ($meta['files'] as $f) {
    if ((int) $f['idx'] === $idx) {
        $entry = $f;
        break;
    }
}
if ($entry === null) {
    http_response_code(404);
    exit('Файл не найден');
}

$path = realpath(fs_files_dir($id) . '/' . $entry['stored']);
$filesDirReal = realpath(fs_files_dir($id));
if ($path === false || $filesDirReal === false || !str_starts_with($path, $filesDirReal) || !is_file($path)) {
    http_response_code(404);
    exit('Файл не найден');
}

$mime = $entry['mime'];
$config = fs_config();

if ($mode === 'raw') {
    if (!str_starts_with($mime, 'text/') && !in_array($mime, ['application/json', 'application/xml', 'application/javascript'], true)) {
        http_response_code(415);
        exit('Предпросмотр недоступен для этого типа файла');
    }
    if (filesize($path) > $config['preview_max_bytes']) {
        http_response_code(413);
        exit('Файл слишком большой для предпросмотра');
    }
    header('Content-Type: text/plain; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    readfile($path);
    exit;
}

if ($mode === 'inline' && !fs_is_inlineable_mime($mime)) {
    $mode = 'attachment';
}

$safeName = str_replace(['"', "\r", "\n"], '', $entry['name']);
$disposition = $mode === 'inline' ? 'inline' : 'attachment';

header('Content-Type: ' . $mime);
header("Content-Disposition: {$disposition}; filename=\"{$safeName}\"; filename*=UTF-8''" . rawurlencode($safeName));
header('Content-Length: ' . filesize($path));
header('X-Content-Type-Options: nosniff');
if ($mode === 'inline') {
    // Файл рендерится в браузере — жёстко режем возможность исполнения скриптов
    header("Content-Security-Policy: default-src 'none'; sandbox");
}
header('Cache-Control: private, max-age=0, no-cache');

$fp = fopen($path, 'rb');
if ($fp) {
    fpassthru($fp);
    fclose($fp);
}
exit;
