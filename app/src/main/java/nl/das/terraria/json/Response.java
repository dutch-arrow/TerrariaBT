package nl.das.terraria.json;

import com.google.gson.JsonObject;

import java.util.UUID;

public class Response {
    private UUID msgId;
    private String command;
    private JsonObject response;

    public Response() { }

    public Response(UUID msgId, String cmd) {
        this.msgId = msgId;
        this.command = cmd;
    }

    public UUID getMsgId () {
        return this.msgId;
    }
    public void setMsgId (UUID msgId) {
        this.msgId = msgId;
    }
    public String getCommand () {
        return this.command;
    }
    public void setCommand (String command) {
        this.command = command;
    }
    public JsonObject getResponse () {
        return this.response;
    }
    public void setResponse (JsonObject response) {
        this.response = response;
    }
}
