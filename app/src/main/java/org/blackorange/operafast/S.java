package org.blackorange.operafast;

import java.util.HashMap;
import java.util.Map;

/** Tiny translation layer: English literals in code, Ukrainian looked up by exact text. */
final class S {
    private S() {}

    private static final Map<String, String> UK = new HashMap<String, String>();

    private static void p(String en, String uk) {
        UK.put(en, uk);
    }

    static {
        // menu / toggles
        p("Ad blocker", "Блокування реклами");
        p("Night mode", "Нічний режим");
        p("Desktop site", "Версія для ПК");
        p("No images", "Без картинок");
        p("Bookmarks", "Закладки");
        p("Downloads", "Завантаження");
        p("History", "Історія");
        p("Add bookmark", "Додати закладку");
        p("Remove bookmark", "Видалити закладку");
        p("Find in page", "Пошук на сторінці");
        p("Share page", "Поділитись сторінкою");
        p("Clear data", "Очистити дані");
        p("New private tab", "Нова приватна вкладка");
        p("Passwords", "Паролі");
        p("Settings", "Налаштування");
        p("On", "Увімк.");
        p("Off", "Вимк.");
        // switcher
        p("Tabs", "Вкладки");
        p("Private", "Приватні");
        p("No private tabs", "Немає приватних вкладок");
        p("No tabs", "Немає вкладок");
        p("New tab", "Нова вкладка");
        p("Start page", "Стартова сторінка");
        p("Private tab", "Приватна вкладка");
        // bars / dialogs
        p("Search or enter address", "Пошук або адреса");
        p("Turn off", "Вимкнути");
        p("Turn on", "Увімкнути");
        p("Close", "Закрити");
        p("Block on this site", "Блокувати на цьому сайті");
        p("Allow on this site", "Дозволити на цьому сайті");
        p("Blocked on this page: ", "Заблоковано на сторінці: ");
        p("Blocked in total: ", "Заблоковано всього: ");
        p("Rules loaded: ", "Завантажено правил: ");
        p("Bookmark added", "Закладку додано");
        p("Bookmark removed", "Закладку видалено");
        p("Share link", "Поділитись посиланням");
        p("Open in new tab", "Відкрити в новій вкладці");
        p("Copy link", "Копіювати посилання");
        p("Opened in a new tab", "Відкрито в новій вкладці");
        p("Link copied", "Посилання скопійовано");
        p("Clear history, cookies, site data and cache?",
                "Очистити історію, cookie, дані сайтів і кеш?");
        p("Clear", "Очистити");
        p("Cancel", "Скасувати");
        p("Browsing data cleared", "Дані очищено");
        p("This kind of download is not supported yet", "Цей тип завантаження поки не підтримується");
        p("Could not start the download", "Не вдалося почати завантаження");
        p("Storage permission is needed to save files", "Для збереження файлів потрібен доступ до пам'яті");
        p("This page keeps crashing", "Ця сторінка постійно падає");
        p("Downloading ", "Завантаження ");
        p("Downloading… ", "Завантаження… ");
        p("Completed", "Завершено");
        p("Failed", "Помилка");
        p("Paused", "Пауза");
        p("Waiting…", "Очікування…");
        // history / downloads
        p("Clear all browsing history?", "Очистити всю історію?");
        p("No bookmarks yet", "Закладок ще немає");
        p("No history yet", "Історія порожня");
        p("Today", "Сьогодні");
        p("Yesterday", "Вчора");
        p("No downloads yet", "Завантажень ще немає");
        p("No app can open this file", "Немає програми для відкриття файлу");
        p("Cancel this download?", "Скасувати це завантаження?");
        p("Cancel download", "Скасувати завантаження");
        p("Delete", "Видалити");
        p("Keep", "Залишити");
        p("download", "завантаження");
        // passwords
        p("Save password?", "Зберегти пароль?");
        p("Update password?", "Оновити пароль?");
        p("Save", "Зберегти");
        p("Update", "Оновити");
        p("Not now", "Не зараз");
        p("Password saved", "Пароль збережено");
        p("Could not save the password", "Не вдалося зберегти пароль");
        p("No saved passwords", "Збережених паролів немає");
        p("Copy password", "Копіювати пароль");
        p("Password copied", "Пароль скопійовано");
        p("Save passwords", "Зберігати паролі");
        p("Manage passwords", "Керування паролями");
        p("(no username)", "(без імені)");
        // settings
        p("Search", "Пошук");
        p("Search suggestions", "Підказки пошукової системи");
        p("History and bookmarks in suggestions", "Історія й закладки в підказках");
        p("Prefer cache", "Спершу кеш, потім тихе оновлення (швидше відкриття)");
        p("Clear cache", "Очистити кеш");
        p("Cache cleared", "Кеш очищено");
        p("Saved login", "Збережений вхід");
        p("Fill", "Заповнити");
        p("Confirm it's you", "Підтвердіть, що це ви");
        p("Connection is not private", "З'єднання не захищене");
        p("The site's security certificate cannot be trusted.", "Сертифікату безпеки сайту не можна довіряти.");
        p("Go back", "Назад");
        p("Proceed anyway", "Все одно продовжити");
        p("camera and microphone", "камеру та мікрофон");
        p("camera", "камеру");
        p("microphone", "мікрофон");
        p("Allow this site to use the", "Дозволити сайту використовувати");
        p("Allow this site to use your location?", "Дозволити сайту доступ до вашого місцезнаходження?");
        p("Allow", "Дозволити");
        p("Deny", "Заборонити");
        p("Ask for screen lock", "Запитувати блокування екрана (паролі, приватні вкладки)");
        p("HTTPS-only mode", "Режим «Тільки HTTPS»");
        p("Reset site permissions", "Скинути дозволи сайтів");
        p("Site permissions reset", "Дозволи сайтів скинуто");
        p("Red", "Червоний");
        p("Blue", "Синій");
        p("Green", "Зелений");
        p("Purple", "Фіолетовий");
        p("Orange", "Помаранчевий");
        p("Pink", "Рожевий");
        p("Accent color", "Колір акценту");
        p("Theme", "Тема");
        p("Download", "Завантажити");
        p("Choose folder", "Обрати папку");
        p("Use Downloads", "Стандартна (Download)");
        p("No file manager found", "Файловий менеджер не знайдено");
        p("Download folder changed", "Папку завантажень змінено");
        p("Could not use this folder", "Цю папку не вдалося використати");
        p("Library", "Бібліотека");
        p("More", "Інше");
        p("tap to retry", "торкніться, щоб повторити");
        p("QR code", "QR-код");
        p("The link is too long for a QR code", "Посилання завелике для QR-коду");
        p("Download folder", "Папка завантажень");
        p("Dark", "Темна");
        p("Light", "Світла");
        p("System default", "Як у системі");
        p("Hide bars while scrolling", "Ховати панелі під час прокручування");
        p("Show page title in address bar", "Показувати назву сторінки в адресному рядку");
        p("Update ad block list", "Оновити список блокування реклами");
        p("Ad block list updated", "Список оновлено");
        p("Could not update the list", "Не вдалося оновити список");
        p("rules", "правил");
        p("Set as default browser", "Зробити браузером за замовчуванням");
        p("Start page wallpaper", "Фон стартової сторінки");
        p("Choose picture", "Вибрати зображення");
        p("Remove", "Прибрати");
        p("Tab closed", "Вкладку закрито");
        p("Undo", "Скасувати");
        p("Recently closed", "Нещодавно закриті");
        p("Nothing closed yet", "Нічого не закрито");
        p("Reader view", "Режим читання");
        p("Reader view isn't available for this page", "Режим читання недоступний для цієї сторінки");
        p("Translate page", "Перекласти сторінку");
        p("Add to home screen", "Додати на головний екран");
        p("Text size", "Розмір тексту");
        p("Shortcut requested", "Ярлик запитано");
        p("Browsing", "Перегляд");
        p("Language", "Мова");
        p("Automatic", "Автоматично");
        p("Search engine", "Пошукова система");
        p("Start page", "Стартова сторінка");
        p("Speed dial", "Швидкий доступ");
        p("Custom address", "Власна адреса");
        p("Address", "Адреса");
        p("Name", "Назва");
        p("OK", "Гаразд");
        p("Reset speed dial", "Скинути швидкий доступ");
        p("Speed dial reset", "Швидкий доступ скинуто");
        p("General", "Загальне");
        p("Privacy", "Конфіденційність");
        p("Home page", "Головна сторінка");
        p("Add site", "Додати сайт");
        p("Edit", "Змінити");
        p("Private tabs leave no history, no saved logins and no session. Cookies of sites visited are removed when the last private tab closes.",
                "Приватні вкладки не залишають історії, паролів і сесії. Cookie відвіданих сайтів видаляються, коли закривається остання приватна вкладка.");
        p("Private tabs don't keep history or saved passwords. Cookies of the sites you visit are deleted when you close the last private tab.",
                "Приватні вкладки не зберігають історію й паролі. Cookie відвіданих сайтів видаляються після закриття останньої приватної вкладки.");
        p("Page screenshots are hidden while a private tab is open.",
                "Поки відкрита приватна вкладка, знімки екрана вимкнено.");
    }

    static String t(String en) {
        if (en == null || !Settings.uk()) return en;
        String u = UK.get(en);
        return u != null ? u : en;
    }

    /** "3 tabs" / "3 вкладки". */
    static String tabs(int n) {
        if (!Settings.uk()) return n == 1 ? "1 tab" : n + " tabs";
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return n + " вкладка";
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return n + " вкладки";
        return n + " вкладок";
    }
}
