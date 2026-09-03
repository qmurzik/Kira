<?php
/**
 * Общие вспомогательные функции файлообменника.
 * Хранилище: storage/files/<id>/<idx>.<ext> — сами файлы,
 *            storage/meta/<id>.json          — метаданные ссылки.
 */

function fs_config(): array
{
    static $config = null;
    if ($config === null) {
        $config = require __DIR__ . '/config.php';
    }
    return $config;
}

function fs_new_id(): string
{
    return bin2hex(random_bytes(16));
}

function fs_is_valid_id(string $id): bool
{
    return (bool) preg_match('/^[a-f0-9]{32}$/', $id);
}

function fs_files_dir(string $id): string
{
    return fs_config()['storage_dir'] . '/files/' . $id;
}

function fs_meta_path(string $id): string
{
    return fs_config()['storage_dir'] . '/meta/' . $id . '.json';
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
    $data = json_decode($raw, true);
    return is_array($data) ? $data : null;
}

function fs_save_meta(string $id, array $data): void
{
    $path = fs_meta_path($id);
    file_put_contents($path, json_encode($data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT), LOCK_EX);
}

function fs_is_expired(array $meta): bool
{
    return $meta['expires_at'] !== null && $meta['expires_at'] < time();
}

function fs_delete_share(string $id): void
{
    $dir = fs_files_dir($id);
    if (is_dir($dir)) {
        foreach (scandir($dir) as $f) {
            if ($f !== '.' && $f !== '..') {
                @unlink($dir . '/' . $f);
            }
        }
        @rmdir($dir);
    }
    @unlink(fs_meta_path($id));
}

/** Достаёт метаданные, попутно удаляя ссылку, если она истекла. */
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

/** Безопасное отображаемое имя: убираем пути и служебные символы. */
function fs_sanitize_display_name(string $name): string
{
    $name = basename(str_replace('\\', '/', $name));
    $name = preg_replace('/[\x00-\x1F\x7F]/', '', $name);
    $name = trim($name);
    return $name === '' ? 'file' : mb_substr($name, 0, 200);
}

function fs_safe_ext(string $name): string
{
    $ext = strtolower(pathinfo($name, PATHINFO_EXTENSION));
    $ext = preg_replace('/[^a-z0-9]/', '', $ext);
    return substr($ext, 0, 10);
}

function fs_detect_mime(string $path): string
{
    $finfo = finfo_open(FILEINFO_MIME_TYPE);
    $mime = $finfo ? finfo_file($finfo, $path) : false;
    if ($finfo) {
        finfo_close($finfo);
    }
    return $mime ?: 'application/octet-stream';
}

/** Категория файла для иконки/превью на странице просмотра. */
function fs_file_category(string $mime, string $ext): string
{
    if ($mime === 'image/svg+xml' || $ext === 'svg') {
        return 'other'; // SVG может содержать скрипты — не встраиваем
    }
    if (str_starts_with($mime, 'image/')) {
        return 'image';
    }
    if (str_starts_with($mime, 'video/')) {
        return 'video';
    }
    if (str_starts_with($mime, 'audio/')) {
        return 'audio';
    }
    if ($mime === 'application/pdf') {
        return 'pdf';
    }
    $textLike = [
        'application/json', 'application/xml', 'application/javascript',
        'application/x-httpd-php', 'application/x-sh',
    ];
    $textExt = ['txt', 'md', 'markdown', 'json', 'xml', 'log', 'csv', 'ini',
        'yml', 'yaml', 'js', 'css', 'html', 'htm', 'php', 'py', 'java', 'c',
        'cpp', 'h', 'sh', 'sql', 'conf', 'env'];
    if (str_starts_with($mime, 'text/') || in_array($mime, $textLike, true) || in_array($ext, $textExt, true)) {
        return 'text';
    }
    return 'other';
}

/** Можно ли отдавать mime как inline (в браузере), а не только на скачивание. */
function fs_is_inlineable_mime(string $mime): bool
{
    if ($mime === 'image/svg+xml' || $mime === 'text/html' || $mime === 'application/xhtml+xml') {
        return false; // потенциально исполняемый контент — только скачивание
    }
    return str_starts_with($mime, 'image/')
        || str_starts_with($mime, 'video/')
        || str_starts_with($mime, 'audio/')
        || $mime === 'application/pdf'
        || str_starts_with($mime, 'text/')
        || in_array($mime, ['application/json', 'application/xml', 'application/javascript'], true);
}

function fs_json_response(array $data, int $code = 200): void
{
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}
