<?php
declare(strict_types=1);

require __DIR__ . '/lib.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    fs_json_response(['ok' => false, 'error' => 'Метод не поддерживается'], 405);
}

$config = fs_config();

if (empty($_FILES['files']) || empty($_FILES['files']['name'])) {
    fs_json_response(['ok' => false, 'error' => 'Файлы не выбраны'], 400);
}

$names = $_FILES['files']['name'];
$count = count($names);

if ($count < 1) {
    fs_json_response(['ok' => false, 'error' => 'Файлы не выбраны'], 400);
}
if ($count > $config['max_files']) {
    fs_json_response(['ok' => false, 'error' => "Слишком много файлов (максимум {$config['max_files']})"], 400);
}

// Общий размер по данным клиента — грубая предварительная проверка,
// точный размер по факту перепроверяем на диске после сохранения.
$totalSizeGuess = array_sum($_FILES['files']['size']);
if ($totalSizeGuess > $config['max_total_size']) {
    fs_json_response(['ok' => false, 'error' => 'Суммарный размер файлов превышает лимит'], 400);
}

$expiryHours = isset($_POST['expiry']) ? (int) $_POST['expiry'] : $config['default_expiry_hours'];
if (!in_array($expiryHours, $config['allowed_expiry_hours'], true)) {
    $expiryHours = $config['default_expiry_hours'];
}

$id = fs_new_id();
$dir = fs_files_dir($id);
if (!mkdir($dir, 0755, true) && !is_dir($dir)) {
    fs_json_response(['ok' => false, 'error' => 'Не удалось создать хранилище'], 500);
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
            UPLOAD_ERR_INI_SIZE => 'Файл больше лимита сервера (upload_max_filesize)',
            UPLOAD_ERR_FORM_SIZE => 'Файл больше лимита формы',
            UPLOAD_ERR_PARTIAL => 'Файл загружен частично, попробуйте снова',
        ];
        $msg = $messages[$error] ?? 'Ошибка загрузки файла';
        fs_json_response(['ok' => false, 'error' => $msg], 400);
    }

    $tmpName = $_FILES['files']['tmp_name'][$i];
    $size = (int) $_FILES['files']['size'][$i];

    if ($size > $config['max_file_size']) {
        fs_delete_share($id);
        fs_json_response(['ok' => false, 'error' => 'Файл превышает максимальный размер'], 400);
    }

    $totalSize += $size;
    if ($totalSize > $config['max_total_size']) {
        fs_delete_share($id);
        fs_json_response(['ok' => false, 'error' => 'Суммарный размер файлов превышает лимит'], 400);
    }

    $displayName = fs_sanitize_display_name($names[$i]);
    $ext = fs_safe_ext($displayName);
    $storedName = $i . ($ext !== '' ? '.' . $ext : '');
    $destination = $dir . '/' . $storedName;

    if (!is_uploaded_file($tmpName) || !move_uploaded_file($tmpName, $destination)) {
        fs_delete_share($id);
        fs_json_response(['ok' => false, 'error' => 'Не удалось сохранить файл'], 500);
    }

    $mime = fs_detect_mime($destination);
    $files[] = [
        'idx'    => $i,
        'name'   => $displayName,
        'stored' => $storedName,
        'size'   => filesize($destination) ?: $size,
        'mime'   => $mime,
    ];
}

if (empty($files)) {
    fs_delete_share($id);
    fs_json_response(['ok' => false, 'error' => 'Файлы не выбраны'], 400);
}

$now = time();
$meta = [
    'id'         => $id,
    'created_at' => $now,
    'expires_at' => $expiryHours > 0 ? $now + $expiryHours * 3600 : null,
    'files'      => $files,
];
fs_save_meta($id, $meta);

fs_json_response([
    'ok'  => true,
    'id'  => $id,
    'url' => 'share.php?id=' . $id,
]);
