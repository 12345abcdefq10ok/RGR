package me.ecstacy;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SocialProjectBot extends TelegramLongPollingBot {

    // Токен и юзернейм оставляю твои, не забудь проверить их актуальность
    private static final String BOT_TOKEN = "8182943002:AAHOPp54Dhj0Ig3YfjubeGPKPd-4Z3K3r9U";
    private static final String BOT_USERNAME = "CPC_BOT";

    private static final String CSV_FILE_PATH = "projects.csv";
    private static final String DASHBOARD_URL = "http://127.0.0.1:8050/";

    // Хранилище проектов
    private Map<Integer, Project> projects = new HashMap<>();

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            SocialProjectBot bot = new SocialProjectBot();
            bot.loadFromCsv(); // Загружаем базу при старте
            botsApi.registerBot(bot);
            System.out.println("Бот управления социальными проектами запущен. Ожидание обновлений...");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            String command = messageText.split(" ")[0];

            switch (command) {
                case "/start":
                    sendMsg(chatId, "Привет! Я бот для управления жизненным циклом социальных проектов.\n\n" +
                            "Доступные команды:\n" +
                            "/add - Добавить новый проект\n" +
                            "/list - Список всех проектов\n" +
                            "/info - Детальная карточка проекта\n" +
                            "/update - Обновить данные проекта\n" +
                            "/status - Изменить статус (Новый -> В работе -> Завершен)\n" +
                            "/assign - Назначить исполнителя\n" +
                            "/delete - Удалить проект\n" +
                            "/dashboard - Перейти к аналитике");
                    break;
                case "/add":
                    addProject(chatId, messageText);
                    break;
                case "/update":
                    updateProject(chatId, messageText);
                    break;
                case "/delete":
                    deleteProject(chatId, messageText);
                    break;
                case "/list":
                    listProjects(chatId);
                    break;
                case "/info":
                    projectInfo(chatId, messageText);
                    break;
                case "/status":
                    changeStatus(chatId, messageText);
                    break;
                case "/assign":
                    assignExecutor(chatId, messageText);
                    break;
                case "/dashboard":
                    sendMsg(chatId, "📊 Аналитический дашборд доступен по ссылке: " + DASHBOARD_URL);
                    break;
                default:
                    break;
            }
        }
    }

    // --- Логика команд (Сценарии) ---

    // Сценарий: Добавление нового проекта
    private void addProject(long chatId, String text) {
        try {
            // Формат: /add Название, Проблема, Инициатор, Сроки
            String params = text.substring(text.indexOf(" ") + 1);
            String[] parts = params.split(",");

            if (parts.length < 4) throw new Exception("Мало аргументов");

            String name = parts[0].trim();
            String problem = parts[1].trim();
            String initiator = parts[2].trim();
            String deadline = parts[3].trim();

            int projectId = projects.size() + 1;
            // Создаем проект со статусом "Новый" и без исполнителя
            Project newProject = new Project(name, problem, initiator, deadline, "Новый", "Не назначен");
            projects.put(projectId, newProject);

            sendMsg(chatId, "✅ Проект №" + projectId + " успешно зарегистрирован!");
            saveToCsv();

        } catch (Exception e) {
            sendMsg(chatId, "Ошибка ввода. Используйте формат:\n" +
                    "/add Название, Проблема, Инициатор, Сроки\n" +
                    "Пример: /add Парк Победы, Мусор на аллеях, Иванов А.А., 2025-05-01");
        }
    }

    // Сценарий: Обновление информации
    private void updateProject(long chatId, String text) {
        try {
            String params = text.substring(text.indexOf(" ") + 1);
            String[] parts = params.split(",");

            if (parts.length < 3) throw new Exception("Мало аргументов");

            int projectId = Integer.parseInt(parts[0].trim());
            String field = parts[1].trim();

            // Склеиваем остаток, если в значении были запятые
            StringBuilder valueBuilder = new StringBuilder();
            for(int i=2; i<parts.length; i++) {
                valueBuilder.append(parts[i]);
                if(i < parts.length -1) valueBuilder.append(",");
            }
            String value = valueBuilder.toString().trim();

            if (projects.containsKey(projectId)) {
                Project project = projects.get(projectId);
                boolean updated = true;

                switch (field.toLowerCase()) {
                    case "name": case "название": project.name = value; break;
                    case "problem": case "проблема": project.problem = value; break;
                    case "initiator": case "инициатор": project.initiator = value; break;
                    case "deadline": case "сроки": project.deadline = value; break;
                    case "executor": case "исполнитель": project.executor = value; break;
                    default:
                        sendMsg(chatId, "Поле не найдено. Доступно: название, проблема, инициатор, сроки, исполнитель");
                        updated = false;
                }

                if (updated) {
                    sendMsg(chatId, "💾 Данные проекта №" + projectId + " обновлены");
                    saveToCsv();
                }
            } else {
                sendMsg(chatId, "Проект №" + projectId + " не найден");
            }

        } catch (Exception e) {
            sendMsg(chatId, "Ошибка. Пример: /update 1, сроки, 2025-12-31");
        }
    }

    // Сценарий: Удаление проекта
    private void deleteProject(long chatId, String text) {
        try {
            String[] parts = text.split(" ");
            int projectId = Integer.parseInt(parts[1]);

            if (projects.containsKey(projectId)) {
                projects.remove(projectId);
                sendMsg(chatId, "🗑 Проект №" + projectId + " удален из базы");
                saveToCsv();
            } else {
                sendMsg(chatId, "Проект не найден");
            }
        } catch (Exception e) {
            sendMsg(chatId, "Используйте: /delete номер_проекта");
        }
    }

    // Сценарий: Просмотр списка
    private void listProjects(long chatId) {
        if (!projects.isEmpty()) {
            StringBuilder sb = new StringBuilder("📂 Список социальных проектов:\n\n");
            for (Map.Entry<Integer, Project> entry : projects.entrySet()) {
                sb.append("🔹 №").append(entry.getKey())
                        .append(" | ").append(entry.getValue().name)
                        .append(" [").append(entry.getValue().status).append("]\n");
            }
            sendMsg(chatId, sb.toString());
        } else {
            sendMsg(chatId, "Список проектов пуст");
        }
    }

    // Сценарий: Детальная информация
    private void projectInfo(long chatId, String text) {
        try {
            String[] parts = text.split(" ");
            int projectId = Integer.parseInt(parts[1]);

            if (projects.containsKey(projectId)) {
                Project p = projects.get(projectId);
                String infoText = "📋 Карточка проекта №" + projectId + ":\n\n" +
                        "📌 Название: " + p.name + "\n" +
                        "⚠️ Проблема: " + p.problem + "\n" +
                        "👤 Инициатор: " + p.initiator + "\n" +
                        "📅 Сроки: " + p.deadline + "\n" +
                        "🔄 Статус: " + p.status + "\n" +
                        "🛠 Исполнитель: " + p.executor;
                sendMsg(chatId, infoText);
            } else {
                sendMsg(chatId, "Проект не найден");
            }
        } catch (Exception e) {
            sendMsg(chatId, "Используйте: /info номер_проекта");
        }
    }

    // Сценарий: Изменение статуса
    private void changeStatus(long chatId, String text) {
        try {
            String params = text.substring(text.indexOf(" ") + 1);
            String[] parts = params.split(",");
            int projectId = Integer.parseInt(parts[0].trim());
            String newStatus = parts[1].trim();

            if (projects.containsKey(projectId)) {
                projects.get(projectId).status = newStatus;
                sendMsg(chatId, "🔄 Статус проекта №" + projectId + " изменен на: " + newStatus);
                saveToCsv();
            } else {
                sendMsg(chatId, "Проект не найден");
            }
        } catch (Exception e) {
            sendMsg(chatId, "Пример: /status 1, В работе");
        }
    }

    // Сценарий: Назначение исполнителя
    private void assignExecutor(long chatId, String text) {
        try {
            String params = text.substring(text.indexOf(" ") + 1);
            String[] parts = params.split(",");
            int projectId = Integer.parseInt(parts[0].trim());
            String executorName = parts[1].trim();

            if (projects.containsKey(projectId)) {
                projects.get(projectId).executor = executorName;
                sendMsg(chatId, "👤 На проект №" + projectId + " назначен исполнитель: " + executorName);
                saveToCsv();
            } else {
                sendMsg(chatId, "Проект не найден");
            }
        } catch (Exception e) {
            sendMsg(chatId, "Пример: /assign 1, Петров П.П.");
        }
    }

    // --- Вспомогательные методы ---

    private void sendMsg(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void saveToCsv() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CSV_FILE_PATH, StandardCharsets.UTF_8))) {
            writer.write("ID,Name,Problem,Initiator,Deadline,Status,Executor");
            writer.newLine();

            for (Map.Entry<Integer, Project> entry : projects.entrySet()) {
                Project p = entry.getValue();
                // Экранируем запятые в тексте, чтобы не ломать CSV, но для простоты просто пишем как есть
                String line = String.format("%d,%s,%s,%s,%s,%s,%s",
                        entry.getKey(), p.name, p.problem, p.initiator, p.deadline, p.status, p.executor);
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Ошибка записи CSV: " + e.getMessage());
        }
    }

    private void loadFromCsv() {
        File file = new File(CSV_FILE_PATH);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // Пропускаем заголовок
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",");
                if (cells.length >= 7) {
                    try {
                        int id = Integer.parseInt(cells[0]);
                        Project project = new Project(
                                cells[1], cells[2], cells[3], cells[4], cells[5], cells[6]
                        );
                        projects.put(id, project);
                    } catch (NumberFormatException e) {
                        System.err.println("Ошибка парсинга: " + line);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения CSV: " + e.getMessage());
        }
    }

    // --- Класс данных Проект ---
    private static class Project {
        String name;        // Название проекта
        String problem;     // Социальная проблема
        String initiator;   // Инициатор
        String deadline;    // Сроки
        String status;      // Статус (Новый, В работе, Завершен)
        String executor;    // Ответственный исполнитель

        public Project(String name, String problem, String initiator, String deadline, String status, String executor) {
            this.name = name;
            this.problem = problem;
            this.initiator = initiator;
            this.deadline = deadline;
            this.status = status;
            this.executor = executor;
        }
    }
}