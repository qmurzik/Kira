<?php
/**
 * Файлообменник без регистрации — один файл.
 *
 * Специально сделан ОДНИМ PHP-файлом (без подпапок с JS/CSS, без .htaccess,
 * без .user.ini): на многих недорогих хостингах при заливке по FTP теряется
 * вложенность папок, AllowOverride для .htaccess бывает выключен, а PHP
 * может быть старой версии или с урезанным набором расширений. Один файл
 * с HTML/CSS/JS внутри и данными в JSON — минимум того, что может сломаться
 * при переносе на чужой хостинг.
 *
 * Требования: PHP 7.2+. Расширение fileinfo — желательно (иначе тип файла
 * определяется по расширению). Расширение zip — опционально, только для
 * кнопки «Скачать всё архивом»; если его нет, кнопка просто не показывается.
 *
 * Диагностика ограничений хостинга: index.php?diag=1
 */

declare(strict_types=1);
error_reporting(E_ALL);
ini_set('display_errors', '0');

define('FS_DATA_DIR', __DIR__ . '/data');
define('FS_MAX_FILE_SIZE', 1024 * 1024 * 1024);       // 1 GB — верхняя граница; реальный лимит всё равно диктует хостинг
define('FS_MAX_TOTAL_SIZE', 4 * 1024 * 1024 * 1024);   // 4 GB на одну ссылку
define('FS_MAX_FILES', 50);
define('FS_PREVIEW_MAX_BYTES', 2 * 1024 * 1024);       // до 2 MB — текстовый предпросмотр
define('FS_DEFAULT_EXPIRY_HOURS', 24 * 7);
define('FS_ALLOWED_EXPIRY_HOURS', [1, 24, 24 * 7, 24 * 30, 0]); // 0 = без ограничения
define('FS_CLEANUP_CHANCE', 20); // раз в ~20 запросов подчищаем истёкшие ссылки (крон на дешёвом хостинге не всегда доступен)

// ---------------------------------------------------------------------------
// Вспомогательные функции (без str_starts_with/match — для совместимости с PHP 7.2+)
// ---------------------------------------------------------------------------

function fs_starts_with(string $haystack, string $needle): bool
{
    return $needle === '' || substr($haystack, 0, strlen($needle)) === $needle;
}

function fs_ensure_data_dir(): ?string
{
    if (!is_dir(FS_DATA_DIR)) {
        if (!@mkdir(FS_DATA_DIR, 0777, true) && !is_dir(FS_DATA_DIR)) {
            return 'Не удалось создать папку "data" рядом с index.php. Создайте её вручную через FTP и выставьте права 755 (или 777, если хостинг требует).';
        }
    }
    if (!is_writable(FS_DATA_DIR)) {
        return 'Папка "data" рядом с index.php не доступна для записи. Выставьте на неё права 755 или 777 через FTP-клиент/панель хостинга.';
    }
    return null;
}

// Без похожих друг на друга символов (0/O, 1/l/I) — короткую ссылку легко
// продиктовать или перепечатать вручную без ошибок.
define('FS_ID_ALPHABET', '23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ');
define('FS_ID_LENGTH', 5);

/**
 * Короткие ссылки (5 символов из алфавита в 54 знака — это около
 * 459 миллионов комбинаций) — размен точности ради удобства: их проще
 * продиктовать, но и перебрать вручную/скриптом уже реальнее, чем
 * 128-битный идентификатор. Для ссылок с чувствительными файлами имеет
 * смысл ставить короткий срок действия.
 */
function fs_new_id(): string
{
    $alphabetLen = strlen(FS_ID_ALPHABET);
    do {
        $id = '';
        for ($i = 0; $i < FS_ID_LENGTH; $i++) {
            $id .= FS_ID_ALPHABET[random_int(0, $alphabetLen - 1)];
        }
    } while (is_dir(fs_share_dir($id)));
    return $id;
}

function fs_is_valid_id(string $id): bool
{
    return (bool) preg_match('/^[23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ]{' . FS_ID_LENGTH . '}$/', $id);
}

/**
 * Ссылка отдаётся без "id=" в query string ("?ab3xZ" вместо "?id=ab3xZ"),
 * чтобы была короче. Дополнительные параметры (f=, mode=, zip=) идут через
 * "&" как обычно и парсятся PHP в $_GET штатно.
 */
function fs_extract_short_id(): string
{
    if (isset($_GET['id']) && $_GET['id'] !== '') {
        return (string) $_GET['id'];
    }
    $query = $_SERVER['QUERY_STRING'] ?? '';
    if ($query === '') {
        return '';
    }
    $first = strtok($query, '&');
    if ($first !== false && strpos($first, '=') === false) {
        return urldecode($first);
    }
    return '';
}

function fs_share_dir(string $id): string
{
    return FS_DATA_DIR . '/' . $id;
}

function fs_meta_path(string $id): string
{
    return fs_share_dir($id) . '/meta.json';
}

function fs_load_meta(string $id): ?array
{
    if (!fs_is_valid_id($id)) {
        return null;
    }
    $path = fs_meta_path($id);
    if (!is_file($path)) {
        return null;
    }
    $raw = file_get_contents($path);
    $data = $raw === false ? null : json_decode($raw, true);
    return is_array($data) ? $data : null;
}

function fs_save_meta(string $id, array $data): void
{
    file_put_contents(fs_meta_path($id), json_encode($data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT), LOCK_EX);
}

function fs_is_expired(array $meta): bool
{
    return $meta['expires_at'] !== null && $meta['expires_at'] < time();
}

function fs_delete_share(string $id): void
{
    $dir = fs_share_dir($id);
    if (!is_dir($dir)) {
        return;
    }
    foreach (scandir($dir) ?: [] as $f) {
        if ($f !== '.' && $f !== '..') {
            @unlink($dir . '/' . $f);
        }
    }
    @rmdir($dir);
}

function fs_load_meta_fresh(string $id): ?array
{
    $meta = fs_load_meta($id);
    if ($meta === null) {
        return null;
    }
    if (fs_is_expired($meta)) {
        fs_delete_share($id);
        return null;
    }
    return $meta;
}

/** Без крона на дешёвом хостинге истёкшие ссылки чистим при случайных запросах. */
function fs_maybe_cleanup(): void
{
    if (!is_dir(FS_DATA_DIR) || random_int(1, FS_CLEANUP_CHANCE) !== 1) {
        return;
    }
    foreach (glob(FS_DATA_DIR . '/*', GLOB_ONLYDIR) ?: [] as $dir) {
        $id = basename($dir);
        $meta = fs_load_meta($id);
        if ($meta !== null && fs_is_expired($meta)) {
            fs_delete_share($id);
        }
    }
}

function fs_human_size(int $bytes): string
{
    $units = ['Б', 'КБ', 'МБ', 'ГБ', 'ТБ'];
    $i = 0;
    $size = (float) $bytes;
    while ($size >= 1024 && $i < count($units) - 1) {
        $size /= 1024;
        $i++;
    }
    return ($i === 0 ? (string) $bytes : number_format($size, 1, '.', ' ')) . ' ' . $units[$i];
}

function fs_sanitize_display_name(string $name): string
{
    $name = basename(str_replace('\\', '/', $name));
    $name = preg_replace('/[\x00-\x1F\x7F]/', '', $name) ?? $name;
    $name = trim($name);
    return $name === '' ? 'file' : mb_substr($name, 0, 200);
}

function fs_safe_ext(string $name): string
{
    $ext = strtolower(pathinfo($name, PATHINFO_EXTENSION));
    $ext = preg_replace('/[^a-z0-9]/', '', $ext) ?? '';
    return substr($ext, 0, 10);
}

/** Примерный маппинг на случай, если на хостинге отключено расширение fileinfo. */
function fs_ext_mime_fallback(string $ext): string
{
    $map = [
        'jpg' => 'image/jpeg', 'jpeg' => 'image/jpeg', 'png' => 'image/png', 'gif' => 'image/gif',
        'webp' => 'image/webp', 'bmp' => 'image/bmp',
        'mp4' => 'video/mp4', 'webm' => 'video/webm', 'mov' => 'video/quicktime',
        'mp3' => 'audio/mpeg', 'wav' => 'audio/wav', 'ogg' => 'audio/ogg',
        'pdf' => 'application/pdf',
        'txt' => 'text/plain', 'md' => 'text/markdown', 'csv' => 'text/csv',
        'json' => 'application/json', 'xml' => 'application/xml',
        'html' => 'text/html', 'htm' => 'text/html',
        'zip' => 'application/zip',
    ];
    return $map[$ext] ?? 'application/octet-stream';
}

function fs_detect_mime(string $path, string $ext): string
{
    if (function_exists('finfo_open')) {
        $finfo = @finfo_open(FILEINFO_MIME_TYPE);
        if ($finfo) {
            $mime = finfo_file($finfo, $path);
            finfo_close($finfo);
            if ($mime) {
                return $mime;
            }
        }
    }
    if (function_exists('mime_content_type')) {
        $mime = @mime_content_type($path);
        if ($mime) {
            return $mime;
        }
    }
    return fs_ext_mime_fallback($ext);
}

function fs_file_category(string $mime, string $ext): string
{
    if ($mime === 'image/svg+xml' || $ext === 'svg') {
        return 'other'; // SVG может содержать скрипты — не встраиваем как картинку
    }
    if (fs_starts_with($mime, 'image/')) return 'image';
    if (fs_starts_with($mime, 'video/')) return 'video';
    if (fs_starts_with($mime, 'audio/')) return 'audio';
    if ($mime === 'application/pdf') return 'pdf';

    $textLike = ['application/json', 'application/xml', 'application/javascript', 'application/x-sh'];
    $textExt = ['txt', 'md', 'markdown', 'json', 'xml', 'log', 'csv', 'ini', 'yml', 'yaml', 'js',
        'css', 'html', 'htm', 'php', 'py', 'java', 'c', 'cpp', 'h', 'sh', 'sql', 'conf', 'env'];
    if (fs_starts_with($mime, 'text/') || in_array($mime, $textLike, true) || in_array($ext, $textExt, true)) {
        return 'text';
    }
    return 'other';
}

function fs_is_inlineable_mime(string $mime): bool
{
    if ($mime === 'image/svg+xml' || $mime === 'text/html' || $mime === 'application/xhtml+xml') {
        return false; // потенциально исполняемый контент — отдаём только на скачивание
    }
    return fs_starts_with($mime, 'image/') || fs_starts_with($mime, 'video/') || fs_starts_with($mime, 'audio/')
        || $mime === 'application/pdf' || fs_starts_with($mime, 'text/')
        || in_array($mime, ['application/json', 'application/xml', 'application/javascript'], true);
}

function fs_json_response(array $data, int $code = 200): void
{
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}

function h(string $s): string
{
    return htmlspecialchars($s, ENT_QUOTES, 'UTF-8');
}

// ---------------------------------------------------------------------------
// Общий CSS (используется и страницей загрузки, и страницей просмотра)
// ---------------------------------------------------------------------------

function fs_style(): string
{
    return <<<'CSS'
:root {
  color-scheme: light dark;
  --bg: #faf7fc; --card-bg: #fff; --text: #241b2e; --muted: #8b8298;
  --border: #ece3f3; --accent: #a855f7; --accent-2: #ec4899; --accent-hover: #9333ea;
  --danger: #e0356b; --radius: 18px;
  --grad: linear-gradient(135deg, var(--accent), var(--accent-2));
  --shadow: 0 20px 50px -25px rgba(168,85,247,.35);
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg:#15121c; --card-bg:#1f1a29; --text:#f3eef9; --muted:#a094ad;
    --border:#332a40; --accent:#c084fc; --accent-2:#f472b6; --accent-hover:#d6a8ff;
    --danger:#ff6b9d; --shadow: 0 20px 50px -25px rgba(0,0,0,.6);
  }
}
/* Toggle visibility only with the `hidden` attribute (el.hidden in JS) —
   this rule guarantees it always wins over any other display:* below. */
[hidden] { display: none !important; }

* { box-sizing: border-box; }
body {
  margin:0; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
  background:var(--bg); color:var(--text); position:relative; overflow-x:hidden;
}
body::before, body::after {
  content:""; position:fixed; width:46vmax; height:46vmax; border-radius:50%;
  filter:blur(90px); opacity:.16; z-index:-1; pointer-events:none;
}
body::before { top:-18vmax; right:-14vmax; background:var(--accent); }
body::after { bottom:-20vmax; left:-16vmax; background:var(--accent-2); }

.page { max-width:760px; margin:0 auto; padding:24px 16px 48px; min-height:100vh; display:flex; flex-direction:column; }
.topbar { padding:8px 0 22px; }
.brand {
  font-weight:800; font-size:1.15rem; text-decoration:none; letter-spacing:-.01em;
  background:var(--grad); -webkit-background-clip:text; background-clip:text; color:transparent;
}
.card { background:var(--card-bg); border:1px solid var(--border); border-radius:var(--radius); padding:30px; flex:1; box-shadow:var(--shadow); }
h1 { margin-top:0; font-size:1.5rem; letter-spacing:-.01em; }
.muted { color:var(--muted); }
.small { font-size:.85rem; }

.dropzone { border:2px dashed var(--border); border-radius:var(--radius); padding:38px 16px; text-align:center; cursor:pointer; margin:20px 0; transition:border-color .15s,background .15s; }
.dropzone.dragover { border-color:var(--accent); background:rgba(168,85,247,.08); }
.dropzone-icon { font-size:2rem; margin-bottom:8px; }

.selected-list { margin:8px 0; display:flex; flex-direction:column; gap:6px; }
.selected-item { display:flex; align-items:center; gap:10px; padding:8px 10px; border:1px solid var(--border); border-radius:10px; font-size:.9rem; }
.selected-item .name { flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.selected-item .remove { cursor:pointer; color:var(--danger); border:none; background:none; font-size:1rem; }

.options-row { display:flex; align-items:center; gap:10px; margin:16px 0; flex-wrap:wrap; }
.options-row select { padding:7px 12px; border-radius:999px; border:1px solid var(--border); background:var(--card-bg); color:var(--text); }

.btn {
  display:inline-flex; align-items:center; justify-content:center; gap:6px;
  border:1px solid var(--border); background:var(--card-bg); color:var(--text);
  border-radius:999px; padding:9px 16px; font-size:.95rem; cursor:pointer; text-decoration:none;
  transition:transform .1s, border-color .15s;
}
.btn:hover { border-color:var(--accent); }
.btn:active { transform:scale(.97); }
.btn-sm { padding:6px 12px; font-size:.85rem; }
.btn-lg { padding:13px 20px; font-size:1rem; width:100%; }
.btn-primary { background:var(--grad); border-color:transparent; color:#fff; font-weight:600; }
.btn-primary:hover { filter:brightness(1.06); border-color:transparent; }
.btn:disabled { opacity:.5; cursor:not-allowed; transform:none; }

.progress-wrap { margin:16px 0; }
.progress-bar { height:8px; border-radius:4px; background:var(--border); overflow:hidden; }
.progress-fill { height:100%; width:0; background:var(--grad); transition:width .1s; }

.result { margin-top:20px; padding:16px; border:1px solid var(--border); border-radius:14px; }
.link-row { display:flex; gap:8px; margin:10px 0; }
.link-row input { flex:1; padding:9px 12px; border-radius:999px; border:1px solid var(--border); background:var(--bg); color:var(--text); font-size:.95rem; }

.error { margin-top:16px; padding:12px 14px; border-radius:12px; background:rgba(224,53,107,.1); color:var(--danger); border:1px solid var(--danger); }
.footer { text-align:center; padding-top:24px; font-size:.8rem; }

.share-head { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; flex-wrap:wrap; margin-bottom:20px; }
.share-actions { display:flex; gap:8px; flex-wrap:wrap; }

.file-list { display:flex; flex-direction:column; gap:12px; }
.file-card { border:1px solid var(--border); border-radius:14px; padding:12px; transition:border-color .15s; }
.file-card:hover { border-color:var(--accent); }
.file-row { display:flex; align-items:center; gap:10px; }
.file-icon { font-size:1.6rem; }
.file-info { flex:1; min-width:0; }
.file-name { font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.file-buttons { display:flex; gap:6px; flex-shrink:0; }

.preview-thumb { display:block; max-width:100%; max-height:320px; margin-top:10px; border-radius:10px; object-fit:contain; cursor:zoom-in; }
.preview-open { display:block; }
.preview-media, .preview-pdf { width:100%; margin-top:10px; border-radius:10px; border:none; }
.preview-media { max-height:400px; }
.preview-pdf { height:480px; }
.preview-audio { width:100%; margin-top:10px; }
.pdf-details summary { cursor:pointer; margin-top:8px; }
.text-preview { margin-top:10px; padding:10px; background:var(--bg); border-radius:10px; max-height:360px; overflow:auto; white-space:pre-wrap; word-break:break-word; font-size:.85rem; }

.empty-state { text-align:center; padding:48px 20px; }
.empty-state .sakura { font-size:2.4rem; display:block; margin-bottom:8px; }

.diag-table { width:100%; border-collapse:collapse; margin-top:12px; font-size:.9rem; }
.diag-table td { padding:6px 8px; border-bottom:1px solid var(--border); }
.diag-ok { color:#2e9e4e; } .diag-bad { color:var(--danger); }

.lightbox {
  position:fixed; inset:0; background:rgba(10,6,15,.92);
  display:flex; align-items:center; justify-content:center; z-index:1000;
}
.lightbox img { max-width:92vw; max-height:92vh; object-fit:contain; border-radius:10px; }
.lightbox-close {
  position:absolute; top:16px; right:16px; width:40px; height:40px; border-radius:50%;
  font-size:1.2rem; background:rgba(255,255,255,.1); border:none; color:#fff; cursor:pointer;
}
.lightbox-close:hover { background:rgba(255,255,255,.2); }
CSS;
}

function fs_head(string $title): void
{
    echo "<!doctype html>\n<html lang=\"ru\">\n<head>\n<meta charset=\"utf-8\">\n";
    echo "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n";
    echo '<title>' . h($title) . "</title>\n<style>" . fs_style() . "</style>\n</head>\n<body>\n<div class=\"page\">\n";
    echo "<header class=\"topbar\"><a href=\"?\" class=\"brand\">🌸 Файлообменник</a></header>\n";
}

function fs_foot(): void
{
    echo "<footer class=\"footer muted\">Файлы хранятся временно и удаляются автоматически по истечении срока ссылки.</footer>\n</div>\n";
}

// ---------------------------------------------------------------------------
// Диагностика ограничений хостинга: index.php?diag=1
// ---------------------------------------------------------------------------

function fs_bytes_to_human_ini(string $iniValue): string
{
    return $iniValue === '' ? '0' : $iniValue;
}

function render_diag_page(): void
{
    $dataErr = fs_ensure_data_dir();
    $rows = [
        ['Версия PHP', PHP_VERSION, version_compare(PHP_VERSION, '7.2.0', '>=')],
        ['upload_max_filesize', fs_bytes_to_human_ini(ini_get('upload_max_filesize') ?: ''), true],
        ['post_max_size', fs_bytes_to_human_ini(ini_get('post_max_size') ?: ''), true],
        ['max_file_uploads', (string) ini_get('max_file_uploads'), true],
        ['max_execution_time', ini_get('max_execution_time') . ' сек', true],
        ['Папка data доступна для записи', $dataErr === null ? 'да' : $dataErr, $dataErr === null],
        ['Расширение fileinfo (определение типа файла)', extension_loaded('fileinfo') ? 'есть' : 'нет — используется резервный вариант по расширению файла', extension_loaded('fileinfo')],
        ['Расширение zip (кнопка «Скачать всё архивом»)', extension_loaded('zip') ? 'есть' : 'нет — кнопка будет скрыта', extension_loaded('zip')],
        ['random_bytes (генерация ссылок)', function_exists('random_bytes') ? 'есть' : 'нет — критично!', function_exists('random_bytes')],
    ];

    fs_head('Диагностика — Файлообменник');
    echo '<main class="card"><h1>Диагностика хостинга</h1><p class="muted">Показывает реальные ограничения вашего хостинга, чтобы было понятно, почему что-то может не работать.</p>';
    echo '<table class="diag-table">';
    foreach ($rows as $r) {
        [$label, $value, $ok] = $r;
        $cls = $ok ? 'diag-ok' : 'diag-bad';
        echo '<tr><td>' . h($label) . '</td><td class="' . $cls . '">' . h((string) $value) . '</td></tr>';
    }
    echo '</table>';
    echo '<p class="muted small" style="margin-top:16px">Путь к папке данных: ' . h(FS_DATA_DIR) . '</p>';
    echo '<p style="margin-top:20px"><a class="btn" href="?">← На главную</a></p></main>';
    fs_foot();
    echo '</body></html>';
}

// ---------------------------------------------------------------------------
// Загрузка файлов (POST на этот же index.php)
// ---------------------------------------------------------------------------

function handle_upload(): void
{
    $dataErr = fs_ensure_data_dir();
    if ($dataErr !== null) {
        fs_json_response(['ok' => false, 'error' => $dataErr], 500);
    }

    if (empty($_FILES['files']) || empty($_FILES['files']['name'])) {
        fs_json_response(['ok' => false, 'error' => 'Файлы не выбраны'], 400);
    }

    $names = $_FILES['files']['name'];
    $count = is_array($names) ? count($names) : 0;

    if ($count < 1) {
        fs_json_response(['ok' => false, 'error' => 'Файлы не выбраны'], 400);
    }
    if ($count > FS_MAX_FILES) {
        fs_json_response(['ok' => false, 'error' => 'Слишком много файлов (максимум ' . FS_MAX_FILES . ')'], 400);
    }

    $totalSizeGuess = array_sum($_FILES['files']['size']);
    if ($totalSizeGuess > FS_MAX_TOTAL_SIZE) {
        fs_json_response(['ok' => false, 'error' => 'Суммарный размер файлов превышает лимит'], 400);
    }

    $expiryHours = isset($_POST['expiry']) ? (int) $_POST['expiry'] : FS_DEFAULT_EXPIRY_HOURS;
    if (!in_array($expiryHours, FS_ALLOWED_EXPIRY_HOURS, true)) {
        $expiryHours = FS_DEFAULT_EXPIRY_HOURS;
    }

    $id = fs_new_id();
    $dir = fs_share_dir($id);
    if (!@mkdir($dir, 0777, true) && !is_dir($dir)) {
        fs_json_response(['ok' => false, 'error' => 'Не удалось создать папку для файлов. Проверьте права на "data" (755/777).'], 500);
    }

    $files = [];
    $totalSize = 0;

    for ($i = 0; $i < $count; $i++) {
        $error = $_FILES['files']['error'][$i];
        if ($error === UPLOAD_ERR_NO_FILE) {
            continue;
        }
        if ($error !== UPLOAD_ERR_OK) {
            fs_delete_share($id);
            $messages = [
                UPLOAD_ERR_INI_SIZE => 'Файл больше лимита сервера upload_max_filesize (см. index.php?diag=1)',
                UPLOAD_ERR_FORM_SIZE => 'Файл больше лимита формы',
                UPLOAD_ERR_PARTIAL => 'Файл загружен частично, попробуйте снова',
            ];
            fs_json_response(['ok' => false, 'error' => $messages[$error] ?? 'Ошибка загрузки файла'], 400);
        }

        $tmpName = $_FILES['files']['tmp_name'][$i];
        $size = (int) $_FILES['files']['size'][$i];

        if ($size > FS_MAX_FILE_SIZE) {
            fs_delete_share($id);
            fs_json_response(['ok' => false, 'error' => 'Файл превышает максимальный размер'], 400);
        }

        $totalSize += $size;
        if ($totalSize > FS_MAX_TOTAL_SIZE) {
            fs_delete_share($id);
            fs_json_response(['ok' => false, 'error' => 'Суммарный размер файлов превышает лимит'], 400);
        }

        $displayName = fs_sanitize_display_name($names[$i]);
        $ext = fs_safe_ext($displayName);
        $storedName = $i . ($ext !== '' ? '.' . $ext : '');
        $destination = $dir . '/' . $storedName;

        if (!is_uploaded_file($tmpName) || !move_uploaded_file($tmpName, $destination)) {
            fs_delete_share($id);
            fs_json_response(['ok' => false, 'error' => 'Не удалось сохранить файл на диск. Проверьте права на папку "data".'], 500);
        }

        $mime = fs_detect_mime($destination, $ext);
        $files[] = [
            'idx' => $i,
            'name' => $displayName,
            'stored' => $storedName,
            'size' => filesize($destination) ?: $size,
            'mime' => $mime,
        ];
    }

    if (empty($files)) {
        fs_delete_share($id);
        fs_json_response(['ok' => false, 'error' => 'Файлы не выбраны'], 400);
    }

    $now = time();
    fs_save_meta($id, [
        'id' => $id,
        'created_at' => $now,
        'expires_at' => $expiryHours > 0 ? $now + $expiryHours * 3600 : null,
        'files' => $files,
    ]);

    fs_json_response(['ok' => true, 'id' => $id, 'url' => '?' . $id]);
}

// ---------------------------------------------------------------------------
// Отдача содержимого файла: index.php?id=..&f=IDX&mode=inline|attachment|raw
// ---------------------------------------------------------------------------

function serve_file(string $id, int $idx, string $mode): void
{
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

    $path = realpath(fs_share_dir($id) . '/' . $entry['stored']);
    $dirReal = realpath(fs_share_dir($id));
    if ($path === false || $dirReal === false || !fs_starts_with($path, $dirReal) || !is_file($path)) {
        http_response_code(404);
        exit('Файл не найден');
    }

    $mime = $entry['mime'];

    if ($mode === 'raw') {
        if (!fs_starts_with($mime, 'text/') && !in_array($mime, ['application/json', 'application/xml', 'application/javascript'], true)) {
            http_response_code(415);
            exit('Предпросмотр недоступен для этого типа файла');
        }
        if (filesize($path) > FS_PREVIEW_MAX_BYTES) {
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
        header("Content-Security-Policy: default-src 'none'; sandbox");
    }
    header('Cache-Control: private, max-age=0, no-cache');

    $fp = fopen($path, 'rb');
    if ($fp) {
        fpassthru($fp);
        fclose($fp);
    }
    exit;
}

// ---------------------------------------------------------------------------
// ZIP всех файлов ссылки: index.php?id=..&zip=1
// ---------------------------------------------------------------------------

function serve_zip(string $id): void
{
    $meta = fs_load_meta_fresh($id);
    if ($meta === null) {
        http_response_code(410);
        exit('Ссылка не найдена или срок её действия истёк.');
    }
    if (!class_exists('ZipArchive')) {
        http_response_code(501);
        exit('На этом хостинге не установлено расширение PHP zip — архивирование недоступно. Скачайте файлы по отдельности.');
    }

    $dir = fs_share_dir($id);
    $tmpZip = tempnam(sys_get_temp_dir(), 'share_');
    if ($tmpZip === false) {
        http_response_code(500);
        exit('Не удалось создать архив (нет доступа к временной папке)');
    }

    $zip = new ZipArchive();
    $zip->open($tmpZip, ZipArchive::OVERWRITE);
    $usedNames = [];
    foreach ($meta['files'] as $f) {
        $path = $dir . '/' . $f['stored'];
        if (!is_file($path)) {
            continue;
        }
        $name = $f['name'];
        if (isset($usedNames[$name])) {
            $usedNames[$name]++;
            $name = $f['idx'] . '_' . $name;
        } else {
            $usedNames[$name] = 1;
        }
        $zip->addFile($path, $name);
    }
    $zip->close();

    header('Content-Type: application/zip');
    header('Content-Disposition: attachment; filename="share-' . substr($id, 0, 8) . '.zip"');
    header('Content-Length: ' . filesize($tmpZip));

    $fp = fopen($tmpZip, 'rb');
    if ($fp) {
        fpassthru($fp);
        fclose($fp);
    }
    unlink($tmpZip);
    exit;
}

// ---------------------------------------------------------------------------
// Страница загрузки: index.php
// ---------------------------------------------------------------------------

function render_upload_page(): void
{
    $dataErr = fs_ensure_data_dir();
    $uploadLimit = min(
        fs_ini_bytes(ini_get('upload_max_filesize') ?: '2M'),
        fs_ini_bytes(ini_get('post_max_size') ?: '8M')
    );

    fs_head('Файлообменник — без регистрации');
    echo '<main class="card">';
    echo '<h1>Поделитесь файлами за секунду</h1>';
    echo '<p class="muted">Без регистрации и входа. Загрузите файлы — получите ссылку, по которой их можно просмотреть и скачать.</p>';

    if ($dataErr !== null) {
        echo '<div class="error">' . h($dataErr) . '</div>';
    } else {
        echo '<p class="muted small">Лимит хостинга на один файл в этой форме: ' . h(fs_human_size($uploadLimit)) . ' — <a href="?diag=1">подробнее об ограничениях сервера</a>.</p>';
    }

    echo '<div id="dropzone" class="dropzone">';
    echo '<input type="file" id="file-input" multiple hidden>';
    echo '<div class="dropzone-inner"><div class="dropzone-icon">⬆️</div>';
    echo '<p><strong>Перетащите файлы сюда</strong> или нажмите, чтобы выбрать</p>';
    echo '<p class="muted small">Фото, видео, документы, архивы — что угодно</p></div></div>';

    echo '<div id="selected-list" class="selected-list"></div>';

    echo '<div class="options-row"><label for="expiry-select">Срок действия ссылки:</label>';
    echo '<select id="expiry-select">';
    echo '<option value="1">1 час</option><option value="24">1 день</option>';
    echo '<option value="168" selected>7 дней</option><option value="720">30 дней</option>';
    echo '<option value="0">Без ограничения</option></select></div>';

    echo '<button id="upload-btn" class="btn btn-primary btn-lg" disabled>Загрузить</button>';

    echo '<div id="progress-wrap" class="progress-wrap" hidden><div class="progress-bar"><div id="progress-fill" class="progress-fill"></div></div><div id="progress-text" class="muted small">0%</div></div>';

    echo '<div id="result" class="result" hidden><p>✅ Готово! Ссылка на файлы:</p>';
    echo '<div class="link-row"><input type="text" id="result-link" readonly><button id="result-copy" class="btn">Скопировать</button></div>';
    echo '<a id="result-open" class="btn btn-primary" target="_blank" rel="noopener">Открыть страницу с файлами</a></div>';

    echo '<div id="error" class="error" hidden></div>';
    echo '</main>';
    fs_foot();
    ?>
<script>
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
    var units = ['Б', 'КБ', 'МБ', 'ГБ']; var i = 0; var size = bytes;
    while (size >= 1024 && i < units.length - 1) { size /= 1024; i++; }
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
      remove.className = 'remove'; remove.type = 'button';
      remove.setAttribute('aria-label', 'Убрать файл'); remove.textContent = '✕';
      remove.addEventListener('click', function () { selectedFiles.splice(index, 1); renderSelected(); });
      row.appendChild(name); row.appendChild(remove);
      selectedList.appendChild(row);
    });
    uploadBtn.disabled = selectedFiles.length === 0;
  }

  function addFiles(fileList) {
    for (var i = 0; i < fileList.length; i++) selectedFiles.push(fileList[i]);
    renderSelected();
  }

  dropzone.addEventListener('click', function () { fileInput.click(); });
  fileInput.addEventListener('change', function () { addFiles(fileInput.files); fileInput.value = ''; });
  ['dragenter', 'dragover'].forEach(function (evt) {
    dropzone.addEventListener(evt, function (e) { e.preventDefault(); dropzone.classList.add('dragover'); });
  });
  ['dragleave', 'drop'].forEach(function (evt) {
    dropzone.addEventListener(evt, function (e) { e.preventDefault(); dropzone.classList.remove('dragover'); });
  });
  dropzone.addEventListener('drop', function (e) {
    if (e.dataTransfer && e.dataTransfer.files) addFiles(e.dataTransfer.files);
  });

  function showError(message) { errorBox.textContent = message; errorBox.hidden = false; }

  resultCopy.addEventListener('click', function () {
    navigator.clipboard.writeText(resultLink.value).then(function () {
      resultCopy.textContent = 'Скопировано!';
      setTimeout(function () { resultCopy.textContent = 'Скопировать'; }, 1500);
    }).catch(function () { resultLink.select(); });
  });

  uploadBtn.addEventListener('click', function () {
    if (selectedFiles.length === 0) return;
    errorBox.hidden = true; resultBox.hidden = true;

    var formData = new FormData();
    selectedFiles.forEach(function (file) { formData.append('files[]', file); });
    formData.append('expiry', expirySelect.value);

    uploadBtn.disabled = true;
    progressWrap.hidden = false;
    progressFill.style.width = '0%'; progressText.textContent = '0%';

    var xhr = new XMLHttpRequest();
    xhr.open('POST', '?');
    xhr.upload.addEventListener('progress', function (e) {
      if (e.lengthComputable) {
        var percent = Math.round((e.loaded / e.total) * 100);
        progressFill.style.width = percent + '%'; progressText.textContent = percent + '%';
      }
    });
    xhr.addEventListener('load', function () {
      progressWrap.hidden = true; uploadBtn.disabled = false;
      var data = null;
      try { data = JSON.parse(xhr.responseText); } catch (err) { data = null; }
      if (xhr.status >= 200 && xhr.status < 300 && data && data.ok) {
        var fullUrl = new URL(data.url, window.location.href).href;
        resultLink.value = fullUrl; resultOpen.href = fullUrl; resultBox.hidden = false;
        selectedFiles = []; renderSelected();
      } else {
        showError((data && data.error) || 'Ошибка загрузки. Попробуйте снова.');
      }
    });
    xhr.addEventListener('error', function () {
      progressWrap.hidden = true; uploadBtn.disabled = false;
      showError('Ошибка сети при загрузке файлов.');
    });
    xhr.send(formData);
  });
})();
</script>
</body>
</html>
    <?php
}

function fs_ini_bytes(string $val): int
{
    $val = trim($val);
    if ($val === '') {
        return 0;
    }
    $last = strtolower($val[strlen($val) - 1]);
    $num = (int) $val;
    switch ($last) {
        case 'g': return $num * 1024 * 1024 * 1024;
        case 'm': return $num * 1024 * 1024;
        case 'k': return $num * 1024;
        default: return $num;
    }
}

// ---------------------------------------------------------------------------
// Страница просмотра ссылки: index.php?id=...
// ---------------------------------------------------------------------------

function render_share_page(string $id): void
{
    $meta = fs_load_meta_fresh($id);
    if ($meta === null) {
        http_response_code(404);
        fs_head('Ссылка не найдена — Файлообменник');
        echo '<main class="card empty-state"><span class="sakura">🌸</span><h1>Ссылка недоступна</h1>';
        echo '<p>Файлы не найдены — ссылка неверна или срок её действия истёк.</p>';
        echo '<a class="btn" href="?">Загрузить свои файлы</a></main>';
        fs_foot();
        echo '</body></html>';
        return;
    }

    $totalSize = array_sum(array_column($meta['files'], 'size'));
    $fileCount = count($meta['files']);
    $expiresText = $meta['expires_at'] ? 'до ' . date('d.m.Y H:i', $meta['expires_at']) : 'без ограничения по времени';

    fs_head('Файлы по ссылке — Файлообменник');
    echo '<main class="card"><div class="share-head"><div>';
    $wordEnd = $fileCount === 1 ? '' : ($fileCount < 5 ? 'а' : 'ов');
    echo '<h1>' . (int) $fileCount . ' файл' . $wordEnd . '</h1>';
    echo '<p class="muted">Общий размер: ' . h(fs_human_size((int) $totalSize)) . ' · Ссылка действует ' . h($expiresText) . '</p>';
    echo '</div><div class="share-actions">';
    echo '<button class="btn" id="copy-link">🔗 Скопировать ссылку</button>';
    if ($fileCount > 1 && class_exists('ZipArchive')) {
        echo '<a class="btn btn-primary" href="?' . h($id) . '&zip=1">⬇ Скачать всё (ZIP)</a>';
    }
    echo '</div></div><div class="file-list">';

    $icons = ['image' => '🖼️', 'video' => '🎬', 'audio' => '🎵', 'pdf' => '📕', 'text' => '📄', 'other' => '📦'];

    foreach ($meta['files'] as $f) {
        $ext = fs_safe_ext($f['name']);
        $cat = fs_file_category($f['mime'], $ext);
        $base = '?' . h($id) . '&f=' . (int) $f['idx'];
        $downloadUrl = $base . '&mode=attachment';
        $inlineUrl = $base . '&mode=inline';
        $rawUrl = $base . '&mode=raw';

        echo '<div class="file-card" data-category="' . h($cat) . '"><div class="file-row">';
        echo '<span class="file-icon">' . $icons[$cat] . '</span>';
        echo '<div class="file-info"><div class="file-name">' . h($f['name']) . '</div>';
        echo '<div class="file-size muted">' . h(fs_human_size((int) $f['size'])) . '</div></div>';
        echo '<div class="file-buttons">';
        if ($cat === 'text' && $f['size'] <= FS_PREVIEW_MAX_BYTES) {
            echo '<button class="btn btn-sm js-preview-toggle" data-raw-url="' . h($rawUrl) . '">👁 Просмотр</button>';
        }
        echo '<a class="btn btn-sm" href="' . $downloadUrl . '">⬇ Скачать</a></div></div>';

        if ($cat === 'image') {
            echo '<a class="preview-open js-image-open" href="' . $inlineUrl . '">';
            echo '<img class="preview-thumb" src="' . $inlineUrl . '" alt="' . h($f['name']) . '" loading="lazy"></a>';
        } elseif ($cat === 'video') {
            echo '<video class="preview-media" controls preload="metadata"><source src="' . $inlineUrl . '" type="' . h($f['mime']) . '"></video>';
        } elseif ($cat === 'audio') {
            echo '<audio class="preview-audio" controls preload="metadata"><source src="' . $inlineUrl . '" type="' . h($f['mime']) . '"></audio>';
        } elseif ($cat === 'pdf') {
            echo '<details class="pdf-details"><summary>📕 Открыть PDF предпросмотр</summary>';
            echo '<iframe class="preview-pdf" src="' . $inlineUrl . '" loading="lazy"></iframe></details>';
        }

        echo '<pre class="text-preview" hidden></pre></div>';
    }

    echo '</div></main>';
    fs_foot();
    ?>
<div class="lightbox" id="lightbox" hidden>
  <button class="lightbox-close" id="lightbox-close" aria-label="Закрыть">✕</button>
  <img id="lightbox-img" alt="">
</div>
<script>
(function () {
  'use strict';
  var copyBtn = document.getElementById('copy-link');
  if (copyBtn) {
    copyBtn.addEventListener('click', function () {
      navigator.clipboard.writeText(window.location.href).then(function () {
        var original = copyBtn.textContent;
        copyBtn.textContent = '✅ Скопировано';
        setTimeout(function () { copyBtn.textContent = original; }, 1500);
      }).catch(function () { prompt('Скопируйте ссылку:', window.location.href); });
    });
  }

  document.querySelectorAll('.js-preview-toggle').forEach(function (btn) {
    var card = btn.closest('.file-card');
    var pre = card ? card.querySelector('.text-preview') : null;
    var loaded = false;
    btn.addEventListener('click', function () {
      if (!pre) return;
      if (!pre.hidden) { pre.hidden = true; btn.textContent = '👁 Просмотр'; return; }
      if (loaded) { pre.hidden = false; btn.textContent = '🙈 Скрыть'; return; }
      btn.disabled = true; btn.textContent = 'Загрузка…';
      fetch(btn.dataset.rawUrl)
        .then(function (res) { if (!res.ok) throw new Error('preview failed'); return res.text(); })
        .then(function (text) { pre.textContent = text; pre.hidden = false; loaded = true; btn.textContent = '🙈 Скрыть'; })
        .catch(function () { btn.textContent = 'Не удалось загрузить'; })
        .finally(function () { btn.disabled = false; });
    });
  });

  var lightbox = document.getElementById('lightbox');
  var lightboxImg = document.getElementById('lightbox-img');
  var lightboxClose = document.getElementById('lightbox-close');
  document.querySelectorAll('.js-image-open').forEach(function (link) {
    link.addEventListener('click', function (e) {
      e.preventDefault();
      lightboxImg.src = link.getAttribute('href');
      var img = link.querySelector('img');
      lightboxImg.alt = img ? img.alt : '';
      lightbox.hidden = false;
    });
  });
  function closeLightbox() { lightbox.hidden = true; lightboxImg.src = ''; }
  if (lightboxClose) lightboxClose.addEventListener('click', closeLightbox);
  if (lightbox) lightbox.addEventListener('click', function (e) { if (e.target === lightbox) closeLightbox(); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && lightbox && !lightbox.hidden) closeLightbox(); });
})();
</script>
</body>
</html>
    <?php
}

// ---------------------------------------------------------------------------
// Маршрутизация
// ---------------------------------------------------------------------------

fs_maybe_cleanup();

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$id = fs_extract_short_id();
$fidx = isset($_GET['f']) ? (int) $_GET['f'] : null;
$mode = isset($_GET['mode']) ? (string) $_GET['mode'] : 'attachment';

if ($method === 'POST') {
    handle_upload();
} elseif (isset($_GET['diag'])) {
    render_diag_page();
} elseif ($id !== '' && $fidx !== null) {
    serve_file($id, $fidx, $mode);
} elseif ($id !== '' && isset($_GET['zip'])) {
    serve_zip($id);
} elseif ($id !== '') {
    render_share_page($id);
} else {
    render_upload_page();
}
