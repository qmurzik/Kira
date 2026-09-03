<?php
declare(strict_types=1);

/**
 * Удаляет истёкшие ссылки. Рассчитан на запуск по cron:
 *   0 * * * * php /path/to/file-sharing/cleanup.php
 * По HTTP не работает намеренно — только из командной строки.
 */

if (PHP_SAPI !== 'cli') {
    http_response_code(403);
    exit('Доступно только из командной строки');
}

require __DIR__ . '/lib.php';

$metaDir = fs_config()['storage_dir'] . '/meta';
$removed = 0;

foreach (glob($metaDir . '/*.json') ?: [] as $path) {
    $id = pathinfo($path, PATHINFO_FILENAME);
    $meta = fs_load_meta($id);
    if ($meta !== null && fs_is_expired($meta)) {
        fs_delete_share($id);
        $removed++;
    }
}

echo "Удалено истёкших ссылок: {$removed}\n";
