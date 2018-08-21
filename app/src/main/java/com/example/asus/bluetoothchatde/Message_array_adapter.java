package com.example.asus.bluetoothchatde;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Message_array_adapter extends ArrayAdapter<cMessage>{
    private LayoutInflater layoutInflater;
    private List<cMessage> Messages; Context ctx;
    public Message_array_adapter(Context context, int resource, ArrayList<cMessage> objects) {
        super(context,resource,objects);
        ctx = context;
        layoutInflater = LayoutInflater.from(context);
        this.Messages = objects;
    }

    public View getView(int position,View convertView,ViewGroup parent) {
        cMessage Message = Messages.get(position);
        TextView msg = null,tm;
        if(Message.getMe()==1){
            convertView = layoutInflater.inflate(R.layout.mymessage, null);
        }else {
            convertView = layoutInflater.inflate(R.layout.hismessage, null);
        }
        msg.setText(Message.getMsg());
        tm =(TextView) convertView.findViewById(R.id.ts);
        String formattedDate = new SimpleDateFormat("HH:mm").format(new Date(Message.getRecu()));
        tm.setText(formattedDate);
        return convertView;
    }
}
