package emg.backend;
import android.content.Context;
import android.util.Log;


public class DataProtocol implements IBluetoothDataListener {

	ITransformListener transformListener;
	
	private String TAG = "DataProtocol";
	
	private static byte CONTROL_BYTE = (byte)255;	//deliminates chunks of data
	private static int CONNECT_RETRIES = 3;
	
	private enum ExpectedNext{
		CONTROL,
		GAIN,
		DATA,
		FAILURE
	};
	
	private byte[] gains;
	private byte[][] data;
	
	private int numChannels;
	private int channelIndex, dataIndex;
	private ExpectedNext expected;
	
	private Bluetooth bluetooth;
	private String address;
	private byte channels;
	
	public DataProtocol(Context context, ITransformListener transformListener, byte channels, String address)
	{
		this.channels = channels;
		this.transformListener = transformListener;
		this.numChannels = Utils.getNumChannels(channels);
		this.gains = new byte[this.numChannels];
		this.data = new byte[this.numChannels][Constants.NUM_FOURIER_BINS];
		
		this.channelIndex = 0;
		this.dataIndex = 0;
		this.expected = ExpectedNext.CONTROL;
		
		try {
			bluetooth = new Bluetooth(context, this);
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
		
		this.address = address;
	}
	
	/*
	 * Starts up the bluetooth connection, and waits for the ACK bit before returning whether it makes sense
	 */
	
	public boolean Start()
	{
		for(int retry = 0;retry < CONNECT_RETRIES;retry++)
		{
			if(retry > 0){
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Log.e(TAG, "wow...we even fail to sleep...:" + e.toString());
				}
			}
			
			boolean connected = this.bluetooth.Connect(this.address, false);
			if(!connected)
			{
				Log.e(TAG, "Bluetooth failed to connect");
				continue;
			}
			
			byte toSend = (byte)((1<<7) | this.channels);
			this.bluetooth.sendByte(toSend);
			byte[] buffer = this.bluetooth.readDirectly(1, 1000);
			if(buffer == null)
			{
				Log.e(TAG, "Buffer is null");
				this.StopAndDisconnect();
				continue;
			}
			else if(buffer[0] == toSend)
			{
				this.bluetooth.startReading();
				return true;
			}
			else
			{
				Log.e(TAG, "Failed to start - received " + Byte.toString(buffer[0]) + " when " + Byte.toString(this.channels) + " was expected (note- not unsigned cause java doesn't do that");
				this.StopAndDisconnect();
			}
		}

		this.StopAndDisconnect();
		return false;
	}
	
	/*
	public boolean Start()
	{
		boolean connected = false;
		
		for(int retry = 0;retry < CONNECT_RETRIES;retry++)
		{
			connected = this.bluetooth.Connect(this.address, false);
			if(!connected)
			{
				Log.e(TAG, "Bluetooth failed to connect");
			}
			else
			{
				break;
			}
		}
		
		if(!connected)
		{
			Log.e(TAG, "Failed to connect after all tries");
			return false;
		}
		
		for(int retry = 0;retry < CONNECT_RETRIES;retry++)
		{
			byte toSend = (byte)((1<<7) | this.channels);
			this.bluetooth.sendByte(toSend);
			byte[] buffer = this.bluetooth.readDirectly(1, 1000);
			if(buffer == null)
			{
				Log.e(TAG, "Buffer is null");
				this.stopMicrocontroller();
			}
			else if(buffer[0] == toSend)
			{
				this.bluetooth.startReading();
				return true;
			}
			else if(buffer[0] == CONTROL_BYTE)
			{
				Log.d(TAG, "Got a control byte when expecting an ACK");
				buffer = this.bluetooth.readDirectly(1, 1000);
				if(buffer[0] == toSend)
				{
					Log.d(TAG, "After control byte got correct ACK");
					this.bluetooth.startReading();
					return true;
					
				}
			}
			else
			{
				Log.e(TAG, "Failed to start - received " + Byte.toString(buffer[0]) + " when " + Byte.toString(this.channels) + " was expected (note- not unsigned cause java doesn't do that");
				this.stopMicrocontroller();
				//this.bluetooth.readDirectly(1, 1000);	//if we send Start() when it's already running, we get 2 control bytes.  Swallow them and any other garbage here
				//Log.d(TAG, "After reading buffer2");
			}
		}

		this.StopAndDisconnect();
		return false;
	}*/
	
	public void StopAndDisconnect()
	{
		this.stopMicrocontroller();
		this.bluetooth.Disconnect();
	}
	
	public void stopMicrocontroller()
	{
		this.bluetooth.sendByte((byte)0);
	}
	
	/* multiple gains by data, and return everything as one big happy array
	 */
	public int[] getDataArray()
	{
		int[] result = new int[this.numChannels * Constants.NUM_FOURIER_BINS];
		int x =0;
		for(int i=0;i<this.numChannels;i++)
		{
			for(int j=0;j< Constants.NUM_FOURIER_BINS;j++)
			{
				result[x] = (0xFF & this.data[i][j]) * (0xFF & this.gains[i]);	//convert from unsigned to signed
				x++;
			}
		}
		return result;
	}
	
	//int count = 0;
	public void addByte(byte b) {
		//count++;
		//if(count> 100)
		//	this.Stop();
		
		switch(this.expected)
		{
		case FAILURE:
			if(b == CONTROL_BYTE)
			{
				Log.e(TAG, "From failed state - received control byte");
				this.expected = ExpectedNext.GAIN;
				this.channelIndex = 0;
				this.dataIndex = 0;
			}
			break;
		case CONTROL:
			if(b != CONTROL_BYTE)
			{
				Log.e(TAG, "Expected Control byte, instead received " + Byte.toString(b));
				this.expected = ExpectedNext.FAILURE;
			}
			if(this.channelIndex > 0)	//true if it's not the first loop
			{
				this.transformListener.addData(this.getDataArray());
				this.expected = ExpectedNext.GAIN;
			}
			
			//reset everything for the next batch of data
			this.channelIndex = 0;
			this.dataIndex = 0;
			break;
		
		case GAIN:
			if(b == CONTROL_BYTE)
			{
				Log.e(TAG, "Expected gain, received control byte for channel " + Integer.toString(this.channelIndex));
			}
			this.gains[this.channelIndex] = b;
			this.expected = ExpectedNext.DATA;
			break;
			
		case DATA:
			if(b == CONTROL_BYTE)
			{
				Log.e(TAG, "Expected Data, received control byte for channel " + Integer.toString(this.channelIndex));
			}
			this.data[this.channelIndex][this.dataIndex] = b;
			this.dataIndex++;
			
			if(this.dataIndex == Constants.NUM_FOURIER_BINS)
			{
				this.dataIndex = 0;
				this.channelIndex++;
				if(this.channelIndex == this.numChannels)
				{
					this.expected = ExpectedNext.CONTROL;
				}
				else 
				{
					this.expected = ExpectedNext.GAIN;
				}
			}
			break;
		}
	}

}
