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
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.Presence;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main {

    private static boolean allowedToTalk = true;
    private static Scanner llamaIn;
    private static PrintWriter llamaOutPrinter;
    private static final Jaxmpp bot = new Jaxmpp();
    private static String joiningNickname = "chariclea" + new Random().nextInt(10000);

    private static String botAccount = "";
    private static String botAccountPassword = "";
    private static File cfgRooms;
    private static Room handlingRoom;

    private static final int ignoreLines = 6;

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
            bot.getProperties().setUserProperty(SessionObject.USER_BARE_JID, BareJID.bareJIDInstance(botAccount));
            bot.getProperties().setUserProperty(SessionObject.PASSWORD, botAccountPassword);

            bot.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, new MucModule.MucMessageReceivedHandler() {
                @Override
                public void onMucMessageReceived(SessionObject sessionObject, Message message, Room room, String nickname, Date timestamp) {
                    try {
                        if (message.getBody() != null) {
                            String messageTxt = message.getBody().toString();
                            //handleBotAction(messageTxt, room);
                            System.out.println("[DEBUG RECV] " + messageTxt);
                            if(allowedToTalk && llamaOutPrinter != null && checkLine(messageTxt) && messageTxt.startsWith(joiningNickname)) {
                                handlingRoom = room;
                                messageTxt = messageTxt.substring(joiningNickname.length() + 1);
                                System.out.println("[DEBUG SEND TO BOT] " + messageTxt);
                                llamaOutPrinter.println(messageTxt);
                                llamaOutPrinter.flush();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            bot.login();
            int tryCounter = 0;
            while (!bot.isConnected() && tryCounter <= 10) {
                Thread.sleep(1000);
                tryCounter++;
            }
            if (bot.isConnected()) {
                System.out.println("[INIT] Connected as " + joiningNickname);
                connectToRooms(cfgRooms);
                startLlama();

                while (true) { //XXX: This shouldn't be necessary, but my connection is killed without it?
                    if(!bot.isConnected()) {
                        joiningNickname = "chariclea" + new Random().nextInt(10000);
                        bot.login();
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

    public static boolean checkLine(String line) {
        return !line.startsWith("> ") && !line.startsWith("https://");
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

    public static void handleBotAction(String message, Room room) {
        try {
            if (message.equalsIgnoreCase(joiningNickname + " stop") && allowedToTalk) {
                allowedToTalk = false;
                bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage("Disabled responses");
            }
            if (message.equalsIgnoreCase(joiningNickname + " start") && !allowedToTalk) {
                allowedToTalk = true;
                bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage("Enabled responses");
            }
            if (message.equalsIgnoreCase(joiningNickname + " status")) {
                bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage("Still here...");
            }
            if (message.equalsIgnoreCase(joiningNickname + " halt")) {
                bot.getModule(MucModule.class).getRoom(room.getRoomJid()).sendMessage("Goodbye!");
                bot.disconnect();
                System.exit(0);
            }
        } catch (JaxmppException e) {
            e.printStackTrace();
        }
    }

    public static void startLlama() {
        try {
            Process llamaProcess = Runtime.getRuntime().exec("/usr/bin/bash examples/chat-13B.sh");
            llamaIn = new Scanner(new InputStreamReader(llamaProcess.getInputStream()));
            llamaOutPrinter = new PrintWriter(new OutputStreamWriter(llamaProcess.getOutputStream()));
            Thread llamaHandler = new Thread(() -> {
                boolean llamaStarted = false;
                boolean multiline = false;
                while(true) {
                    try {
                        while(llamaIn.hasNextLine()) {
                            String line = llamaIn.nextLine();
                            System.out.println("[DEBUG] " + line);

                            if(llamaStarted) {
                                line = line.trim().replaceAll("User:Bob:", "").replaceAll("Bob:", "").trim();
                                bot.getModule(MucModule.class).getRoom(handlingRoom.getRoomJid()).sendMessage(line);
                            }

                            //if(line.startsWith("Bob: Sure. The largest city in Europe is Moscow, the capital of Russia.")) {
                            if(line.startsWith("Bob: Blue")) {
                                llamaStarted = true;
                                System.out.println("[DEBUG] ENABLED RESPONSES");
                            }
                        }
                        System.out.println("[DEBUG] " + "sleeping");
                        Thread.sleep(50);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            llamaHandler.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
