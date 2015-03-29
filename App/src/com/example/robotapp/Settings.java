package com.example.robotapp;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import android.support.v7.app.ActionBarActivity;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class Settings extends ActionBarActivity {

	private final static int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_SELECT_DEVICE = 2;

	// seems to be a working UUID for the HC-05 Bluetooth module
	private final static UUID APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private String cameraIPAddress;
	private String cameraPort;
	private int movementUpdateSpeed;
	private int cameraHeight;
	private int cameraWidth;
	private boolean isCameraEnabled;

	private ApplicationState appState;
	private BluetoothStreamManager btStreamManager;
	private SharedPreferences sharedPref;
	
	TextView updateSpeedTextView;
	TextView cameraIPAddressTextView;
	TextView cameraPortTextView;
	
	TextView cameraHeightTextView;
	TextView cameraWidthTextView;
	
	CheckBox isCameraEnabledCheckBox;
	
	
	private BluetoothAdapter bluetoothAdapter;

	
    private ArrayList<BluetoothDevice> bluetoothDevices;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice deviceInUse;
    
    private OutputStream bluetoothOutput;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		appState = (ApplicationState)this.getApplication();
		btStreamManager = appState.getStateManager();
		btStreamManager.setCurrentActivity(this);
		btStreamManager.initializeBluetoothAdapter();
		sharedPref = getApplicationContext().getSharedPreferences("RobotPreferences", 0);
			
		cameraIPAddress = sharedPref.getString("CAMERA_IP_ADDRESS", "127.0.0.1");
		cameraPort = sharedPref.getString("CAMERA_PORT", "8080");
		movementUpdateSpeed = sharedPref.getInt("BT_UPDATE_SPEED", 115);
		cameraHeight = sharedPref.getInt("CAMERA_HEIGHT", 240);
		cameraWidth = sharedPref.getInt("CAMERA_WIDTH", 320);
		isCameraEnabled = sharedPref.getBoolean("IS_CAMERA_ENABLED", true);
		
		
		cameraIPAddressTextView = (TextView) findViewById(R.id.cameraIPText);
		cameraPortTextView = (TextView) findViewById(R.id.cameraPortText);
		updateSpeedTextView = (TextView) findViewById(R.id.BTupdateSpeedText);
		cameraHeightTextView = (TextView) findViewById(R.id.cameraHeightText);
		cameraWidthTextView = (TextView) findViewById(R.id.cameraWidthText);
		isCameraEnabledCheckBox = (CheckBox) findViewById(R.id.isCameraEnabledCheckBox);
		
		cameraIPAddressTextView.setText(cameraIPAddress);
		cameraPortTextView.setText(String.valueOf(cameraPort));
		updateSpeedTextView.setText(String.valueOf(movementUpdateSpeed));
		cameraHeightTextView.setText(String.valueOf(cameraHeight));
		cameraWidthTextView.setText(String.valueOf(cameraWidth));
		isCameraEnabledCheckBox.setChecked(isCameraEnabled);
		
		bluetoothDevices = new ArrayList<BluetoothDevice>();
		initiateBluetooth();		
		
	}

	@Override
	protected void onResume(){
		super.onResume();
		System.out.println("changed activity");
		btStreamManager.setCurrentActivity(this);
	}
	
	protected void onStop() {
		super.onStop();
		
		SharedPreferences.Editor editor = sharedPref.edit();
		
		editor.putString("CAMERA_IP_ADDRESS", cameraIPAddressTextView.getText().toString());
		editor.putString("CAMERA_PORT", cameraPortTextView.getText().toString());
		editor.putInt("BT_UPDATE_SPEED", Integer.parseInt(updateSpeedTextView.getText().toString()));
		editor.putInt("CAMERA_HEIGHT", Integer.parseInt(cameraHeightTextView.getText().toString()));
		editor.putInt("CAMERA_WIDTH", Integer.parseInt(cameraWidthTextView.getText().toString()));
		editor.putBoolean("IS_CAMERA_ENABLED", isCameraEnabledCheckBox.isChecked());
		
		com.camera.simplemjpeg.MjpegView.setImageSize(Integer.parseInt(cameraWidthTextView.getText().toString()),  Integer.parseInt(cameraHeightTextView.getText().toString()));
		
		editor.commit();
	}
	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.settings, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

        case REQUEST_SELECT_DEVICE:
        	//When the DeviceListActivity return, with the selected device address
            if (resultCode == Activity.RESULT_OK && data != null) {
                String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
               // mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);
               btStreamManager.connectToDevice(deviceAddress);
               
               
                //Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                //((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
               // mService.connect(deviceAddress);
                            

            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

            } else {
                // User did not enable Bluetooth or an error occurred
               // Log.d(TAG, "BT not enabled");
                Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                finish();
            }
            break;
        default:
            break;
        }
    }
	
	public void switchToFeed(View view)
	{
	/*	if (btStreamManager.getInputStream() == null)
		{
			Toast.makeText(getApplicationContext(), "You must have a valid bluetooth device paired to continue!", Toast.LENGTH_LONG).show();
			return;
		}
		Intent intent = new Intent(getApplicationContext(), Feed.class);
		intent.putExtra("BT_UPDATE_SPEED", Integer.parseInt(updateSpeedTextView.getText().toString()));
		intent.putExtra("CAMERA_IP_ADDRESS", cameraIPAddressTextView.getText().toString());
		intent.putExtra("CAMERA_PORT", cameraPortTextView.getText().toString());
		intent.putExtra("IS_CAMERA_ENABLED", isCameraEnabledCheckBox.isChecked());

		startActivity(intent);*/
		String currentState = btStreamManager.getConnectionState();
		if (currentState == "connected")
		{
			Intent intent = new Intent(getApplicationContext(), Feed.class);
			intent.putExtra("BT_UPDATE_SPEED", Integer.parseInt(updateSpeedTextView.getText().toString()));
			intent.putExtra("CAMERA_IP_ADDRESS", cameraIPAddressTextView.getText().toString());
			intent.putExtra("CAMERA_PORT", cameraPortTextView.getText().toString());
			intent.putExtra("IS_CAMERA_ENABLED", isCameraEnabledCheckBox.isChecked());
			
			startActivity(intent);
		}
		else if (currentState == "connecting")
		{
			Toast.makeText(this, "Still connecting! Please wait.", Toast.LENGTH_LONG).show();
		}
		else if (currentState == "disconnected")
		{
			Toast.makeText(this, "Connect to robot before moving on.", Toast.LENGTH_LONG).show();
		}
		
	}
	
	public void checForAvailableBLEDevices(View view)
	{
		
		Intent newIntent = new Intent(this, DeviceListActivity.class);
		startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);

	}
	
	protected void onDestroy() {
		super.onDestroy();
	}	
	
	private void connectToDevice(String address)
	{
		
	}
	
	private void initiateBluetooth() {
		try {
			
		System.out.println("Initiating bluetooth...");
	
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		System.out.println("checking if adapter is null...");
		
		if (bluetoothAdapter == null)
		{
			Toast.makeText(this, "Device does not support bluetooth :(", Toast.LENGTH_LONG).show();
			System.exit(0);
		}
		
		System.out.println("checking if adapter is enabled...");

		
		if (!bluetoothAdapter.isEnabled()) {
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		    //Feed.this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		}
		catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
		
	}
	
	
}
