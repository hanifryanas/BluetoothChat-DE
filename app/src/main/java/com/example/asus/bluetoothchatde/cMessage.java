package com.example.asus.bluetoothchatde;

import java.util.Date;

public class cMessage {
    int Me;
    String Msg;
    long Recu;
    String Adresse;
    public cMessage(long recu, String msg, int me, String adresse){
        Recu = recu;
        Msg = msg;
        Me = me;
        Adresse = adresse;
    }

    public cMessage(Date time, String writeMessage, int me, String mConnectedDeviceAddress) {
    }

    public int getMe() {
        return Me;
    }

    public void setMe(int me) {
        Me = me;
    }

    public String getMsg() {
        return Msg;
    }

    public void setMsg(String msg) {
        Msg = msg;
    }

    public long getRecu() {
        return Recu;
    }

    public void setRecu(long recu) {
        Recu = recu;
    }

    public String getAdresse() {
        return Adresse;
    }

    public void setAdresse(String adresse) {
        Adresse = adresse;
    }
}
