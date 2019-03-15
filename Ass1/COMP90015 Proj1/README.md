COMP90015 Distributed System
Project1 Specification

1. Setup servers

Run 'ActivityStreamerServer.jar' with following command to setup root server

java -jar ActivityStreamerServer.jar -lp <local port number> (-lh <local host name>) 

A secret will be established automatically and it could be seen in the console. And other servers will use the same secret.

Run 'ActivityStreamerServer.jar' with following command to setup child server

java -jar ActivityStreamerServer.jar -rp <remote port number> (-rh <remote host name>) -lp <local port number> (-lh <local host name>)

Attributes:

-lp local port number
-lh local host name (optional, default as 'localhost')
-rp remote port number
-rh remote host name (optional, default as 'localhost')

2. The register and login of the Client 

Run 'ActivityStreamerClient.jar' with following command 

java -jar ActivityStreamerClient.jar (-u <username>) -rp <remote port number> (-rh <remote host name>)

After Client and server connected successfully, the activity objects will pop out. The messages should be in the proper format of JSON. For example: {"activity":{"a","b"}}

Attributes:

-u  username (optional, default as 'anonymous')
-rp remote port number
-rh remote host name (optional, default as 'localhost')
