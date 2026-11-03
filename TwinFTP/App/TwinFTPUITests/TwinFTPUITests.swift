import XCTest

/// Сквозной UI-тест: реальный FTP-сервер на хост-машине CI (127.0.0.1:2121), снимки экрана ключевых экранов.
final class TwinFTPUITests: XCTestCase {
    var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = true
    }

    private func launch(_ extra: [String] = []) {
        app = XCUIApplication()
        let host = ProcessInfo.processInfo.environment["TWINFTP_UITEST_FTP"] ?? "127.0.0.1:2121:twin:пароль 123"
        app.launchArguments = ["-uitest-ftp", host] + extra
        app.launch()
    }

    private func snapshot(_ name: String) {
        let shot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func element(_ id: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: id).firstMatch
    }

    func testMainFlowWithRealFTP() throws {
        launch(["-uitest-dark"])
        XCTAssertTrue(app.staticTexts["TwinFTP"].waitForExistence(timeout: 15))
        snapshot("01-главный-экран-тёмная")

        // Подключение к серверу из правой панели
        let server = element("server-Тестовый сервер")
        XCTAssertTrue(server.waitForExistence(timeout: 10), "нет сервера в панели")
        server.tap()
        let remoteRow = element("row-remote-readme.txt")
        XCTAssertTrue(remoteRow.waitForExistence(timeout: 20), "листинг сервера не загрузился")
        snapshot("02-подключено-к-ftp")

        // Локальная панель: Сайты → мой-сайт
        let sites = element("row-left-Сайты")
        XCTAssertTrue(sites.waitForExistence(timeout: 10))
        sites.tap()
        let mySite = element("row-left-мой-сайт")
        XCTAssertTrue(mySite.waitForExistence(timeout: 10))
        mySite.tap()
        let index = element("row-left-index.php")
        XCTAssertTrue(index.waitForExistence(timeout: 10))

        // Выбор и загрузка на сервер
        element("select-button").tap()
        index.tap()
        element("row-left-css").tap()
        snapshot("03-выбраны-файлы")
        element("transfer-button").tap()
        let uploaded = element("row-remote-index.php")
        XCTAssertTrue(uploaded.waitForExistence(timeout: 30), "файл не появился на сервере после загрузки")
        XCTAssertTrue(element("row-remote-css").waitForExistence(timeout: 10), "папка не загрузилась рекурсивно")
        snapshot("04-после-загрузки")

        // Открыть удалённый файл в редакторе
        uploaded.tap()
        XCTAssertTrue(app.staticTexts["index.php"].waitForExistence(timeout: 20))
        sleep(2)
        snapshot("05-редактор-php")
        element("editor-close").tap()

        // Очередь
        element("queue-button").tap()
        XCTAssertTrue(app.navigationBars["Передачи"].waitForExistence(timeout: 10))
        app.buttons["История (1)"].firstMatch.tap()
        snapshot("06-очередь-история")
        app.buttons["Закрыть"].firstMatch.tap()

        // Настройки
        element("settings-button").tap()
        XCTAssertTrue(app.navigationBars["Настройки"].waitForExistence(timeout: 10))
        snapshot("07-настройки")
        app.buttons["Готово"].firstMatch.tap()

        // Горизонтальная ориентация
        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(2)
        snapshot("08-альбомная")
        XCUIDevice.shared.orientation = .portrait
    }

    func testLightThemeAndServerEditor() throws {
        launch(["-uitest-light"])
        XCTAssertTrue(app.staticTexts["TwinFTP"].waitForExistence(timeout: 15))
        snapshot("09-главный-экран-светлая")
        element("servers-button").tap()
        XCTAssertTrue(app.navigationBars["Серверы"].waitForExistence(timeout: 10))
        snapshot("10-список-серверов")
        app.buttons["Добавить сервер"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Новый сервер"].waitForExistence(timeout: 10))
        snapshot("11-новый-сервер")
    }
}
