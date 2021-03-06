package org.antennae.server.notifier.ws;

import org.antennae.common.messages.*;
import org.antennae.common.messages.util.JsonUtil;
import org.antennae.server.notifier.rest.RestClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * <code>ClientTextWebSocketHandler</code> handles the incoming web-socket messages.
 *
 * reads the json, identifies the <code>topic</code> and then sends it to the <code>TopicProcessor</code>
 */
public class ClientTextWebSocketHandler extends org.springframework.web.socket.handler.TextWebSocketHandler implements IClientHandler {

    private static Logger logger = LoggerFactory.getLogger(ClientTextWebSocketHandler.class);
    private Map<String,WebSocketSession> clientSessions = new ConcurrentHashMap<String,WebSocketSession>();
    private Map<String, ClientAddress> clientAddresses = new ConcurrentHashMap<String,ClientAddress>();

    @Inject
    ServerTextWebSocketHandler serverHandler;


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("Client connection established : " + session.getId());
        clientSessions.put( session.getId(), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.info("connection error");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("Client connection closed: " + session.getId() + ", status :" + status.toString());
        if( clientSessions.get(session.getId()) != null ){
            clientSessions.remove(session.getId());
        }
        if( clientAddresses.get(session.getId()) != null ){
            clientAddresses.remove(session.getId());
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage incomingMessage) {

        if (incomingMessage == null) {
            return;
        }

        String textmessage = incomingMessage.getPayload();
        logger.info("SessionId: " + session.getId() + ", incomingMessage: " + textmessage );

        String clazzName = JsonUtil.identifyClassType(textmessage);

        try {

            if( clazzName == null ){

                logger.debug("ClassName is unknown. Unable to parse the incoming message.");
                return;

            }else if (clazzName.equals(ServerMessage.class.getName())) {

                ServerMessage serverMessage = ServerMessage.fromJson(textmessage);
                processServerMessage( session, serverMessage);

            } else if (clazzName.equals(ServerRestMessage.class.getName())) {

                ServerRestMessage serverRestMessage = ServerRestMessage.fromJson(textmessage);
                processRestServerMessage(session, serverRestMessage );
            }

        }catch( Throwable throwable ){
            logger.error("Unable to parse the incoming incomingMessage. ignoring...");
            return;
        }
    }

    private void processServerMessage( WebSocketSession session, ServerMessage serverMessage){
        if( serverMessage == null ){
            return;
        }

        // Cache the client address
        if( serverMessage.getFrom() != null ){
            cacheTheClientAddress( session, serverMessage.getFrom());
        }

        // TODO: Store the message in a DB before proecessing

        // check whether the incomingMessage is meant for the server or a user/app
        // if topic is set, then it is meant for server side processing
        // if "ServerAddress" object is set, then it should be sent to the appropriate user/app.

        switch ( serverMessage.getMessageType()){
            case PUB_SUB:
                // the message is sent to the correct processor (consumer) of that message.
                // no response expected
                processPubSub( serverMessage);
                break;
            case REQUEST_RESPONSE:
                // Request-Response is similar to PUB_SUB.
                // but the "consumer" sends the a response, which will be sent back the original "producer"
                processRequestResponse( session.getId(), serverMessage );
                break;
        }
    }

    private void cacheTheClientAddress( WebSocketSession session, ClientAddress clientAddress){
        ClientAddress stored = clientAddresses.get( session.getId());

        if( stored == null ){
            clientAddresses.put(session.getId(), clientAddress);
        }else if( !clientAddress.equals(stored)) {
            // TODO: handle error situations
        }
    }

    private void processRestServerMessage( WebSocketSession session, ServerRestMessage serverRestMessage ){

        // Cache the client address
        if( serverRestMessage.getFrom() != null ){
            cacheTheClientAddress( session, serverRestMessage.getFrom());
        }

        // construct the REST call
        RestClient client = new RestClient( serverRestMessage.getHost());
        String result = client.GET( serverRestMessage.getPath());

        ClientMessage response = new ClientMessage( serverRestMessage.getRequestId());
        response.setTo( serverRestMessage.getFrom());
        response.setPayLoad( result );
        response.setMessageQOS( ClientMessageQOSEnum.DIRECT_CONNECTION_ONLY );

        sendToClient(session, response);
    }

    // This message should be processed by server side
    private void processPubSub(ServerMessage message) {
        // no response expected from client
        serverHandler.processPubSub(message);
    }

    private void processRequestResponse(String sessionId , ServerMessage message){

        // response expected from client
        serverHandler.processRequestResponse( sessionId, message);

    }

    private void processPointToPoint( ServerMessage message ){

    }

    @Override
    public void receiveFromClient(ServerMessage message) {

    }

    /**
     * <code>sendToClient</code> sends the message to clients ( real-world clients ).
     * It first tries to send through an existing session (connection).
     * If connection is not found, then sends the message through GCM/APNS.
     *
     * @param clientMessageWrapper
     */
    @Override
    public void sendToClient(ClientMessageWrapper clientMessageWrapper) {

        if( clientMessageWrapper == null || clientMessageWrapper.getClientMessage() == null ){
            logger.debug("incoming client message is null. returning...");
            return;
        }

        ClientMessage clientMessage = clientMessageWrapper.getClientMessage();

        // find a session
        WebSocketSession session = null;
        String sessionId = clientMessageWrapper.getSessionId();
        if( sessionId != null && clientSessions.get(sessionId) != null ){
            session = clientSessions.get(sessionId);
        }else{
            session = getSesssionForClient(clientMessage.getTo());
        }

        // send the message
        sendToClient( session, clientMessage );
/*        if( session != null && session.isOpen() ){

            TextMessage textMessage = new TextMessage( clientMessage.getPayLoad());
            try {
                session.sendMessage( textMessage );
            } catch (IOException e) {
                e.printStackTrace();
                // TODO: send thru GCM or when the app wakes up
            }

        }else{
            // TODO: send the message using GCM, or when the app wakes up
        }*/
    }

    public void sendToClient( WebSocketSession session, ClientMessage clientMessage ){

        // send the message
        if( session != null && session.isOpen() ){

            TextMessage textMessage = new TextMessage( clientMessage.getPayLoad());
            try {
                session.sendMessage( textMessage );
            } catch (IOException e) {
                e.printStackTrace();
                // TODO: send thru GCM or when the app wakes up
            }

        }else{
            // TODO: send the message using GCM, or when the app wakes up
        }
    }


    private WebSocketSession getSesssionForClient(ClientAddress clientAddress ){

        WebSocketSession result=null;

        Set<String> sessionIds = clientAddresses.keySet();
        String found = null;
        for( String sessionId : sessionIds ){
            ClientAddress address = clientAddresses.get(sessionId);
            if( address.getAppName().equals(clientAddress.getAppName()) &&
                    address.getAppVersion().equals(clientAddress.getAppVersion()) &&
                    address.getDeviceId().equals(clientAddress.getDeviceId()) ){
                    // TODO: make sure to search based on userId
                found = sessionId;
                break;
            }
        }
        if( found != null ) {
            result = clientSessions.get(found);
        }

        return result;
    }


}