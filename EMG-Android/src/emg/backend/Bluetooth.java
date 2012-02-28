package emg.backend;
import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Bluetooth {
	//required to start the bluetooth connection
	private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	//class name for logging
	private String TAG = "Bluetooth";
	
	//bluetooth communication stuff
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothSocket socket;
	
	//bluetooth input and output stream that we can read and write to/from
	private InputStream inStream;
	private OutputStream outStream;
		
	private IBluetoothDataListener dataListener;
	
	private ConnectedThread connectedThread;
	
	public Bluetooth(Context context, IBluetoothDataListener dataListener) throws Exception
	{
		this.dataListener = dataListener;
		
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(bluetoothAdapter == null)
			throw new Exception("Bluetooth not supported on this device");
		
		if(!bluetoothAdapter.isEnabled())
		{
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			context.startActivity(enableBtIntent);
		}
	}

	public boolean Connect(String address)
	{
		return this.Connect(address, true);
	}
	
	/*
	 * Connect to the device at the provided address
	 * Returns whether this was successful, prints stuff to log
	 * if it failed
	 */
	public boolean Connect(String address, boolean startReadThread)
	{
		if(this.socket != null)
		{
			Log.d(TAG, "Aborting connect - socket is not null");
			return false;
		}
		
		if(!BluetoothAdapter.checkBluetoothAddress(address))
		{
			Log.d(TAG, "Aborting connect - checkBluetoothAddress() failed");
			return false;
		}
		
		BluetoothDevice device = this.bluetoothAdapter.getRemoteDevice(address);
		
		if(device == null)
		{
			Log.d(TAG, "Connect failed - device is null");
			return false;
		}
		
		try
		{
			this.socket = device.createRfcommSocketToServiceRecord(this.SPP_UUID);
		} catch(IOException e) {
			Log.d(TAG, "failed to createRfcommSocketToServiceRecord(): " + e.toString());
			return false;
		}
			
		try
		{
			this.socket.connect();
		} catch (IOException e){
			Log.d(TAG, "failed to connect to socket: " + e.toString());
			try
			{
				this.socket.close();
			} catch (Exception e2){}
			
			return false;
		}
		
		try
		{
			this.inStream = this.socket.getInputStream();
		} catch (IOException e)
		{
			Log.d(TAG, "Failed to get input stream from socket: " + e.toString());
			try
			{
				this.socket.close();
			} catch (Exception e2){}
			
			return false;
		}
		
		try
		{
			this.outStream = this.socket.getOutputStream();
		} catch (IOException e)
		{
			Log.d(TAG, "Failed to get output stream from socket: " + e.toString());
			try
			{
				this.socket.close();
			} catch (Exception e2){}
			
			return false;
		}
		
		Log.d(TAG, "Connect succeeded");
		this.connectedThread = new ConnectedThread(this.inStream);
		
		if(startReadThread)
			this.connectedThread.run();
		
		return true;
	}
	
	
	public byte[] readDirectly(int numBytes, int timeoutMs)
	{
		if(this.inStream == null || this.outStream == null)
			return null;
		if(this.connectedThread.isAlive())
			return null;
		
		byte[] result = new byte[numBytes];
		int index = 0;
		long start = System.currentTimeMillis();
		
		while(index < numBytes && (System.currentTimeMillis() - start) < timeoutMs)
		{
			try {
				byte b = (byte)this.inStream.read();
				result[index] = b;
				index++;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				
			}
		}
		
		return result;
	}

	public void startReading()
	{
		if(!this.connectedThread.isAlive())
			this.connectedThread.start();
	}
	
	/*
	 * Tell the bluetooth connection to stop, make socket, inStream and outStream all null too
	 */
	public boolean Disconnect()
	{
		if(this.connectedThread != null)
		{
			this.connectedThread.stopReading();
			try
			{
				this.connectedThread.join(1000);
			}
			catch(InterruptedException e)
			{
				Log.d(TAG, "Joining readThread threw exception: " + e.toString());
			}
		}
		
		if(socket == null)
		{
			return false;
		}
		
		try
		{
			socket.close();
		} catch(IOException e)
		{
			Log.d(TAG, "Caught exception trying to close socket: " + e.toString());
			return false;
		}
		

		this.inStream = null;
		this.outStream = null;
		this.socket = null;
		
		Log.d(TAG, "Disconnect succeeded");
		
		return true;
	}
	
	/*
	 * Theres probably a better way to check than this
	 */
	public boolean isConnected()
	{
		return socket != null;
	}
	
	public boolean sendByte(byte b)
	{
		if(this.inStream == null || this.outStream == null)
			return false;
	
		byte[] buffer = new byte[1];
		buffer[0] = b;
		
		try
		{
			this.outStream.write(buffer);
		} catch (IOException e)
		{
			return false;
		}	
		
		return true;
	}
	
	private class ConnectedThread extends Thread
	{
		//copy of stream from the bluetooth socket
		private InputStream inStream;
		
		//used to get this thread to stop from another thread without just killing it, cause thats not nice
		private boolean continueReading = true;
		
		//Name for logging
		private String TAG = "ReadThread";
		
		public ConnectedThread(InputStream inStream)
		{
			this.inStream = inStream;
		}
		
		public void stopReading()
		{
			this.continueReading = false;
		}
		
		public void run()
		{
			Log.d(TAG, "Started to run ReadThread");
			
			//create a buffer to receive stuff from
			byte[] buffer = new byte[10];
			
			//number of bytes read from the stream
			int numBytes;
			
			//continueReading gets set to false when we wanna stop the thread
			while(this.continueReading)
			{
				//get some bytes
				try
				{
					numBytes = this.inStream.read(buffer);
				} catch (IOException e){
					Log.d(TAG, "Caught IOException with message: " + e.toString());
					break;
				}
				
				//send the data off to someone who cares (and is listening)
				for(int i=0;i<numBytes;i++)
					dataListener.addByte(buffer[i]);
				
			}
		}	
	}
}
