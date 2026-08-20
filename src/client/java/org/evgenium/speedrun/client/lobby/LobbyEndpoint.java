package org.evgenium.speedrun.client.lobby;

public record LobbyEndpoint(String host, int port) {
    public static LobbyEndpoint parse(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Введите IP:порт");
        }

        String host;
        String portText;
        if (value.startsWith("[")) {
            int closing = value.indexOf(']');
            if (closing < 0 || closing + 2 > value.length() || value.charAt(closing + 1) != ':') {
                throw new IllegalArgumentException("IPv6: [адрес]:порт");
            }
            host = value.substring(1, closing);
            portText = value.substring(closing + 2);
        } else {
            int colon = value.lastIndexOf(':');
            if (colon <= 0 || colon == value.length() - 1) {
                throw new IllegalArgumentException("Формат: IP:порт");
            }
            host = value.substring(0, colon).trim();
            portText = value.substring(colon + 1).trim();
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Порт должен быть числом");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Допустимый порт: 1–65535");
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("Не указан адрес хозяина");
        }
        return new LobbyEndpoint(host, port);
    }
}
