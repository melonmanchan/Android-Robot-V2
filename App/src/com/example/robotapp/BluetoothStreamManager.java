package com.example.robotapp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.nordicsemi.nrfUARTv2.UartService;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import android.app.Service;

public class BluetoothStreamManager {
	
    private final static String TAG = BluetoothStreamManager.class.getSimpleName();

	
	// magical working UUIDs (Thanks to nordic semiconductor)
    public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

	
	// reference to current Activity
	private Activity currentActivity;
	
	private String deviceAddress;
	
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothManager bluetoothManager;
	private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;

    private int connectionState = STATE_DISCONNECTED;

    private int signalStrength;
    
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private String currentDeviceAddress;
    
	// Queue data structure to hold robot commands in byte array form
	private ConcurrentLinkedQueue<byte[]> commandQueue;
		// Seperate thread to keep running in background during the whole she-bang, running new commands if necessary. Thread is best implementation of
	// threading in Android for this use-case imo. AsyncTask too bulky, only for "short" asynchronous tasks. Don't see a need to update UI thread from here.
	// Thread seems to be the fastest implementation
	
	//Callback function to call on connection
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionState = STATE_CONNECTED;
                Log.i(TAG, "Connected to GATT server");
                // Attempts to discover services after successful connection.
                Log.i(TAG,  "Attempting to start service discovery:" +
                        bluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT");
                
	        	currentActivity.runOnUiThread(new Runnable() {
	        	    public void run() {
	        	    	
	        	       new AlertDialog.Builder(currentActivity).setTitle("Bluetooth failed!")
	        	       .setMessage("Oops! The bluetooth connection has failed. Go back to settings and initiate a new connection?").setPositiveButton("Settings", new DialogInterface.OnClickListener() {
	        	           public void onClick(DialogInterface dialog, int which) { 
	        	               // Go back to settings
	        	        	   Intent intent = new Intent(currentActivity, Settings.class);
	        	       		   currentActivity.startActivity(intent);
	        	        	   
	        	           }
	        	        })
	        	       .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	        	           public void onClick(DialogInterface dialog, int which) { 
	        	               // do nothing
	        	        	   return ;
	        	           }
	        	        })
	        	       .setIcon(android.R.drawable.ic_dialog_alert)
	        	        .show();
	        	       
	        	    }
	        	});
                
                }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
           if (status == BluetoothGatt.GATT_SUCCESS) {
        	   connectionState = STATE_CONNECTED;
            	//Log.w(TAG, "mBluetoothGatt = " + bluetoothGatt );
            	Log.i(TAG, "bluetoothGatt has discovever services");
            } else {
              //  Log.w(TAG, "onServicesDiscovered received: " + status);
            	
            	//Log.i(TAG, "onServicesDiscovered odd status = " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
           if (status == BluetoothGatt.GATT_SUCCESS) {
               // broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        	  // Log.i(TAG, "Data available: " + characteristic.toString());
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
           // broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
     	  // Log.i(TAG, "Data available: " + characteristic.toString());

        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
        	if (status == BluetoothGatt.GATT_SUCCESS)
        	{
        		//Log.i(TAG,"Write success!");
        	}
        }
        
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status)
        {
        	if (status == BluetoothGatt.GATT_SUCCESS)
        		signalStrength = rssi;
        }
    };
	
	
	public BluetoothStreamManager()
	{
		//Log.i(TAG,"BluetoothStreamManager constructor!");

		deviceAddress = "";
		//commandQueue = new ConcurrentLinkedQueue<byte[]>();
		/*workThread = new Thread()
		{
			@Override
		    public void run() {
			        Log.i(TAG, "Hello world");
				if (connectionState == STATE_CONNECTED)
				{
					
					try 
				    	{
					        		while(!Thread.interrupted())
					        		{
										if (!commandQueue.isEmpty())
										{
												Log.i(TAG, "===============================");
												byte[] msgBuffer = commandQueue.poll();
												writeData(msgBuffer);
												Log.i(TAG, "===============================");
	
										}
										
					        		}
								
						}
				        catch (Exception ex) {
				        	Log.i(TAG, "IOError!");
				        	
				        	currentActivity.runOnUiThread(new Runnable() {
				        	    public void run() {
				        	    	
				        	       new AlertDialog.Builder(currentActivity).setTitle("Bluetooth failed!")
				        	       .setMessage("Fuck! The bluetooth connection has failed. Go back to settings and initiate a new connection?").setPositiveButton("Settings", new DialogInterface.OnClickListener() {
				        	           public void onClick(DialogInterface dialog, int which) { 
				        	               // Go back to settings
				        	        	   Intent intent = new Intent(currentActivity, Settings.class);
				        	       		   currentActivity.startActivity(intent);
				        	        	   
				        	           }
				        	        })
				        	       .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				        	           public void onClick(DialogInterface dialog, int which) { 
				        	               // do nothing
				        	        	   return ;
				        	           }
				        	        })
				        	       .setIcon(android.R.drawable.ic_dialog_alert)
				        	        .show();
				        	       
				        	    }
				        	});
				        }
			}
				else {
				}
			}*/
		//};
	}
	
	
	public boolean initializeBluetoothAdapter()
	{
        if (bluetoothManager == null) {
        	bluetoothManager = (BluetoothManager) currentActivity.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.i(TAG, "Can't get manager");
                return false;
            }
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.i(TAG, "Can't get adapter");

            return false;
        }

        return true;
		
	}
	
	public boolean connectToDevice(String address)
	{
		if (bluetoothAdapter == null || address ==null){
			showToast("BluetoothAdapter is not initialized or address is not legit");
			return false;
		}
		
	/*	if (currentDeviceAddress != null && address.equalsIgnoreCase(currentDeviceAddress) && bluetoothGatt != null)
		{
			
		}*/
		
		BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
		
		if (device == null)
		{
			showToast("Device not found at address: " + address);
			return false;
		}

		showToast("Connecting...");

		bluetoothGatt = device.connectGatt(currentActivity, false, gattCallback);
		
		currentDeviceAddress = address;
		
		return true;
	}
	
	public int getSignalStrength()
	{
		if (getConnectionState() == "connected")
		{
			if (bluetoothGatt.readRemoteRssi());
				return signalStrength;
		
		}
		
		return 0;
	}
	
	public String getConnectionState()
	{
		switch(connectionState)
		{
		case STATE_DISCONNECTED:
			return "disconnected";
		case STATE_CONNECTING:
			return "connecting";
		case STATE_CONNECTED:
			return "connected";			
		}
		return "null";
	}
	
	public void disconnectFromDevice()
	{
		bluetoothGatt.disconnect();
	}
	
	public void writeData(byte[] data)
	{
		if (connectionState == STATE_CONNECTED)
		{
			BluetoothGattService rxService = bluetoothGatt.getService(RX_SERVICE_UUID);
			
			if (rxService == null)
			{
				//Log.i(TAG, "rxService is null at streammanager!");
				return;
			}
			
			BluetoothGattCharacteristic RxChar = rxService.getCharacteristic(RX_CHAR_UUID);
			
			if (RxChar == null)
			{
				//Log.i(TAG, "RxChar is null at streammanager!");
				return;
			}
			RxChar.setValue(data);
			bluetoothGatt.writeCharacteristic(RxChar);
		}
	}
	
	private void showToast(final String message)
	{
		Toast.makeText(currentActivity, message, Toast.LENGTH_LONG).show();
	}
	
	

	public String getDeviceAddress()
	{
		return deviceAddress;
	}
	
	public void setDeviceAddress(String deviceAddress)
	{
		this.deviceAddress = deviceAddress;
	}
	
	public ConcurrentLinkedQueue<byte[]> getCommandStack() 
	{
		return commandQueue;
	}

	public void setCommandStack(ConcurrentLinkedQueue<byte[]> commandQueue) 
	{
		this.commandQueue = commandQueue;
	}

	public void push(byte[] command) 
	{
		commandQueue.add(command);
	}
	
	public byte[] peek() 
	{
		return commandQueue.peek();
	}

	public void setCurrentActivity(Activity activity) 
	{
		this.currentActivity = activity;
	}

}
