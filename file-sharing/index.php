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
    if ($mime === 'application/zip' || $ext === 'zip') return 'archive';

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
// SVG-иконки (один инлайновый спрайт, без внешних библиотек)
// ---------------------------------------------------------------------------

function fs_icon(string $name, string $class = 'icon'): string
{
    return '<svg class="' . h($class) . '" aria-hidden="true" focusable="false"><use href="#icon-' . h($name) . '"></use></svg>';
}

function fs_icon_sprite(): string
{
    return <<<'SVG'
<svg style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true" focusable="false">
<defs>
<symbol id="icon-upload" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <path d="M7 18a4.2 4.2 0 0 1-.7-8.34A5.5 5.5 0 0 1 17 8.1a3.6 3.6 0 0 1 .3 7.19"/>
  <path d="M12 10v8"/><path d="M9 13l3-3 3 3"/>
</symbol>
<symbol id="icon-link" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <path d="M9.5 14.5l5-5"/>
  <path d="M8.2 16.8a3.2 3.2 0 0 1 0-6.4h2.1"/>
  <path d="M15.8 7.2a3.2 3.2 0 0 1 0 6.4h-2.1"/>
</symbol>
<symbol id="icon-copy" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <rect x="8.5" y="8.5" width="11" height="11" rx="2.5"/>
  <path d="M4.5 15.5v-9a2 2 0 0 1 2-2h9"/>
</symbol>
<symbol id="icon-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
  <path d="M4.5 12.5l5 5L20 6.5"/>
</symbol>
<symbol id="icon-close" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
  <path d="M6 6l12 12M18 6L6 18"/>
</symbol>
<symbol id="icon-download" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <path d="M12 3.5v11"/><path d="M7.2 10.7L12 15.5l4.8-4.8"/><path d="M5 20h14"/>
</symbol>
<symbol id="icon-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <path d="M2.5 12S6.2 5.5 12 5.5 21.5 12 21.5 12 17.8 18.5 12 18.5 2.5 12 2.5 12z"/>
  <circle cx="12" cy="12" r="3"/>
</symbol>
<symbol id="icon-image" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <rect x="3" y="4.5" width="18" height="15" rx="2.5"/>
  <circle cx="8.7" cy="9.7" r="1.6"/>
  <path d="M21 15.5l-5.4-5.2a1.5 1.5 0 0 0-2.05.06L6 18"/>
</symbol>
<symbol id="icon-video" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <rect x="2.5" y="5.5" width="13.5" height="13" rx="2.5"/>
  <path d="M16 9.7l5.5-3v10.6l-5.5-3"/>
</symbol>
<symbol id="icon-audio" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <path d="M9 17.5V5.8L19 4v11.7"/><circle cx="6.3" cy="17.7" r="2.7"/><circle cx="16.3" cy="15.7" r="2.7"/>
</symbol>
<symbol id="icon-doc" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <path d="M6 2.5h8l5 5v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3.5a1 1 0 0 1 1-1z"/><path d="M14 2.5v5h5"/>
</symbol>
<symbol id="icon-doc-text" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <path d="M6 2.5h8l5 5v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3.5a1 1 0 0 1 1-1z"/><path d="M14 2.5v5h5"/>
  <path d="M8 13.2h8M8 16.8h5.2"/>
</symbol>
<symbol id="icon-archive" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <rect x="3" y="4" width="18" height="4.4" rx="1.2"/>
  <path d="M5 8.4v9.1a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8.4"/>
  <path d="M10.2 12.6h3.6"/>
</symbol>
<symbol id="icon-file" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <path d="M6 2.5h8l5 5v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3.5a1 1 0 0 1 1-1z"/><path d="M14 2.5v5h5"/>
</symbol>
<symbol id="icon-chevron-down" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
  <path d="M6 9.5l6 6 6-6"/>
</symbol>
<symbol id="icon-external" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
  <path d="M14 3.5h6.5V10"/><path d="M20.3 3.7l-9 9"/>
  <path d="M18.5 13v5.5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h5.5"/>
</symbol>
<symbol id="icon-sparkle" viewBox="0 0 24 24" fill="currentColor" stroke="none">
  <path d="M12 2.2l1.9 5.9 5.9 1.9-5.9 1.9L12 21.8l-1.9-5.9-5.9-1.9 5.9-1.9L12 2.2z"/>
</symbol>
</defs>
</svg>
SVG;
}

// ---------------------------------------------------------------------------
// Общий CSS (используется и страницей загрузки, и страницей просмотра)
// ---------------------------------------------------------------------------

function fs_style(): string
{
    return <<<'CSS'
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');

:root {
  color-scheme: dark;
  --bg-0: #07050c; --bg-1: #0d0916; --bg-2: #140f22;
  --glass: rgba(255,255,255,.045); --glass-strong: rgba(255,255,255,.075);
  --border: rgba(255,255,255,.09); --border-strong: rgba(255,255,255,.17);
  --text: #f4f1fa; --muted: #a79db8; --muted-2: #7d7290;
  --accent: #b083ff; --accent-2: #ff8fc9; --accent-3: #63d9ff;
  --danger: #ff6b8b; --success: #57e6a1;
  --grad: linear-gradient(135deg,#b083ff 0%,#ff8fc9 100%);
  --grad-soft: linear-gradient(135deg, rgba(176,131,255,.18), rgba(255,143,201,.18));
  --radius-lg: 28px; --radius-md: 18px; --radius-sm: 12px;
  --shadow-glow: 0 0 0 1px rgba(255,255,255,.04), 0 20px 60px -20px rgba(176,131,255,.45);
  --ease: cubic-bezier(.22,1,.36,1);
}

/* Toggle visibility only with the `hidden` attribute (el.hidden in JS) —
   this rule guarantees it always wins over any other display:* below. */
[hidden] { display: none !important; }

@keyframes blobDrift { 0%,100% { transform:translate(0,0) scale(1); } 50% { transform:translate(3%,-4%) scale(1.08); } }
@keyframes sparkFloat { 0% { transform:translateY(0) rotate(0deg); opacity:0; } 10% { opacity:.9; } 90% { opacity:.35; } 100% { transform:translateY(-46px) rotate(25deg); opacity:0; } }
@keyframes shimmer { 0% { background-position:-200% 0; } 100% { background-position:200% 0; } }
@keyframes fadeUp { from { opacity:0; transform:translateY(14px); } to { opacity:1; transform:translateY(0); } }
@keyframes scaleIn { from { opacity:0; transform:scale(.94); } to { opacity:1; transform:scale(1); } }
@keyframes toastIn { from { opacity:0; transform:translateY(12px) scale(.96); } to { opacity:1; transform:translateY(0) scale(1); } }
@keyframes toastOut { to { opacity:0; transform:translateY(8px) scale(.96); } }

* { box-sizing: border-box; }
body {
  margin:0; font-family:'Plus Jakarta Sans',-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
  background:var(--bg-0); color:var(--text); position:relative; overflow-x:hidden;
}

.bg-ambient { position:fixed; inset:0; z-index:-1; overflow:hidden; pointer-events:none; }
.bg-ambient .blob { position:absolute; border-radius:50%; filter:blur(70px); opacity:.35; animation:blobDrift 18s ease-in-out infinite; }
.bg-ambient .b1 { width:50vmax; height:50vmax; top:-20vmax; right:-16vmax; background:radial-gradient(circle, var(--accent), transparent 70%); }
.bg-ambient .b2 { width:42vmax; height:42vmax; bottom:-18vmax; left:-14vmax; background:radial-gradient(circle, var(--accent-2), transparent 70%); animation-delay:-6s; }
.bg-ambient .b3 { width:30vmax; height:30vmax; top:35%; left:50%; background:radial-gradient(circle, var(--accent-3), transparent 70%); opacity:.14; animation-delay:-11s; }
.bg-ambient .grid {
  position:absolute; inset:0; opacity:.05;
  background-image:linear-gradient(var(--border-strong) 1px, transparent 1px), linear-gradient(90deg, var(--border-strong) 1px, transparent 1px);
  background-size:48px 48px; mask-image:radial-gradient(ellipse at 50% 0%, black, transparent 70%);
  -webkit-mask-image:radial-gradient(ellipse at 50% 0%, black, transparent 70%);
}
.bg-ambient .spark { position:absolute; animation:sparkFloat 6s ease-in infinite; opacity:0; }
.bg-ambient .spark .icon { width:12px; height:12px; color:var(--accent-2); }

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration:.001ms !important; animation-iteration-count:1 !important; transition-duration:.001ms !important; }
  .bg-ambient .blob, .bg-ambient .spark { animation:none !important; }
}

.icon { width:20px; height:20px; flex-shrink:0; display:inline-block; vertical-align:-5px; }
.btn .icon { width:16px; height:16px; vertical-align:-3px; }
.dropzone-icon { width:52px; height:52px; margin:0 auto 16px; display:block; color:var(--accent); filter:drop-shadow(0 0 14px rgba(176,131,255,.45)); }
.file-icon-wrap .icon { width:22px; height:22px; }
.sakura { width:56px; height:56px; color:var(--accent-2); margin:0 auto 18px; display:block; filter:drop-shadow(0 0 16px rgba(255,143,201,.5)); }

.page { max-width:780px; margin:0 auto; padding:28px 18px 56px; min-height:100vh; display:flex; flex-direction:column; position:relative; }
.topbar { display:flex; align-items:center; padding:6px 0 28px; }
.brand { display:inline-flex; align-items:center; gap:8px; font-weight:800; font-size:1.2rem; letter-spacing:-.02em; text-decoration:none; }
.brand-icon { display:inline-flex; color:var(--accent-2); }
.brand-icon .icon { width:22px; height:22px; }
.brand-text { background:var(--grad); -webkit-background-clip:text; background-clip:text; color:transparent; }

.card {
  background:var(--glass); border:1px solid var(--border); border-radius:var(--radius-lg);
  backdrop-filter:blur(20px) saturate(140%); -webkit-backdrop-filter:blur(20px) saturate(140%);
  box-shadow:var(--shadow-glow); padding:34px; flex:1; animation:fadeUp .5s var(--ease) both;
}
h1 { margin:0 0 10px; font-size:clamp(1.6rem,4vw,2.3rem); font-weight:800; letter-spacing:-.02em; line-height:1.15; }
.grad-text { background:var(--grad); -webkit-background-clip:text; background-clip:text; color:transparent; }
.muted { color:var(--muted); }
.small { font-size:.85rem; }
.lead { font-size:1.02rem; line-height:1.55; margin:0 0 4px; }

.inline-error {
  margin:18px 0; padding:14px 16px; border-radius:14px; background:rgba(255,107,139,.1);
  border:1px solid rgba(255,107,139,.35); color:#ffb3c6; display:flex; align-items:center; gap:10px; font-size:.9rem;
}
.inline-error .icon { color:var(--danger); }

.dropzone {
  position:relative; border:1.5px dashed var(--border-strong); border-radius:var(--radius-md);
  padding:52px 20px; text-align:center; cursor:pointer; margin:24px 0; background:var(--grad-soft); overflow:hidden;
  transition:border-color .25s var(--ease), transform .2s var(--ease), box-shadow .25s var(--ease);
}
.dropzone::before { content:""; position:absolute; inset:0; background:var(--grad); opacity:0; transition:opacity .3s; pointer-events:none; }
.dropzone:hover { border-color:var(--accent); box-shadow:0 0 40px -10px rgba(176,131,255,.5); transform:translateY(-2px); }
.dropzone:hover .dropzone-icon { transform:translateY(-4px) scale(1.08); }
.dropzone.dragover { border-color:var(--accent-2); box-shadow:0 0 60px -6px rgba(255,143,201,.6); transform:scale(1.01); }
.dropzone.dragover::before { opacity:.12; }
.dropzone-icon { transition:transform .3s var(--ease); }
.dropzone-title { font-weight:700; font-size:1.05rem; margin:0 0 4px; position:relative; }
.dropzone-hint { color:var(--muted); font-size:.88rem; margin:0; position:relative; }

.selected-list { margin:14px 0; display:flex; flex-direction:column; gap:8px; }
.selected-item {
  display:flex; align-items:center; gap:10px; padding:10px 12px; background:var(--glass-strong);
  border:1px solid var(--border); border-radius:var(--radius-sm); font-size:.9rem; animation:fadeUp .3s var(--ease) both;
}
.chip-icon { display:flex; color:var(--accent); flex-shrink:0; }
.selected-item .name { flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.selected-item .remove {
  display:inline-flex; align-items:center; justify-content:center; width:26px; height:26px; border-radius:50%;
  cursor:pointer; color:var(--muted); border:none; background:transparent; transition:.2s; padding:0;
}
.selected-item .remove .icon { width:14px; height:14px; }
.selected-item .remove:hover { background:rgba(255,107,139,.15); color:var(--danger); }

.options-row { display:flex; align-items:center; gap:10px; margin:18px 0; flex-wrap:wrap; }
.options-row select {
  padding:9px 14px; border-radius:999px; border:1px solid var(--border); background:var(--glass-strong);
  color:var(--text); font-family:inherit; font-size:.9rem; cursor:pointer;
}
.options-row select:focus-visible, .btn:focus-visible, input:focus-visible, .dropzone:focus-visible {
  outline:2px solid var(--accent); outline-offset:2px;
}

.btn {
  display:inline-flex; align-items:center; justify-content:center; gap:7px;
  border:1px solid var(--border); background:var(--glass-strong); color:var(--text);
  border-radius:999px; padding:11px 18px; font-size:.92rem; font-weight:600; font-family:inherit; cursor:pointer; text-decoration:none;
  transition:transform .15s var(--ease), box-shadow .2s var(--ease), border-color .2s, background .2s;
}
.btn:hover { border-color:var(--border-strong); transform:translateY(-1px); }
.btn:active { transform:translateY(0) scale(.97); }
.btn-sm { padding:7px 13px; font-size:.82rem; }
.btn-lg { padding:15px 22px; font-size:1rem; width:100%; }
.btn-primary { background:var(--grad); background-size:160% 160%; border-color:transparent; color:#fff; box-shadow:0 10px 30px -8px rgba(176,131,255,.55); }
.btn-primary:hover { box-shadow:0 14px 40px -6px rgba(255,143,201,.6); transform:translateY(-2px) scale(1.01); background-position:100% 0; }
.btn-primary:active { transform:translateY(0) scale(.98); }
.btn:disabled { opacity:.45; cursor:not-allowed; transform:none; box-shadow:none; }
.js-archive-toggle.is-open .icon { transform:rotate(180deg); transition:transform .2s var(--ease); }

.progress-wrap { margin:20px 0; }
.progress-bar { height:10px; border-radius:999px; background:var(--glass-strong); overflow:hidden; border:1px solid var(--border); }
.progress-fill { height:100%; width:0; background:var(--grad); background-size:200% 100%; border-radius:999px; transition:width .2s var(--ease); position:relative; overflow:hidden; }
.progress-fill::after {
  content:""; position:absolute; inset:0; background:linear-gradient(90deg,transparent,rgba(255,255,255,.5),transparent);
  background-size:60% 100%; animation:shimmer 1.4s linear infinite;
}
.progress-text { margin-top:8px; text-align:right; font-variant-numeric:tabular-nums; }

.result {
  margin-top:24px; padding:22px; background:var(--glass-strong); border:1px solid var(--border);
  border-radius:var(--radius-md); animation:scaleIn .4s var(--ease) both; position:relative; overflow:hidden;
}
.result::before { content:""; position:absolute; inset:0; background:radial-gradient(circle at 20% 0%, rgba(87,230,161,.15), transparent 60%); pointer-events:none; }
.result-title { display:flex; align-items:center; gap:8px; font-weight:700; color:var(--success); margin:0; position:relative; }
.link-row { display:flex; gap:8px; margin:12px 0; position:relative; }
.link-row input { flex:1; padding:11px 14px; border-radius:999px; border:1px solid var(--border); background:var(--bg-1); color:var(--text); font-size:.9rem; font-family:inherit; min-width:0; }

.footer { text-align:center; padding-top:28px; font-size:.78rem; color:var(--muted-2); }

.toast-root { position:fixed; left:50%; bottom:24px; transform:translateX(-50%); display:flex; flex-direction:column; gap:10px; z-index:2000; pointer-events:none; width:min(92vw,420px); }
.toast {
  pointer-events:auto; display:flex; align-items:center; gap:10px; padding:13px 16px; border-radius:14px;
  background:rgba(20,15,34,.92); border:1px solid var(--border-strong); backdrop-filter:blur(16px);
  box-shadow:0 12px 30px -8px rgba(0,0,0,.5); animation:toastIn .35s var(--ease) both; font-size:.88rem;
}
.toast.leaving { animation:toastOut .25s var(--ease) forwards; }
.toast-error { border-color:rgba(255,107,139,.4); } .toast-error .icon { color:var(--danger); }
.toast-success { border-color:rgba(87,230,161,.4); } .toast-success .icon { color:var(--success); }

.share-head { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; flex-wrap:wrap; margin-bottom:22px; }
.share-actions { display:flex; gap:8px; flex-wrap:wrap; }

.file-list { display:flex; flex-direction:column; gap:14px; margin-top:4px; }
.file-card {
  background:var(--glass); border:1px solid var(--border); border-radius:var(--radius-md); padding:16px;
  transition:transform .25s var(--ease), border-color .25s, box-shadow .25s; animation:fadeUp .45s var(--ease) both;
}
.file-card:hover { transform:translateY(-3px); border-color:var(--border-strong); box-shadow:0 16px 40px -18px rgba(176,131,255,.5); }
.file-row { display:flex; align-items:center; gap:12px; }
.file-icon-wrap { width:42px; height:42px; border-radius:12px; display:flex; align-items:center; justify-content:center; background:var(--grad-soft); color:var(--accent); flex-shrink:0; }
.file-info { flex:1; min-width:0; }
.file-name { font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.file-size { font-size:.82rem; }
.file-buttons { display:flex; gap:6px; flex-shrink:0; flex-wrap:wrap; justify-content:flex-end; }

.preview-thumb { display:block; max-width:100%; max-height:320px; margin-top:12px; border-radius:12px; object-fit:contain; cursor:zoom-in; }
.preview-open { display:block; }
.preview-media, .preview-pdf { width:100%; margin-top:12px; border-radius:12px; border:none; }
.preview-media { max-height:400px; }
.preview-pdf { height:480px; }
.preview-audio { width:100%; margin-top:12px; }
.pdf-details summary { cursor:pointer; margin-top:8px; display:flex; align-items:center; gap:8px; }
.text-preview { margin-top:12px; padding:12px; background:var(--bg-1); border-radius:12px; max-height:360px; overflow:auto; white-space:pre-wrap; word-break:break-word; font-size:.85rem; }

.archive-list { margin-top:12px; padding:10px 12px; background:var(--bg-1); border:1px solid var(--border); border-radius:var(--radius-sm); max-height:280px; overflow:auto; }
.archive-entry { display:flex; justify-content:space-between; gap:12px; padding:6px 2px; font-size:.83rem; border-bottom:1px dashed var(--border); }
.archive-entry:last-child { border-bottom:none; }
.archive-entry .name { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.archive-entry .size { color:var(--muted); flex-shrink:0; }
.archive-more { padding-top:8px; color:var(--muted); font-size:.8rem; text-align:center; }

.empty-state { text-align:center; padding:60px 20px; }

.diag-table { width:100%; border-collapse:collapse; margin-top:14px; font-size:.88rem; }
.diag-table td { padding:10px 10px; border-bottom:1px solid var(--border); }
.diag-ok { color:var(--success); } .diag-bad { color:var(--danger); }

.lightbox { position:fixed; inset:0; background:rgba(5,3,10,.88); backdrop-filter:blur(6px); display:flex; align-items:center; justify-content:center; z-index:1000; animation:fadeUp .25s var(--ease) both; padding:24px; }
.lightbox img { max-width:92vw; max-height:88vh; object-fit:contain; border-radius:16px; box-shadow:0 30px 80px -20px rgba(0,0,0,.7); animation:scaleIn .3s var(--ease) both; }
.lightbox-close {
  position:absolute; top:20px; right:20px; width:44px; height:44px; border-radius:50%; display:flex; align-items:center; justify-content:center;
  background:var(--glass-strong); border:1px solid var(--border-strong); color:#fff; cursor:pointer; transition:.2s;
}
.lightbox-close:hover { background:rgba(255,255,255,.18); transform:rotate(90deg); }

@media (max-width:560px) {
  .card { padding:22px 18px; border-radius:20px; }
  .dropzone { padding:38px 14px; }
  .share-head { flex-direction:column; }
  .share-actions { width:100%; }
  .share-actions .btn { flex:1; }
  .file-buttons { width:100%; }
  .file-buttons .btn { flex:1; }
}
CSS;
}

function fs_head(string $title): void
{
    echo "<!doctype html>\n<html lang=\"ru\">\n<head>\n<meta charset=\"utf-8\">\n";
    echo "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n";
    echo '<meta name="theme-color" content="#07050c">' . "\n";
    echo '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>' . "\n";
    echo '<title>' . h($title) . "</title>\n<style>" . fs_style() . "</style>\n</head>\n<body>\n";
    echo fs_icon_sprite();

    echo '<div class="bg-ambient" aria-hidden="true"><span class="blob b1"></span><span class="blob b2"></span><span class="blob b3"></span><span class="grid"></span>';
    $sparks = [[8, 18, '0s'], [85, 14, '-1.4s'], [72, 68, '-3s'], [20, 76, '-4.4s'], [48, 32, '-2.1s'], [92, 55, '-0.8s']];
    foreach ($sparks as $sp) {
        echo '<span class="spark" style="left:' . $sp[0] . '%;top:' . $sp[1] . '%;animation-delay:' . $sp[2] . '">' . fs_icon('sparkle') . '</span>';
    }
    echo '</div>';
    echo '<div class="toast-root" id="toast-root" aria-live="polite"></div>';
    ?>
<script>
(function () {
  var root = document.getElementById('toast-root');
  window.toast = function (type, message) {
    if (!root) return;
    var el = document.createElement('div');
    el.className = 'toast toast-' + type;
    var iconId = type === 'error' ? 'close' : (type === 'success' ? 'check' : 'sparkle');
    el.innerHTML = '<svg class="icon"><use href="#icon-' + iconId + '"></use></svg><span></span>';
    el.querySelector('span').textContent = message;
    root.appendChild(el);
    setTimeout(function () {
      el.classList.add('leaving');
      setTimeout(function () { el.remove(); }, 250);
    }, 4200);
  };
})();
</script>
    <?php
    echo "<div class=\"page\">\n";
    echo '<header class="topbar"><a href="?" class="brand"><span class="brand-icon">' . fs_icon('sparkle') . '</span><span class="brand-text">Файлообменник</span></a></header>' . "\n";
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
    echo '<p style="margin-top:20px"><a class="btn" href="?">' . fs_icon('external') . ' На главную</a></p></main>';
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
    if (!in_array($mode, ['inline', 'attachment', 'raw', 'list'], true)) {
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

    if ($mode === 'list') {
        if ($mime !== 'application/zip' || !class_exists('ZipArchive')) {
            fs_json_response(['ok' => false, 'error' => 'Просмотр содержимого недоступен для этого файла'], 415);
        }
        $zip = new ZipArchive();
        if ($zip->open($path) !== true) {
            fs_json_response(['ok' => false, 'error' => 'Не удалось открыть архив'], 500);
        }
        $total = $zip->numFiles;
        $max = min($total, 500);
        $entries = [];
        for ($i = 0; $i < $max; $i++) {
            $stat = $zip->statIndex($i);
            if ($stat === false || substr($stat['name'], -1) === '/') {
                continue;
            }
            $entries[] = ['name' => $stat['name'], 'size' => (int) $stat['size']];
        }
        $zip->close();
        fs_json_response(['ok' => true, 'entries' => $entries, 'total' => $total, 'truncated' => $total > $max]);
    }

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
    echo '<h1>Делись файлами <span class="grad-text">красиво</span></h1>';
    echo '<p class="muted lead">Без регистрации и входа. Загрузите файлы — получите короткую ссылку, по которой их можно посмотреть и скачать.</p>';

    if ($dataErr !== null) {
        echo '<div class="inline-error">' . fs_icon('close') . '<span>' . h($dataErr) . '</span></div>';
    } else {
        echo '<p class="muted small">Лимит хостинга на один файл: <strong>' . h(fs_human_size($uploadLimit)) . '</strong> — <a href="?diag=1">подробнее об ограничениях сервера</a>.</p>';
    }

    echo '<div id="dropzone" class="dropzone" tabindex="0" role="button" aria-label="Выбрать или перетащить файлы">';
    echo '<input type="file" id="file-input" multiple hidden>';
    echo '<div class="dropzone-inner">';
    echo fs_icon('upload', 'icon dropzone-icon');
    echo '<p class="dropzone-title">Перетащите файлы сюда</p>';
    echo '<p class="dropzone-hint">или нажмите, чтобы выбрать — фото, видео, документы, архивы</p>';
    echo '</div></div>';

    echo '<div id="selected-list" class="selected-list"></div>';

    echo '<div class="options-row"><label for="expiry-select" class="muted small">Срок действия ссылки</label>';
    echo '<select id="expiry-select">';
    echo '<option value="1">1 час</option><option value="24">1 день</option>';
    echo '<option value="168" selected>7 дней</option><option value="720">30 дней</option>';
    echo '<option value="0">Без ограничения</option></select></div>';

    echo '<button id="upload-btn" class="btn btn-primary btn-lg" disabled>' . fs_icon('upload') . ' Загрузить</button>';

    echo '<div id="progress-wrap" class="progress-wrap" hidden><div class="progress-bar"><div id="progress-fill" class="progress-fill"></div></div><div id="progress-text" class="muted small">0%</div></div>';

    echo '<div id="result" class="result" hidden>';
    echo '<p class="result-title">' . fs_icon('check') . ' Готово! Ссылка на файлы</p>';
    echo '<div class="link-row"><input type="text" id="result-link" readonly><button id="result-copy" class="btn">' . fs_icon('copy') . ' Скопировать</button></div>';
    echo '<a id="result-open" class="btn btn-primary" target="_blank" rel="noopener">' . fs_icon('external') . ' Открыть страницу с файлами</a>';
    echo '</div>';

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
  var selectedFiles = [];

  function humanSize(bytes) {
    var units = ['Б', 'КБ', 'МБ', 'ГБ']; var i = 0; var size = bytes;
    while (size >= 1024 && i < units.length - 1) { size /= 1024; i++; }
    return (i === 0 ? bytes : size.toFixed(1)) + ' ' + units[i];
  }

  function extToIcon(name) {
    var ext = (name.split('.').pop() || '').toLowerCase();
    if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].indexOf(ext) !== -1) return 'image';
    if (['mp4', 'webm', 'mov', 'mkv', 'avi'].indexOf(ext) !== -1) return 'video';
    if (['mp3', 'wav', 'ogg', 'flac', 'm4a'].indexOf(ext) !== -1) return 'audio';
    if (ext === 'zip') return 'archive';
    if (ext === 'pdf') return 'doc';
    if (['txt', 'md', 'json', 'csv', 'log', 'js', 'css', 'html', 'php', 'py'].indexOf(ext) !== -1) return 'doc-text';
    return 'file';
  }

  function renderSelected() {
    selectedList.innerHTML = '';
    selectedFiles.forEach(function (file, index) {
      var row = document.createElement('div');
      row.className = 'selected-item';
      var iconWrap = document.createElement('span');
      iconWrap.className = 'chip-icon';
      iconWrap.innerHTML = '<svg class="icon"><use href="#icon-' + extToIcon(file.name) + '"></use></svg>';
      var name = document.createElement('span');
      name.className = 'name';
      name.textContent = file.name + ' · ' + humanSize(file.size);
      var remove = document.createElement('button');
      remove.className = 'remove'; remove.type = 'button';
      remove.setAttribute('aria-label', 'Убрать файл');
      remove.innerHTML = '<svg class="icon"><use href="#icon-close"></use></svg>';
      remove.addEventListener('click', function () { selectedFiles.splice(index, 1); renderSelected(); });
      row.appendChild(iconWrap); row.appendChild(name); row.appendChild(remove);
      selectedList.appendChild(row);
    });
    uploadBtn.disabled = selectedFiles.length === 0;
  }

  function addFiles(fileList) {
    for (var i = 0; i < fileList.length; i++) selectedFiles.push(fileList[i]);
    renderSelected();
  }

  dropzone.addEventListener('click', function () { fileInput.click(); });
  dropzone.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fileInput.click(); }
  });
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

  resultCopy.addEventListener('click', function () {
    navigator.clipboard.writeText(resultLink.value).then(function () {
      toast('success', 'Ссылка скопирована');
    }).catch(function () { resultLink.select(); });
  });

  uploadBtn.addEventListener('click', function () {
    if (selectedFiles.length === 0) return;
    resultBox.hidden = true;

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
        toast('success', 'Файлы загружены');
        selectedFiles = []; renderSelected();
      } else {
        toast('error', (data && data.error) || 'Ошибка загрузки. Попробуйте снова.');
      }
    });
    xhr.addEventListener('error', function () {
      progressWrap.hidden = true; uploadBtn.disabled = false;
      toast('error', 'Ошибка сети при загрузке файлов.');
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
        echo '<main class="card empty-state">' . fs_icon('sparkle', 'icon sakura') . '<h1>Ссылка недоступна</h1>';
        echo '<p class="muted">Файлы не найдены — ссылка неверна или срок её действия истёк.</p>';
        echo '<a class="btn btn-primary" href="?">' . fs_icon('upload') . ' Загрузить свои файлы</a></main>';
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
    echo '<button class="btn" id="copy-link">' . fs_icon('link') . ' Скопировать ссылку</button>';
    if ($fileCount > 1 && class_exists('ZipArchive')) {
        echo '<a class="btn btn-primary" href="?' . h($id) . '&zip=1">' . fs_icon('download') . ' Скачать всё (ZIP)</a>';
    }
    echo '</div></div><div class="file-list">';

    $iconMap = ['image' => 'image', 'video' => 'video', 'audio' => 'audio', 'pdf' => 'doc', 'text' => 'doc-text', 'archive' => 'archive', 'other' => 'file'];

    foreach ($meta['files'] as $f) {
        $ext = fs_safe_ext($f['name']);
        $cat = fs_file_category($f['mime'], $ext);
        $base = '?' . h($id) . '&f=' . (int) $f['idx'];
        $downloadUrl = $base . '&mode=attachment';
        $inlineUrl = $base . '&mode=inline';
        $rawUrl = $base . '&mode=raw';
        $listUrl = $base . '&mode=list';
        $delay = min((int) $f['idx'], 10) * 45;

        echo '<div class="file-card" data-category="' . h($cat) . '" style="animation-delay:' . $delay . 'ms"><div class="file-row">';
        echo '<span class="file-icon-wrap">' . fs_icon($iconMap[$cat]) . '</span>';
        echo '<div class="file-info"><div class="file-name">' . h($f['name']) . '</div>';
        echo '<div class="file-size muted">' . h(fs_human_size((int) $f['size'])) . '</div></div>';
        echo '<div class="file-buttons">';
        if ($cat === 'text' && $f['size'] <= FS_PREVIEW_MAX_BYTES) {
            echo '<button class="btn btn-sm js-preview-toggle" data-raw-url="' . h($rawUrl) . '">' . fs_icon('eye') . ' Просмотр</button>';
        }
        if ($cat === 'archive' && class_exists('ZipArchive')) {
            echo '<button class="btn btn-sm js-archive-toggle" data-list-url="' . h($listUrl) . '">' . fs_icon('chevron-down') . ' Содержимое</button>';
        }
        echo '<a class="btn btn-sm" href="' . $downloadUrl . '">' . fs_icon('download') . ' Скачать</a></div></div>';

        if ($cat === 'image') {
            echo '<a class="preview-open js-image-open" href="' . $inlineUrl . '">';
            echo '<img class="preview-thumb" src="' . $inlineUrl . '" alt="' . h($f['name']) . '" loading="lazy"></a>';
        } elseif ($cat === 'video') {
            echo '<video class="preview-media" controls preload="metadata"><source src="' . $inlineUrl . '" type="' . h($f['mime']) . '"></video>';
        } elseif ($cat === 'audio') {
            echo '<audio class="preview-audio" controls preload="metadata"><source src="' . $inlineUrl . '" type="' . h($f['mime']) . '"></audio>';
        } elseif ($cat === 'pdf') {
            echo '<details class="pdf-details"><summary>' . fs_icon('doc') . ' Открыть PDF предпросмотр</summary>';
            echo '<iframe class="preview-pdf" src="' . $inlineUrl . '" loading="lazy"></iframe></details>';
        }

        echo '<pre class="text-preview" hidden></pre>';
        if ($cat === 'archive') {
            echo '<div class="archive-list" hidden></div>';
        }
        echo '</div>';
    }

    echo '</div></main>';
    fs_foot();
    ?>
<div class="lightbox" id="lightbox" hidden>
  <button class="lightbox-close" id="lightbox-close" aria-label="Закрыть"><svg class="icon"><use href="#icon-close"></use></svg></button>
  <img id="lightbox-img" alt="">
</div>
<script>
(function () {
  'use strict';

  function humanSize(bytes) {
    var units = ['Б', 'КБ', 'МБ', 'ГБ']; var i = 0; var size = bytes;
    while (size >= 1024 && i < units.length - 1) { size /= 1024; i++; }
    return (i === 0 ? bytes : size.toFixed(1)) + ' ' + units[i];
  }

  function setBtnLabel(btn, iconId, text) {
    btn.innerHTML = '<svg class="icon"><use href="#icon-' + iconId + '"></use></svg> ' + text;
  }

  var copyBtn = document.getElementById('copy-link');
  if (copyBtn) {
    copyBtn.addEventListener('click', function () {
      navigator.clipboard.writeText(window.location.href).then(function () {
        toast('success', 'Ссылка скопирована');
      }).catch(function () { prompt('Скопируйте ссылку:', window.location.href); });
    });
  }

  document.querySelectorAll('.js-preview-toggle').forEach(function (btn) {
    var card = btn.closest('.file-card');
    var pre = card ? card.querySelector('.text-preview') : null;
    var loaded = false;
    btn.addEventListener('click', function () {
      if (!pre) return;
      if (!pre.hidden) { pre.hidden = true; setBtnLabel(btn, 'eye', 'Просмотр'); return; }
      if (loaded) { pre.hidden = false; setBtnLabel(btn, 'eye', 'Скрыть'); return; }
      btn.disabled = true; setBtnLabel(btn, 'eye', 'Загрузка…');
      fetch(btn.dataset.rawUrl)
        .then(function (res) { if (!res.ok) throw new Error('preview failed'); return res.text(); })
        .then(function (text) { pre.textContent = text; pre.hidden = false; loaded = true; setBtnLabel(btn, 'eye', 'Скрыть'); })
        .catch(function () { setBtnLabel(btn, 'eye', 'Просмотр'); toast('error', 'Не удалось загрузить предпросмотр'); })
        .finally(function () { btn.disabled = false; });
    });
  });

  document.querySelectorAll('.js-archive-toggle').forEach(function (btn) {
    var card = btn.closest('.file-card');
    var box = card ? card.querySelector('.archive-list') : null;
    var loaded = false;
    btn.addEventListener('click', function () {
      if (!box) return;
      if (!box.hidden) { box.hidden = true; btn.classList.remove('is-open'); setBtnLabel(btn, 'chevron-down', 'Содержимое'); return; }
      if (loaded) { box.hidden = false; btn.classList.add('is-open'); setBtnLabel(btn, 'chevron-down', 'Скрыть'); return; }
      btn.disabled = true; setBtnLabel(btn, 'chevron-down', 'Загрузка…');
      fetch(btn.dataset.listUrl)
        .then(function (res) { return res.json(); })
        .then(function (data) {
          if (!data.ok) throw new Error(data.error || 'list failed');
          box.innerHTML = '';
          data.entries.forEach(function (entry) {
            var row = document.createElement('div');
            row.className = 'archive-entry';
            var name = document.createElement('span');
            name.className = 'name'; name.textContent = entry.name;
            var size = document.createElement('span');
            size.className = 'size'; size.textContent = humanSize(entry.size);
            row.appendChild(name); row.appendChild(size);
            box.appendChild(row);
          });
          if (data.entries.length === 0) {
            var empty = document.createElement('div');
            empty.className = 'archive-more'; empty.textContent = 'Архив пуст';
            box.appendChild(empty);
          } else if (data.truncated) {
            var more = document.createElement('div');
            more.className = 'archive-more';
            more.textContent = 'Показаны первые ' + data.entries.length + ' из ' + data.total + ' файлов';
            box.appendChild(more);
          }
          box.hidden = false; loaded = true;
          btn.classList.add('is-open'); setBtnLabel(btn, 'chevron-down', 'Скрыть');
        })
        .catch(function () { setBtnLabel(btn, 'chevron-down', 'Содержимое'); toast('error', 'Не удалось прочитать содержимое архива'); })
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
