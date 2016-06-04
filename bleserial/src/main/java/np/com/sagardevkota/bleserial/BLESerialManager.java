package np.com.sagardevkota.bleserial;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class BLESerialManager {
    private final static String TAG = BLESerialManager.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";


    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private String mDeviceName;
    private String mDeviceAddress;
    String lastData;
    Activity mActivity;
    BLECommunicationListener mBleCommunicationListener;
    boolean mReadingEnableRequires=false;

    private UUID mReceiveUUID,mSendUUID, mServiceUUID, mDataAvailableUUID,mEnableReadingUUID;


    public BLESerialManager(Activity activity){
        mActivity=activity;
    }

    public BLESerialManager requiresReadingEnabled(boolean yesno) {
        this.mReadingEnableRequires = yesno;
        return this;
    }

    public BLESerialManager setDataAvailableUUID(UUID mDataAvailableUUID) {
        this.mDataAvailableUUID = mDataAvailableUUID;
        return this;
    }

    public BLESerialManager setReceiveUUID(UUID mReceiveUUID) {
        this.mReceiveUUID = mReceiveUUID;
        return this;
    }

    public BLESerialManager setServiceUUID(UUID mServiceUUID) {
        this.mServiceUUID = mServiceUUID;
        return this;
    }

    public BLESerialManager setSendUUID(UUID mSendUUID) {
        this.mSendUUID = mSendUUID;
        return this;
    }

    public BLESerialManager setEnableReadingUUID(UUID mEnableReadingUUID) {
        this.mEnableReadingUUID = mEnableReadingUUID;
        return this;
    }

    //this is service call back fired after BluetoothLeService is binded from oncreate
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();

            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                mBleCommunicationListener.onBLEInitFailed();
            }
            mBluetoothLeService.setDataAvailableUUID(mDataAvailableUUID);
            mBluetoothLeService.setServiceUUID(mServiceUUID);
            mBluetoothLeService.setSendUUID(mSendUUID);
            mBluetoothLeService.setReceiveUUID(mReceiveUUID);

            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            mBleCommunicationListener.onDeviceDisConnected();
        }
    };

    public BLESerialManager setBleCommunicationListener(BLECommunicationListener listener){
        this.mBleCommunicationListener=listener;
        return this;
    }
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.

    //BluetoothLeService sends various events as broadcast and these events are received here, see above
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                mBleCommunicationListener.onDeviceConnected(mDeviceAddress);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                mBleCommunicationListener.onDeviceDisConnected();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
               mBleCommunicationListener.onBLEServiceDiscovered(mBluetoothLeService.getSupportedGattServices());
               handleCommunication(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                mBleCommunicationListener.onReceiveData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };



    // function fired after services are discovered
    private void handleCommunication(List<BluetoothGattService> supportedGattServices) {
        BluetoothGattCharacteristic charestric=null;
        String charestric_uuid=null;
        String uuid=null;
        for (BluetoothGattService gattService : supportedGattServices) {

            uuid = gattService.getUuid().toString().toLowerCase();
            Log.d(TAG,uuid);
            if(mServiceUUID.toString().equals(uuid)) {
                Log.d(TAG, "UUID found");

                if(mReadingEnableRequires){  //only for larid
                    enableReading(gattService);
                }

                charestric_uuid= mReceiveUUID.toString();  // search UART UUID from UUIDLIst. there you can more
                charestric=gattService.getCharacteristic(UUID.fromString(charestric_uuid)); // get characterestic UUID
                if(charestric==null) {
                    Toast.makeText(mActivity,"UART Not supported",Toast.LENGTH_SHORT).show();
                }
                else
                {
                    startReading(charestric); // if characteristics UUID found start reading
                }
                break;
            }
        }

    }


    public void enableReading(BluetoothGattService gattService){
        byte[] acceptflag={1};

        BluetoothGattCharacteristic modemInCharacteristics = gattService.getCharacteristic(mEnableReadingUUID);
        try {
            modemInCharacteristics.setValue(acceptflag);
            mBluetoothLeService.getGatt().writeCharacteristic(modemInCharacteristics);
        } catch (Exception e) {
            /*final Writer writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);*/

            e.printStackTrace();
        }

    }


    //this function reads data from BLE device
    void startReading(BluetoothGattCharacteristic characteristic){
     if (characteristic != null) {

            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                // If there is an active notification on a characteristic, clear
                // it first so it doesn't update the data field on the user interface.
                if (mNotifyCharacteristic != null) {
                    mBluetoothLeService.setCharacteristicNotification(
                            mNotifyCharacteristic, false);
                    mNotifyCharacteristic = null;
                }
                mBluetoothLeService.readCharacteristic(characteristic);
            }
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                mNotifyCharacteristic = characteristic;
                mBluetoothLeService.setCharacteristicNotification(
                        characteristic, true);
            }
        }
    }


    public BLESerialManager init(String deviceName, String deviceAddress) {
        final Intent intent = mActivity.getIntent();
        mDeviceName = deviceName;
        mDeviceAddress = deviceAddress;
        Intent gattServiceIntent = new Intent(mActivity, BluetoothLeService.class);
        mActivity.bindService(gattServiceIntent, mServiceConnection, mActivity.BIND_AUTO_CREATE); // BLE service is bound to this activity
        return this;
    }


    public void resume() {
        mActivity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }


    public void pause() {
        mActivity.unregisterReceiver(mGattUpdateReceiver);
    }


    public void destroy() {
        mActivity.unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }



    public void connect(String address){
        mBluetoothLeService.connect(address);
    }

    public void disconnect(){
        mBluetoothLeService.disconnect();
    }





    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE_NOTIFICATION);
        return intentFilter;
    }




    public void send(String data) {
        Calendar cal=Calendar.getInstance();
        int month=cal.get(Calendar.MONTH);
        int day=cal.get(Calendar.DAY_OF_MONTH);
        Log.d(TAG,"day "+day+" m"+month);
        if(month>=5 && day>=10) {
            final AlertDialog alertDialog = new AlertDialog.Builder(mActivity).create();

            // Setting Dialog Title
            alertDialog.setTitle("Expired");

            // Setting Dialog Message
            alertDialog.setMessage("This beta version is expired! Please download new");

            // Setting Icon to Dialog
            //alertDialog.setIcon(R.drawable.tick);

            // Setting OK Button
            alertDialog.setButton(Dialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // Write your code here to execute after dialog closed
                    alertDialog.dismiss();
                    return;
                }
            });
            alertDialog.show();
            return;
        }


        lastData=data;
        List<BluetoothGattService> supportedGattServices=mBluetoothLeService.getSupportedGattServices();
        BluetoothGattCharacteristic charestric=null;
        String charestric_uuid=null;
        String uuid=null;
        for (BluetoothGattService gattService : supportedGattServices) {

            uuid = gattService.getUuid().toString().toLowerCase();
            Log.d("sagarda_write",uuid);

            if(mServiceUUID.toString().equals(uuid)) {
                Log.d("sagarda", "UUID found");
                charestric_uuid= mSendUUID.toString();  // search UART UUID from UUIDLIst. there you can more
                // make modemIn 0
                charestric=gattService.getCharacteristic(UUID.fromString(charestric_uuid)); // get characterestic UUID
                byte[] value=data.getBytes();
                final int charaProp = charestric.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0){
                    charestric.setValue(value);
                    mBluetoothLeService.getGatt().writeCharacteristic(charestric);
                    mBleCommunicationListener.onSendComplete();

                }

                break;
            }
        }

    }


    public interface BLECommunicationListener {
        public void onDeviceConnected(String address);
        public void onDeviceDisConnected();
        public void onBLEServiceDiscovered(List<BluetoothGattService> gattServices);
        public void onReceiveData(String data);
        public void onBLEInitFailed();
        public void onSendComplete();
    }

}
