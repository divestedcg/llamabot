Llamabot
=======

Overview
--------
LLaMA over XMPP

Quick Start
-----------
- Setup llama.cpp
- Apply the included patch file to llama.cpp: `git am 0001-llamabot.patch`
- Run the program once to generate example configs
- Populate Rooms.txt with rooms you want to interact with
- Populate Account.txt with first line JID and second line password for the bot account
- You can also message the bot 1:1, but it has issues with MAM and repeating itself

Known Issues
------------
- Underlying jaxmpp library can sometimes spew the entire message buffer again

Prebuilts
---------
- via CI: https://gitlab.com/divested/llamabot/-/jobs/artifacts/master/browse?job=build

Credits
-------
- Facebook for the model itself
- llama.cpp (MIT), https://github.com/ggerganov/llama.cpp
- Tigase Java XMPP Client Library (AGPL-3.0), https://github.com/tigase/jaxmpp

Donate
-------
- https://divested.dev/donate
