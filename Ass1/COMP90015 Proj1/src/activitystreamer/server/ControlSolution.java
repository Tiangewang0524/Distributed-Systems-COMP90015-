package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ControlSolution extends Control {

    private static final Logger log = LogManager.getLogger();
    private static String id;
    private static Map<Connection, String> connectID;
    private static int num_client_connected;
    private static Map<String, String> user_info;
    private static ArrayList<ServerAnnounce> servers;
    private static Map<String,Connection> processinguser;
    private static Map<String,ArrayList<Connection>> LockRequest;
    private static Map<String,Connection> request_source;
    // identify: server == true, client == false
    private static Map<Connection,Boolean> identify;


    public static ControlSolution getInstance()
    {
        if(control==null)
        {
            control=new ControlSolution();
        }
        return (ControlSolution) control;
    }

    public ControlSolution()
    {
        super();
        id = Settings.nextSecret();
        connectID = new HashMap<>();
        num_client_connected = 0;
        user_info = new HashMap<>();
        servers = new ArrayList<>();
        processinguser = new HashMap<>();
        request_source = new HashMap<>();
        LockRequest = new HashMap<>();
        identify = new HashMap<>();


        // initiate the connection
        initiateConnection();

        start();
    }

    // a new incoming connection has been established, and a reference is returned to it.
    @Override
    public Connection incomingConnection(Socket s) throws IOException
    {
        Connection con = super.incomingConnection(s);
        identify.put(con,false);
        System.out.println(s);
        connectID.put(con,null);
        return con;
    }

    // a new outgoing connection has been established, and a reference is returned to it.
    @Override
    public Connection outgoingConnection(Socket s) throws IOException
    {
        Connection con = super.outgoingConnection(s);
        JSONObject authen=new JSONObject();
        authen.put("command", "AUTHENTICATE");
        authen.put("secret", Settings.getSecret());
        con.writeMsg(authen.toJSONString());
        identify.put(con, true);
        connectID.put(con,"");

        return con;
    }


    // close the connection.
    @Override
    public void connectionClosed(Connection con)
    {
        super.connectionClosed(con);
        identify.remove(con);
        connectID.remove(con);
        num_client_connected = num_client_connected - 1;
    }


    // the main process function.
    // process the message from server and client.
    @Override
    public synchronized boolean process(Connection con,String msg)
    {
        log.info("Message Received    :"+msg);
        String command;
        JSONParser parser = new JSONParser();
        JSONObject messageObject;
        try {
            messageObject = (JSONObject) parser.parse(msg);
            if (messageObject.get("command") == null){
                command = "";

            }else {
                command = messageObject.get("command").toString();
            }

            switch(command) {
                case "":
                    JSONObject blank = new JSONObject();
                    blank.put("command", "INVALID_MESSAGE");
                    blank.put("info","received a bland command.");
                    log.error("received a blank command");
                    return true;

                case "AUTHENTICATE":
                    return authenticate(con, messageObject);

                case "AUTHENTICATION_FAIL":
                    log.debug("the supplied secret is incorrect");
                    return true;

                case "LOGIN":
                    return login(con, messageObject);

                case "LOGOUT":
                    return true;

                case "INVALID_MESSAGE":
                    return true;

                case "ACTIVITY_MESSAGE":
                    return activityMessage(con, messageObject);

                case "SERVER_ANNOUNCE":
                    return serverAnnounce(con, messageObject);

                case "ACTIVITY_BROADCAST":
                    return activityBroadcast(con, messageObject);

                case "REGISTER":
                    return register(con, messageObject);

                case "LOCK_REQUEST":
                    return lockRequest(con, messageObject);

                case "LOCK_DENIED":
                    return lockDenied(con, messageObject);

                case "LOCK_ALLOWED":
                    return lockAllowed(con, messageObject);

                default:
                    JSONObject invalid = new JSONObject();
                    invalid.put("command", "INVALID_MESSAGE");
                    invalid.put("info","the received message did not contain a valid command");
                    con.writeMsg(invalid.toJSONString());
                    break;
            }
        } catch (org.json.simple.parser.ParseException e)
                {
            e.printStackTrace();
                }
        return true;
    }

    
    @Override
    public boolean doActivity()
    {
        announced_server();
        return false;
    }

    // store servers which has announced.
    private void announced_server()
    {
        JSONObject Broadcast = new JSONObject();
        Broadcast.put("command", "SERVER_ANNOUNCE");
        Broadcast.put("id", id);
        Broadcast.put("load", num_client_connected);
        Broadcast.put("hostname", Settings.getLocalHostname());
        Broadcast.put("port", Settings.getLocalPort());

        for (Connection con : getConnections()) {
            if(identify.get(con) == true)
            {
                con.writeMsg(Broadcast.toJSONString());
            }
        }
    }

    // Broadcast from every server to every other server (between servers only) on a regular basis (once every 5 seconds by default).
    private boolean serverAnnounce(Connection con, JSONObject msg)
    {
        JSONObject invalida = new JSONObject();
        invalida.put("command", "INVALID_MESSAGE");

        if(msg.get("port") == null | msg.get("hostname") == null | msg.get("load") == null | msg.get("id") == null)
        {
            invalida.put("info","invalid Announce, message is incorrect");
            con.writeMsg(invalida.toJSONString());
            return true;
        }
        String hostname = msg.get("hostname").toString();
        int port = Integer.parseInt(msg.get("port").toString());
        String ID = msg.get("id").toString();
        int load = Integer.parseInt(msg.get("load").toString());

        if(identify.get(con) == true && connectID.get(con).equals(""))
        {
            boolean isExist = false;

            for(ServerAnnounce server:servers)
            {
                if(server.getID().equals(ID))
                {
                    server.updateLoad(load);
                    isExist = true;
                    break;
                }
            }
            if(isExist == false)
            {
                servers.add(new ServerAnnounce(ID, load, hostname, port));
            }
            for(Connection connection: getConnections())
            {
                if(identify.get(connection) == true
                        && !connection.equals(con)
                        )
                {
                    connection.writeMsg(msg.toJSONString());
                }
            }
            return false;
        }
        else
        {
            invalida.put("info","server is not yet authenticated");
            con.writeMsg(invalida.toJSONString());
            log.error("Server ID:" + ID);
            return true;
        }
    }

    // Message broadcast between servers, and from each server to its clients, that contains a processed activity object.
    private boolean activityBroadcast(Connection con, JSONObject activitymsg)
    {
        JSONObject invalidactivity = new JSONObject();
        invalidactivity.put("command", "INVALID_MESSAGE");
        if(identify.get(con) == true && connectID.get(con).equals(""))
        {
            for(Connection connection: getConnections())
            {
                if(!connection.equals(con))
                {
                    connection.writeMsg(activitymsg.toJSONString());
                }
            }
            return false;
        }
        else
        {
            invalidactivity.put("info","server is not yet authenticated");
            con.writeMsg(invalidactivity.toJSONString());
            return true;
        }
    }

    // Sent from one server to another always and only as the rst message when connecting.
    private boolean authenticate(Connection con, JSONObject authenmsg)
    {
        String authensecret = authenmsg.get("secret").toString();
        if(authenmsg.get("secret") == null && Settings.getSecret() == null)
        {
            identify.put(con,true);
            connectID.put(con,"");
            return false;
        }else if(authenmsg.get("secret").equals(Settings.getSecret()))
        {
            identify.put(con,true);
            connectID.put(con,"");
            return false;
        }
        else
        {
            JSONObject fail = new JSONObject();
            fail.put("command", "AUTHENTICATION_FAIL");
            fail.put("info","the supplied secret is incorrect: "+authensecret);
            log.info("the secret is incorrect: " + authensecret);
            con.writeMsg(fail.toJSONString());
            return true;
        }
    }

    // Sent from a client to a server for clients login.
    private boolean login(Connection con, JSONObject loginmsg)
    {
        String loginusername = loginmsg.get("username").toString();
        for(ServerAnnounce server: servers)
        {
            if(num_client_connected  >= server.getLoad() + 2)
            {
                JSONObject redirect = new JSONObject();
                redirect.put("command", "REDIRECT");
                redirect.put("hostname", server.getHostname());
                redirect.put("port", server.getPort());
                con.writeMsg(redirect.toJSONString());
                log.info("Redirecting to host " + server.getHostname()
                        + "Port: " + server.getPort());
                return true;
            }
        }
        if (loginmsg.get("username") == null)
        {
            JSONObject failu = new JSONObject();
            failu.put("command", "INVALID_MESSAGE");
            failu.put("info","the username is null");
            con.writeMsg(failu.toJSONString());
            return true;
        }
        if (loginmsg.get("username").equals("anonymous"))
        {
            if (loginmsg.get("secret") == null)
            {
                num_client_connected = num_client_connected + 1;
                connectID.put(con,loginusername);
                identify.put(con, false);
                JSONObject success = new JSONObject();
                success.put("command", "LOGIN_SUCCESS");
                success.put("info","logged in as user "+loginusername);
                con.writeMsg(success.toJSONString());
                log.info("Login Success. User: " + loginusername);
                return false;
            }
        }
        if (!loginmsg.get("username").equals("anonymous"))
        {
            if (loginmsg.get("secret") == null)
            {
                JSONObject fails = new JSONObject();
                fails.put("command", "INVALID_MESSAGE");
                fails.put("info","the secret is null");
                con.writeMsg(fails.toJSONString());
                return true;
            }
        }

        String loginsecret = loginmsg.get("secret").toString();

        if(user_info.containsKey(loginusername)&&user_info.get(loginusername).equals(loginsecret))
        {
            num_client_connected = num_client_connected + 1;
            connectID.put(con,loginusername);
            identify.put(con, false);
            JSONObject success = new JSONObject();
            success.put("command", "LOGIN_SUCCESS");
            success.put("info","logged in as user "+loginusername);
            con.writeMsg(success.toJSONString());
            log.info("Login Success. User: " + loginusername);
            return false;
        }
        else
        {
            JSONObject failed = new JSONObject();
            failed.put("command", "LOGIN_FAILED");
            failed.put("info","attempt to login with wrong secret");
            con.writeMsg(failed.toJSONString());
            log.info("Login Failed");
            return true;
        }
    }

    // Sent from client to server when publishing an activity object.
    private boolean activityMessage(Connection con, JSONObject actmsg)
    {
        String actuser = actmsg.get("username").toString();
        String actsecret;

        String actactivity = actmsg.get("activity").toString();


        if(connectID.get(con) == null)
        {
            JSONObject fail1 = new JSONObject();
            fail1.put("command", "AUTHENTICATION_FAIL");
            fail1.put("info","the user has not logged in yet.");
            con.writeMsg(fail1.toJSONString());
            return true;
        }
        if (actmsg.get("username") == null)
        {
            JSONObject failu = new JSONObject();
            failu.put("command", "INVALID_MESSAGE");
            failu.put("info","the username is null");
            con.writeMsg(failu.toJSONString());
            return true;
        }
        if (!actmsg.get("username").equals("anonymous"))
        {
            if (actmsg.get("secret") == null)
            {
                JSONObject fails = new JSONObject();
                fails.put("command", "INVALID_MESSAGE");
                fails.put("info","the secret is null");
                con.writeMsg(fails.toJSONString());
                return true;
            }
        }
        if (actmsg.get("activity") == null)
        {
            JSONObject faila = new JSONObject();
            faila.put("command", "INVALID_MESSAGE");
            faila.put("info","the activity is null");
            con.writeMsg(faila.toJSONString());
            return true;
        }

        if(!actmsg.get("username").equals("anonymous"))
        {
            actsecret = actmsg.get("secret").toString();
            if(actmsg.get("username").equals(connectID.get(con))                    )
            {
                JSONObject activityObject = new JSONObject();
                JSONParser par= new JSONParser();
                try {
                    activityObject = (JSONObject) par.parse(actactivity);
                    activityObject.put("authenticated_user", actuser);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                JSONObject actbroadcast = new JSONObject();
                actbroadcast.put("command","ACTIVITY_BROADCAST");
                actbroadcast.put("activity",activityObject.toJSONString());

                for(Connection connection:getConnections())
                {
                    connection.writeMsg(actbroadcast.toJSONString());
                    log.info("Activity Broadcasting--"+actbroadcast.toString());
                }
                return false;
            }
            else
            {
                JSONObject fail2 = new JSONObject();
                fail2.put("command", "AUTHENTICATION_FAIL");
                fail2.put("info","the username and secret do not match the logged in the user");
            }
        }
        else
        {
            JSONObject activityObject = new JSONObject();
            JSONParser par= new JSONParser();
            try {
                activityObject = (JSONObject) par.parse(actactivity);
                activityObject.put("authenticated_user", actuser);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            JSONObject actbroadcast = new JSONObject();
            actbroadcast.put("command","ACTIVITY_BROADCAST");
            actbroadcast.put("activity",activityObject.toString());

            for(Connection connection:getConnections())
            {
                connection.writeMsg(actbroadcast.toJSONString());
                log.info("Activity Broadcasting--"+actbroadcast.toString());
            }
            return false;
        }
        return false;
    }

    // Sent from client to server when the client wishes to register a new username.
    private boolean register(Connection con, JSONObject regmsg)
    {
        {
            String reguser = regmsg.get("username").toString();
            String regsecret = regmsg.get("secret").toString();
            ArrayList<Connection> sentLockRequests;
            sentLockRequests = new ArrayList<>();

            // Send invalid messages
            if(connectID.get(con) != null)
            {
                JSONObject fail_register1 = new JSONObject();
                fail_register1.put("command", "INVALID_MESSAGE");
                fail_register1.put("info","Already logged in the system, cannot register.");
                con.writeMsg(fail_register1.toJSONString());
                return true;
            }
            // Register failed
            if(user_info.containsKey(regmsg.get("username")))
            {
                JSONObject fail_register2 = new JSONObject();
                fail_register2.put("command", "REGISTER_FAILED");
                fail_register2.put("info","The username is already registered with the system.");
                con.writeMsg(fail_register2.toJSONString());
                return true;
            }
            else
            {
                //Send lock request
                user_info.put(reguser,regsecret);

                JSONObject lock_request = new JSONObject();
                lock_request.put("command", "LOCK_REQUEST");
                lock_request.put("username", reguser);
                lock_request.put("secret",regsecret);

                for(Connection connection: getConnections())
                {
                    if(identify.get(connection) == true)
                    {
                        connection.writeMsg(lock_request.toJSONString());
                        sentLockRequests.add(connection);
                        log.info("sent lock request: " + reguser);
                    }
                }

                // Register successfully
                if(sentLockRequests.isEmpty())
                {
                    JSONObject register_success = new JSONObject();
                    register_success.put("command", "REGISTER_SUCCESS");
                    register_success.put("info","register success for: "+reguser);
                    con.writeMsg(register_success.toJSONString());
                    log.info("register success for: " + reguser);
                }
                else
                {
                    processinguser.put(reguser,con);
                    LockRequest.put(reguser,sentLockRequests);
                }

                return false;
            }
        }

    }

    // Broadcast from a server to all other servers (between servers only) to indicate that
    // a client is trying to register a username with a given sercret.
    private boolean lockRequest(Connection con, JSONObject lockRequestMsg)
    {
        String username = lockRequestMsg.get("username").toString();
        String regsecret = lockRequestMsg.get("secret").toString();
        ArrayList<Connection> sentLockRequests;
        sentLockRequests = new ArrayList<>();

        if(identify.get(con) == true && connectID.get(con).equals(""))
        {
            if(user_info.containsKey(username))
            {
                JSONObject lock_denied = new JSONObject();
                lock_denied.put("command", "LOCK_DENIED");
                lock_denied.put("info","register success for: "+username);
                con.writeMsg(lock_denied.toJSONString());
                log.info("denying lock for: " + username + " " + regsecret);
                return false;
            }
            else
            {
                user_info.put(username,regsecret);
                request_source.put(username,con);
                for(Connection connection: getConnections())
                {
                    if(identify.get(connection) == true && !connection.equals(con))
                    {
                        connection.writeMsg(lockRequestMsg.toJSONString());
                        sentLockRequests.add(connection);
                    }
                }

                if(sentLockRequests.isEmpty())
                {
                    JSONObject register_success = new JSONObject();
                    register_success.put("command", "REGISTER_SUCCESS");
                    register_success.put("info","register success for: "+username);
                    con.writeMsg(register_success.toJSONString());
                    log.info("register success for: " + username);
                }
                else
                {
                    LockRequest.put(username,sentLockRequests);
                }

                return false;
            }
        }
        else
        {
            JSONObject lockinvalid = new JSONObject();
            lockinvalid.put("command", "INVALID_MESSAGE");
            lockinvalid.put("info","from an unauthenticated server");
            con.writeMsg(lockinvalid.toJSONString());
            return true;
        }
    }


    // Broadcast from a server to all other servers (between servers only), if the server received a
    // LOCK_REQUEST and to indicate that a username is already known,
    // and the username and secret pair should not be registered.
    private boolean lockDenied(Connection con, JSONObject deniedmsg)
    {
        String deniedusername = deniedmsg.get("username").toString();
        String deniedsecret = deniedmsg.get("secret").toString();
        log.info("got lock denied from: " + con);
        if(identify.get(con) == true && connectID.get(con).equals(""))
        {

            if(user_info.containsKey(deniedusername) && user_info.get(deniedusername).equals(deniedsecret))
            {
                user_info.remove(deniedusername);
            }

            for(Connection connection: getConnections())
            {
                if(identify.get(connection) == true
                        && !connection.equals(con)
                        )
                {
                    connection.writeMsg(deniedmsg.toJSONString());
                }
            }

            if(LockRequest.containsKey(deniedusername))
            {
                LockRequest.remove(deniedusername);
            }

            if(request_source.containsKey(deniedusername))
            {
                request_source.remove(deniedusername);
            }

            if(processinguser.containsKey(deniedusername))
            {
                JSONObject deniedfail = new JSONObject();
                deniedfail.put("command", "REGISTER_FAILED");
                deniedfail.put("info","The username is already registered with the system.");
                con.writeMsg(deniedfail.toJSONString());
                processinguser.get(deniedusername).writeMsg(deniedfail.toJSONString());
                processinguser.get(deniedusername).closeCon();
                processinguser.remove(deniedusername);
            }

            return false;
        }
        else
        {
            JSONObject lockinvalid = new JSONObject();
            lockinvalid.put("command", "INVALID_MESSAGE");
            lockinvalid.put("info","from an unauthenticated server");
            con.writeMsg(lockinvalid.toJSONString());
            return true;
        }
    }

    // Broadcast from a server to all other servers if the server received a LOCK_REQUEST
    // and the username was not already known to the server in its local storage.
    private boolean lockAllowed(Connection con, JSONObject allowkmsg)
    {
        String allowedusername = allowkmsg.get("username").toString();
        String allowedsecret = allowkmsg.get("secret").toString();
        ArrayList<Connection> sentLockRequests;

        if(identify.get(con) == true && connectID.get(con).equals(""))
        {
            sentLockRequests = LockRequest.get(allowedusername);
            sentLockRequests.remove(con);

            if(sentLockRequests.isEmpty())
            {
                LockRequest.remove(allowedusername);
                if(processinguser.containsKey(allowedusername))
                {
                    JSONObject register_success = new JSONObject();
                    register_success.put("command", "REGISTER_SUCCESS");
                    register_success.put("info","register success for: "+allowedusername);
                    log.info("register success for: " + allowedusername);
                    processinguser.get(allowedusername).writeMsg(register_success.toJSONString());
                    processinguser.remove(allowedusername);
                }
                else
                {
                    request_source.get(allowedusername).writeMsg(allowkmsg.toJSONString());
                }
                request_source.remove(allowedusername);
            }

            LockRequest.put(allowedusername, sentLockRequests);
            return false;
        }
        else
        {
            JSONObject lockinvalid = new JSONObject();
            lockinvalid.put("command", "INVALID_MESSAGE");
            lockinvalid.put("info","from an unauthenticated server");
            con.writeMsg(lockinvalid.toJSONString());
            return true;
        }
    }
    class ServerAnnounce
    {
        private String id;
        private int load;
        private String hostname;
        private int port;

        ServerAnnounce(String id, int load, String hostname, int port)
        {
            this.id = id;
            this.load = load;
            this.hostname = hostname;
            this.port = port;
        }

        public String getID()
        {
            return id;
        }

        public int getLoad()
        {
            return load;
        }

        public void updateLoad(int load)
        {
            this.load = load;
        }

        public String getHostname()
        {
            return hostname;
        }

        public int getPort()
        {
            return port;
        }
    }
}
