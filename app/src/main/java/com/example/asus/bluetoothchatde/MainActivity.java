package com.example.asus.bluetoothchatde;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String TAG ="MainActivity";
    private static final boolean D=true;

    private ArrayList<cMessage> Messages;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    private ArrayAdapter<String> mNewDevicesArrayAdapter;

    public static final int MESSAGE_STATE_CHANGE=1;
    public static final int MESSAGE_READ=2;
    public static final int MESSAGE_WRITE=3;
    public static final int MESSAGE_DEVICE_NAME=4;
    public static final int MESSAGE_TOAST=5;

    public static final String DEVICE_NAME = "device name";
    public static final String DEVICE_ADRESS="DEVICE_ADRESS";
    public static final String TOAST="toast";

    private static final int REQUEST_ENABLE_BT=3;

    private ListView mConversationView;
    private EditText mOutEditText;
    private ImageButton mSendButton;
    private String mConnectedDeviceName=null;
    private String mConnectedDeviceAddress=null;
    private Message_array_adapter mConversationArrayAdapter;
    private StringBuffer mOutStringBuffer;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothChatService mChatService =null;
    private LinearLayout chat,dev;
    private MessageSqlite sql;
    private boolean secure;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG,"+++ ON CREATE +++");
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);
        if(mBluetoothAdapter==null){
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        chat=(LinearLayout)findViewById(R.id.chat);
        dev=(LinearLayout)findViewById(R.id.devices);
        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v){
                doDiscovery();
            }
        });
        mPairedDevicesArrayAdapter=new ArrayAdapter<String>(this, R.layout.device_name);
        mNewDevicesArrayAdapter=new ArrayAdapter<String>(this,R.layout.device_name);

        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener((AdapterView.OnItemClickListener) mDeviceClickListener);

        ListView newDeviceListView = (ListView) findViewById(R.id.new_devices);
        newDeviceListView.setAdapter(mNewDevicesArrayAdapter);
        newDeviceListView.setOnItemClickListener((AdapterView.OnItemClickListener) mDeviceClickListener);

        IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver,filter);

        filter=new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver,filter);
        sql=new MessageSqlite(getApplicationContext());
    }
    void paired(){
        Set<BluetoothDevice> pairedDevices=mBluetoothAdapter.getBondedDevices();
        mPairedDevicesArrayAdapter.clear();

        if(pairedDevices.size()>0){
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for(BluetoothDevice device:pairedDevices){
                mPairedDevicesArrayAdapter.add(device.getName()+"\n"+device.getAddress());
            }
        }else {
            String noDevices = getResources().getText(R.string.none_paired).toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }
        doDiscovery();
    }
    private void doDiscovery(){
        if(D) Log.d(TAG,"doDiscovery()");

        setProgressBarIndeterminateVisibility(true);
        setStatus(R.string.scanning);

        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);
        findViewById(R.id.button_scan).setVisibility(View.GONE);

        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mNewDevicesArrayAdapter.clear();
        mBluetoothAdapter.startDiscovery();
        }

        @Override
    public void onStart(){
        super.onStart();
        if(D) Log.e(TAG,"++ ON START ++");

        if(!mBluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if(mChatService==null) setupChat();
        }
        }

        private AdapterView.OnItemClickListener mDeviceClickListener =
                new AdapterView.OnItemClickListener() {

                    public void  onItemClick(AdapterView<?> av, View v, int arg2, long arg3){
                        mBluetoothAdapter.cancelDiscovery();

                        String info=((TextView) v).getText().toString();
                        if(!info.equals(getResources().getText(R.string.none_found).toString())){
                            String address = info.substring(info.length()-17);
                            connectDevice(address);
                        }
                    }
                };
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if(device.getBondState()!=BluetoothDevice.BOND_BONDED &&
                        mNewDevicesArrayAdapter.getPosition(device.getName() + "\n"+device.getAddress())!=-1){
                    mNewDevicesArrayAdapter.add(device.getName()+"\n"+device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                setProgressBarIndeterminateVisibility(false);
                if(!chat.isShown())
                    setStatus(R.string.select_device);
                    findViewById(R.id.button_scan).setVisibility(View.VISIBLE);
                    if(mNewDevicesArrayAdapter.getCount()==0){
                        String noDevices = getResources().getText(R.string.none_found).toString();
                        mNewDevicesArrayAdapter.add(noDevices);
                }
            }
        }
    };
    @Override
    public synchronized void onResume(){
        super.onResume();
        if(D) Log.e(TAG,"+ ON RESUME +");
        if(!mBluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }else {
            if(mChatService==null)
                setupChat();
        }
        if (mChatService!=null){
            if (mChatService.getState()==BluetoothChatService.STATE_NONE){
                mChatService.start();
            }
        }
    }
    private void setupChat(){
        Log.d(TAG,"setupChat()");
        Messages=new ArrayList<>();
        mConversationArrayAdapter=new Message_array_adapter(this,0,Messages);
        mConversationView =(ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        mOutEditText=(EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        mSendButton=(ImageButton) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new View.OnClickListener() {
             public void onClick(View v) {
                TextView view=(TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });
        paired();

        mChatService = new BluetoothChatService(this,mHandler);

        mOutStringBuffer = new StringBuffer("");

    }
    @Override
    public synchronized void onPause(){
        super.onPause();
        if(D) Log.e(TAG,"- ON PAUSE -");
    }

    @Override
    public void onStop(){
        super.onStop();
        if(D) Log.e(TAG,"-ON STOP-");
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if(mBluetoothAdapter!=null){
            mBluetoothAdapter.cancelDiscovery();
        }
        this.unregisterReceiver(mReceiver);
        if(mChatService !=null) mChatService.stop();
        if(D) Log.e(TAG,"--- ON DESTROY ---");
    }

    private void ensureDiscoverable(){
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE){
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
            startActivity(discoverableIntent);
        }
    }
    private void sendMessage(String message){
        if(mChatService.getState() != BluetoothChatService.STATE_CONNECTED){
            Toast.makeText(this,R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        if(message.length()>0){
            byte[] send=message.getBytes();
            mChatService.write(send);
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }
    private TextView.OnEditorActionListener mWriteListener=
            new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView view, int actionid, KeyEvent event) {
                    if (actionid== EditorInfo.IME_NULL && event.getAction()==KeyEvent.ACTION_UP){
                        String message = view.getText().toString();
                        sendMessage(message);
                    }
                    if(D) Log.i(TAG,"END on Editor Action");
                    return true;
                }
            };
    private final void setStatus(int resId){
        final ActionBar actionBar=getActionBar();
        actionBar.setSubtitle(resId);
    }
    private final void setStatus(CharSequence subTitle){
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(subTitle);
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler(){
    @SuppressLint("StringFormatInvalid")
    @Override
        public void handleMessage(Message msg){
        switch (msg.what){
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG,"MESSAGE_STATE_CHANGE: "+msg.arg1);
                switch (msg.arg1){
                    case BluetoothChatService.STATE_CONNECTED:
                        setStatus(getString(R.string.title_connected_to,mConnectedDeviceName));
                        chatVisible(true);
                        break;
                    case BluetoothChatService.STATE_CONNECTING:
                            setStatus(getString(R.string.title_connecting));
                            chatVisible(true);
                    case BluetoothChatService.STATE_LISTEN:
                    case BluetoothChatService.STATE_NONE:
                        if(chat.isShown())
                        setStatus(R.string.title_not_connected);
                        else setStatus(R.string.scanning);
                                break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf=(byte[]) msg.obj;

                String writeMessage = new String(writeBuf);
                cMessage mssg= new cMessage(Calendar.getInstance().getTime(),writeMessage,1,mConnectedDeviceAddress);
                sql.addMessage(mssg);
                break;
            case MESSAGE_READ:
                byte[] readBuf=(byte[]) msg.obj;

                String readMessage = new String(readBuf);
                cMessage ms= new cMessage(Calendar.getInstance().getTime(),readMessage,0,mConnectedDeviceAddress);
                sql.addMessage(ms);
                break;
            case MESSAGE_DEVICE_NAME:
                mConnectedDeviceName=msg.getData().getString(DEVICE_NAME);
                mConnectedDeviceAddress=msg.getData().getString(DEVICE_ADRESS);
                Toast.makeText(getApplicationContext(), "Connected to "
                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                chatVisible(true);
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(),msg.getData().getString(TOAST),
                        Toast.LENGTH_SHORT).show();
                break;
        }
    }
    };
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if(D) Log.d(TAG,"onActivityResult "+resultCode);
        if (requestCode==REQUEST_ENABLE_BT){
            if(resultCode==Activity.RESULT_OK){
                setupChat();
            }
        }
    }
    private void connectDevice(String address){
        BluetoothDevice device=mBluetoothAdapter.getRemoteDevice(address);
        mChatService.connect(device,secure);
        mConnectedDeviceAddress=address;
        chatVisible(true);
    }
    private void chatVisible(boolean Visible){
        if(Visible=true){
            if(mBluetoothAdapter!=null){
                mBluetoothAdapter.cancelDiscovery();
            }
            chat.setVisibility(View.VISIBLE);
            dev.setVisibility(View.GONE);
            mConversationArrayAdapter.clear();
            mConversationArrayAdapter.addAll(sql.getMessages(mConnectedDeviceAddress));
        }else {
            chat.setVisibility(View.GONE);
            dev.setVisibility(View.VISIBLE);
            doDiscovery();
        }
    }
    @Override
    public void onBackPressed(){
        if(chat.isShown()){chatVisible((false));}
        else super.onBackPressed();
    }
    @Override
    public boolean onCreateOptionsMenu (Menu menu){
        MenuInflater inflater=getMenuInflater();
        inflater.inflate(R.menu.option_menu,menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        Intent serverIntent = null;
        switch (item.getItemId()){
            case R.id.secure_connect_scan:
                secure=true;
                chatVisible(false);
                return true;
            case R.id.insecure_connect_scan:
                secure=false;
                chatVisible(false);
                return true;
            case R.id.discoverable:
                ensureDiscoverable();
                return true;
        }
        return false;
    }

}


