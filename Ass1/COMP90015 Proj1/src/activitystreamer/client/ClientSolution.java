package activitystreamer.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;

public class ClientSolution extends Thread {
    // Same variables from Control.java and Connect.java
    private static final Logger log = LogManager.getLogger();
    private static ClientSolution clientSolution;
    private TextFrame textFrame = new TextFrame();
    private boolean term;
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader inreader;
    private PrintWriter outwriter;
    private boolean open = false;
    private Socket socket;


    public static ClientSolution getInstance() {
        if (clientSolution == null) {
            clientSolution = new ClientSolution();
        }
        return clientSolution;
    }

    // Same as initiateConnection() in Control.java.
    // Make a connection to another server if remote hostname is supplied.
    public void initiateConnection() {
            try {
                clientConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
            } catch (IOException e) {
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }

    }

    // Same as writeMsg(String msg) in Connect.java.
    // Return true if the message was written, otherwise false.
    public boolean writeMsg(String msg) {
        if (open) {
            outwriter.println(msg);
            outwriter.flush();
            return true;
        }
        return false;
    }

    // Same as closeCon() in Connect.java.
    // Client closes the connection with server.
    // public void closeCon(){
    // 	if(open){
    // 		log.info("closing connection "+Settings.socketAddress(socket));
    // 		try {
    // 			term=true;
    // 			inreader.close();
    // 			out.close();
    // 		} catch (IOException e) {
    // 			// already closed?
    // 			log.error("received exception closing the connection "+Settings.socketAddress(socket)+": "+e);
    // 		}
    // 	}
    // }

    // Client login the server by using settings.
    public void Client_login() {
        //try {
        String loginusername = Settings.getUsername();
        String loginsecret = Settings.getSecret();
        JSONObject loginmsg = new JSONObject();
        loginmsg.put("command","LOGIN");
        loginmsg.put("username", loginusername);
        loginmsg.put("secret", loginsecret);
        writeMsg(loginmsg.toJSONString());
        //}
//		catch (IOException e){
//        	log.error("login failed" + "closed with exception" +Settings.socketAddress(socket)+": "+e);
//        }
    }

    // Client register the server by using settings.
    public void Client_register() {
        // try{
        String registerusername = Settings.getUsername();
        String registersecret = Settings.getSecret();
        JSONObject registermsg = new JSONObject();
        registermsg.put("command", "REGISTER");
        registermsg.put("username", registerusername);
        registermsg.put("secret", registersecret);
        writeMsg(registermsg.toJSONString());
        //}
//        catch (IOException e){
//        	log.error("register failed" + "closed with exception" +Settings.socketAddress(socket)+": "+e);
//        }
    }

    // Initiate the connection and let client login or register
    // if the secret is null and not "anonymous".
    public ClientSolution() {
        log.debug("opening the gui");
        textFrame = new TextFrame();
        term = false;
        initiateConnection();
        // if(Settings.getSecret() != null || Settings.getUsername().equals("anonymous"))
        // 	login();
        // else if (Settings.getSecret() == null){
        // 	register();
        // }
        // if(Settings.getSecret() == null){
        // 	if(!(Settings.getUsername().equals("anonymous"))){
        // 		register();
        // 	}
        // 	else{
        // 		login();
        // 	}
        // }
        if (Settings.getSecret() == null && (Settings.getUsername().equals("anonymous") == false)) {
            Settings.setSecret(Settings.nextSecret());
            Client_register();
        } else {
            Client_login();
        }
        start();
    }

    @SuppressWarnings("unchecked")
    public void sendActivityObject(JSONObject activityObj) {
        JSONObject activitymsg = new JSONObject();
        String activityuser = Settings.getUsername();
        String activitysecret = Settings.getSecret();
        String activity = activityObj.get("activity").toString();
        activitymsg.put("username", activityuser);
        activitymsg.put("command", "ACTIVITY_MESSAGE");
        activitymsg.put("secret", activitysecret);
        activitymsg.put("activity", activity);
        writeMsg(activitymsg.toJSONString());
    }

    // Similar to closeCon() in Connect.java.
    // Client click disconnect to logout and the GUI closes the connection with server.
    public void disconnect() {
        // if(!term) connections.remove(con);
        textFrame.setVisible(false);
        JSONObject logoutmsg = new JSONObject();
        logoutmsg.put("command", "LOGOUT");
        logoutmsg.put("info", "the client has logout");
        writeMsg(logoutmsg.toJSONString());
        if (open) {
            log.info("closing connection " + Settings.socketAddress(socket));
            try {
                term = true;
                inreader.close();
                out.close();
            } catch (IOException e) {
                // already closed?
                log.error("received exception closing the connection " + Settings.socketAddress(socket) + ": " + e);
            }
        }
    }

    public boolean client_process(String clientmsg) {
        log.info("Message Received    :" + clientmsg);
        try{
            String command;
            JSONParser parser = new JSONParser();
            JSONObject messageObject;

            messageObject = (JSONObject) parser.parse(clientmsg);
            command = messageObject.get("command").toString();

            switch (command) {

                case "":
                    JSONObject blank = new JSONObject();
                    blank.put("command", "INVALID_MESSAGE");
                    blank.put("info", "received a bland command.");
                    log.error("received a blank command");
                    return true;

                case "REGISTER_SUCCESS":
                    log.info("Have registered successfully");
                    Client_login();
                    return false;

                case "REGISTER_FAILED":
                    log.error("Have registered failed");
                    return true;

                case "LOGIN_SUCCESS":
                    log.info("Have login successfully");
                    return false;

                case "LOGIN_FAILED":
                    log.error("Login failed with the wrong secret");
                    return true;

                case "INVALID_MESSAGE":
                    log.error("The command is not valid");
                    return true;

                case "AUTHENTICATE_FAIL":
                    log.error("Server authenticated failed");
                    return true;

                case "ACTIVITY_BROADCAST":
                    String activtymsg;
                    activtymsg = messageObject.get("activity").toString();
                    JSONObject activtymsg2JSON;
                    activtymsg2JSON = (JSONObject) parser.parse(activtymsg);
                    textFrame.setOutputText(activtymsg2JSON);
                    return false;

                case "REDIRECT":
                    String hostname = messageObject.get("hostname").toString();
                    int port = Integer.parseInt(messageObject.get("port").toString());
                    Settings.setRemoteHostname(hostname);
                    Settings.setRemotePort(port);
                    clientSolution = new ClientSolution();
                    log.info("The connection redirected to"+hostname+":"+port);
                    return true;

                default:
                    log.error("");
                    JSONObject invalidmsg = new JSONObject();
                    invalidmsg.put("command", "INVALID_MESSAGE");
                    invalidmsg.put("info", "The message did not contain a valid command");
                    writeMsg(invalidmsg.toJSONString());
                    return true;

            }
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return true;
    }

        // Similar to the run function in Connection.java.
        // Receive messages for client.
        @Override
        public void run () {
            try {
                String data;
                while (!term && (data = inreader.readLine()) != null) {
                    //term=Control.getInstance().process(this,data);
                    term = client_process(data);
                }
                log.debug("connection closed to " + Settings.socketAddress(socket));
                // Control.getInstance().connectionClosed(this);
                // in.close();
                disconnect();
            } catch (IOException e) {
                log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);
                disconnect();
            }
            open = false;
        }


        // Similar to the outgoingConnection in Control.java and Connect.java.
        // A new outgoing connection has been established, and a reference is returned to it.



    public synchronized void clientConnection(Socket s) throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        // Connection c = new Connection(s);
        // connections.add(c);
        // return c;
        socket = s;
        in = new DataInputStream(s.getInputStream());
        out = new DataOutputStream(s.getOutputStream());
        inreader = new BufferedReader(new InputStreamReader(in));
        outwriter = new PrintWriter(out, true);
        open = true;
    }
}
