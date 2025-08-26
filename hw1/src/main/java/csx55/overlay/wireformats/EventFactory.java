package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class EventFactory {
    
    private static EventFactory instance = null;
    
    private EventFactory() {
    }
    
    public static EventFactory getInstance() {
        if (instance == null) {
            instance = new EventFactory();
        }
        return instance;
    }
    
    public Event createEvent(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        
        baInputStream.close();
        din.close();
        
        switch (messageType) {
            case Protocol.REGISTER_REQUEST:
                return new RegisterRequest(marshalledBytes);
                
            case Protocol.REGISTER_RESPONSE:
                return new RegisterResponse(marshalledBytes);
                
            case Protocol.DEREGISTER_REQUEST:
                return new DeregisterRequest(marshalledBytes);
                
            case Protocol.DEREGISTER_RESPONSE:
                return new DeregisterResponse(marshalledBytes);
                
            case Protocol.MESSAGING_NODES_LIST:
                return new MessagingNodesList(marshalledBytes);
                
            case Protocol.LINK_WEIGHTS:
                return new LinkWeights(marshalledBytes);
                
            case Protocol.TASK_INITIATE:
                return new TaskInitiate(marshalledBytes);
                
            case Protocol.TASK_COMPLETE:
                return new TaskComplete(marshalledBytes);
                
            case Protocol.PULL_TRAFFIC_SUMMARY:
                return new PullTrafficSummary(marshalledBytes);
                
            case Protocol.TRAFFIC_SUMMARY:
                return new TrafficSummary(marshalledBytes);
                
            case Protocol.DATA_MESSAGE:
                return new DataMessage(marshalledBytes);
                
            default:
                throw new IOException("Unknown message type: " + messageType);
        }
    }
}