package com.example.asus.bluetoothchatde;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothAdapter.STATE_CONNECTING;

public class BluetoothChatService {
    private static final String TAG = "BluetoothChatService";
    private static final boolean D= true;

    private static final String NAME_SECURE = "BluetoothChatSecure";
    private static final String NAME_INSECURE = "BluetoothChatInsecure";

    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int nState;

    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN =1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED =3;

    public BluetoothChatService(Context context,Handler handler){
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        nState = STATE_NONE;
        mHandler = handler;
    }

    private synchronized void setState(int state){
        if(D) Log.d(TAG,"setState()"+nState+" -> "+state);
        nState=state;
        mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state,-1).sendToTarget();
    }

    public synchronized int getState(){
        return nState;
    }

    public synchronized void start(){
        if(D) Log.d(TAG,"start");

        if (mConnectThread != null) {mConnectThread.cancel();mConnectThread=null;}

        if (mConnectedThread != null) {mConnectedThread.cancel();mConnectedThread=null;}
        setState(STATE_LISTEN);

        if (mSecureAcceptThread==null){
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }

        if (mInsecureAcceptThread==null){
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }
    }

    public synchronized void connect(BluetoothDevice device, boolean secure){
        if(D) Log.d(TAG,"connect to: "+device);

        if(nState==STATE_CONNECTING){
            if (mConnectThread!=null){mConnectThread.cancel();mConnectThread=null;}
        }
        if (mConnectedThread !=null){mConnectedThread.cancel();mConnectedThread=null;}

        mConnectThread = new ConnectThread(device,secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device, final String socketType){
        if(D) Log.d(TAG,"connected, Socket Type: "+socketType);

        if (mConnectThread!=null){mConnectThread.cancel();mConnectThread=null;}

        if (mConnectedThread !=null){mConnectedThread.cancel();mConnectedThread=null;}

        if(mSecureAcceptThread!=null){
            mSecureAcceptThread.cancel();
            mSecureAcceptThread=null;
        }
        if (mInsecureAcceptThread!=null){
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread=null;
        }

        mConnectedThread = new ConnectedThread(socket,socketType);
        mConnectThread.start();

        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, device.getName());
        bundle.putString(MainActivity.DEVICE_ADRESS, device.getAddress());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(STATE_CONNECTED);
    }

    public synchronized void stop(){
        if(D) Log.d(TAG,"stop");
        if(mConnectThread!=null){
            mConnectThread.cancel();
            mConnectThread=null;
        }
        if(mConnectedThread!=null){
            mConnectedThread.cancel();
            mConnectedThread=null;
        }
        if(mSecureAcceptThread!=null){
            mSecureAcceptThread.cancel();
            mSecureAcceptThread=null;
        }
        if(mInsecureAcceptThread!=null){
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread=null;
        }
        setState(STATE_NONE);
    }

    public void write(byte[] out){
        ConnectedThread r;
        synchronized (this){
            if(nState!=STATE_CONNECTED) return;
            r=mConnectedThread;
        }
        r.write(out);
    }

    public void connectionFailed() {
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        BluetoothChatService.this.start();
    }

    public void connectionLost(){
        Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST,"Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        BluetoothChatService.this.start();
    }

    private class AcceptThread extends Thread{
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE,MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type :" + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if(D)Log.i(TAG, "SocketType" + mSocketType+
                    "BEGIN mAcceptThread"+this);
            setName("AcceptThread" + mSocketType);
            BluetoothSocket socket = null;
            while (nState != STATE_CONNECTED){
            try {
               socket =mmServerSocket.accept();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType +
                            " accept() failed,", e);
                break;
                }
            }
            if(socket!=null){
                synchronized (BluetoothChatService.this){
                    switch (nState){
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            connected(socket,socket.getRemoteDevice(),mSocketType);
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            try {
                                socket.close();
                            }catch (IOException e){
                                Log.e(TAG,"Could not close unwanted socket",e);
                            }
                            break;

                    }
                }
            }
            if(D) Log.i(TAG,"END mAcceptThread, socket Type: "+mSocketType);
        }


        public void cancel(){
            if(D) Log.d(TAG,"Socket Type" +mSocketType+"cancel"+this);
            try{
                mmServerSocket.close();
            }catch (IOException e){
                Log.e(TAG,"Socket Type"+mSocketType+"close() of server failed"+e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                } else {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type :" + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType" + mSocketType);
            setName("ConnecThread" + mSocketType);
            mAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close()" + mSocketType +
                            " socket during connection failure,", e2);
                }
                connectionFailed();
                return;
            }

            synchronized (BluetoothChatService.this)

            {
                mConnectThread = null;
            }

            connected(mmSocket, mmDevice, mSocketType);
        }

    public void cancel(){
        try{
            mmSocket.close();
        }catch (IOException e){
            Log.e(TAG,"close() of connect"+mSocketType+"socket failed"+e);
        }
        }
    }

    private class ConnectedThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG,"create ConnectedThread: "+socketType);
            mmSocket=socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn=socket.getInputStream();
                tmpOut=socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp socket not created",e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;
            while(true){
            try {
                bytes = mmInStream.read(buffer);
                mHandler.obtainMessage(MainActivity.MESSAGE_READ, bytes,-1,buffer).sendToTarget();
            } catch (IOException e) {

                    Log.e(TAG, "disconnected" ,e);
                    connectionLost();
                    BluetoothChatService.this.start();
                    break;}
            }
        }
        public void write(byte[] buffer){
            try{
                mmOutStream.write(buffer);
                mHandler.obtainMessage(MainActivity.MESSAGE_WRITE,-1,-1,buffer).sendToTarget();
            }catch (IOException e){
                Log.e(TAG,"Exception during write",e);
            }
        }
        public void cancel(){
            try{
                mmSocket.close();
            }catch (IOException e){
                Log.e(TAG,"close() of connect socket failed"+e);
            }
        }
    }
}

