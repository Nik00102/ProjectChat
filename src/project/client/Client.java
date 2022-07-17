package project.client;


import project.Connection;
import project.ConsoleHelper;
import project.Message;
import project.MessageType;

import java.io.IOException;
import java.net.Socket;

public class Client {
    protected Connection connection;
    private volatile boolean clientConnected;

    protected String getServerAddress() {
        ConsoleHelper.writeMessage("Введите адрес сервера:");
        return ConsoleHelper.readString();
    }

    protected int getServerPort() {
        ConsoleHelper.writeMessage("Введите порт сервера:");
        return ConsoleHelper.readInt();
    }

    protected String getUserName() {
        ConsoleHelper.writeMessage("Введите ваше имя:");
        return ConsoleHelper.readString();
    }

    public class SocketThread extends Thread {
        protected void processIncomingMessage(String message) throws IOException {
            // Выводим текст сообщения в консоль
            ConsoleHelper.writeMessage(message);
        }

        protected void informAboutAddingNewUser(String userName) {
            // Выводим информацию о добавлении участника
            String message = String.format("Участник с именем %s присоединился к чату.",userName);
            ConsoleHelper.writeMessage(message);
        }

        protected void informAboutDeletingNewUser(String userName) {
            // Выводим информацию о выходе участника
            String message = String.format("Участник с именем %s покинул чат.",userName);
            ConsoleHelper.writeMessage(message);
        }

        //notify главному потоку
        protected void notifyConnectionStatusChanged(boolean clientConnected) {
            Client.this.clientConnected = clientConnected;
            synchronized (Client.this) {
                Client.this.notify();
            }
        }

        //предоставление клиента серверу
        protected void clientHandshake() throws IOException, ClassNotFoundException {
            while (true) {
                //получение сообщения
                Message message = connection.receive();
                //если сообщение - "сервер запросил имя", отправить имя серверу
                if (message.getType() == MessageType.NAME_REQUEST) {
                    String userName = getUserName();
                    connection.send(new Message(MessageType.USER_NAME, userName));
                }
                //если сообщение - "сервер принял имя", сообщить главному потоку и выйти из метода
                else if (message.getType() == MessageType.NAME_ACCEPTED) {
                    notifyConnectionStatusChanged(true);
                    return;
                }
                //если сообщение - другого типа, возникает исключение
                else throw new IOException("Unexpected project.MessageType");
            }
        }

        //цикл обработки сообщений сервера
        protected void clientMainLoop() throws IOException, ClassNotFoundException {
            while (true) {
                //получение сообщения
                Message message = connection.receive();
                //если сообщение - "текст", вывести в консоль
                if (message.getType() == MessageType.TEXT) {
                    processIncomingMessage(message.getData());
                }
                //если сообщение - "добавлен пользователь", сообщить всем
                else if (message.getType() == MessageType.USER_ADDED) {
                    informAboutAddingNewUser(message.getData());
                }
                //если сообщение - "удален пользователь", сообщить всем
                else if (message.getType() == MessageType.USER_REMOVED) {
                    informAboutDeletingNewUser(message.getData());
                }
                //если сообщение - другого типа, возникает исключение
                else throw new IOException("Unexpected project.MessageType");
            }
        }

        //основной метод работы клиентского Thread
        @Override
        public void run() {
            //Запрос адреса и порта сервера
            String serverAddress = getServerAddress();
            int serverPort = getServerPort();
            //создаем соединение, выполняем "рукопожатие" и обрабатываем сообщания сервера
            try {
                connection = new Connection(new Socket(serverAddress,serverPort));
                clientHandshake();
                clientMainLoop();
            } catch (IOException | ClassNotFoundException e) {
                notifyConnectionStatusChanged(false);
            }
        }
    }

    protected SocketThread getSocketThread() {
        return new SocketThread();
    }

    protected void sendTextMessage(String text) {
        try {
            connection.send(new Message(MessageType.TEXT, text));
        } catch (IOException e) {
            ConsoleHelper.writeMessage("Не удалось отправить сообщение");
            clientConnected = false;
        }
    }

    protected boolean shouldSendTextFromConsole() {
        return true;
    }

    //newThread устанавливает соединение с сервером,
    //после этого в цикле считывать сообщения с консоли и отправлять их серверу
    public void run() {
        SocketThread newThread = getSocketThread();
        // Помечаем поток как daemon
        newThread.setDaemon(true);
        newThread.start();

        try {
            synchronized (this) {
                wait();
            }
        } catch (InterruptedException e) {
            ConsoleHelper.writeMessage("Произошла ошибка во время работы клиента.");
            return;
        }

        if (clientConnected)
            ConsoleHelper.writeMessage("Соединение установлено. Для выхода наберите команду 'exit'.");
        else
            ConsoleHelper.writeMessage("Произошла ошибка во время работы клиента.");

        //Пока есть соединение читаем текст с консоли, и если требуется отправляем его.
        //Если будет введена команда 'exit', то выйти из цикла
        while (clientConnected) {
            String text = ConsoleHelper.readString();
            if (text.equals("exit")) {
                return;
            }

            if (shouldSendTextFromConsole()) {
                sendTextMessage(text);
            }
        }
    }



    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }
}
