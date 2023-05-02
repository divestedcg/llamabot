/*
 * Copyright (c) 2023 Divested Computing Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.Presence;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main {

    public static final Jaxmpp bot = new Jaxmpp();
    public static String joiningNickname = "llamabot" + new Random().nextInt(10000);

    private static String botAccount = "";
    private static String botAccountPassword = "";
    private static File cfgRooms;
    private static HashMap<String, ChatThread> chatThreads = new HashMap<>();

    public static void main(String[] args) {
        boolean fatal = false;
        try {
            if (args.length == 0) {
                System.out.println("[INIT] Must provide config directory path");
                System.exit(1);
            }
            File configDirectory = new File(args[0]);
            if (!configDirectory.exists() || !configDirectory.isDirectory()) {
                System.out.println("[INIT] Invalid config directory path");
                System.exit(1);
            }
            cfgRooms = new File(configDirectory + "/Rooms.txt");
            if (!cfgRooms.exists()) {
                cfgRooms.createNewFile();
                PrintWriter out = new PrintWriter(cfgRooms, "UTF-8");
                out.println("ontopic@conference.example.org");
                out.println("offtopic@conference.example.org");
                out.close();
                System.out.println("[INIT] Room list doesn't exist, creating, please populate.");
                fatal = true;
            }
            File cfgAccount = new File(configDirectory + "/Account.txt");
            if (cfgAccount.exists()) {
                parseAccountFromFile(cfgAccount);
                if (botAccount.length() == 0 || botAccountPassword.length() == 0) {
                    fatal = true;
                }
            } else {
                cfgAccount.createNewFile();
                PrintWriter out = new PrintWriter(cfgAccount, "UTF-8");
                out.println("llamabot@example.org");
                out.println("password");
                out.close();
                System.out.println("[INIT] Room list doesn't exist, creating, please populate.");
                fatal = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (fatal) {
            System.out.println("[INIT] Input requirements unsatisfied. Exiting!");
            System.exit(1);
        }

        try {
            Presence.initialize(bot);
            bot.getModulesManager().register(new MucModule());
            bot.getModulesManager().register(new MessageModule());
            bot.getProperties().setUserProperty(SessionObject.USER_BARE_JID, BareJID.bareJIDInstance(botAccount));
            bot.getProperties().setUserProperty(SessionObject.PASSWORD, botAccountPassword);
            bot.getEventBus().addHandler(MessageModule.MessageReceivedHandler.MessageReceivedEvent.class, (sessionObject, chat, message) -> {
                try {
                    if (chat != null && chat.getJid() != null && chat.getJid().getBareJid() != null && message != null && message.getBody() != null) {
                        if(chatThreads.containsKey(chat.getJid().getBareJid().toString())) {
                            chatThreads.get(chat.getJid().getBareJid().toString()).handleMessage(message);
                        } else {
                            chatThreads.put(chat.getJid().getBareJid().toString(), new ChatThread(chat));
                            chatThreads.get(chat.getJid().getBareJid().toString()).handleMessage(message);
                        }
                    }
                } catch (XMLException e) {
                    throw new RuntimeException(e);
                }
            });
            bot.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, (sessionObject, message, room, nickname, timestamp) -> {
                try {
                    if(room != null && room.getRoomJid() != null && message != null && message.getBody() != null) {
                        if(chatThreads.containsKey(room.getRoomJid().toString())) {
                            chatThreads.get(room.getRoomJid().toString()).handleMessage(message);
                        } else {
                            chatThreads.put(room.getRoomJid().toString(), new ChatThread(room));
                            chatThreads.get(room.getRoomJid().toString()).handleMessage(message);
                        }
                    }
                } catch (XMLException e) {
                    throw new RuntimeException(e);
                }
            });
            bot.login(true);
            if (bot.isConnected()) {
                System.out.println("[INIT] Connected as " + joiningNickname);
                connectToRooms(cfgRooms);

                while (true) { //XXX: This shouldn't be necessary, but my connection is killed without it?
                    if(!bot.isConnected()) {
                        joiningNickname = "llamabot" + new Random().nextInt(10000);
                        bot.login(true);
                        connectToRooms(cfgRooms);
                        System.out.println("[INIT] Reconnected as " + joiningNickname);
                    }
                    Thread.sleep(1000);
                    bot.keepalive();
                }
            } else {
                System.out.println("[INIT] Unable to connect within 10 seconds.");
                System.exit(1);
            }
        } catch (JaxmppException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<String> readFileToArray(File file) {
        ArrayList<String> contents = new ArrayList<>();
        if (file.exists() && file.canRead()) {
            try {
                Scanner reader = new Scanner(file);
                while (reader.hasNextLine()) {
                    contents.add(reader.nextLine());
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return contents;
    }

    public static void connectToRooms(File cfgRooms) {
        int count = 0;
        ArrayList<String> rooms = readFileToArray(cfgRooms);
        try {
            for (String room : rooms) {
                if (room.contains("@")) {
                    String[] roomSplit = room.split("@");
                    bot.getModule(MucModule.class).join(roomSplit[0], roomSplit[1], joiningNickname);
                    count++;
                }
            }
        } catch (JaxmppException e) {
            e.printStackTrace();
        }
        System.out.println("[BOT] Connecting to " + count + " rooms");
    }

    public static void parseAccountFromFile(File cfgAccount) {
        ArrayList<String> account = readFileToArray(cfgAccount);
        botAccount = account.get(0);
        botAccountPassword = account.get(1);
        System.out.println("[BOT] Operating under " + botAccount);
    }
}
