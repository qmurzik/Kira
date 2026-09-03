<?php
declare(strict_types=1);

require __DIR__ . '/lib.php';

$id = (string) ($_GET['id'] ?? '');
$meta = fs_load_meta_fresh($id);
if ($meta === null) {
    http_response_code(410);
    exit('Ссылка не найдена или срок её действия истёк.');
}

if (!class_exists('ZipArchive')) {
    http_response_code(500);
    exit('На сервере не установлено расширение zip');
}

$filesDir = fs_files_dir($id);
$tmpZip = tempnam(sys_get_temp_dir(), 'share_');
if ($tmpZip === false) {
    http_response_code(500);
    exit('Не удалось создать архив');
}

$zip = new ZipArchive();
$zip->open($tmpZip, ZipArchive::OVERWRITE);

$usedNames = [];
foreach ($meta['files'] as $f) {
    $path = $filesDir . '/' . $f['stored'];
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
