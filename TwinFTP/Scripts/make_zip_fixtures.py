#!/usr/bin/env python3
"""Генерирует тестовые ZIP-архивы для TwinCoreTests (запускать при изменении фикстур)."""
import io, os, struct, subprocess, sys, tempfile, zipfile

out = sys.argv[1] if len(sys.argv) > 1 else "Fixtures"
os.makedirs(out, exist_ok=True)

# 1. Обычный архив с кириллицей и вложенными папками (python: UTF-8 флаг)
with zipfile.ZipFile(os.path.join(out, "site.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("сайт/", "")
    z.writestr("сайт/index.php", "<?php echo 'Привет'; ?>\n")
    z.writestr("сайт/css/style.css", "body { color: #333; }\n" * 50)
    z.writestr("сайт/пустой.txt", "")
    z.writestr("readme.txt", "TwinFTP")

# 2. ZipCrypto через Info-ZIP
with tempfile.TemporaryDirectory() as d:
    p = os.path.join(d, "secret.txt")
    open(p, "w").write("секретные данные\n" * 100)
    target = os.path.abspath(os.path.join(out, "zipcrypto.zip"))
    if os.path.exists(target): os.remove(target)
    subprocess.check_call(["zip", "-q", "-j", "-P", "пароль", target, p])

# 3. AES-256 (pyzipper)
try:
    import pyzipper
    with pyzipper.AESZipFile(os.path.join(out, "aes.zip"), "w", compression=pyzipper.ZIP_DEFLATED,
                             encryption=pyzipper.WZ_AES) as z:
        z.setpassword("пароль".encode("utf-8"))
        z.writestr("aes.txt", "AES содержимое\n" * 100)
except ImportError:
    print("pyzipper не установлен — aes.zip не создан")

# 4. Имя в CP866 без флага UTF-8
name = "Отчёт.txt"
placeholder = "X" * len(name.encode("cp866"))
buf = io.BytesIO()
with zipfile.ZipFile(buf, "w", zipfile.ZIP_STORED) as z:
    z.writestr(placeholder, "cp866")
data = buf.getvalue().replace(placeholder.encode(), name.encode("cp866"))
open(os.path.join(out, "cp866.zip"), "wb").write(data)

# 5. Zip Slip и символьная ссылка
with zipfile.ZipFile(os.path.join(out, "evil.zip"), "w") as z:
    z.writestr("../evil.txt", "evil")
    z.writestr("/abs.txt", "abs")
    z.writestr("ok/../../evil2.txt", "evil2")
    z.writestr("good/fine.txt", "fine")
    info = zipfile.ZipInfo("good/link")
    info.create_system = 3
    info.external_attr = (0o120777 << 16)
    z.writestr(info, "/etc/passwd")

# 6. Заниженный размер в заголовке (распакованных данных больше, чем заявлено)
buf = io.BytesIO()
with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("bomb.bin", b"\0" * 1_000_000)
raw = bytearray(buf.getvalue())
cd = raw.rfind(b"PK\x01\x02")
struct.pack_into("<I", raw, cd + 24, 1000)  # uncompressed size в центральном каталоге
open(os.path.join(out, "liar.zip"), "wb").write(bytes(raw))
print("ok")
