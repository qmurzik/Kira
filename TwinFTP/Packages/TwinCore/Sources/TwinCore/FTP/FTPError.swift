import Foundation

/// Ошибки FTP-клиента. Все описания — на русском языке, без раскрытия паролей.
public enum FTPError: Error, Equatable, Sendable {
    case invalidHost
    case dnsFailure(host: String, detail: String)
    case connectionFailed(host: String, port: Int, detail: String)
    case connectionClosed
    case timeout(operation: String)
    case loginFailed(code: Int, message: String)
    case unexpectedReply(command: String, code: Int, message: String)
    case permanentFailure(command: String, code: Int, message: String)
    case transientFailure(command: String, code: Int, message: String)
    case malformedReply(String)
    case passiveModeFailed(String)
    case dataConnectionFailed(String)
    case notConnected
    case cancelled
    case invalidPath(String)
    case notADirectory(String)
    case fileNotFound(String)
    case remoteFileChanged(String)
    case verificationFailed(String)
    case encodingFailed(String)
    case unsupported(String)

    /// Код ответа сервера, если ошибка пришла от FTP-сервера.
    public var replyCode: Int? {
        switch self {
        case .loginFailed(let c, _), .unexpectedReply(_, let c, _),
             .permanentFailure(_, let c, _), .transientFailure(_, let c, _):
            return c
        default:
            return nil
        }
    }

    /// Ошибка связана с потерей соединения и операцию имеет смысл повторить после переподключения.
    public var isConnectionLoss: Bool {
        switch self {
        case .connectionClosed, .timeout, .notConnected, .dataConnectionFailed, .connectionFailed:
            return true
        case .transientFailure(_, let code, _):
            return code == 421 || code == 425 || code == 426
        default:
            return false
        }
    }

    /// 550 / 450 / «нет такого файла».
    public var isNotFound: Bool {
        if case .fileNotFound = self { return true }
        if case .permanentFailure(_, let code, let msg) = self {
            if code == 550 || code == 553 {
                let m = msg.lowercased()
                return m.contains("no such") || m.contains("not found") || m.contains("not exist")
                    || m.contains("doesn't exist") || m.contains("does not exist") || m.contains("нет такого")
                    || m.contains("cannot find") || m.contains("can't find") || m.contains("can't check for file existence")
                    || m.contains("could not get file size")
            }
        }
        return false
    }
}

extension FTPError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case .invalidHost:
            return "Не указан адрес сервера."
        case .dnsFailure(let host, let detail):
            return "Не удалось найти сервер «\(host)». Проверьте адрес и подключение к сети. (\(detail))"
        case .connectionFailed(let host, let port, let detail):
            return "Не удалось подключиться к \(host):\(port). \(detail)"
        case .connectionClosed:
            return "Сервер закрыл соединение."
        case .timeout(let op):
            return "Превышено время ожидания: \(op)."
        case .loginFailed(let code, let message):
            return "Вход не выполнен (\(code)): \(message). Проверьте имя пользователя и пароль."
        case .unexpectedReply(let cmd, let code, let message):
            return "Неожиданный ответ сервера на \(cmd): \(code) \(message)"
        case .permanentFailure(let cmd, let code, let message):
            return "Сервер отклонил \(cmd): \(code) \(message)"
        case .transientFailure(let cmd, let code, let message):
            return "Временная ошибка сервера на \(cmd): \(code) \(message)"
        case .malformedReply(let s):
            return "Некорректный ответ сервера: \(s)"
        case .passiveModeFailed(let s):
            return "Не удалось открыть пассивное соединение: \(s)"
        case .dataConnectionFailed(let s):
            return "Ошибка канала данных: \(s)"
        case .notConnected:
            return "Нет подключения к серверу."
        case .cancelled:
            return "Операция отменена."
        case .invalidPath(let p):
            return "Недопустимый путь: \(p)"
        case .notADirectory(let p):
            return "«\(p)» не является папкой."
        case .fileNotFound(let p):
            return "Файл не найден: \(p)"
        case .remoteFileChanged(let p):
            return "Файл «\(p)» был изменён на сервере после открытия."
        case .verificationFailed(let s):
            return "Проверка после передачи не пройдена: \(s)"
        case .encodingFailed(let s):
            return "Не удалось закодировать имя «\(s)» в кодировке сервера."
        case .unsupported(let s):
            return "Не поддерживается сервером: \(s)"
        }
    }
}
