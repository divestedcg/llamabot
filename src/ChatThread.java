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

import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ChatThread {

    private boolean allowedToTalk = true;
    private boolean timeout = true;
    private Room handlingRoom = null;
    private Chat handlingChat = null;
    private Process llamaProcess = null;
    private Thread llamaHandler = null;
    private Scanner llamaIn = null;
    private PrintWriter llamaOutPrinter = null;
    private String name = "";
    public boolean oneOne = false;
    private long lastMessageReceived = System.currentTimeMillis();
    private boolean stopped = true;

    public ChatThread(Chat chat) {
        handlingChat = chat;
        name = chat.getJid().getBareJid().toString();
        oneOne = true;
        System.out.println("[DEBUG CREATED NEW THREAD @ " + name + "]");
        startLlama();
    }

    public ChatThread(Room room) {
        handlingRoom = room;
        name = room.getRoomJid().toString();
        System.out.println("[DEBUG CREATED NEW THREAD @ " + name + "]");
        startLlama();
    }

    private void sendMessage(String message) throws JaxmppException {
        message = message.trim().replaceAll("\\p{Cntrl}", " --- ");
        if(StandardCharsets.UTF_8.newEncoder().canEncode(message)) {
            if (handlingChat != null) {
                Main.bot.getModule(MessageModule.class).sendMessage(handlingChat, message);
                System.out.println("[DEBUG SEND TO USER @ " + name + "] " /*+ message*/);
            }
            if (handlingRoom != null) {
                Main.bot.getModule(MucModule.class).getRoom(handlingRoom.getRoomJid()).sendMessage(message);
                System.out.println("[DEBUG SEND TO ROOM @ " + name + "] " /*+ message*/);
            }
        } else {
            System.out.println("[DEBUG INVALID MESSAGE @ " + name + "] " /*+ message*/);
        }
    }

    public void handleMessage(Message message) {
        try {
            lastMessageReceived = System.currentTimeMillis();
            if (message != null && message.getBody() != null) {
                String messageTxt = message.getBody().trim().replaceAll("\\p{Cntrl}", " ").trim();
                System.out.println("[DEBUG RECV @ " + name + "] " /*+ message*/);

                if ((messageTxt.startsWith(Main.joiningNickname) || oneOne) && checkLine(messageTxt)) {
                    System.out.println("[DEBUG PROCESSING @ " + name + "]");
                    if (handleBotAction(messageTxt) && allowedToTalk) {
                        if (!oneOne) {
                            messageTxt = messageTxt.substring(Main.joiningNickname.length() + 1);
                        }
                        if (llamaProcess == null || llamaHandler == null || stopped) {
                            startLlama();
                        }
                        if (llamaOutPrinter != null) {
                            System.out.println("[DEBUG SEND TO BOT @ " + name + "]");
                            llamaOutPrinter.println(messageTxt);
                            llamaOutPrinter.flush();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startLlama() {
        try {
            stopped = false;
            System.out.println("[DEBUG STARTING LLAMA @ " + name + "]");
            if (oneOne) {
                sendMessage("Starting, please wait a moment...");
            }
            llamaProcess = Runtime.getRuntime().exec("/usr/bin/bash examples/chat-13B.sh");
            llamaIn = new Scanner(new InputStreamReader(llamaProcess.getInputStream()));
            llamaOutPrinter = new PrintWriter(new OutputStreamWriter(llamaProcess.getOutputStream()));
            llamaHandler = new Thread(() -> {
                boolean llamaStarted = false;
                while (!stopped) {
                    try {
                        while (llamaIn.hasNextLine()) {
                            String line = llamaIn.nextLine();

                            if (llamaStarted) {
                                line = line.trim().replaceAll("User:Bob:", "").replaceAll("Bob:", "").replaceAll("User:", "").trim();
                                if (line.length() > 3) {
                                    sendMessage(line);
                                }
                            }

                            if (line.startsWith("Bob: Blue")) {
                                llamaStarted = true;
                                System.out.println("[DEBUG STARTED SUCCESSFULLY @ " + name + "]");
                            }
                        }
                        Thread.sleep(250);
                    } catch (Exception e) {
                        e.printStackTrace();
                        stopLlama();
                    }
                }
            });
            llamaHandler.start();

            new Thread(() -> {
                while (!stopped) {
                    if ((System.currentTimeMillis() - lastMessageReceived) >= (1000 * 60 * 10) && timeout) {
                        stopLlama();
                    }
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println("Stopped thread for " + name + " due to timeout");
            }).start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean handleBotAction(String message) {
        try {
            if ((message.equalsIgnoreCase(Main.joiningNickname + " stop") || message.equalsIgnoreCase("stop") && oneOne) && allowedToTalk) {
                allowedToTalk = false;
                sendMessage("Disabled responses");
                return false;
            } else if ((message.equalsIgnoreCase(Main.joiningNickname + " start") || message.equalsIgnoreCase("start") && oneOne) && !allowedToTalk) {
                allowedToTalk = true;
                sendMessage("Enabled responses");
                return false;
            } else if ((message.equalsIgnoreCase(Main.joiningNickname + " status") || message.equalsIgnoreCase("status") && oneOne)) {
                sendMessage("Still here...");
                return false;
            } else if ((message.equalsIgnoreCase(Main.joiningNickname + " restart") || message.equalsIgnoreCase("restart") && oneOne)) {
                restartLlama();
                sendMessage("Restarting llama.cpp");
                return false;
            } else if ((message.equalsIgnoreCase(Main.joiningNickname + " halt") || message.equalsIgnoreCase("halt") && oneOne)) {
                stopLlama();
                sendMessage("Goodbye!");
                return false;
            } else if ((message.equalsIgnoreCase(Main.joiningNickname + " source") || message.equalsIgnoreCase("source") && oneOne)) {
                sendMessage("llama.cpp Source Code (MIT): https://github.com/ggerganov/llama.cpp");
                sendMessage("Llamabot Source Code (AGPL-3.0): https://github.com/divestedcg/llamabot");
                return false;
            } else if ((message.equalsIgnoreCase(Main.joiningNickname + " timeout") || message.equalsIgnoreCase("timeout") && oneOne)) {
                timeout = !timeout;
                sendMessage("Process timeout is now " + timeout);
                return false;
            }  else if ((message.equalsIgnoreCase(Main.joiningNickname + " help") || message.equalsIgnoreCase("help") && oneOne)) {
                sendMessage("The following commands are supported, they must have the name before without any comma or colon in between.");
                sendMessage("stop: disallow talking");
                sendMessage("start: allow talking");
                sendMessage("status: simple ping");
                sendMessage("restart: restart the llama.cpp process");
                sendMessage("halt: terminate the llama.cpp process");
                sendMessage("timeout: toggle the 10 minute process timeout");
                sendMessage("source: link the source code");
                return false;
            }
        } catch (JaxmppException e) {
            e.printStackTrace();
        }
        return true;
    }

    public boolean checkLine(String line) {
        return !line.startsWith("> ") && !line.startsWith("https://") && !line.startsWith("http://") && StandardCharsets.UTF_8.newEncoder().canEncode(line);
    }

    public void stopLlama() {
        stopped = true;
        try {
            llamaOutPrinter.close();
            llamaOutPrinter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            llamaIn.close();
            llamaIn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            llamaHandler.interrupt();
            llamaHandler.interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            llamaHandler.stop();
            llamaHandler.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            llamaProcess.destroyForcibly();
            llamaProcess.destroyForcibly();
        } catch (Exception e) {
            e.printStackTrace();
        }
        llamaHandler = null;
        llamaProcess = null;
        llamaOutPrinter = null;
        llamaIn = null;
    }

    public void restartLlama() {
        stopLlama();
        startLlama();
    }

}
