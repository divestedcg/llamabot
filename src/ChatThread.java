import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.j2se.Jaxmpp;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Scanner;

public class ChatThread {

    private boolean allowedToTalk = true;
    private Room handlingRoom = null;
    private Chat handlingChat = null;
    private Process llamaProcess;
    private Thread llamaHandler;
    private static Scanner llamaIn = null;
    private static PrintWriter llamaOutPrinter = null;
    private String name = "";

    public ChatThread(Chat chat) {
        handlingChat = chat;
        name = chat.getJid().toString();
        System.out.println("[DEBUG CREATED NEW THREAD @ " + name + "]");
    }

    public ChatThread(Room room) {
        handlingRoom = room;
        name = room.getRoomJid().toString();
        System.out.println("[DEBUG CREATED NEW THREAD @ " + name + "]");
    }

    private void sendMessage(String message) throws JaxmppException {
        if(handlingChat != null) {
            Main.bot.getModule(MessageModule.class).sendMessage(handlingChat, message);
            System.out.println("[DEBUG SEND TO USER @ " + name + "]" + message);
        }
        if(handlingRoom != null) {
            Main.bot.getModule(MucModule.class).getRoom(handlingRoom.getRoomJid()).sendMessage(message);
            System.out.println("[DEBUG SEND TO ROOM @ " + name + "]" + message);
        }
    }

    public void handleMessage(Message message) {
        try {
            if (message != null && message.getBody() != null) {
                String messageTxt = message.getBody();
                System.out.println("[DEBUG RECV @ " + name + "] " + messageTxt);

                if (messageTxt.startsWith(Main.joiningNickname) && checkLine(messageTxt)) {
                    System.out.println("[DEBUG PROCESSING @ " + name + "]");
                    handleBotAction(messageTxt);

                    if (allowedToTalk) {
                        messageTxt = messageTxt.substring(Main.joiningNickname.length() + 1);
                        if (!llamaProcess.isAlive() || llamaHandler.isAlive()) {
                            startLlama();
                            System.out.println("[DEBUG STARTING LLAMA @ " + name + "]");
                        }
                        if (llamaOutPrinter != null) {
                            System.out.println("[DEBUG SEND TO BOT @ " + name + "]");
                            llamaOutPrinter.println(messageTxt);
                            llamaOutPrinter.flush();
                        }
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void startLlama() {
        try {
            llamaProcess = Runtime.getRuntime().exec("/usr/bin/bash examples/chat-13B.sh");
            llamaIn = new Scanner(new InputStreamReader(llamaProcess.getInputStream()));
            llamaOutPrinter = new PrintWriter(new OutputStreamWriter(llamaProcess.getOutputStream()));
            llamaHandler = new Thread(() -> {
                boolean llamaStarted = false;
                while(true) {
                    try {
                        while(llamaIn.hasNextLine()) {
                            String line = llamaIn.nextLine();

                            if(llamaStarted) {
                                line = line.trim().replaceAll("User:Bob:", "").replaceAll("Bob:", "").replaceAll("User:", "").trim();
                                if(line.length() > 3) {
                                    sendMessage(line);
                                }
                            }

                            if(line.startsWith("Bob: Blue")) {
                                llamaStarted = true;
                            }
                        }
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

    public void handleBotAction(String message) {
        try {
            if (message.equalsIgnoreCase(Main.joiningNickname + " stop") && allowedToTalk) {
                allowedToTalk = false;
                sendMessage("Disabled responses");
            }
            if (message.equalsIgnoreCase(Main.joiningNickname + " start") && !allowedToTalk) {
                allowedToTalk = true;
                sendMessage("Enabled responses");
            }
            if (message.equalsIgnoreCase(Main.joiningNickname + " status")) {
                sendMessage("Still here...");
            }
            if (message.equalsIgnoreCase(Main.joiningNickname + " restart")) {
                restartLlama();
                sendMessage("Restarting llama.cpp");
            }
            if (message.equalsIgnoreCase(Main.joiningNickname + " halt")) {
                sendMessage("Goodbye!");
                stopLlama();
            }
        } catch (JaxmppException e) {
            e.printStackTrace();
        }
    }

    public static boolean checkLine(String line) {
        return !line.startsWith("> ") && !line.startsWith("https://") && !line.startsWith("http://");
    }

    public void stopLlama() {
        try {
            llamaHandler.interrupt();
            llamaHandler.interrupt();
        } catch(Exception e) {
            e.printStackTrace();
        }
        try {
            llamaHandler.stop();
            llamaHandler.stop();
        } catch(Exception e) {
            e.printStackTrace();
        }
        try {
            llamaProcess.destroyForcibly();
            llamaProcess.destroyForcibly();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void restartLlama() {
        stopLlama();
        startLlama();
    }

}
