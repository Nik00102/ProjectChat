package project;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        ConsoleHelper.writeMessage("Введите номер порта сервера");
        int port = ConsoleHelper.readInt();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            ConsoleHelper.writeMessage("Сервер запущен");

            while (true) {
                Socket socket = serverSocket.accept();
                new Handler(socket).start();
            }
        } catch (Exception e) {
            ConsoleHelper.writeMessage("Произошла ошибка при запуске либо работе сервера");
        }
    }


    private static class Handler extends Thread {
        private Socket socket;


        public Handler(Socket socket) {
            this.socket = socket;
        }

        //основной метод работы серверного Thread
        @Override
        public void run() {
            ConsoleHelper.writeMessage("Установлено новое соединение с удаленным адресом" + socket.getRemoteSocketAddress());
            String userName = null;

            try (Connection connection = new Connection(socket)) {
                userName = serverHandshake(connection);

                // Сообщаем всем участникам, что присоединился новый участник
                sendBroadcastMessage(new Message(MessageType.USER_ADDED, userName));

                // Сообщаем о новом участнике всех существующих пользователей чата
                notifyUsers(connection, userName);

                // Обрабатываем сообщения пользователей
                serverMainLoop(connection, userName);

            } catch (IOException | ClassNotFoundException e) {
                ConsoleHelper.writeMessage("Ошибка при обмене данными с " + socket.getRemoteSocketAddress());
            }

            if (userName != null) {
                connectionMap.remove(userName);
                sendBroadcastMessage(new Message(MessageType.USER_REMOVED, userName));
            }

            ConsoleHelper.writeMessage("Соединение с " + socket.getRemoteSocketAddress() + " закрыто.");
        }

        //запрос имени у нового пользователя
        private String serverHandshake(Connection connection) throws IOException, ClassNotFoundException {
            while (true) {
                connection.send(new Message(MessageType.NAME_REQUEST));
                Message response = connection.receive();

                if (response.getType() != MessageType.USER_NAME) {
                    ConsoleHelper.writeMessage("Получен ответ от хоста" + connection.getRemoteSocketAddress() + ", не соответсвующий типу команды USER_NAME");
                    continue;
                }

                String userName = response.getData();

                if (userName.isEmpty()) {
                    ConsoleHelper.writeMessage("Получен ответ с пустым полем 'Имя_пользователя' от хоста" + connection.getRemoteSocketAddress());
                    continue;
                }

                if (connectionMap.containsKey(userName)) {
                    ConsoleHelper.writeMessage("Данный пользователь уже существует");
                    continue;
                }

                connection.send(new Message(MessageType.NAME_ACCEPTED));
                connectionMap.put(userName, connection);

                return userName;
            }
        }

        //оповещение о новом пользователе всех участников чата
        private void notifyUsers(Connection connection, String userName) throws IOException {
            for (String user : connectionMap.keySet()) {
                if (!connectionMap.containsKey(userName))
                    connection.send(new Message(MessageType.USER_ADDED, user));
            }
        }

        //обработка входящих сообщений сервером и рассылка сообщений
        private void serverMainLoop(Connection connection, String userName) throws IOException, ClassNotFoundException {
            while (true) {
                Message message = connection.receive();

                if (message.getType() == MessageType.TEXT) {
                    String textForSending = userName + ": " +message.getData();
                    sendBroadcastMessage(new Message(MessageType.TEXT, textForSending));
                } else
                    ConsoleHelper.writeMessage("При получении сообщения от хоста" +
                            connection.getRemoteSocketAddress() + " возникла ошибка. " +
                            "Тип сообщения не соответсвует протоколу. Тип команды не TEXT");
            }
        }
    }



    //отправка сообщения всем пользователям
    public static void sendBroadcastMessage(Message message) {
        for (Connection connection : connectionMap.values()) {
            try {
                connection.send(message);
            } catch (IOException e) {
                ConsoleHelper.writeMessage("Не смогли отправить сообщение " + connection.getRemoteSocketAddress());
            }
        }
    }
}
