package nl.das.terraria.json;

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SprayerRule {

    @SerializedName("delay")
    @Expose
    private Integer delay;
    @SerializedName("actions")
    @Expose
    private List<Action> actions = null;

    public Integer getDelay() {
        return delay;
    }

    public void setDelay(Integer delay) {
        this.delay = delay;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

}