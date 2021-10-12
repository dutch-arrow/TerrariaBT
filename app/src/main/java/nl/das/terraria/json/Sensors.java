package nl.das.terraria.json;

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Sensors {

    @SerializedName("clock")
    @Expose
    private String clock;
    @SerializedName("sensors")
    @Expose
    private List<Sensor> sensors = null;

    public String getClock() {
        return clock;
    }

    public void setClock(String clock) {
        this.clock = clock;
    }

    public List<Sensor> getSensors() {
        return sensors;
    }

    public void setSensors(List<Sensor> sensors) {
        this.sensors = sensors;
    }

}